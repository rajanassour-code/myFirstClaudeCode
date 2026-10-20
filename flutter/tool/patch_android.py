"""Run after `flutter create` in demo_bank: adds permissions, app name, minSdk and R8 keep rules."""
import pathlib, re
app = pathlib.Path('android/app')
m = app / 'src/main/AndroidManifest.xml'
s = m.read_text()
perms = ['INTERNET', 'CAMERA', 'RECORD_AUDIO', 'MODIFY_AUDIO_SETTINGS', 'ACCESS_NETWORK_STATE', 'BLUETOOTH_CONNECT']
add = ''.join(f'    <uses-permission android:name="android.permission.{p}"/>\n' for p in perms if p not in s)
add += '    <uses-feature android:name="android.hardware.camera" android:required="false"/>\n'
s = re.sub(r'(<manifest[^>]*>\n)', lambda mm: mm.group(1) + add, s, count=1)
s = re.sub(r'android:label="[^"]*"', 'android:label="Cedar Bank"', s)
m.write_text(s)
for name in ['build.gradle.kts', 'build.gradle']:
    g = app / name
    if g.exists():
        t = g.read_text()
        t = t.replace('minSdk = flutter.minSdkVersion', 'minSdk = 24').replace('minSdkVersion flutter.minSdkVersion', 'minSdkVersion 24')
        g.write_text(t)
        print('patched', g)
(app / 'proguard-rules.pro').write_text('-keep class org.webrtc.** { *; }\n-keep class com.cloudwebrtc.webrtc.** { *; }\n-dontwarn org.webrtc.**\n')
print(s[:900])
