import 'package:flutter/material.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';

import 'controller.dart';

const _brand = Color(0xFF0F766E);
const _orange = Color(0xFFF97316);

/// Wraps the whole app (via MaterialApp.builder). Everything inside the RepaintBoundary is what the advisor
/// can see when co-browsing; the call UI is drawn on top, outside it, so it never appears in the shared frames.
class LiveAssistScope extends StatelessWidget {
  const LiveAssistScope({super.key, required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final c = LiveAssistController.instance;
    return Stack(
      textDirection: TextDirection.ltr,
      children: [
        Positioned.fill(
          child: RepaintBoundary(
            key: c.boundaryKey,
            child: Listener(
              behavior: HitTestBehavior.translucent,
              onPointerDown: (e) => c.cobrowse.customerTap(e.position),
              child: child,
            ),
          ),
        ),
        Positioned.fill(
          child: Overlay(initialEntries: [
            OverlayEntry(builder: (_) => AnimatedBuilder(animation: c, builder: (context, _) => _Layer(c))),
          ]),
        ),
      ],
    );
  }
}

class _Layer extends StatelessWidget {
  const _Layer(this.c);
  final LiveAssistController c;

  @override
  Widget build(BuildContext context) {
    final pad = MediaQuery.of(context).padding;
    final size = MediaQuery.of(context).size;
    final sharing = c.cobrowse.state == 'active';
    return Material(
      type: MaterialType.transparency,
      child: Stack(children: [
        if (sharing)
          Positioned.fill(
            child: IgnorePointer(
              child: DecoratedBox(decoration: BoxDecoration(border: Border.all(color: c.cobrowse.control ? _orange : _brand, width: 3))),
            ),
          ),
        if (sharing) Positioned(top: pad.top + 4, left: 8, right: 8, child: _banner()),
        if (c.ring != null)
          Positioned.fromRect(
            rect: c.ring!.inflate(4),
            child: IgnorePointer(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  border: Border.all(color: _orange, width: 3),
                  borderRadius: BorderRadius.circular(8),
                  color: _orange.withOpacity(0.12),
                ),
              ),
            ),
          ),
        if (c.pointer != null)
          Positioned(
            left: c.pointer!.dx - 11,
            top: c.pointer!.dy - 11,
            child: IgnorePointer(
              child: Container(
                width: 22,
                height: 22,
                decoration: BoxDecoration(
                  color: _orange.withOpacity(0.85),
                  shape: BoxShape.circle,
                  border: Border.all(color: Colors.white, width: 2),
                  boxShadow: const [BoxShadow(blurRadius: 6, color: Colors.black26)],
                ),
              ),
            ),
          ),
        if (c.phase == LiveAssistPhase.waiting) Positioned(left: 16, right: 16, bottom: pad.bottom + 24, child: _waiting()),
        if (c.phase == LiveAssistPhase.inCall && c.call != null) _pip(context, size, pad),
        if (c.chatOpen && c.phase == LiveAssistPhase.inCall) Positioned(left: 0, right: 0, bottom: 0, height: size.height * 0.55, child: _ChatPanel(c)),
        if (c.toast != null)
          Positioned(
            left: 24,
            right: 24,
            bottom: pad.bottom + (c.chatOpen ? size.height * 0.55 + 12 : 96),
            child: IgnorePointer(
              child: Center(
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
                  decoration: BoxDecoration(color: const Color(0xEE111827), borderRadius: BorderRadius.circular(20)),
                  child: Text(c.toast!, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white, fontSize: 13.5)),
                ),
              ),
            ),
          ),
        if (c.consent != null) Positioned.fill(child: _consent(c.consent!)),
      ]),
    );
  }

  Widget _banner() {
    final control = c.cobrowse.control;
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 6, 6, 6),
      decoration: BoxDecoration(color: control ? _orange : _brand, borderRadius: BorderRadius.circular(12), boxShadow: const [BoxShadow(blurRadius: 8, color: Colors.black26)]),
      child: Row(children: [
        Icon(control ? Icons.edit : Icons.visibility, color: Colors.white, size: 18),
        const SizedBox(width: 8),
        Expanded(
          child: Text(control ? 'Your advisor can fill in fields' : 'Your advisor can see this app',
              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 13)),
        ),
        if (control) _pill('Take back', () => c.cobrowse.setControl(false)),
        const SizedBox(width: 4),
        _pill('Stop', () => c.cobrowse.stop(notify: true)),
      ]),
    );
  }

  Widget _pill(String text, VoidCallback onTap) => Material(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            child: Text(text, style: TextStyle(color: c.cobrowse.control ? _orange : _brand, fontWeight: FontWeight.w700, fontSize: 12.5)),
          ),
        ),
      );

  Widget _waiting() => Material(
        elevation: 8,
        borderRadius: BorderRadius.circular(16),
        color: Colors.white,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(children: [
            const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.5, color: _brand)),
            const SizedBox(width: 14),
            Expanded(child: Text(c.waitingText, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w500))),
            TextButton(onPressed: c.endCall, child: const Text('Cancel')),
          ]),
        ),
      );

  Widget _pip(BuildContext context, Size size, EdgeInsets pad) {
    final call = c.call!;
    final w = c.minimized ? 96.0 : 160.0;
    final h = c.minimized ? 128.0 : 214.0;
    const bar = 44.0;
    final total = h + (c.minimized ? 0 : bar);
    final o = c.pipOffset ?? Offset(size.width - w - 12, pad.top + 64);
    final pos = Offset(o.dx.clamp(4.0, size.width - w - 4), o.dy.clamp(pad.top + 4, size.height - total - pad.bottom - 4));
    return Positioned(
      left: pos.dx,
      top: pos.dy,
      child: GestureDetector(
        onPanUpdate: (d) => c.movePip(pos + d.delta),
        onDoubleTap: c.toggleMinimized,
        child: Material(
          elevation: 10,
          borderRadius: BorderRadius.circular(14),
          clipBehavior: Clip.antiAlias,
          color: const Color(0xFF111827),
          child: SizedBox(
            width: w,
            height: total,
            child: Column(children: [
              SizedBox(
                width: w,
                height: h,
                child: Stack(children: [
                  Positioned.fill(child: RTCVideoView(call.remoteRenderer, objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover)),
                  Positioned(
                    right: 6,
                    bottom: 6,
                    width: w * 0.32,
                    height: w * 0.42,
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(6),
                      child: c.camOn
                          ? RTCVideoView(call.localRenderer, mirror: true, objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover)
                          : Container(color: Colors.black54, child: const Icon(Icons.videocam_off, color: Colors.white70, size: 16)),
                    ),
                  ),
                  Positioned(
                    left: 6,
                    top: 6,
                    right: 6,
                    child: Text('${c.advisor}  ${c.status}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w600, shadows: [Shadow(blurRadius: 4)])),
                  ),
                  if (c.minimized)
                    Positioned(
                      left: 4,
                      bottom: 4,
                      child: _round(Icons.call_end, Colors.red, c.endCall, 30),
                    ),
                ]),
              ),
              if (!c.minimized)
                SizedBox(
                  height: bar,
                  child: Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
                    _icon(c.micOn ? Icons.mic : Icons.mic_off, c.toggleMic, active: c.micOn),
                    _icon(c.camOn ? Icons.videocam : Icons.videocam_off, c.toggleCamera, active: c.camOn),
                    _icon(Icons.cameraswitch, c.switchCamera),
                    _badge(_icon(Icons.chat_bubble, () => c.setChatOpen(!c.chatOpen)), c.unread),
                    _round(Icons.call_end, Colors.red, c.endCall, 30),
                  ]),
                ),
            ]),
          ),
        ),
      ),
    );
  }

  Widget _icon(IconData icon, VoidCallback onTap, {bool active = true}) => InkResponse(
        onTap: onTap,
        radius: 18,
        child: Icon(icon, size: 20, color: active ? Colors.white : Colors.redAccent),
      );

  Widget _round(IconData icon, Color color, VoidCallback onTap, double d) => Material(
        color: color,
        shape: const CircleBorder(),
        child: InkWell(customBorder: const CircleBorder(), onTap: onTap, child: SizedBox(width: d, height: d, child: Icon(icon, color: Colors.white, size: d * 0.55))),
      );

  Widget _badge(Widget child, int n) => n == 0
      ? child
      : Stack(clipBehavior: Clip.none, children: [
          child,
          Positioned(
            right: -6,
            top: -4,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
              decoration: BoxDecoration(color: _orange, borderRadius: BorderRadius.circular(8)),
              child: Text('$n', style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.w700)),
            ),
          ),
        ]);

  Widget _consent(ConsentRequest r) => GestureDetector(
        onTap: () {},
        child: Container(
          color: Colors.black54,
          alignment: Alignment.bottomCenter,
          child: SafeArea(
            child: Container(
              margin: const EdgeInsets.all(12),
              constraints: const BoxConstraints(maxWidth: 520),
              child: Material(
              color: Colors.white,
              borderRadius: BorderRadius.circular(18),
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  Text(r.title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
                  const SizedBox(height: 10),
                  Text(r.message, style: const TextStyle(fontSize: 14.5, height: 1.4, color: Color(0xFF374151))),
                  const SizedBox(height: 18),
                  Row(children: [
                    Expanded(child: OutlinedButton(onPressed: () => c.answerConsent(false), child: Text(r.no))),
                    const SizedBox(width: 10),
                    Expanded(
                      child: FilledButton(
                        style: FilledButton.styleFrom(backgroundColor: _brand),
                        onPressed: () => c.answerConsent(true),
                        child: Text(r.yes),
                      ),
                    ),
                  ]),
                ]),
              ),
            ),
          ),
          ),
        ),
      );
}

class _ChatPanel extends StatefulWidget {
  const _ChatPanel(this.c);
  final LiveAssistController c;
  @override
  State<_ChatPanel> createState() => _ChatPanelState();
}

class _ChatPanelState extends State<_ChatPanel> {
  final _text = TextEditingController();
  final _scroll = ScrollController();

  @override
  void dispose() {
    _text.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _send() {
    if (widget.c.sendChat(_text.text)) _text.clear();
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.c;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) _scroll.jumpTo(_scroll.position.maxScrollExtent);
    });
    final kb = MediaQuery.of(context).viewInsets.bottom;
    return Material(
      elevation: 16,
      color: Colors.white,
      borderRadius: const BorderRadius.vertical(top: Radius.circular(18)),
      child: Padding(
        padding: EdgeInsets.only(bottom: kb),
        child: Column(children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 4, 0),
            child: Row(children: [
              Text('Chat with ${c.advisor}', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
              const Spacer(),
              IconButton(onPressed: () => c.setChatOpen(false), icon: const Icon(Icons.close)),
            ]),
          ),
          const Divider(height: 1),
          Expanded(
            child: ListView.builder(
              controller: _scroll,
              padding: const EdgeInsets.all(12),
              itemCount: c.chat.length,
              itemBuilder: (_, i) {
                final m = c.chat[i];
                if (m.mine == null) {
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Text(m.text, textAlign: TextAlign.center, style: const TextStyle(color: Colors.black45, fontSize: 12)),
                  );
                }
                return Align(
                  alignment: m.mine! ? Alignment.centerRight : Alignment.centerLeft,
                  child: Container(
                    margin: const EdgeInsets.symmetric(vertical: 3),
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                    constraints: const BoxConstraints(maxWidth: 280),
                    decoration: BoxDecoration(color: m.mine! ? _brand : const Color(0xFFF1F5F9), borderRadius: BorderRadius.circular(14)),
                    child: Text(m.text, style: TextStyle(color: m.mine! ? Colors.white : Colors.black87)),
                  ),
                );
              },
            ),
          ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 4, 6, 8),
              child: Row(children: [
                Expanded(
                  child: TextField(
                    controller: _text,
                    textInputAction: TextInputAction.send,
                    onSubmitted: (_) => _send(),
                    decoration: InputDecoration(
                      hintText: 'Type a message',
                      isDense: true,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(20)),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                    ),
                  ),
                ),
                IconButton(onPressed: _send, icon: const Icon(Icons.send, color: _brand)),
              ]),
            ),
          ),
        ]),
      ),
    );
  }
}
