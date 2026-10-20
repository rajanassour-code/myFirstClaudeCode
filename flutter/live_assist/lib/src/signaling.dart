import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:web_socket_channel/web_socket_channel.dart';

/// Talks to the Live Video Assist server (sdk/PROTOCOL.md §1).
/// Tries a WebSocket first; if the server doesn't answer within 6 s (e.g. cPanel shared hosting blocks
/// WebSockets) it switches automatically to HTTP long-polling.
class SignalingClient {
  SignalingClient(String serverUrl) : _base = serverUrl.endsWith('/') ? serverUrl : '$serverUrl/';

  final String _base;
  void Function(Map<String, dynamic> msg)? onMessage;
  void Function()? onClose;
  String transport = 'none';

  WebSocketChannel? _ws;
  bool _gotFirst = false;
  bool _closed = false;
  String? _token;
  final List<Map<String, dynamic>> _outbox = [];
  bool _sending = false;
  final http.Client _http = http.Client();

  Uri _u(String path) => Uri.parse('$_base$path');

  Future<List<Map<String, dynamic>>> fetchIceServers() async {
    try {
      final r = await _http.get(_u('config')).timeout(const Duration(seconds: 8));
      final list = (jsonDecode(r.body)['iceServers'] as List).cast<Map<String, dynamic>>();
      return list;
    } catch (_) {
      return [
        {'urls': 'stun:stun.l.google.com:19302'}
      ];
    }
  }

  void connect(Map<String, dynamic> hello) => _tryWebSocket(hello);

  void send(Map<String, dynamic> msg) {
    if (_closed) return;
    if (transport == 'websocket') {
      _ws?.sink.add(jsonEncode(msg));
    } else {
      _outbox.add(msg);
      _flush();
    }
  }

  void close() {
    if (_closed) return;
    final t = _token;
    if (transport == 'polling' && t != null) {
      _http.post(_u('signal/close'), headers: {'Content-Type': 'application/json'}, body: jsonEncode({'token': t})).catchError((_) => http.Response('', 0));
    }
    _ws?.sink.close();
    _finish();
  }

  // ---- WebSocket ----

  void _tryWebSocket(Map<String, dynamic> hello) {
    final wsUrl = '${_base}ws'.replaceFirst('https://', 'wss://').replaceFirst('http://', 'ws://');
    try {
      final ch = WebSocketChannel.connect(Uri.parse(wsUrl));
      _ws = ch;
      ch.sink.add(jsonEncode(hello));
      ch.stream.listen((data) {
        if (_ws != ch || _closed) return;
        if (!_gotFirst) {
          _gotFirst = true;
          transport = 'websocket';
        }
        try {
          onMessage?.call((jsonDecode(data as String) as Map).cast<String, dynamic>());
        } catch (_) {}
      }, onError: (_) {
        if (_ws == ch) _gotFirst ? _finish() : _fallBack(hello);
      }, onDone: () {
        if (_ws == ch) _gotFirst ? _finish() : _fallBack(hello);
      }, cancelOnError: true);
    } catch (_) {
      _fallBack(hello);
      return;
    }
    Timer(const Duration(seconds: 6), () {
      if (!_gotFirst && !_closed && transport == 'none') _fallBack(hello);
    });
  }

  void _fallBack(Map<String, dynamic> hello) {
    if (transport != 'none' || _closed) return;
    final old = _ws;
    _ws = null;
    try {
      old?.sink.close();
    } catch (_) {}
    _startPolling(hello);
  }

  // ---- HTTP long-polling ----

  Future<void> _startPolling(Map<String, dynamic> hello) async {
    transport = 'polling';
    try {
      final r = await _http.post(_u('signal/open')).timeout(const Duration(seconds: 15));
      if (r.statusCode != 200) return _finish();
      _token = jsonDecode(r.body)['token'] as String;
    } catch (_) {
      return _finish();
    }
    _outbox.insert(0, hello);
    _flush();
    _poll();
  }

  Future<void> _poll() async {
    var failures = 0;
    while (!_closed && _token != null) {
      try {
        final r = await _http.get(_u('signal/poll?token=${Uri.encodeQueryComponent(_token!)}')).timeout(const Duration(seconds: 40));
        if (_closed) return;
        if (r.statusCode == 410) return _finish();
        if (r.statusCode != 200) throw Exception('HTTP ${r.statusCode}');
        failures = 0;
        for (final m in (jsonDecode(r.body) as List)) {
          if (_closed) break;
          onMessage?.call((m as Map).cast<String, dynamic>());
        }
      } catch (_) {
        if (++failures > 5) return _finish();
        await Future.delayed(Duration(seconds: failures));
      }
    }
  }

  Future<void> _flush() async {
    final t = _token;
    if (transport != 'polling' || _sending || _outbox.isEmpty || t == null || _closed) return;
    final msgs = List<Map<String, dynamic>>.from(_outbox);
    _outbox.clear();
    _sending = true;
    try {
      final r = await _http.post(_u('signal/send'), headers: {'Content-Type': 'application/json'}, body: jsonEncode({'token': t, 'msgs': msgs}));
      if (r.statusCode == 410) {
        _sending = false;
        return _finish();
      }
    } catch (_) {}
    _sending = false;
    _flush();
  }

  void _finish() {
    if (_closed) return;
    _closed = true;
    _ws = null;
    onClose?.call();
  }
}
