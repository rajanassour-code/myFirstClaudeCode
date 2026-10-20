import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/widgets.dart';
import 'package:image/image.dart' as img;

/// Hide this widget from the advisor: it is painted dark in every frame sent to them.
/// Password fields (obscureText) are hidden automatically.
class LiveAssistMask extends StatefulWidget {
  const LiveAssistMask({super.key, required this.child});
  final Widget child;
  @override
  State<StatefulWidget> createState() => _MarkerState(_Kind.masked);
}

/// The advisor can never tap or change this widget, even when the customer granted control.
/// Buttons labelled Send / Confirm / Pay / Submit / Approve / Save / Delete / Sign are blocked automatically.
class LiveAssistNoRemote extends StatefulWidget {
  const LiveAssistNoRemote({super.key, required this.child});
  final Widget child;
  @override
  State<StatefulWidget> createState() => _MarkerState(_Kind.noRemote);
}

enum _Kind { masked, noRemote }

class _MarkerState extends State<StatefulWidget> {
  _MarkerState(this.kind);
  final _Kind kind;
  final GlobalKey _key = GlobalKey();
  @override
  void initState() {
    super.initState();
    _Registry.add(_key, kind);
  }

  @override
  void dispose() {
    _Registry.remove(_key);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final child = (widget is LiveAssistMask) ? (widget as LiveAssistMask).child : (widget as LiveAssistNoRemote).child;
    return KeyedSubtree(key: _key, child: child);
  }
}

class _Registry {
  static final Map<GlobalKey, _Kind> _items = {};
  static void add(GlobalKey k, _Kind kind) => _items[k] = kind;
  static void remove(GlobalKey k) => _items.remove(k);

  /// rects in global logical coordinates
  static List<Rect> rects(_Kind kind) {
    final out = <Rect>[];
    _items.forEach((k, v) {
      if (v != kind) return;
      final r = _globalRect(k.currentContext);
      if (r != null) out.add(r);
    });
    return out;
  }
}

Rect? _globalRect(BuildContext? ctx) {
  final ro = ctx?.findRenderObject();
  if (ro is! RenderBox || !ro.attached || !ro.hasSize) return null;
  return ro.localToGlobal(Offset.zero) & ro.size;
}

final RegExp _commit = RegExp(r'\b(send|confirm|pay|submit|approve|save|delete|sign|transfer now|authori[sz]e)\b', caseSensitive: false);

/// Native co-browse for Flutter (sdk/PROTOCOL.md §3).
/// Captures the app inside [boundaryKey] (never the SDK's own overlay), paints sensitive areas dark ON THE PHONE,
/// and performs assisted-control actions through Flutter's semantics tree (like TalkBack / VoiceOver).
class CoBrowseEngine {
  CoBrowseEngine(this.boundaryKey);
  final GlobalKey boundaryKey;

  String state = 'idle'; // idle | asking | active
  bool control = false;

  bool Function(Map<String, dynamic>)? send;
  // UI hooks (implemented by the overlay)
  Future<bool> Function(String title, String message, String no, String yes)? ask;
  void Function(Offset? p)? showPointer;
  void Function(Rect r)? showRing;
  void Function(String text)? toast;
  void Function()? onChanged;

  Timer? _timer;
  bool _busy = false;
  int _lastHash = 0;
  bool _force = true;
  SemanticsHandle? _semantics;
  final Map<String, int> _fields = {}; // id → semantics node id
  int _nextId = 1;
  int frameWidth = 420;
  int jpegQuality = 55;

  // ---------------- messages from the operator ----------------

  Future<void> handle(Map<String, dynamic> m) async {
    switch (m['kind']) {
      case 'cb:request':
        if (state == 'active') {
          send?.call({'kind': 'cb:accepted'});
          _force = true;
          return;
        }
        state = 'asking';
        final ok = await (ask?.call('Share your screen with your advisor?',
                'Your advisor will see this app\'s screens to guide you.\n\n• They can\'t tap, type or make transactions for you.\n• Passwords, PINs, card numbers and codes are hidden.\n• You can stop at any time.',
                'Not now', 'Allow') ??
            Future.value(false));
        if (state != 'asking') return;
        ok ? _accept() : _decline();
        break;
      case 'cb:stop':
        stop(notify: false);
        break;
      case 'cb:control-request':
        if (state != 'active') return;
        if (control) {
          send?.call({'kind': 'cb:control-granted'});
          return;
        }
        final ok = await (ask?.call('Let your advisor fill in fields for you?',
                'They can type in forms, choose options and move between screens.\n\n• Only you can press Send, Confirm or Save.\n• They can\'t see or fill in passwords, PINs or codes.\n• Everything they change is highlighted. Tap "Take back" any time.',
                'No, just watch', 'Allow') ??
            Future.value(false));
        if (state != 'active') return;
        if (ok) {
          setControl(true);
          send?.call({'kind': 'cb:control-granted'});
          toast?.call('Your advisor can now help fill in');
        } else {
          send?.call({'kind': 'cb:control-denied'});
        }
        break;
      case 'cb:control-release':
        setControl(false, by: 'advisor');
        break;
      case 'cb:pointer':
        if (state != 'active') return;
        showPointer?.call(m['hide'] == true ? null : _point(m));
        break;
      case 'cb:highlight':
        if (state != 'active') return;
        final p = _point(m);
        if (p == null) return;
        final hit = _semanticsAt(p);
        final r = hit?.rect ?? Rect.fromCenter(center: p, width: 48, height: 48);
        showRing?.call(r);
        send?.call({'kind': 'cb:highlighted', 'rect': _norm(r)});
        break;
      case 'cb:remote':
        if (state == 'active') _remote(m);
        break;
    }
  }

  void _accept() {
    state = 'active';
    send?.call({'kind': 'cb:accepted'});
    _semantics ??= SemanticsBinding.instance.ensureSemantics();
    _force = true;
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(milliseconds: 250), (_) => _capture());
    onChanged?.call();
  }

  void _decline() {
    state = 'idle';
    send?.call({'kind': 'cb:declined'});
  }

  void stop({required bool notify}) {
    if (state == 'idle') return;
    if (control) setControl(false, by: 'stop');
    state = 'idle';
    _timer?.cancel();
    _timer = null;
    _semantics?.dispose();
    _semantics = null;
    _fields.clear();
    showPointer?.call(null);
    if (notify) send?.call({'kind': 'cb:stopped'});
    onChanged?.call();
  }

  void setControl(bool on, {String by = 'customer'}) {
    if (control == on) return;
    control = on;
    if (!on && by == 'customer') {
      send?.call({'kind': 'cb:control-revoked'});
      toast?.call('You took back control');
    }
    if (!on && by == 'advisor') toast?.call('Advisor released control');
    onChanged?.call();
  }

  /// customer touched the screen (so the advisor can follow)
  void customerTap(Offset global) {
    if (state != 'active') return;
    final s = _size;
    if (s == null) return;
    send?.call({'kind': 'cb:tap', 'nx': global.dx / s.width, 'ny': global.dy / s.height});
    _force = true;
  }

  // ---------------- capture (≤ 4 fps, only on change) ----------------

  RenderRepaintBoundary? get _boundary {
    final ro = boundaryKey.currentContext?.findRenderObject();
    return ro is RenderRepaintBoundary && ro.attached && ro.hasSize ? ro : null;
  }

  Size? get _size => _boundary?.size;

  Future<void> _capture() async {
    if (_busy || state != 'active') return;
    final b = _boundary;
    if (b == null) return;
    _busy = true;
    try {
      final size = b.size;
      final ratio = frameWidth / size.width;
      final shot = await b.toImage(pixelRatio: ratio);
      // paint masks on top, on the phone
      final rec = ui.PictureRecorder();
      final canvas = Canvas(rec);
      canvas.drawImage(shot, Offset.zero, Paint());
      final paint = Paint()..color = const Color(0xFF111827);
      final origin = b.localToGlobal(Offset.zero);
      for (final r in _maskRects()) {
        final local = r.shift(-origin);
        canvas.drawRect(Rect.fromLTRB(local.left * ratio - 2, local.top * ratio - 2, local.right * ratio + 2, local.bottom * ratio + 2), paint);
        final tp = TextPainter(
          text: const TextSpan(text: '🔒 hidden', style: TextStyle(color: Color(0xFFBBBBBB), fontSize: 12, fontWeight: FontWeight.w600)),
          textDirection: TextDirection.ltr,
        )..layout();
        tp.paint(canvas, Offset(local.left * ratio + 8, (local.center.dy * ratio) - tp.height / 2));
      }
      final w = shot.width, h = shot.height;
      final masked = await rec.endRecording().toImage(w, h);
      shot.dispose();
      final data = await masked.toByteData(format: ui.ImageByteFormat.rawRgba);
      masked.dispose();
      if (data == null) return;
      final bytes = data.buffer.asUint8List();
      var hash = 0x811c9dc5;
      for (var i = 0; i < bytes.length; i += 97) {
        hash = ((hash ^ bytes[i]) * 0x01000193) & 0xFFFFFFFF;
      }
      if (hash == _lastHash && !_force) return;
      final jpg = await compute(_encodeJpg, _EncodeArgs(bytes, w, h, jpegQuality));
      final ok = send?.call({
            'kind': 'cb:frame',
            'w': size.width.round(),
            'h': size.height.round(),
            'jpg': base64Encode(jpg),
            'title': 'Flutter app',
          }) ??
          false;
      if (ok) {
        _lastHash = hash;
        _force = false;
      }
    } catch (_) {
      // never break the app because of co-browse
    } finally {
      _busy = false;
    }
  }

  List<Rect> _maskRects() {
    final rects = <Rect>[..._Registry.rects(_Kind.masked)];
    // password / obscured fields, found in the widget tree
    void visit(Element e) {
      final w = e.widget;
      if (w is EditableText && w.obscureText) {
        final r = _globalRect(e);
        if (r != null) rects.add(r);
        return;
      }
      e.visitChildElements(visit);
    }

    final ctx = boundaryKey.currentContext;
    if (ctx is Element) ctx.visitChildElements(visit);
    return rects;
  }

  // ---------------- assisted control (rules enforced here) ----------------

  Future<void> _remote(Map<String, dynamic> m) async {
    final action = m['action'] as String?;
    void deny(String reason) => send?.call({'kind': 'cb:denied', 'action': action, 'reason': reason});
    if (!control) return deny('control is not enabled');
    final owner = _owner;
    if (owner == null) return deny('not available on this screen');

    switch (action) {
      case 'scroll':
        final p = _point(m);
        final dy = (m['dy'] as num?)?.toDouble() ?? 0;
        if (p == null || dy == 0) return;
        final node = _semanticsAt(p, accept: (d) => d.hasAction(SemanticsAction.scrollUp) || d.hasAction(SemanticsAction.scrollDown));
        if (node == null) return;
        owner.performAction(node.id, dy > 0 ? SemanticsAction.scrollUp : SemanticsAction.scrollDown);
        _force = true;
        break;

      case 'tap':
        final p = _point(m);
        if (p == null) return;
        if (_Registry.rects(_Kind.masked).any((r) => r.contains(p))) return deny('sensitive field — only the customer can fill it');
        if (_Registry.rects(_Kind.noRemote).any((r) => r.contains(p))) return deny('only the customer can use this');
        final hit = _semanticsAt(p);
        if (hit == null) return deny('nothing to tap there');
        final d = hit.data;
        if (d.hasFlag(SemanticsFlag.isObscured)) return deny('sensitive field — only the customer can fill it');
        if (d.hasFlag(SemanticsFlag.isTextField)) {
          if (d.hasFlag(SemanticsFlag.isReadOnly)) return deny('read-only field');
          final id = 'f${_nextId++}';
          _fields[id] = hit.id;
          send?.call({'kind': 'cb:field', 'id': id, 'rect': _norm(hit.rect), 'value': d.value, 'placeholder': d.hint, 'input': d.hasFlag(SemanticsFlag.isMultiline) ? 'multiline' : 'text'});
          return;
        }
        final label = [d.label, d.value, d.tooltip].where((s) => s.isNotEmpty).join(' ');
        if (d.hasFlag(SemanticsFlag.isButton) && _commit.hasMatch(label)) return deny('only the customer can submit');
        if (!d.hasAction(SemanticsAction.tap)) return deny('this item can\'t be tapped remotely');
        owner.performAction(hit.id, SemanticsAction.tap);
        _applied(hit.rect, d.hasFlag(SemanticsFlag.hasToggledState) || d.hasFlag(SemanticsFlag.hasCheckedState) ? 'Changed by your advisor' : 'Advisor tapped "${label.isEmpty ? 'an item' : label}"');
        break;

      case 'set':
        final id = m['id'] as String?;
        final nodeId = _fields[id];
        final node = nodeId == null ? null : _findNode(nodeId);
        if (node == null) return deny('field no longer on screen');
        if (node.getSemanticsData().hasFlag(SemanticsFlag.isObscured)) return deny('sensitive field — only the customer can fill it');
        if (_Registry.rects(_Kind.noRemote).any((r) => r.overlaps(node.rect))) return deny('only the customer can use this');
        final value = ((m['value'] as String?) ?? '');
        owner.performAction(node.id, SemanticsAction.setText, value.length > 500 ? value.substring(0, 500) : value);
        _applied(node.rect, 'Filled in by your advisor', id: id);
        break;

      default:
        deny('not allowed');
    }
  }

  void _applied(Rect r, String message, {String? id}) {
    showRing?.call(r);
    toast?.call(message);
    send?.call({'kind': 'cb:applied', 'rect': _norm(r), if (id != null) 'id': id});
    _force = true;
  }

  // ---------------- semantics helpers ----------------

  SemanticsOwner? get _owner {
    for (final v in RendererBinding.instance.renderViews) {
      final o = v.owner?.semanticsOwner;
      if (o != null) return o;
    }
    return null;
  }

  /// Smallest actionable semantics node at a global logical point (rect returned in global logical coordinates)
  _Hit? _semanticsAt(Offset p, {bool Function(SemanticsData d)? accept}) {
    final root = _owner?.rootSemanticsNode;
    if (root == null) return null;
    final logical = _size;
    if (logical == null) return null;
    // The root node's transform maps logical → physical pixels; calibrate from the root's size
    final rootRect = MatrixUtils.transformRect(root.transform ?? Matrix4.identity(), root.rect);
    final scale = rootRect.width > 0 ? rootRect.width / logical.width : 1.0;
    final ok = accept ?? (d) => d.hasAction(SemanticsAction.tap) || d.hasFlag(SemanticsFlag.isTextField) || d.hasAction(SemanticsAction.setText);
    _Hit? best;
    void visit(SemanticsNode n, Matrix4 parent) {
      final m = n.transform == null ? parent : (parent.clone()..multiply(n.transform!));
      final physical = MatrixUtils.transformRect(m, n.rect);
      final r = Rect.fromLTRB(physical.left / scale, physical.top / scale, physical.right / scale, physical.bottom / scale);
      if (!r.inflate(1).contains(p)) return;
      if (!n.isInvisible && !n.isMergedIntoParent) {
        final d = n.getSemanticsData();
        if (ok(d) && (best == null || r.width * r.height <= best!.rect.width * best!.rect.height)) best = _Hit(n.id, r, d);
      }
      n.visitChildren((c) {
        visit(c, m);
        return true;
      });
    }

    visit(root, Matrix4.identity());
    return best;
  }

  SemanticsNode? _findNode(int id) {
    SemanticsNode? found;
    void visit(SemanticsNode n) {
      if (found != null) return;
      if (n.id == id) {
        found = n;
        return;
      }
      n.visitChildren((c) {
        visit(c);
        return found == null;
      });
    }

    final root = _owner?.rootSemanticsNode;
    if (root != null) visit(root);
    return found;
  }

  Offset? _point(Map<String, dynamic> m) {
    final s = _size;
    final nx = (m['nx'] as num?)?.toDouble(), ny = (m['ny'] as num?)?.toDouble();
    if (s == null || nx == null || ny == null) return null;
    return Offset(nx * s.width, ny * s.height);
  }

  List<double> _norm(Rect r) {
    final s = _size ?? const Size(1, 1);
    return [r.left / s.width, r.top / s.height, r.width / s.width, r.height / s.height];
  }
}

class _Hit {
  _Hit(this.id, this.rect, this.data);
  final int id;
  final Rect rect;
  final SemanticsData data;
}

class _EncodeArgs {
  _EncodeArgs(this.rgba, this.w, this.h, this.q);
  final Uint8List rgba;
  final int w, h, q;
}

Uint8List _encodeJpg(_EncodeArgs a) {
  final image = img.Image.fromBytes(width: a.w, height: a.h, bytes: a.rgba.buffer, numChannels: 4, order: img.ChannelOrder.rgba);
  return Uint8List.fromList(img.encodeJpg(image, quality: a.q));
}
