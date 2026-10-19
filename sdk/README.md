# Live Video Assist: native mobile SDKs (iOS & Android)

Add **video calls with your advisors** and **native co-browsing** to your mobile banking app. The SDKs talk to the same server and operator console as the web version (`https://video.rajanassour.com/agent.html`), so nothing changes for your operators.

| | iOS | Android |
|---|---|---|
| SDK | `ios/LiveAssistSDK` (Swift Package) | `android/liveassist` (Kotlin library) |
| Demo app | `ios/DemoBank` (UIKit) | `android/demo` (Android Views) |
| Minimum OS | iOS 15 | Android 7.0 (API 24) |
| WebRTC | `stasel/WebRTC` (Google WebRTC, prebuilt) | `io.getstream:stream-webrtc-android` (Google WebRTC, prebuilt) |

## Features

- **Video call** with camera and microphone, speakerphone, mute, camera on/off, front/back switch, call timer
- **Chat** with the advisor
- **Floating call window** the customer can drag and enlarge. It stays on screen while they use the app and across screens and activities
- **Native co-browse**: the advisor sees the app's own screens live
  - Only **your app** is captured: never other apps, notifications, the keyboard, or the SDK's own call window
  - **Masking on the phone, before anything is sent**: secure text fields (passwords, PIN, SMS code, card number) are hidden automatically, plus anything you mark
  - Advisor **pointer** and **highlight** ("tap here")
  - **Assisted control** with separate consent: the advisor can fill in fields, choose options, flip switches, tap tabs and buttons, and scroll. They can **never** press submit, confirm or save buttons, or touch secure fields. Every change is outlined in orange with a short note, and the customer can tap **Take back** at any time
  - Respects Android `FLAG_SECURE` screens (nothing is captured there)
- **Works on any hosting**: WebSocket when available, automatic fallback to HTTP polling (as on your cPanel server)

## Try the demo apps

### iOS (Mac with Xcode 15+)

```bash
brew install xcodegen            # one time
cd sdk/ios/DemoBank
xcodegen                         # creates DemoBank.xcodeproj
open DemoBank.xcodeproj
```
In Xcode: select the **DemoBank** target → **Signing & Capabilities** → choose your Team. Plug in your iPhone, select it, and press **Run ▶**. The camera does not work in the Simulator, so use a real iPhone.

> Without XcodeGen: Xcode → File → New → Project → iOS App (UIKit, Swift) named `DemoBank`. Delete its generated Swift files and storyboard, drag in the two files from `DemoBank/DemoBank/`, then File → Add Package Dependencies → **Add Local…** → `sdk/ios/LiveAssistSDK`. Add `NSCameraUsageDescription` and `NSMicrophoneUsageDescription` to Info.plist.

### Android (Android Studio Hedgehog or newer)

1. Android Studio → **Open** → select the `sdk/android` folder
2. Wait for Gradle sync. It downloads Gradle 8.7, WebRTC and OkHttp the first time
3. Plug in your phone (USB debugging on), choose the **demo** configuration, press **Run ▶**

### Make a call

1. Laptop: `https://video.rajanassour.com/agent.html` → sign in
2. Phone: open **Cedar Bank** → tap **📹 Advisor**
3. Laptop: **Answer**. The console shows "Customer is in the iOS app" (or Android). Then use **👁 Co-browse** and **✍️ Request control**

No phone handy? `https://video.rajanassour.com/native-sim.html` simulates a native app in the browser, using the exact same protocol.

---

## Add the SDK to your app

### iOS

**1. Add the package.** In Xcode: File → Add Package Dependencies → **Add Local…** → `sdk/ios/LiveAssistSDK`. You can also host it in a Git repository and add it by URL.

**2. Info.plist**
```xml
<key>NSCameraUsageDescription</key><string>Your camera is used for video calls with your advisor.</string>
<key>NSMicrophoneUsageDescription</key><string>Your microphone is used for video calls with your advisor.</string>
```

**3. Configure once and start calls**
```swift
import LiveAssist

// AppDelegate / SceneDelegate
LiveAssist.shared.configure(serverURL: URL(string: "https://video.rajanassour.com")!)

// "Talk to an advisor" button
LiveAssist.shared.startCall(name: customer.firstName, topic: "Cards", reference: sessionRef)

// optional
LiveAssist.shared.onStateChange = { state in /* .connecting, .waiting, .inCall, .ended */ }
LiveAssist.shared.endCall()
LiveAssist.shared.coBrowseEnabled = false   // video + chat only
```

**4. Mark sensitive and commitment UI**
```swift
// UIKit
ibanLabel.liveAssistMasked = true          // painted dark in every frame
confirmButton.liveAssistNoRemote = true    // the advisor can never press it

// SwiftUI
Text(card.number).liveAssistMasked()
Button("Confirm") { … }.liveAssistNoRemote()
```

### Android

**1. Add the module.** Copy `sdk/android/liveassist` into your project, then:
```kotlin
// settings.gradle.kts
include(":liveassist")
// app/build.gradle.kts
dependencies { implementation(project(":liveassist")) }
```
The SDK's manifest already declares the INTERNET, CAMERA and RECORD_AUDIO permissions. Runtime permission is requested when the call starts.

**2. Configure once and start calls**
```kotlin
class MyApp : Application() {
    override fun onCreate() { super.onCreate(); LiveAssist.configure(this, "https://video.rajanassour.com") }
}

// "Talk to an advisor" button (from any activity)
LiveAssist.startCall(name = customer.firstName, topic = "Cards", reference = sessionRef)

// optional
LiveAssist.onStateChange = { state -> /* Connecting, Waiting, InCall, Ended */ }
LiveAssist.endCall()
LiveAssist.coBrowseEnabled = false
```

**3. Mark sensitive and commitment UI**
```kotlin
// Views
LiveAssist.mask(ibanText)
LiveAssist.noRemote(confirmButton)

// Jetpack Compose
Text(card.number, Modifier.liveAssistMasked())
Button(onClick = confirm, modifier = Modifier.liveAssistNoRemote()) { Text("Confirm") }
```

---

## What the advisor can and can't do (both platforms)

| Advisor CAN (with the customer's permission) | Advisor can NEVER |
|---|---|
| See the app's screens (masked areas hidden) | See other apps, notifications, the keyboard, or `FLAG_SECURE` screens |
| Point, and highlight an element | See or fill password, PIN, SMS-code, CVV or card-number fields |
| Type in text fields, choose segment/spinner options | Press buttons marked no-remote |
| Flip switches, tap buttons, tabs, list rows | Press buttons labelled Send, Confirm, Pay, Submit, Approve, Save, Delete, Sign (safety net, even if unmarked) |
| Scroll the screen | Keep control after the customer taps **Take back** or **Stop** |

The rules are enforced **inside the SDK on the phone**, so a modified operator console cannot bypass them.

### Platform notes

- **UIKit / Android Views**: full support.
- **SwiftUI / Jetpack Compose**: viewing, masking (with the modifiers), pointer and no-remote markers are fully supported. Remote taps and typing go through the accessibility tree (the same mechanism VoiceOver and TalkBack use). That works for standard buttons, toggles and text fields, but it's best-effort for custom-drawn controls. Test your key screens.
- **Bandwidth**: about 20–40 KB per changed screen, at most 10 per second, and only when something changes. The SDK backs off automatically on slow connections.
- **Calls over mobile data**: add a TURN relay on the server if some mobile networks show black video (see `DEPLOY-CPANEL.md`).

## How it works

See **[PROTOCOL.md](PROTOCOL.md)**: signaling (WebSocket or long-polling), WebRTC roles, data-channel messages, screen frames, and the remote-control rules. `public/native-sim.html` is a complete reference implementation in JavaScript.

## Status

The console side and the protocol are tested end to end (`native-sim.html`, 21 automated checks). The Swift and Kotlin sources are syntax-checked but were **not compiled in this environment** (no Xcode or Android SDK available). Build them on your Mac. If Xcode or Android Studio reports an error, send it over and it'll be fixed quickly.
