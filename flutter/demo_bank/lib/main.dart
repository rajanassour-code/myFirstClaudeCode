import 'package:flutter/material.dart';
import 'package:live_assist/live_assist.dart';

const server = String.fromEnvironment('LIVE_ASSIST_SERVER', defaultValue: 'https://video.rajanassour.com');
const brand = Color(0xFF0F766E);

void main() {
  LiveAssist.configure(serverUrl: server);
  runApp(const CedarBankApp());
}

class CedarBankApp extends StatelessWidget {
  const CedarBankApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'Cedar Bank',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(colorSchemeSeed: brand, useMaterial3: true),
        builder: LiveAssist.builder, // ← the only line needed to add the call overlay
        home: const Shell(),
      );
}

class Shell extends StatefulWidget {
  const Shell({super.key});
  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> {
  int tab = 0;
  static const titles = ['Cedar Bank', 'Transfer', 'Cards', 'Help'];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(titles[tab], style: const TextStyle(fontWeight: FontWeight.w700)),
        backgroundColor: brand,
        foregroundColor: Colors.white,
        actions: [
          AnimatedBuilder(
            animation: LiveAssist.changes,
            builder: (_, __) => Padding(
              padding: const EdgeInsets.only(right: 8),
              child: LiveAssist.inCall
                  ? TextButton.icon(
                      onPressed: LiveAssist.endCall,
                      icon: const Icon(Icons.call_end, color: Colors.white),
                      label: const Text('End', style: TextStyle(color: Colors.white)),
                    )
                  : TextButton.icon(
                      onPressed: _callAdvisor,
                      icon: const Icon(Icons.videocam, color: Colors.white),
                      label: const Text('Advisor', style: TextStyle(color: Colors.white)),
                    ),
            ),
          ),
        ],
      ),
      body: IndexedStack(index: tab, children: [
        HomeTab(onGo: (i) => setState(() => tab = i)),
        const TransferTab(),
        const CardsTab(),
        HelpTab(onCall: _callAdvisor),
      ]),
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab,
        onDestinationSelected: (i) => setState(() => tab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: 'Home'),
          NavigationDestination(icon: Icon(Icons.swap_horiz), label: 'Transfer'),
          NavigationDestination(icon: Icon(Icons.credit_card), label: 'Cards'),
          NavigationDestination(icon: Icon(Icons.help_outline), selectedIcon: Icon(Icons.help), label: 'Help'),
        ],
      ),
    );
  }

  void _callAdvisor() {
    LiveAssist.startCall(name: 'Sara Haddad (Flutter app)', topic: titles[tab], reference: 'CB-10293');
  }
}

// ---------------- Home ----------------

class HomeTab extends StatelessWidget {
  const HomeTab({super.key, required this.onGo});
  final void Function(int) onGo;

  @override
  Widget build(BuildContext context) {
    return ListView(padding: const EdgeInsets.all(16), children: [
      Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(gradient: const LinearGradient(colors: [brand, Color(0xFF115E59)]), borderRadius: BorderRadius.circular(18)),
        child: const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('Current account', style: TextStyle(color: Colors.white70)),
          SizedBox(height: 6),
          Text('\$12,480.55', style: TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.w800)),
          SizedBox(height: 10),
          // The IBAN is hidden from the advisor (painted dark in the frames they receive)
          LiveAssistMask(child: Text('IBAN LB62 0999 0000 0001 0019 0122 9114', style: TextStyle(color: Colors.white, letterSpacing: 0.5))),
        ]),
      ),
      const SizedBox(height: 16),
      Row(children: [
        _quick(context, Icons.swap_horiz, 'Transfer', () => onGo(1)),
        _quick(context, Icons.credit_card, 'Cards', () => onGo(2)),
        _quick(context, Icons.support_agent, 'Help', () => onGo(3)),
      ]),
      const SizedBox(height: 20),
      const Text('Recent activity', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
      const SizedBox(height: 6),
      for (final t in const [
        ['Spinneys Achrafieh', '-\$84.20', 'Today'],
        ['Salary — Cedar Tech', '+\$3,200.00', 'Mon'],
        ['Electricité du Liban', '-\$46.75', 'Sun'],
        ['Netflix', '-\$9.99', 'Sat'],
        ['Transfer from Rami', '+\$150.00', 'Fri'],
      ])
        Card(
          child: ListTile(
            leading: CircleAvatar(backgroundColor: brand.withOpacity(0.12), child: Icon(t[1].startsWith('+') ? Icons.south_west : Icons.north_east, color: brand)),
            title: Text(t[0]),
            subtitle: Text(t[2]),
            trailing: Text(t[1], style: TextStyle(fontWeight: FontWeight.w700, color: t[1].startsWith('+') ? Colors.green[700] : Colors.black87)),
          ),
        ),
    ]);
  }

  Widget _quick(BuildContext context, IconData icon, String label, VoidCallback onTap) => Expanded(
        child: Card(
          child: InkWell(
            onTap: onTap,
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: Column(children: [Icon(icon, color: brand), const SizedBox(height: 6), Text(label)]),
            ),
          ),
        ),
      );
}

// ---------------- Transfer ----------------

class TransferTab extends StatefulWidget {
  const TransferTab({super.key});
  @override
  State<TransferTab> createState() => _TransferTabState();
}

class _TransferTabState extends State<TransferTab> {
  final beneficiary = TextEditingController();
  final iban = TextEditingController();
  final amount = TextEditingController();
  final note = TextEditingController();
  final otp = TextEditingController();
  String from = 'Current account • 0019';
  String currency = 'USD';
  bool instant = false;
  bool save = true;

  @override
  Widget build(BuildContext context) {
    return ListView(padding: const EdgeInsets.all(16), children: [
      const Text('Send money', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
      const SizedBox(height: 14),
      DropdownButtonFormField<String>(
        value: from,
        decoration: const InputDecoration(labelText: 'From account', border: OutlineInputBorder()),
        items: const ['Current account • 0019', 'Savings • 4410', 'USD Fresh • 7782'].map((e) => DropdownMenuItem(value: e, child: Text(e))).toList(),
        onChanged: (v) => setState(() => from = v ?? from),
      ),
      const SizedBox(height: 12),
      TextField(controller: beneficiary, decoration: const InputDecoration(labelText: 'Beneficiary name', border: OutlineInputBorder())),
      const SizedBox(height: 12),
      TextField(controller: iban, decoration: const InputDecoration(labelText: 'Beneficiary IBAN', hintText: 'LB…', border: OutlineInputBorder())),
      const SizedBox(height: 12),
      Row(children: [
        Expanded(
          flex: 3,
          child: TextField(
            controller: amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(labelText: 'Amount', border: OutlineInputBorder()),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          flex: 2,
          child: DropdownButtonFormField<String>(
            value: currency,
            decoration: const InputDecoration(labelText: 'Currency', border: OutlineInputBorder()),
            items: const ['USD', 'LBP', 'EUR'].map((e) => DropdownMenuItem(value: e, child: Text(e))).toList(),
            onChanged: (v) => setState(() => currency = v ?? currency),
          ),
        ),
      ]),
      const SizedBox(height: 12),
      TextField(controller: note, decoration: const InputDecoration(labelText: 'Reference / note', border: OutlineInputBorder())),
      SwitchListTile(contentPadding: EdgeInsets.zero, title: const Text('Instant transfer'), value: instant, onChanged: (v) => setState(() => instant = v)),
      CheckboxListTile(
        contentPadding: EdgeInsets.zero,
        controlAffinity: ListTileControlAffinity.leading,
        title: const Text('Save as beneficiary'),
        value: save,
        onChanged: (v) => setState(() => save = v ?? save),
      ),
      const SizedBox(height: 4),
      // Obscured fields are hidden from the advisor and can never be filled remotely
      TextField(
        controller: otp,
        obscureText: true,
        keyboardType: TextInputType.number,
        decoration: const InputDecoration(labelText: 'SMS code (OTP)', border: OutlineInputBorder()),
      ),
      const SizedBox(height: 16),
      // "Send" is never pressable by the advisor (commit words are blocked automatically, and LiveAssistNoRemote makes it explicit)
      LiveAssistNoRemote(
        child: FilledButton.icon(
          style: FilledButton.styleFrom(backgroundColor: brand, minimumSize: const Size.fromHeight(50)),
          onPressed: _send,
          icon: const Icon(Icons.send),
          label: const Text('Send transfer'),
        ),
      ),
      const SizedBox(height: 80),
    ]);
  }

  void _send() {
    final ok = beneficiary.text.isNotEmpty && iban.text.isNotEmpty && amount.text.isNotEmpty && otp.text.length >= 4;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(ok ? 'Transfer of ${amount.text} $currency to ${beneficiary.text} sent (demo)' : 'Please fill in all fields and the SMS code'),
    ));
  }
}

// ---------------- Cards ----------------

class CardsTab extends StatefulWidget {
  const CardsTab({super.key});
  @override
  State<CardsTab> createState() => _CardsTabState();
}

class _CardsTabState extends State<CardsTab> {
  bool frozen = false;
  bool online = true;
  bool abroad = false;
  double limit = 1500;

  @override
  Widget build(BuildContext context) {
    return ListView(padding: const EdgeInsets.all(16), children: [
      AspectRatio(
        aspectRatio: 1.6,
        child: Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            gradient: LinearGradient(colors: frozen ? [Colors.blueGrey, Colors.grey] : const [Color(0xFF134E4A), Color(0xFF0F766E)]),
            borderRadius: BorderRadius.circular(18),
          ),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              const Text('Cedar Visa Platinum', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
              const Spacer(),
              if (frozen) const Icon(Icons.ac_unit, color: Colors.white),
            ]),
            const Spacer(),
            const LiveAssistMask(child: Text('4532 7718 0042 9917', style: TextStyle(color: Colors.white, fontSize: 22, letterSpacing: 2))),
            const SizedBox(height: 8),
            const Row(children: [
              Text('SARA HADDAD', style: TextStyle(color: Colors.white70)),
              Spacer(),
              LiveAssistMask(child: Text('09/29  CVV 381', style: TextStyle(color: Colors.white70))),
            ]),
          ]),
        ),
      ),
      const SizedBox(height: 12),
      SwitchListTile(title: const Text('Freeze card'), subtitle: const Text('Temporarily block all payments'), value: frozen, onChanged: (v) => setState(() => frozen = v)),
      SwitchListTile(title: const Text('Online payments'), value: online, onChanged: (v) => setState(() => online = v)),
      SwitchListTile(title: const Text('Use abroad'), value: abroad, onChanged: (v) => setState(() => abroad = v)),
      ListTile(title: const Text('Monthly limit'), trailing: Text('\$${limit.round()}', style: const TextStyle(fontWeight: FontWeight.w700))),
      Slider(value: limit, min: 200, max: 5000, divisions: 48, label: '\$${limit.round()}', onChanged: (v) => setState(() => limit = v)),
      const SizedBox(height: 8),
      LiveAssistNoRemote(
        child: OutlinedButton(
          onPressed: () => ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Card settings saved (demo)'))),
          child: const Text('Save card settings'),
        ),
      ),
      const SizedBox(height: 80),
    ]);
  }
}

// ---------------- Help ----------------

class HelpTab extends StatelessWidget {
  const HelpTab({super.key, required this.onCall});
  final VoidCallback onCall;

  @override
  Widget build(BuildContext context) {
    return ListView(padding: const EdgeInsets.all(16), children: [
      Card(
        color: brand.withOpacity(0.08),
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('Talk to an advisor', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
            const SizedBox(height: 6),
            const Text('Video call with a Cedar Bank advisor. With your permission they can see this app and help you fill in forms — they can never press Send for you.'),
            const SizedBox(height: 14),
            FilledButton.icon(style: FilledButton.styleFrom(backgroundColor: brand), onPressed: onCall, icon: const Icon(Icons.videocam), label: const Text('Start video call')),
          ]),
        ),
      ),
      const SizedBox(height: 8),
      const ExpansionTile(title: Text('How do I report a lost card?'), children: [Padding(padding: EdgeInsets.all(16), child: Text('Go to Cards → Freeze card, then call us.'))]),
      const ExpansionTile(title: Text('What are the transfer limits?'), children: [Padding(padding: EdgeInsets.all(16), child: Text('Up to \$10,000 per day for verified customers.'))]),
      const ExpansionTile(title: Text('Is the video call recorded?'), children: [Padding(padding: EdgeInsets.all(16), child: Text('No. Video goes directly between you and the advisor.'))]),
      const SizedBox(height: 12),
      Text('Server: $server', style: const TextStyle(color: Colors.black45, fontSize: 12)),
    ]);
  }
}
