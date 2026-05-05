Quick Start
Mias — Local AI Assistant (Android)
Multi-module Kotlin app with on-device speech-to-text, multi-brain inference, and privacy enforcement.

Prerequisites
Android SDK (API 34+)

JDK 17+

ADB (Android Debug Bridge)

Git

Physical device or emulator with Google Play Services (for ML Kit)

Clone & Bootstrap
bash
git clone <repo-url>
cd mias
If gradlew is missing or broken:

Windows

batch
scripts\bootstrap-gradle.bat
macOS / Linux

bash
chmod +x scripts/bootstrap-gradle.sh
./scripts/bootstrap-gradle.sh
Build
Run from the project root (not the gradle/ directory).

Windows

batch
gradlew.bat assembleDebug
macOS / Linux

bash
./gradlew assembleDebug
APK appears at:
app/build/outputs/apk/debug/app-debug.apk

Install & Launch
Install

bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
Launch

bash
adb shell am start -n dev.kid.app/.app.ui.MainActivity
First Launch Flow
Splash screen appears (neural eye animation).

Permission dialogs appear one at a time:

RECORD_AUDIO (microphone – required for speech)

READ_EXTERNAL_STORAGE (optional for file access)

CAMERA (optional for biometric)

Notifications (optional)

Biometric registration prompt (if camera granted).

Main Chat screen loads.

Using Speech-to-Text
Open Chat screen.

Tap microphone button (bottom input area).

Speak; button pulses red while recording.

Real-time partial transcription is shown.

Tap Stop or pause; final transcription is placed in the message input bar.

Tap Send to submit the text.

Language and confidence settings are in Settings → Speech & Transcription.

Common Build Issues
Error	Fix
Could not find or load main class org.gradle.wrapper.GradleWrapperMain	Run from project root, not gradle/. Use gradlew.bat (Windows) or ./gradlew (Unix).
gradlew: command not found	Run the bootstrap script first.
APK build fails with missing SDK	Install required SDK platforms via Android Studio’s SDK Manager.
adb: device not found	Enable USB debugging on device and run adb devices to confirm.
Microphone permission denied at first launch	Grant when prompted, or manually via system Settings → App Permissions → Microphone.
Speech transcription not working	Ensure internet connection (ML Kit downloads models on first use). Check microphone works in another app.
Project Structure (Key Modules)
text
core/speech/        - Google ML Kit speech recognition
core/model-hub/     - Multi-brain model router (GEMMA, MobileLLM, Qwen3)
core/agent/         - Device-action agents (file, web, calc, etc.)
core/soul/          - LoRA personality blending
core/thermal/       - Thermal-aware model downswitching
core/ui/            - Shared UI components (SpeechButton, FAB)
app/                - Main app, permissions, UI screens
Verification (Minimal)
APK builds and installs

App launches without crash

Splash screen renders

Permission dialogs appear

Chat screen loads

Microphone button is visible and functional

Transcription appears in input bar
