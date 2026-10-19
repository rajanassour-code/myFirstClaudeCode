package com.liveassist.sdk

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.util.UUID

/**
 * One WebRTC call with the operator. The customer is always the callee: the operator sends the offer and
 * creates the "chat" data channel (sdk/PROTOCOL.md §2). Callbacks run on the main thread.
 */
internal class CallSession(private val context: Context) {
    var onSignal: ((JSONObject) -> Unit)? = null
    var onData: ((JSONObject) -> Unit)? = null
    var onDataOpen: (() -> Unit)? = null
    var onConnectionState: ((String) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    val egl: EglBase = EglBase.create()
    val localView = SurfaceViewRenderer(context)
    val remoteView = SurfaceViewRenderer(context)

    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var capturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var localVideo: VideoTrack? = null
    private var localAudio: AudioTrack? = null
    private var remoteVideo: VideoTrack? = null
    private val pendingIce = ArrayList<IceCandidate>()
    private val chunks = HashMap<String, Array<String?>>()
    private var audioManager: AudioManager? = null
    private var previousAudioMode = AudioManager.MODE_NORMAL

    fun start(iceServers: JSONArray) {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions())
        val f = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f

        localView.init(egl.eglBaseContext, null)
        localView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        localView.setMirror(true)
        localView.setZOrderMediaOverlay(true)
        remoteView.init(egl.eglBaseContext, null)
        remoteView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)

        val servers = ArrayList<PeerConnection.IceServer>()
        for (i in 0 until iceServers.length()) {
            val e = iceServers.optJSONObject(i) ?: continue
            val urls = ArrayList<String>()
            when (val u = e.opt("urls")) { is String -> urls.add(u); is JSONArray -> for (j in 0 until u.length()) urls.add(u.getString(j)) }
            if (urls.isEmpty()) continue
            val b = PeerConnection.IceServer.builder(urls)
            e.optString("username").takeIf { it.isNotEmpty() }?.let { b.setUsername(it) }
            e.optString("credential").takeIf { it.isNotEmpty() }?.let { b.setPassword(it) }
            servers.add(b.createIceServer())
        }
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        }
        val conn = f.createPeerConnection(config, observer) ?: return
        pc = conn

        // Speakerphone, voice-call audio mode
        audioManager = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).also {
            previousAudioMode = it.mode
            it.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION") it.isSpeakerphoneOn = true
        }

        val audio = f.createAudioTrack("audio0", f.createAudioSource(MediaConstraints()))
        conn.addTrack(audio, listOf("stream0"))
        localAudio = audio

        val source = f.createVideoSource(false)
        val video = f.createVideoTrack("video0", source)
        conn.addTrack(video, listOf("stream0"))
        video.addSink(localView)
        localVideo = video
        val enumerator = Camera2Enumerator(context)
        val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull()
        if (name != null) {
            val cap = enumerator.createCapturer(name, null)
            val helper = SurfaceTextureHelper.create("LiveAssistCapture", egl.eglBaseContext)
            cap.initialize(helper, context, source.capturerObserver)
            runCatching { cap.startCapture(1280, 720, 30) }
            capturer = cap; surfaceHelper = helper
        }
    }

    fun switchCamera() { capturer?.switchCamera(null) }
    fun toggleMic(): Boolean { val t = localAudio ?: return false; t.setEnabled(!t.enabled()); return t.enabled() }
    fun toggleCamera(): Boolean { val t = localVideo ?: return false; t.setEnabled(!t.enabled()); return t.enabled() }

    fun close() {
        runCatching { capturer?.stopCapture() }
        capturer?.dispose(); capturer = null
        surfaceHelper?.dispose(); surfaceHelper = null
        dataChannel?.unregisterObserver(); dataChannel?.close(); dataChannel = null
        remoteVideo?.removeSink(remoteView); localVideo?.removeSink(localView)
        pc?.close(); pc = null
        factory?.dispose(); factory = null
        localView.release(); remoteView.release()
        egl.release()
        audioManager?.let { @Suppress("DEPRECATION") it.isSpeakerphoneOn = false; it.mode = previousAudioMode }
        pendingIce.clear(); chunks.clear()
    }

    // ---- Signaling in ----

    fun handleSignal(data: JSONObject) {
        val conn = pc ?: return
        val sdp = data.optJSONObject("sdp")
        val ice = data.optJSONObject("ice")
        if (sdp != null) {
            val type = when (sdp.optString("type")) { "offer" -> SessionDescription.Type.OFFER; "answer" -> SessionDescription.Type.ANSWER; else -> SessionDescription.Type.PRANSWER }
            conn.setRemoteDescription(object : SimpleSdp() {
                override fun onSetSuccess() {
                    main.post {
                        pendingIce.forEach { conn.addIceCandidate(it) }; pendingIce.clear()
                        if (type == SessionDescription.Type.OFFER) answer()
                    }
                }
            }, SessionDescription(type, sdp.optString("sdp")))
        } else if (ice != null) {
            val cand = ice.optString("candidate"); if (cand.isEmpty()) return
            val c = IceCandidate(ice.optString("sdpMid", "0"), ice.optInt("sdpMLineIndex", 0), cand)
            if (conn.remoteDescription == null) pendingIce.add(c) else conn.addIceCandidate(c)
        }
    }

    private fun answer() {
        val conn = pc ?: return
        conn.createAnswer(object : SimpleSdp() {
            override fun onCreateSuccess(desc: SessionDescription) {
                conn.setLocalDescription(object : SimpleSdp() {
                    override fun onSetSuccess() {
                        main.post { onSignal?.invoke(JSONObject().put("sdp", JSONObject().put("type", desc.type.canonicalForm()).put("sdp", desc.description))) }
                    }
                }, desc)
            }
        }, MediaConstraints())
    }

    // ---- Data channel out (16 000-char chunking, PROTOCOL §3) ----

    val bufferedAmount: Long get() = dataChannel?.bufferedAmount() ?: 0L

    fun sendData(obj: JSONObject): Boolean {
        val dc = dataChannel ?: return false
        if (dc.state() != DataChannel.State.OPEN || dc.bufferedAmount() > 4L * 1024 * 1024) return false
        val text = obj.toString()
        val max = 16000
        if (text.length <= max) return dc.send(DataChannel.Buffer(ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8)), false))
        val id = UUID.randomUUID().toString().take(10)
        val pieces = text.chunked(max)
        pieces.forEachIndexed { i, piece ->
            val part = JSONObject().put("kind", "chunk").put("id", id).put("i", i).put("n", pieces.size).put("d", piece)
            dc.send(DataChannel.Buffer(ByteBuffer.wrap(part.toString().toByteArray(Charsets.UTF_8)), false))
        }
        return true
    }

    private fun receive(text: String) {
        var msg = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (msg.optString("kind") == "chunk") {
            val id = msg.optString("id"); val i = msg.optInt("i"); val n = msg.optInt("n")
            if (n <= 0 || i !in 0 until n) return
            val parts = chunks.getOrPut(id) { arrayOfNulls(n) }
            parts[i] = msg.optString("d")
            if (parts.any { it == null }) return
            chunks.remove(id)
            msg = runCatching { JSONObject(parts.joinToString("")) }.getOrNull() ?: return
        }
        onData?.invoke(msg)
    }

    private val dataObserver = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) {}
        override fun onStateChange() { main.post { if (dataChannel?.state() == DataChannel.State.OPEN) onDataOpen?.invoke() } }
        override fun onMessage(buffer: DataChannel.Buffer) {
            val bytes = ByteArray(buffer.data.remaining()); buffer.data.get(bytes)
            val text = String(bytes, Charsets.UTF_8)
            main.post { receive(text) }
        }
    }

    // ---- PeerConnection.Observer ----

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidate(c: IceCandidate) {
            val j = JSONObject().put("candidate", c.sdp).put("sdpMid", c.sdpMid).put("sdpMLineIndex", c.sdpMLineIndex)
            main.post { onSignal?.invoke(JSONObject().put("ice", j)) }
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) { main.post { stream.videoTracks.firstOrNull()?.let { attachRemote(it) } } }
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(dc: DataChannel) {
            main.post {
                dataChannel = dc
                dc.registerObserver(dataObserver)
                if (dc.state() == DataChannel.State.OPEN) onDataOpen?.invoke()
            }
        }
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            main.post { (receiver.track() as? VideoTrack)?.let { attachRemote(it) } }
        }
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            val s = state.name.lowercase()
            main.post {
                if (state == PeerConnection.PeerConnectionState.FAILED) pc?.restartIce()
                onConnectionState?.invoke(s)
            }
        }
    }

    private fun attachRemote(track: VideoTrack) {
        if (remoteVideo === track) return
        remoteVideo?.removeSink(remoteView)
        remoteVideo = track
        track.addSink(remoteView)
    }
}

/** SdpObserver with empty defaults */
internal open class SimpleSdp : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
