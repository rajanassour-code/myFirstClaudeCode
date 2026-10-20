/// Live Video Assist for Flutter: video call + chat + co-browse with a bank advisor.
///
/// ```dart
/// void main() {
///   LiveAssist.configure(serverUrl: 'https://video.rajanassour.com');
///   runApp(MaterialApp(builder: LiveAssist.builder, home: const HomePage()));
/// }
/// // anywhere:
/// LiveAssist.startCall(name: 'Sara', topic: 'Card');
/// ```
library live_assist;

import 'package:flutter/widgets.dart';

import 'src/controller.dart';
import 'src/overlay.dart';

export 'src/cobrowse.dart' show LiveAssistMask, LiveAssistNoRemote;
export 'src/controller.dart' show LiveAssistPhase;

class LiveAssist {
  LiveAssist._();

  static LiveAssistController get _c => LiveAssistController.instance;

  /// [serverUrl]: your Live Video Assist server, e.g. https://video.rajanassour.com
  /// [coBrowse]: allow the advisor to ask to see (and, with consent, help fill in) this app.
  static void configure({required String serverUrl, bool coBrowse = true}) {
    _c.serverUrl = serverUrl;
    _c.coBrowseEnabled = coBrowse;
  }

  /// Pass to `MaterialApp(builder: LiveAssist.builder)`. Adds the call overlay above every screen.
  static Widget builder(BuildContext context, Widget? child) => LiveAssistScope(child: child ?? const SizedBox.shrink());

  /// Join the queue and call the next free advisor. The OS asks for camera / microphone permission.
  static Future<void> startCall({String name = 'Customer', String topic = 'General', String? reference}) =>
      _c.startCall(name: name, topic: topic, reference: reference);

  /// Hang up (or leave the queue).
  static void endCall() => _c.endCall();

  static LiveAssistPhase get phase => _c.phase;
  static bool get inCall => _c.busy;

  /// Notifies on every state change (phase, co-browse, chat…).
  static Listenable get changes => _c;
}
