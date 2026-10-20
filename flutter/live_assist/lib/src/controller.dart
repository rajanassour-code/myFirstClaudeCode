import 'dart:async';
import 'dart:io' show Platform;

import 'package:flutter/widgets.dart';

import 'call_session.dart';
import 'cobrowse.dart';
import 'signaling.dart';

enum LiveAssistPhase { idle, waiting, inCall, ended }

class ChatLine {
  ChatLine(this.text, this.mine);
  final String text;
  final bool? mine; // null = system line
}

class ConsentRequest {
  ConsentRequest(this.title, this.message, this.no, this.yes);
  final String title, message, no, yes;
  final Completer<bool> result = Completer<bool>();
}

/// Holds all call state; the overlay rebuilds from it.
class LiveAssistController extends ChangeNotifier {
  LiveAssistController._() {
    cobrowse.ask = (t, m, no, yes) {
      final r = ConsentRequest(t, m, no, yes);
      consent?.result.complete(false);
      consent = r;
      notifyListeners();
      return r.result.future;
    };
    cobrowse.showPointer = (p) {
      pointer = p;
      notifyListeners();
    };
    cobrowse.showRing = (r) {
      ring = r;
      _ringTimer?.cancel();
      _ringTimer = Timer(const Duration(milliseconds: 1800), () {
        ring = null;
        notifyListeners();
      });
      notifyListeners();
    };
    cobrowse.toast = showToast;
    cobrowse.onChanged = notifyListeners;
  }
  static final LiveAssistController instance = LiveAssistController._();

  String serverUrl = '';
  bool coBrowseEnabled = true;

  final GlobalKey boundaryKey = GlobalKey(debugLabel: 'LiveAssistBoundary');
  late final CoBrowseEngine cobrowse = CoBrowseEngine(boundaryKey);

  LiveAssistPhase phase = LiveAssistPhase.idle;
  String waitingText = '';
  String status = '';
  String endReason = '';
  String advisor = 'Advisor';
  final List<ChatLine> chat = [];
  int unread = 0;
  bool chatOpen = false;
  bool micOn = true;
  bool camOn = true;
  bool minimized = false;
  Offset? pipOffset;
  Offset? pointer;
  Rect? ring;
  String? toast;
  ConsentRequest? consent;

  CallSession? call;
  SignalingClient? _sig;
  List<Map<String, dynamic>> _ice = [];
  Timer? _tick, _toastTimer, _ringTimer;
  DateTime? _start;

  bool get busy => phase == LiveAssistPhase.waiting || phase == LiveAssistPhase.inCall;

  void showToast(String text) {
    toast = text;
    _toastTimer?.cancel();
    _toastTimer = Timer(const Duration(milliseconds: 2600), () {
      toast = null;
      notifyListeners();
    });
    notifyListeners();
  }

  void answerConsent(bool yes) {
    final c = consent;
    consent = null;
    notifyListeners();
    if (c != null && !c.result.isCompleted) c.result.complete(yes);
  }

  Future<void> startCall({required String name, required String topic, String? reference}) async {
    if (busy) return;
    if (serverUrl.isEmpty) throw StateError('Call LiveAssist.configure(serverUrl: ...) first');
    phase = LiveAssistPhase.waiting;
    waitingText = 'Connecting to an advisor…';
    chat.clear();
    unread = 0;
    chatOpen = false;
    minimized = false;
    micOn = true;
    camOn = true;
    notifyListeners();

    final sig = SignalingClient(serverUrl);
    _sig = sig;
    sig.onMessage = _onSignal;
    sig.onClose = () => _finish('disconnected');
    _ice = await sig.fetchIceServers();
    if (_sig != sig) return;
    sig.connect({'type': 'hello', 'role': 'customer', 'name': name, 'topic': topic, if (reference != null) 'ref': reference});
  }

  void endCall() {
    _sig?.send({'type': call != null ? 'hangup' : 'cancel'});
    _finish('you');
  }

  void _onSignal(Map<String, dynamic> m) {
    switch (m['type']) {
      case 'ready':
        _sig?.send({'type': 'request'});
        break;
      case 'waiting':
        final pos = (m['position'] as num?)?.toInt() ?? 1;
        final online = (m['agentsOnline'] as num?)?.toInt() ?? 0;
        waitingText = online > 0 ? 'You\'re #$pos in line…' : 'No advisors online right now. Please wait…';
        notifyListeners();
        break;
      case 'matched':
        _startSession((m['peerName'] as String?) ?? 'Advisor');
        break;
      case 'signal':
        final d = m['data'];
        if (d is Map) call?.handleSignal(d.cast<String, dynamic>());
        break;
      case 'hangup':
        _finish((m['reason'] as String?) ?? 'ended');
        break;
    }
  }

  Future<void> _startSession(String name) async {
    final c = CallSession();
    call = c;
    advisor = name;
    phase = LiveAssistPhase.inCall;
    status = 'Connecting…';
    chat.add(ChatLine('Connected to $name', null));
    c.onSignal = (data) => _sig?.send({'type': 'signal', 'data': data});
    c.onDataOpen = _sendCaps;
    c.onData = _onData;
    c.onNotice = showToast;
    c.onConnectionState = (s) {
      if (s == 'connected') _startTimer();
      if (s == 'disconnected') {
        status = 'Reconnecting…';
        notifyListeners();
      }
    };
    cobrowse.send = (msg) => call?.sendData(msg) ?? false;
    notifyListeners();
    try {
      await c.start(_ice);
    } catch (e) {
      showToast('Could not start the call: $e');
    }
    notifyListeners();
  }

  void _sendCaps() {
    call?.sendData({
      'kind': 'caps',
      'cobrowse': coBrowseEnabled,
      'native': true,
      'platform': Platform.isIOS ? 'ios' : 'android',
      'framework': 'flutter',
      'v': 2,
    });
  }

  void _onData(Map<String, dynamic> m) {
    final kind = (m['kind'] as String?) ?? '';
    if (kind == 'chat') {
      chat.add(ChatLine((m['text'] as String?) ?? '', false));
      if (!chatOpen) unread++;
      notifyListeners();
    } else if (kind == 'caps?') {
      _sendCaps();
    } else if (kind.startsWith('cb:')) {
      if (coBrowseEnabled) {
        cobrowse.handle(m);
      } else if (kind == 'cb:request') {
        call?.sendData({'kind': 'cb:declined'});
      }
    }
  }

  bool sendChat(String text) {
    final t = text.trim();
    if (t.isEmpty) return false;
    final ok = call?.sendData({'kind': 'chat', 'text': t.length > 2000 ? t.substring(0, 2000) : t}) ?? false;
    if (ok) {
      chat.add(ChatLine(t, true));
      notifyListeners();
    } else {
      showToast('Chat is not connected yet');
    }
    return ok;
  }

  void toggleMic() {
    micOn = call?.toggleMic() ?? micOn;
    notifyListeners();
  }

  void toggleCamera() {
    camOn = call?.toggleCamera() ?? camOn;
    notifyListeners();
  }

  void switchCamera() => call?.switchCamera();

  void setChatOpen(bool open) {
    chatOpen = open;
    if (open) unread = 0;
    notifyListeners();
  }

  void movePip(Offset o) {
    pipOffset = o;
    notifyListeners();
  }

  void toggleMinimized() {
    minimized = !minimized;
    notifyListeners();
  }

  void _startTimer() {
    _start = DateTime.now();
    _tick?.cancel();
    void update() {
      final s = DateTime.now().difference(_start!).inSeconds;
      status = '🔴 ${(s ~/ 60).toString().padLeft(2, '0')}:${(s % 60).toString().padLeft(2, '0')}';
      notifyListeners();
    }

    update();
    _tick = Timer.periodic(const Duration(seconds: 1), (_) => update());
  }

  void _finish(String reason) {
    if (_sig == null && call == null) return;
    cobrowse.stop(notify: false);
    answerConsent(false);
    _tick?.cancel();
    final c = call;
    call = null;
    c?.close();
    final sig = _sig;
    _sig = null;
    sig?.onClose = null;
    sig?.close();
    endReason = reason;
    phase = LiveAssistPhase.ended;
    chatOpen = false;
    pointer = null;
    showToast(reason == 'you' ? 'Call ended' : 'The call has ended');
    notifyListeners();
    Timer(const Duration(milliseconds: 300), () {
      if (phase == LiveAssistPhase.ended) {
        phase = LiveAssistPhase.idle;
        notifyListeners();
      }
    });
  }
}
