# Mias

A local AI assistant for your Android phone. No internet required. No cloud. Your data stays on your device.

Built for students who spend most of their day on their phone — between classes, commuting, away from a laptop — and need an AI that actually works offline.

---

## What it does

- Chat with an AI model running entirely on your phone
- Offload heavy tasks to your PC over your local network (optional)
- Switch models automatically based on what you're doing (chat, code, reasoning)
- Remember context across sessions
- Work without internet, always

---

## Prerequisites

### For Android (required)
- Android device with Android 10+ (API 35 recommended)
- 4 GB RAM minimum (8 GB recommended for larger models)
- Android Studio (to build from source)
- Java 21+
- ADB (USB debugging enabled on device)

### For Desktop Offload (optional)
- Docker
- Python 3.11+
- GPU with CUDA 12.0+ (CPU works, just slower)
- ~20 GB free disk space for the model

---

## Quick Start

### Build and install the Android app

```bash
# Clone the repo
git clone https://github.com/nikhlgoel/mias.git
cd mias

# Build debug APK (first build takes 3–5 min)
./gradlew assembleDebug

# Connect your phone via USB and install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
adb shell am start -n dev.kid/.app.MainActivity
```

On first launch:
1. Grant permissions (notifications, files, clipboard)
2. Register a biometric (fingerprint or face) — required for app unlock
3. Go to **Brain Market** → download a model (start with MobileLLM, ~1 GB)
4. Start chatting

---

## Desktop Offload (optional)

Use this if you want to run larger, more capable models (like Qwen3 32B) on your PC and access them from your phone over Wi-Fi.

```bash
# Build the server container
cd desktop
docker build -t mias-desktop:latest .

# Download the model (~18 GB, one-time)
pip install huggingface-hub
huggingface-cli download Qwen/Qwen3-Coder-Next-GGUF Qwen3-Coder-Next-32B-Q4_K_M.gguf

# Run the server
docker run --gpus all -p 8400:8400 mias-desktop:latest

# Verify it's running
curl http://localhost:8400/health
# → {"status":"ready","device":"cuda"}
```

Then in the app: **Settings → Networking → enter your PC's IP → Test connection**

---

## Connecting phone and PC (mesh networking)

Mias uses [Tailscale](https://tailscale.com) for secure P2P connection between your phone and desktop — works across different networks, no port forwarding needed.

```bash
# Install Tailscale on both devices
# Android: Play Store → Tailscale
# Desktop:
tailscale up --accept-routes

# Get your desktop IP
tailscale status

# Enter that IP in the app under Settings → Networking
```

---

## Models

| Model | Size | Runs on | Best for |
|-------|------|---------|----------|
| MobileLLM-R1.5 | 1.1 GB | Phone CPU | Lightweight tasks, always-on |
| Gemma-4 INT4 | 3.2 GB | Phone NPU | General chat, faster responses |
| Qwen3-Coder-Next Q4_K_M | 18.5 GB | Desktop GPU | Code, reasoning, heavy tasks |

You can browse and download more models from the **Brain Market** tab inside the app. Supports ONNX and GGUF formats.

---

## Project Structure

```
mias/
├── app/                    # Android app (Kotlin + Jetpack Compose)
├── core/
│   ├── inference/          # ONNX runtime, model loading
│   ├── agent/              # Tool use (files, web, clipboard, etc.)
│   ├── data/               # Room DB, conversation memory
│   ├── security/           # Biometric gate, AES-256 encryption
│   ├── network/            # Ktor client, Tailscale, desktop bridge
│   └── soul/               # Personality/tone adaptation
├── desktop/                # Python FastAPI + llama.cpp server
│   ├── server.py
│   ├── Dockerfile
│   └── requirements.txt
├── scripts/
│   └── init.sh             # Setup, lint, test checks
├── docs/
│   ├── SETUP.md
│   └── V4_ARCHITECTURE.md
└── README.md
```

---

## Privacy

Everything runs locally. There are no cloud calls anywhere in the codebase.

- No OpenAI, Anthropic, or any cloud AI API
- No Firebase, analytics, or crash reporting
- All conversations encrypted on-device (SQLCipher + AES-256-GCM)
- Biometric lock required on every app open
- Android backup blocked by policy (your data doesn't leave the device)
- Desktop offload goes only over your local network or Tailscale mesh (WireGuard encrypted)

---

## Running Tests

```bash
# Unit tests
./gradlew testDebugUnitTest

# Device tests (requires connected phone)
./gradlew connectedDebugAndroidTest

# Desktop server tests
cd desktop && python -m pytest tests/
```

---

## Tech Stack

| Layer | Tech |
|-------|------|
| Language | Kotlin 2.1.21 |
| UI | Jetpack Compose + Material 3 |
| On-device inference | ONNX Runtime Mobile |
| Desktop inference | llama.cpp + FastAPI |
| Database | Room DB + SQLCipher |
| Networking | Ktor + Tailscale |
| Background tasks | WorkManager |
| DI | Hilt |

---

## Status

- Android app: complete, all features implemented
- Desktop server: complete, needs model download to run
- Mesh networking: complete
- LoRA fine-tuning pipeline: planned
- Thermal ML training data: planned

---

## License

Proprietary — personal use on owned hardware only.

Built on open source: Kotlin (Apache 2.0), ONNX Runtime (MIT), llama.cpp (MIT), FastAPI (BSD), Tailscale (BSL 1.1), Gemma (Google DeepMind license).
