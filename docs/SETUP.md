# Mias — Setup Guide

> **Local AI. No cloud. Your data.**  
> Build and run Mias on Android and Desktop

---

## 📋 Table of Contents

1. [Quick Start](#-quick-start)
2. [System Requirements](#-system-requirements)
3. [Prerequisites & Dependencies](#-prerequisites--dependencies)
4. [Android Mobile Setup](#-android-mobile-setup)
5. [Desktop PC Setup](#-desktop-pc-setup)
6. [Tailscale Mesh Network](#-tailscale-mesh-network)
7. [Model Downloads](#-model-downloads)
8. [First Run & Testing](#-first-run--testing)
9. [Troubleshooting](#-troubleshooting)
10. [Next Steps](#-next-steps)

---

## 🚀 Quick Start

### For Android Users
```bash
# 1. Clone repository
git clone https://github.com/nikhlgoel/mias.git
cd mias

# IMPORTANT: Run all commands from project root (mias/), NOT mias/gradle/

# 1.5 If wrapper error appears (GradleWrapperMain), bootstrap it once:
# Windows:
scripts/bootstrap-gradle.bat
# macOS/Linux:
./scripts/bootstrap-gradle.sh

# 2. Connect Android device (USB debugging enabled)
adb devices

# 3. Build & run
./gradlew assembleDebug   # Windows can also use: gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 4. Launch app on device
adb shell am start -n dev.kid.app/.ui.MainActivity
```

### For Desktop (Model Server)
```bash
# 1. Build container
cd desktop
docker build -t kid-desktop-server .

# 2. Run with GPU support
docker run --gpus all -p 8400:8400 kid-desktop-server

# 3. Verify health
curl http://localhost:8400/health
```

---

## 📱 System Requirements

### Android Target Device

| Requirement | Minimum | Recommended |
|---|---|---|
| **OS Version** | Android 17 (SDK 35) | Android 17+ |
| **RAM** | 6 GB | 12+ GB |
| **Storage** | 8 GB free | 16+ GB free |
| **Processor** | Snapdragon 7 Gen 1 | Snapdragon 8 Gen 2 Ultra |
| **NPU** | Qualcomm Hexagon (Q6) | Qualcomm Hexagon (Q7) with Qwen native ops |
| **Battery** | 4,500+ mAh | 6,000+ mAh |
| **Biometric** | Preferred (Class 3+) | Fingerprint + Face (Class 3) |

**Tested Devices:**
- Realme Narzo 80 Pro (Snapdragon 7 Gen 1, 12GB RAM, Hexagon Q6)
- OnePlus 13 (Snapdragon 8 Gen 3, 16GB+ RAM, Hexagon Q7)

### Desktop (PC/Linux Server)

| Component | Requirement |
|---|---|
| **CPU** | Intel i7/i9 (11th gen+) or AMD Ryzen 7/9 (5000 series+) |
| **GPU** | NVIDIA RTX A2000/3060 (12GB+ VRAM) or equivalentCUDA 12.0+, cuDNN 9.x |
| **RAM** | 16 GB+ system RAM |
| **Storage** | 30 GB free (for Qwen3-Coder-Next GGUF) |
| **Network** | Gigabit LAN recommended |
| **OS** | Ubuntu 22.04 LTS, Windows 11, or macOS 12+ |

---

## 🔧 Prerequisites & Dependencies

### Global (Both Android & Desktop)

1. **Git** (latest)
   ```bash
   git --version  # Verify installed
   ```

2. **Docker & Docker Compose** (for desktop model server)
   ```bash
   docker --version
   docker-compose --version
   
   # On Windows: Install Docker Desktop
   # On Linux: sudo apt-get install docker.io docker-compose
   # On macOS: brew install docker docker-compose
   ```

### Android Only

1. **Java Development Kit (JDK 21)**
   ```bash
   java -version
   #Expected: openjdk 21.x.x
   ```

2. **Android Studio** (latest)
   - Download: https://developer.android.com/studio
   - Install Android SDK 35 (API level for Android 17)
   - Install Android Build Tools 35.x.x
   - Install Kotlin 2.1.21

3. **Android SDK Tools**
   ```bash
   # In Android Studio:
   # SDK Manager → SDK Platforms → Install "Android 17 (API 35)"
   # SDK Manager → SDK Tools → Install "Android SDK Build-Tools 35.x.x"
   ```

4. **Gradle Wrapper** (commit to repo)
   - Already present in `/gradle/wrapper/`
   - No manual installation needed

5. **USB Debugging Enabled on Device**
   - Settings → Developer Options → USB Debugging ☑️
   - Settings → Developer Options → Wireless Debugging ☑️ (optional)

### Desktop Only

1. **Python 3.11+**
   ```bash
   python --version  # Expect 3.11+
   ```

2. **CUDA Toolkit 12.0+** (for NVIDIA GPU acceleration)
   ```bash
   nvcc --version  # Verify CUDA compiler
   ```

3. **cuDNN 9.x** (NVIDIA Deep Learning library)
   - Download: https://developer.nvidia.com/cudnn
   - Extract to CUDA installation path

4. **Tailscale** (P2P mesh networking)
   ```bash
   # On Linux
   curl -fsSL https://tailscale.com/install.sh | sh
   
   # On Windows / macOS
   # Download from https://tailscale.com/download
   ```

---

## 📱 Android Mobile Setup

### Step 1: Clone & Setup Project

```bash
# Clone repository
git clone https://github.com/nikhlgoel/mias.git
cd mias

# Initialize build environment
./scripts/init.sh --check
# Expected: ✅ All checks passed (lint, format, tests)
```

### Step 2: Connect Android Device

```bash
# Enable USB debugging on device (Settings → Developer Options)
# Connect via USB

# Verify connection
adb devices
# Expected output:
# List of attached devices
# emulator-5554          device
# OR your device ID      device
```

### Step 3: Build & Install APK

```bash
# Debug build (fastest for development/testing)
./gradlew assembleDebug
# Windows alternative:
# gradlew.bat assembleDebug

# Alternatively, signed debug APK with biometric support
./gradlew assembleDebug -Pandroid.enableOnDemandModules=false

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Expected: Success

# Or use Android Studio: Run → Run 'app'
```

### Step 4: Launch App

```bash
# Via ADB
adb shell am start -n dev.kid.app/.ui.MainActivity

# Or directly from device home screen
# Tap Mias icon (neural eye animation)
```

### Windows Command Location (Exact)

Run these from your terminal at:

```bash
cd w:/###
```

Then run:

```bash
./scripts/bootstrap-gradle.sh   # if gradle-wrapper.jar missing (Git Bash)
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.kid.app/.ui.MainActivity
```

### Step 5: Grant Permissions

On first launch, grant these permissions (Android Privacy Dashboard):
- ✅ Notifications
- ✅ Camera (for biometric + on-device vision)
- ✅ Microphone (future voice input)
- ✅ Files & Media (for agent file operations)
- ✅ Clipboard (for clipboard monitoring)

**Note**: App will not function without these permissions. Biometric gate requires:
- Settings → Biometrics → Register fingerprint AND/OR face

### Step 6: Configure Privacy & Security

On first launch:
1. **Privacy Consent** → Review and accept local-only data policy
2. **Biometric Authentication** → Register fingerprint/face
3. **Manual Data Access Consent** → Enable (required for file/export operations)
4. **Tailscale Connection** (optional) → Link Tailscale account for mesh networking

---

## 🖥️ Desktop PC Setup

### Step 1: Install System Dependencies

#### On Ubuntu/Debian Linux
```bash
# Update package manager
sudo apt-get update && sudo apt-get upgrade -y

# Install Python, CUDA, cuDNN
sudo apt-get install -y python3.11 python3.11-venv python3-pip
sudo apt-get install -y nvidia-cuda-toolkit nvidia-cudnn  # if NVIDIA GPU

# Verify installations
python3.11 --version
nvcc --version
```

#### On Windows 11
```powershell
# Using Windows Package Manager (recommended)
winget install Python.Python.3.11
winget install Docker.Docker
winget install NVIDIA.CUDA

# Verify
python --version
```

#### On macOS
```bash
brew install python@3.11
brew install docker
# For Mac with Apple Silicon (M1/M2):
# Models run on CPU (no CUDA needed — llama.cpp handles it)
```

### Step 2: Clone Repository

```bash
git clone https://github.com/nikhlgoel/mias.git
cd mias
```

### Step 3: Build Desktop Model Server

```bash
cd desktop

# Build Docker image (includes Qwen3-Coder-Next + llama.cpp)
docker build \
  --build-arg CUDA_VERSION=12.4 \
  --build-arg PYTHON_VERSION=3.11 \
  -t kid-desktop-server:latest \
  .

# Expected: Successfully tagged kid-desktop-server:latest
```

### Step 4: Download Model Weights

Before running the server, download the Qwen3-Coder-Next model (quantized GGUF):

```bash
# From HuggingFace (using huggingface-cli)
pip install huggingface-hub

# Download Qwen3-Coder-Next-32B-Q4_K_M.gguf (~18GB)
huggingface-cli download \
  Qwen/Qwen3-Coder-Next-32B-GGUF \
  Qwen3-Coder-Next-32B-Q4_K_M.gguf \
  --local-dir ./models

# Expected: Model saved to ./models/Qwen3-Coder-Next-32B-Q4_K_M.gguf
```

Alternatively, using `curl`:
```bash
# 1. Download from HuggingFace directly
curl -L -o ~/.cache/kid-models/qwen3-coder-next.gguf \
  https://huggingface.co/Qwen/Qwen3-Coder-Next-GGUF/resolve/main/Qwen3-Coder-Next-32B-Q4_K_M.gguf

# 2. Mount in Docker container (see next step)
```

### Step 5: Run Model Server

```bash
# Option A: With GPU (NVIDIA)
docker run \
  --gpus all \
  -p 8400:8400 \
  -v ~/.cache/kid-models:/models:ro \
  -e MODEL_PATH=/models/qwen3-coder-next.gguf \
  -e DEVICE=cuda \
  kid-desktop-server:latest

# Option B: CPU-only (slower, but works everywhere)
docker run \
  -p 8400:8400 \
  -v ~/.cache/kid-models:/models:ro \
  -e MODEL_PATH=/models/qwen3-coder-next.gguf \
  -e DEVICE=cpu \
  -e NUM_THREADS=12  # Adjust to your CPU core count
  kid-desktop-server:latest

# Expected output:
# Uvicorn running on http://0.0.0.0:8400
```

### Step 6: Verify Server Health

```bash
# In another terminal, test the health endpoint
curl http://localhost:8400/health
# Expected JSON response:
# {"status": "ready", "model": "qwen3-coder-next", "device": "cuda"}

# Test inference endpoint
curl -X POST http://localhost:8400/inference \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Write a Python hello world", "max_tokens": 100}'
# Expected: Generated code snippet
```

---

## 🔗 Tailscale Mesh Network

Tailscale enables secure P2P communication between Android and Desktop without port forwarding or firewall configuration.

### Step 1: Install Tailscale

**Android:**
- Google Play Store → Search "Tailscale" → Install
- Or: https://play.google.com/store/apps/details?id=com.tailscale.ipn

**Desktop:**
```bash
# Linux (Ubuntu/Debian)
curl -fsSL https://tailscale.com/install.sh | sh

# Windows / macOS
# Download from https://tailscale.com/download
```

### Step 2: Authenticate Both Devices

```bash
# Android: Open Tailscale app → Tap "CONNECT" → Follow auth URL

# Desktop (CLI)
sudo tailscale up --accept-routes

# This opens browser → authenticate with GitHub/Google account
```

### Step 3: Enable MCP Bridge

```bash
# On Desktop, start MCP server bridge
./scripts/init.sh --mcp

# Expected output:
# MCP Bridge listening on Tailscale IP: 100.x.x.x:9090
```

### Step 4: Configure Android to Connect

In {Kid} Android app:
1. Settings → Networking → Tailscale
2. Toggle "Enable Tailscale Bridge" ☑️
3. Input Desktop MCP API: 100.x.x.x:9090 (Tailscale IP of desktop)
4. Tap "Test Connection" → Should see ✅ Connected

---

## 🧠 Model Downloads

### Supported Models

| Model | Size | Type | Best For | Hardware |
|---|---|---|---|---|
| **Gemma-4-e4b** | 3 GB (INT4) | ONNXLiteRT-LM | Mobile chat | Android NPU / CPU |
| **MobileLLM-R1.5** | 1 GB (INT4) | ONNX | Fast survival | Android CPU |
| **Qwen3-Coder-Next** | 18 GB (Q4_K_M GGUF) | llama.cpp | Code & reasoning | Desktop GPU |

### Step 1: Download to Android

**Via App UI (Recommended):**
1.  Brain Market (bottom nav)
2. Filter by role: CODE, CHAT, REASONING
3. Tap model card → Tap "Download"
4. Observe progress bar (pause/resume supported)
5. Model auto-installs in `{Kid}-models` directory

**Via ADB (CLI):**
```bash
# Download Gemma INT4 directly
adb shell am startservice dev.kid/.app.services/ModelDownloadService \
  --es model_url "https://huggingface.co/.../gemma-4-e4b-int4.onnx" \
  --es model_name "gemma-4-e4b"
```

### Step 2: Verify Models

```bash
# Android (via ADB)
adb shell ls /data/data/dev.kid/files/models/

# Desktop (check Docker mounted path)
ls ~/.cache/kid-models/
```

---

## 🧪 First Run & Testing

### Android First Launch

1. **Splash Screen** → Shows animated neural eye (phase-in animation)
2. **Privacy Consent** → Review data isolation policy
3. **Biometric Setup** → Register fingerprint or face
4. **Home Screen** → Central breathingOrb, nudges panel, 6 nav buttons
5. **Try First Chat** → "Hi Kid, tell me a joke" → Response from available model

### Desktop Model Server First Run

```bash
# Test basic inference
curl -X POST http://localhost:8400/inference \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Explain Tailscale in 2 sentences",
    "temperature": 0.7,
    "max_tokens": 100
  }'

# Test with role-based routing
curl -X POST http://localhost:8400/inference \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "def factorial(n):",
    "role": "CODE",
    "temperature": 0.1,
    "max_tokens": 50
  }'
```

### Cross-Device Testing

1. Both devices on Tailscale mesh
2.  Android
3. Settings → Inference → Select "Desktop (Remote)"
4. Send chat message → Offloads to desktop Qwen3 server via MCP bridge
5. Response should include `[BRAIN: QWEN_DESKTOP]` tag in UI

---

## 🐛 Troubleshooting

### Android Issues

#### Build Fails with "Gradle sync issues"
```bash
# Clean and rebuild
./gradlew clean
./gradlew build

# If still fails, clear cache
rm -rf ~/.gradle/caches
rm -rf .gradle
./gradlew build
```

#### "Failed to install APK"
```bash
# Check device storage
adb shell df /data

# Uninstall previous build
adb uninstall dev.kid

# Re-install
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### Biometric Not Working
```bash
# Verify biometric availability
adb shell getprop ro.hardware.fingerprint
adb shell getprop ro.hardware.nfc

# Re-register biometric in Settings → Biometrics
```

#### Models Not Downloading
```bash
# Check network connectivity
adb shell ping 8.8.8.8

# Check storage space
adb shell du -sh /data/data/dev.kid/

# Manually trigger download
adb logcat | grep ModelDownloadManager
```

### Desktop Issues

#### Docker: "No NVIDIA Runtime"
```bash
# Verify NVIDIA Docker runtime
docker run --rm --gpus all nvidia/cuda:12.4-runtime nvidia-smi

# If fails, install nvidia-docker
distribution=$(. /etc/os-release;echo $ID$VERSION_ID)
curl -s -L https://nvidia.github.io/nvidia-docker/gpgkey | sudo apt-key add -
sudo apt-get update && sudo apt-get install -y nvidia-docker2
sudo systemctl restart docker
```

#### Model Server Slow Response
```bash
# Check GPU utilization
nvidia-smi

# Check model is loaded (should see memory usage)
docker exec <container-id> nvidia-smi

# Reduce batch size or max_tokens
# Re-run inference with lower max_tokens=50
```

#### Can't Connect to Desktop from Android
```bash
# Verify Tailscale is running on both devices
tailscale status  # Desktop
# In app: Settings → Networking → Verify Tailscale IP

# Check firewall
sudo ufw allow 8400/tcp  # Linux

# Test connectivity
ping 100.x.x.x  # Ping desktop Tailscale IP from Android (via app)
curl http://100.x.x.x:8400/health  # Test API
```

---

## ✅ Next Steps

After successful setup:

1. **Explore Brain Market** → Download 2-3 models for different roles
2. **Configure Preferences** → Settings → Persona (empathy, humor, tech-level)
3. **Enable Evolution** → Settings → Evolution → Toggle "Learn from Interactions"
4. **Test Agent Capabilities** → Ask Kid to: read file, fetch webpage, run calculation
5. **Deploy on Tailscale** → Connect desktop + mobile for seamless offloading
6. **Monitor Thermal** → Settings → Thermal Status → Watch model switching on thermal throttle
7. **Review Hindsight Memory** → Settings → Memory → See consolidation progress

---

## 📞 Support & Debugging

```bash
# View detailed logs
adb logcat | grep "kid"  # Android

# Desktop container logs
docker logs <container-id>

# Validate pre-commit checks
./scripts/init.sh --check
```

**Still stuck?** Open an issue: https://github.com/nikhlgoel/mias/issues

---

**Mias** | [Architecture Docs](./V4_ARCHITECTURE.md) | [Privacy Policy](../README.md#-privacy--security)
