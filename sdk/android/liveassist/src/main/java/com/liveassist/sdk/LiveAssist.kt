package com.liveassist.sdk

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import org.json.JSONArray
import org.json.JSONObject

/**
 * Live Video Assist — Android SDK
 *
 *     // once, in Application.onCreate()
 *     LiveAssist.configure(this, "https://video.rajanassour.com")
 *
 *     // when the customer taps "Talk to an advisor"
 *     LiveAssist.startCall(name = "Layla", topic = "Cards", reference = sessionRef)
 *
 * The SDK shows its own floating call window, chat, consent prompts and co-browse indicators, and follows the
 * customer from activity to activity. Mark sensitive UI with LiveAssist.mask(view) / Modifier.liveAssistMasked()
 * and commitment buttons with LiveAssist.noRemote(view) / Modifier.liveAssistNoRemote().
 */
object LiveAssist {
    sealed class State {
        object Idle : State()
        object Connecting : State()
        data class Waiting(val position: Int, val advisorsOnline: Int) : State()
        data class InCall(val advisor: String) : State()
        data class Ended(val reason: String) : State()
    }

    /** Called on the main thread whenever the state changes. */
    var onStateChange: ((State) -> Unit)? = null
    var state: State = State.Idle
        private set(value) { field = value; onStateChange?.invoke(value) }

    /** Allow co-browsing in this app (default true). Set false to offer video + chat only. */
    var coBrowseEnabled = true

    // ---- privacy markers ----
    /** Hide this view (and everything inside it) from the advisor. */
    @JvmStatic fun mask(view: View, masked: Boolean = true) = Sensitivity.setMasked(view, masked)
    /** The advisor can never tap or change this view, even when the customer granted control. */
    @JvmStatic fun noRemote(view: View, on: Boolean = true) = Sensitivity.setNoRemote(view, on)

    private lateinit var app: Application
    private var serverUrl: String? = null
    private val main = Handler(Looper.getMainLooper())
    private var current: Activity? = null
    private var signaling: SignalingClient? = null
    private var call: CallSession? = null
    private var overlay: OverlayController? = null
    private val cobrowse = CoBrowseEngine()
    private var iceServers = JSONArray()
    private var timerStart = 0L
    private var afterPermissions: (() -> Unit)? = null

    @JvmStatic
    fun configure(application: Application, serverUrl: String) {
        this.serverUrl = serverUrl
        if (!this::app.isInitialized) {
            app = application
            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(a: Activity) {
                    current = a
                    if (signaling != null) { overlay?.attach(a); cobrowse.setActivity(a) }
                    afterPermissions?.let { afterPermissions = null; main.post(it) }   // permission dialog closed
                }
                override fun onActivityPaused(a: Activity) {}
                override fun onActivityCreated(a: Activity, b: Bundle?) {}
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
                override fun onActivityDestroyed(a: Activity) { if (current === a) current = null }
            })
        }
    }

    /** Start a call: asks camera/mic permission, joins the queue, shows the floating call window. */
    @JvmStatic @JvmOverloads
    fun startCall(name: String, topic: String = "General", reference: String? = null) {
        val url = serverUrl ?: error("Call LiveAssist.configure(application, serverUrl) first")
        if (signaling != null) return
        val activity = current ?: return
        state = State.Connecting
        withPermissions(activity) {
            val a = current ?: return@withPermissions
            val ov = overlay ?: OverlayController(app).also { overlay = it; wireOverlay(it) }
            ov.attach(a)
            ov.showWaiting("Connecting to an advisor…")
            val sig = SignalingClient(url)
            signaling = sig
            sig.onMessage = { handleSignal(it) }
            sig.onClose = { finish("disconnected") }
            sig.fetchConfig { ice ->
                if (signaling !== sig) return@fetchConfig
                iceServers = ice
                val hello = JSONObject().put("type", "hello").put("role", "customer").put("name", name).put("topic", topic)
                reference?.let { hello.put("ref", it) }
                sig.connect(hello)
            }
        }
    }

    /** End the call (or leave the queue). */
    @JvmStatic
    fun endCall() {
        signaling?.send(JSONObject().put("type", if (call != null) "hangup" else "cancel"))
        finish("you")
    }

    // ---- internals ----

    private fun withPermissions(a: Activity, then: () -> Unit) {
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { a.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) { then(); return }
        afterPermissions = then                       // runs when the activity resumes after the system dialog
        a.requestPermissions(needed.toTypedArray(), 7341)
    }

    private fun handleSignal(m: JSONObject) {
        when (m.optString("type")) {
            "ready" -> signaling?.send(JSONObject().put("type", "request"))
            "waiting" -> {
                val pos = m.optInt("position"); val online = m.optInt("agentsOnline")
                state = State.Waiting(pos, online)
                overlay?.showWaiting(if (online > 0) "You're #$pos in line…" else "No advisors online right now…")
            }
            "matched" -> startSession(m.optString("peerName", "Advisor"))
            "signal" -> m.optJSONObject("data")?.let { call?.handleSignal(it) }
            "hangup" -> finish(m.optString("reason", "ended"))
        }
    }

    private fun startSession(advisor: String) {
        val c = CallSession(app)
        call = c
        c.onSignal = { data -> signaling?.send(JSONObject().put("type", "signal").put("data", data)) }
        c.onDataOpen = { sendCaps() }
        c.onData = { handleData(it) }
        c.onConnectionState = { s ->
            if (s == "connected") startTimer()
            if (s == "disconnected") overlay?.setStatus("Reconnecting…")
        }
        c.start(iceServers)
        state = State.InCall(advisor)
        overlay?.showInCall(c.remoteView, c.localView, advisor)
        overlay?.addChat("Connected to $advisor", null)
        cobrowse.send = { msg -> call?.sendData(msg) ?: false }
        cobrowse.bufferedAmount = { call?.bufferedAmount ?: 0L }
        cobrowse.overlay = overlay
        cobrowse.setActivity(current)
    }

    private fun sendCaps() {
        call?.sendData(JSONObject().put("kind", "caps").put("cobrowse", coBrowseEnabled).put("native", true).put("platform", "android").put("v", 2))
    }

    private fun handleData(m: JSONObject) {
        val kind = m.optString("kind")
        when {
            kind == "chat" -> overlay?.addChat(m.optString("text"), false)
            kind == "caps?" -> sendCaps()
            kind.startsWith("cb:") -> if (coBrowseEnabled) cobrowse.handle(m)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (call == null) return
            val s = (System.currentTimeMillis() - timerStart) / 1000
            overlay?.setStatus("🔴 %02d:%02d".format(s / 60, s % 60))
            main.postDelayed(this, 1000)
        }
    }

    private fun startTimer() { timerStart = System.currentTimeMillis(); main.removeCallbacks(tick); main.post(tick) }

    private fun wireOverlay(ov: OverlayController) {
        ov.onEnd = { endCall() }
        ov.onMute = { call?.toggleMic() ?: true }
        ov.onCamera = { call?.toggleCamera() ?: true }
        ov.onFlip = { call?.switchCamera() }
        ov.onSendChat = { text ->
            val ok = call?.sendData(JSONObject().put("kind", "chat").put("text", text.take(2000))) == true
            if (ok) overlay?.addChat(text, true)
            ok
        }
        ov.onStopSharing = { cobrowse.stop(notify = true) }
        ov.onTakeBack = { cobrowse.setControl(false, "customer") }
    }

    private fun finish(reason: String) {
        if (signaling == null && call == null) return
        cobrowse.stop(notify = false)
        main.removeCallbacks(tick)
        call?.close(); call = null
        val sig = signaling; signaling = null
        sig?.onClose = null
        sig?.close()
        overlay?.hideCall(); overlay?.detach()
        state = State.Ended(reason)
    }
}
