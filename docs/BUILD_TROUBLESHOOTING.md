# ⚠️ CRITICAL: Build Locations & Setup Instructions

## 🚨 WHERE TO RUN COMMANDS

### WRONG ❌
```bash
cd mias/gradle/
./gradlew assembleDebug          # ❌ WRONG - gradle dir has no gradlew!
```

### CORRECT ✅
```bash
cd mias/                         # ✅ PROJECT ROOT
./gradlew assembleDebug
```

---

## 🔧 Fix Gradle Wrapper (Missing gradle-wrapper.jar)

If you get `ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain`, the gradle-wrapper.jar is missing. Fix it:

### Option 1: Windows (Easiest)
```batch
cd w:\###                              # Go to PROJECT ROOT
scripts\bootstrap-gradle.bat           # Run bootstrap script
gradlew.bat assembleDebug              # Build (use .bat on Windows)
```

### Option 2: macOS / Linux
```bash
cd /path/to/mias                       # Go to PROJECT ROOT
chmod +x scripts/bootstrap-gradle.sh
./scripts/bootstrap-gradle.sh          # Run bootstrap script
./gradlew assembleDebug                # Build
```

### Option 3: Manual Gradle Setup (Android Studio)
1. Open project in Android Studio
2. File → Project Structure → Project Settings → Gradle
3. Check "Use gradle wrapper with updated gradle/wrapper/"
4. Click "OK"  
5. Gradle will auto-download wrapper JAR

---

## 📋 Complete Build Checklist

Before running `./gradlew assembleDebug`, verify:

- [ ] **Java 21 installed**: `java -version` → expect openjdk 21.x.x
- [ ] **Android SDK 35 installed**: Android Studio → SDK Manager → API 35 checked
- [ ] **USB Debugging enabled**: Device Settings → Developer Options → USB Debugging ☑️
- [ ] **Device connected**: `adb devices` → device shows as "device" (not "offline")
- [ ] **Project root**: `ls gradle/wrapper/gradle-wrapper.properties` exists
- [ ] **Gradle wrapper JAR**: `ls gradle/wrapper/gradle-wrapper.jar` exists OR run bootstrap
- [ ] **Network access**: Can reach github.com (for dependencies)

---

## 🚀 Full Build & Install Steps (Windows)

```batch
REM 1. Navigate to PROJECT ROOT (NOT gradle folder!)
cd w:\###

REM 2. Fix gradle wrapper if needed
scripts\bootstrap-gradle.bat

REM 3. Clean previous builds
gradlew.bat clean

REM 4. Build debug APK
gradlew.bat assembleDebug

REM 5. Verify APK exists
dir app\build\outputs\apk\debug\app-debug.apk

REM 6. Connect device
adb devices

REM 7. Install APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

REM 8. Launch app
adb shell am start -n dev.kid.app/.app.ui.MainActivity

REM 9. View live logs
adb logcat | findstr "kid"
```

---

## 🚀 Full Build & Install Steps (macOS / Linux)

```bash
# 1. Navigate to PROJECT ROOT (NOT gradle folder!)
cd /path/to/mias

# 2. Fix gradle wrapper if needed
chmod +x scripts/bootstrap-gradle.sh
./scripts/bootstrap-gradle.sh

# 3. Clean previous builds
./gradlew clean

# 4. Build debug APK
./gradlew assembleDebug

# 5. Verify APK exists
ls -lh app/build/outputs/apk/debug/app-debug.apk

# 6. Connect device
adb devices

# 7. Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 8. Launch app
adb shell am start -n dev.kid.app/.app.ui.MainActivity

# 9. View live logs
adb logcat | grep kid
```

---

## ✅ Expected Output After Build

```
BUILD SUCCESSFUL in 2m 34s
143 actionable tasks, 143 executed

# APK location:
app/build/outputs/apk/debug/app-debug.apk (45 MB)
```

---

## 🎤 NEW: Speech-to-Text AI (ChatGPT-Level)

### Features Added ✨
- ✅ **On-device speech recognition** (Google ML Kit)
- ✅ **Real-time transcription** with confidence scores
- ✅ **Multi-language support** (13 languages)
- ✅ **Auto-language detection** (optional)
- ✅ **Permission flow** (requests on startup)
- ✅ **Beautiful UI** (pulsing mic button, status indicators)
- ✅ **Settings menu** (language selection, auto-detect toggle)

### First Launch Flow
1. **Splash Screen** (animated neural eye)
2. **Permission Requests** (one-by-one):
   - 🎤 Microphone (RECORD_AUDIO)
   - 📁 Files & Media (READ_EXTERNAL_STORAGE)
   - 📷 Camera (for biometric auth)
   - 🔔 Notifications (optional)
3. **Privacy Consent** (data isolation policy)
4. **Biometric Registration** (fingerprint/face)
5. **Main App** with speech-to-text ready!

### How to Use Speech-to-Text
1. Open Chat screen
2. Tap **🎤 Microphone button**
3. **Speak clearly** (device records audio)
4. Tap **Stop** or wait for silence auto-detection
5. **Message appears in chat input bar**  
6. Hit Send!

### Language Settings
Settings → Speech & Transcription:
- Language: [English US, English UK, Spanish, French, German, Hindi, Japanese, Chinese, ...]
- Auto-detect: Toggle ON/OFF
- Confidence threshold: Adjust sensitivity

---

## 🐛 Troubleshooting

### Build fails: `Could not find or load main class`
```bash
✅ FIX: Run from PROJECT ROOT
cd w:\###
./gradlew assembleDebug
```

### Build fails: `Permission denied: ./gradlew`
```bash
# macOS/Linux only
chmod +x gradlew
./gradlew assembleDebug
```

### APK won't install: `adb: failed to stat`
```bash
✅ FIX: APK must exist first - build succeeded?
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Microphone not working
```bash
# Check permission
adb shell getprop ro.hardware.microphone

# Re-run permission request
# In app: Settings → Permissions → Grant Microphone
```

### Speech weird/inaccurate
- Ensure device has INTERNET permission (ML Kit downloads models)
- Try quieter environment
- Adjust confidence threshold in Settings
- Check language is correct

---

## 📊 Verification Checklist

After first successful build:

- [ ] APK installed on device
- [ ] App launches without crashes
- [ ] Splash screen animation smooth
- [ ] Permission requests appear (4 total)
- [ ] Can grant each permission
- [ ] Biometric registration completes
- [ ] Home screen shows central orb
- [ ] Chat screen visible
- [ ] **🎤 Microphone button visible in chat**
- [ ] Tap mic → says "Listening..."
- [ ] Speak → text appears in preview
- [ ] Text shows in chat input bar
- [ ] Can submit message

---

## 🎯 New Modules Added

| Module | Purpose | Files |
|--------|---------|-------|
| **core:speech** | Speech-to-text with ML Kit | SpeechEngine.kt, SpeechViewModel.kt |
| **app:permissions** | Permission request flow | PermissionHandler.kt |
| **core:ui** | Speech UI components | SpeechButton.kt (new) |

---

## 📚 Documentation

- Original setup guide: [docs/SETUP.md](../docs/SETUP.md)
- Architecture: [docs/V4_ARCHITECTURE.md](../docs/V4_ARCHITECTURE.md)
- Deployment: [docs/DEPLOYMENT_STATUS.md](../docs/DEPLOYMENT_STATUS.md)

---

**Got stuck? Read this first, then check troubleshooting! 🚀**
