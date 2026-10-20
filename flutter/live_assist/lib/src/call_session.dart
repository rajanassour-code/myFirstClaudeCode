import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter_webrtc/flutter_webrtc.dart';

/// One WebRTC call with the operator. The customer is always the callee: the operator sends the offer and
/// creates the "chat" data channel (sdk/PROTOCOL.md §2).
class CallSession {
  void Function(Map<String, dynamic> data)? onSignal;
  void Function(Map<String, dynamic> msg)? onData;
  void Function()? onDataOpen;
  void Function(String state)? onConnectionState;
  void Function(String notice)? onNotice;

  final RTCVideoRenderer localRenderer = RTCVideoRenderer();
  final RTCVideoRenderer remoteRenderer = RTCVideoRenderer();

  RTCPeerConnection? _pc;
  RTCDataChannel? _dc;
  MediaStream? _local;
  final List<RTCIceCandidate> _pendingIce = [];
  bool _remoteSet = false;
  final Map<String, List<String?>> _chunks = {};
  bool _disposed = false;

  Future<void> start(List<Map<String, dynamic>> iceServers) async {
    await localRenderer.initialize();
    await remoteRenderer.initialize();

    _local = await _getMedia();
    localRenderer.srcObject = _local;

    final pc = await createPeerConnection({
      'iceServers': iceServers,
      'sdpSemantics': 'unified-plan',
      'bundlePolicy': 'max-bundle',
    });
    _pc = pc;

    for (final t in _local!.getTracks()) {
      await pc.addTrack(t, _local!);
    }
    // Always negotiate audio + video so we can receive even without a camera / mic
    if (_local!.getVideoTracks().isEmpty) {
      await pc.addTransceiver(kind: RTCRtpMediaType.RTCRtpMediaTypeVideo, init: RTCRtpTransceiverInit(direction: TransceiverDirection.SendRecv));
    }
    if (_local!.getAudioTracks().isEmpty) {
      await pc.addTransceiver(kind: RTCRtpMediaType.RTCRtpMediaTypeAudio, init: RTCRtpTransceiverInit(direction: TransceiverDirection.SendRecv));
    }

    pc.onIceCandidate = (c) {
      if (c.candidate == null || c.candidate!.isEmpty) return;
      onSignal?.call({
        'ice': {'candidate': c.candidate, 'sdpMid': c.sdpMid, 'sdpMLineIndex': c.sdpMLineIndex}
      });
    };
    pc.onTrack = (RTCTrackEvent e) {
      if (e.streams.isNotEmpty) remoteRenderer.srcObject = e.streams.first;
    };
    pc.onDataChannel = (RTCDataChannel dc) {
      _dc = dc;
      dc.onMessage = (RTCDataChannelMessage m) {
        if (!m.isBinary) _receive(m.text);
      };
      dc.onDataChannelState = (RTCDataChannelState s) {
        if (s == RTCDataChannelState.RTCDataChannelOpen) onDataOpen?.call();
      };
      if (dc.state == RTCDataChannelState.RTCDataChannelOpen) onDataOpen?.call();
    };
    pc.onConnectionState = (RTCPeerConnectionState s) {
      final name = s.toString().split('RTCPeerConnectionState').last.toLowerCase();
      if (s == RTCPeerConnectionState.RTCPeerConnectionStateFailed) {
        pc.restartIce();
      }
      onConnectionState?.call(name);
    };
    try {
      await Helper.setSpeakerphoneOn(true);
    } catch (_) {}
  }

  /// Camera + mic; if one is blocked, keep the other (so the advisor still sees or hears the customer)
  Future<MediaStream> _getMedia() async {
    const video = {'facingMode': 'user', 'width': 1280, 'height': 720};
    try {
      return await navigator.mediaDevices.getUserMedia({'audio': true, 'video': video});
    } catch (_) {
      MediaStream? cam, mic;
      try {
        cam = await navigator.mediaDevices.getUserMedia({'audio': false, 'video': video});
      } catch (_) {}
      try {
        mic = await navigator.mediaDevices.getUserMedia({'audio': true, 'video': false});
      } catch (_) {}
      final s = cam ?? mic ?? await createLocalMediaStream('empty');
      if (cam != null && mic != null) {
        for (final t in mic.getAudioTracks()) {
          await cam.addTrack(t);
        }
      }
      if (cam == null && mic != null) onNotice?.call('Camera unavailable — continuing with audio only.');
      if (cam != null && mic == null) onNotice?.call('Microphone unavailable — the advisor can see you but not hear you.');
      if (cam == null && mic == null) onNotice?.call('Camera and microphone unavailable. You can still see the advisor and chat.');
      return s;
    }
  }

  // ---- signaling in ----

  Future<void> handleSignal(Map<String, dynamic> data) async {
    final pc = _pc;
    if (pc == null) return;
    final sdp = data['sdp'];
    final ice = data['ice'];
    if (sdp is Map) {
      await pc.setRemoteDescription(RTCSessionDescription(sdp['sdp'] as String?, sdp['type'] as String?));
      _remoteSet = true;
      for (final c in _pendingIce) {
        await pc.addCandidate(c);
      }
      _pendingIce.clear();
      if (sdp['type'] == 'offer') {
        final answer = await pc.createAnswer({});
        await pc.setLocalDescription(answer);
        onSignal?.call({
          'sdp': {'type': answer.type, 'sdp': answer.sdp}
        });
      }
    } else if (ice is Map) {
      final cand = ice['candidate'] as String?;
      if (cand == null || cand.isEmpty) return;
      final c = RTCIceCandidate(cand, ice['sdpMid'] as String?, (ice['sdpMLineIndex'] as num?)?.toInt());
      if (_remoteSet) {
        await pc.addCandidate(c);
      } else {
        _pendingIce.add(c);
      }
    }
  }

  // ---- data channel (16 000-char chunking, PROTOCOL §3) ----

  bool get isDataOpen => _dc?.state == RTCDataChannelState.RTCDataChannelOpen;

  bool sendData(Map<String, dynamic> obj) {
    final dc = _dc;
    if (dc == null || dc.state != RTCDataChannelState.RTCDataChannelOpen) return false;
    final text = jsonEncode(obj);
    const max = 16000;
    if (text.length <= max) {
      dc.send(RTCDataChannelMessage(text));
      return true;
    }
    final id = Random().nextInt(1 << 32).toRadixString(36);
    final n = (text.length / max).ceil();
    for (var i = 0; i < n; i++) {
      final piece = text.substring(i * max, min((i + 1) * max, text.length));
      dc.send(RTCDataChannelMessage(jsonEncode({'kind': 'chunk', 'id': id, 'i': i, 'n': n, 'd': piece})));
    }
    return true;
  }

  void _receive(String text) {
    Map<String, dynamic> msg;
    try {
      msg = (jsonDecode(text) as Map).cast<String, dynamic>();
    } catch (_) {
      return;
    }
    if (msg['kind'] == 'chunk') {
      final id = msg['id'] as String, i = (msg['i'] as num).toInt(), n = (msg['n'] as num).toInt();
      if (n <= 0 || i < 0 || i >= n) return;
      final parts = _chunks.putIfAbsent(id, () => List<String?>.filled(n, null));
      parts[i] = msg['d'] as String;
      if (parts.any((p) => p == null)) return;
      _chunks.remove(id);
      try {
        msg = (jsonDecode(parts.join()) as Map).cast<String, dynamic>();
      } catch (_) {
        return;
      }
    }
    onData?.call(msg);
  }

  // ---- controls ----

  bool toggleMic() {
    final t = _local?.getAudioTracks();
    if (t == null || t.isEmpty) return false;
    t.first.enabled = !t.first.enabled;
    return t.first.enabled;
  }

  bool toggleCamera() {
    final t = _local?.getVideoTracks();
    if (t == null || t.isEmpty) return false;
    t.first.enabled = !t.first.enabled;
    return t.first.enabled;
  }

  Future<void> switchCamera() async {
    final t = _local?.getVideoTracks();
    if (t != null && t.isNotEmpty) await Helper.switchCamera(t.first);
  }

  Future<void> close() async {
    if (_disposed) return;
    _disposed = true;
    try {
      await _dc?.close();
    } catch (_) {}
    try {
      await _pc?.close();
    } catch (_) {}
    for (final t in _local?.getTracks() ?? <MediaStreamTrack>[]) {
      try {
        await t.stop();
      } catch (_) {}
    }
    try {
      await _local?.dispose();
    } catch (_) {}
    localRenderer.srcObject = null;
    remoteRenderer.srcObject = null;
    await localRenderer.dispose();
    await remoteRenderer.dispose();
  }
}
