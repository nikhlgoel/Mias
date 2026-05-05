Here’s the same content, reformatted for maximum visual clarity—clean headings, bold keys, code blocks, and a scannable table.

---

# Quick Start: Mias — Local AI Assistant

**Mias** — Android, multi-module Kotlin app.  
On-device speech-to-text, multi-brain inference, privacy enforcement.

---

## 📋 Prerequisites

- **Android SDK** (API 34+)
- **JDK** 17+
- **ADB** (Android Debug Bridge)
- **Git**
- Physical device/emulator with **Google Play Services** (required for ML Kit)

---

## ⬇️ Clone & Bootstrap

```bash
git clone <repo-url>
cd mias
```

If `gradlew` is missing or broken:

**Windows**
```batch
scripts\bootstrap-gradle.bat
```

**macOS / Linux**
```bash
chmod +x scripts/bootstrap-gradle.sh
./scripts/bootstrap-gradle.sh
```

---

## 🔨 Build

> ⚠️ Always run from the **project root**, not the `gradle/` directory.

**Windows**
```batch
gradlew.bat assembleDebug
```

**macOS / Linux**
```bash
./gradlew assembleDebug
```

**Output APK**  
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📲 Install & Launch

**Install**
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Launch**
```bash
adb shell am start -n dev.kid.app/.app.ui.MainActivity
```

---

## 🚀 First Launch Flow

1. **Splash screen** (neural eye animation)
2. **Permission dialogs** (one at a time):
   - `RECORD_AUDIO` — microphone (required for speech)
   - `READ_EXTERNAL_STORAGE` — optional (file access)
   - `CAMERA` — optional (biometric)
   - Notifications — optional
3. **Biometric registration** (if camera granted)
4. **Main Chat screen** loads

---

## 🎤 Speech-to-Text Usage

1. Open the **Chat screen**
2. Tap the **microphone button** (bottom input area)
3. Speak → button **pulses red** while recording
4. Real‑time **partial transcription** is shown
5. Tap **Stop** (or auto‑stop) → final text placed in input bar
6. Tap **Send**

Configure language / confidence:  
**Settings → Speech & Transcription**

---

## 🛠️ Common Build Issues

| Error | Fix |
|-------|-----|
| `Could not find or load main class org.gradle.wrapper.GradleWrapperMain` | Run from **project root**, use `gradlew.bat` (Win) or `./gradlew` (Unix) |
| `gradlew: command not found` | Run the bootstrap script first |
| APK build fails – missing SDK | Install required platforms via Android Studio’s SDK Manager |
| `adb: device not found` | Enable USB debugging; run `adb devices` |
| Microphone permission denied | Grant when prompted, or via **Settings → App Permissions → Microphone** |
| Speech transcription not working | Check internet (ML Kit models download on first use); test mic in another app |

---

## 📁 Project Structure (Key Modules)

```
core/speech/        - Google ML Kit speech recognition
core/model-hub/     - Multi-brain model router (GEMMA, MobileLLM, Qwen3)
core/agent/         - Device-action agents (file, web, calc, etc.)
core/soul/          - LoRA personality blending
core/thermal/       - Thermal-aware model downswitching
core/ui/            - Shared UI components (SpeechButton, FAB)
app/                - Main app, permissions, UI screens
```

---

## ✅ Minimal Verification Checklist

- [ ] APK builds and installs
- [ ] App launches without crash
- [ ] Splash screen renders
- [ ] Permission dialogs appear
- [ ] Chat screen loads
- [ ] Microphone button visible & functional
- [ ] Transcription appears in input bar
