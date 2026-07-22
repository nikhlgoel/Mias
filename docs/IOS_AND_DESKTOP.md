# S8 — iOS & Desktop Readiness

Where the codebase stands for shipping beyond Android, and the concrete plan to
finish each target. **Honest constraint:** this stage was prepared on Windows
with no macOS/Xcode, so the Swift below is a **reference/starting point that has
not been compiled**, and the iOS app has not been built or run. What *is* proven
here is that the architecture is ready for it.

## 1. Why the codebase is already iOS-ready

The migration deliberately split the app into a **platform-free TypeScript core**
+ **thin native adapters per platform**. That split is what makes iOS mostly a
matter of writing the adapters, not re-writing the app.

- **Shared TS core is portable, and proven so.** `@mias/bridge-protocol` (frames,
  SecureChannel, MCP client) and `@mias/domain` (ReAct parsing, chat session,
  personas, sanitation) have **zero platform dependencies** and pass **51 tests on
  Node** — the same Hermes-class JS engine iOS uses. All screens, navigation,
  theming, and the Bridge protocol run unchanged on iOS.
- **The app degrades gracefully with no Swift at all.** Every native-module TS
  wrapper (`mobile/src/native/*.ts`) guards on `isAvailable` (`NativeModules.MiasX
  != null`). On iOS, before any Swift module exists, `MiasInference`,
  `MiasData`, … are simply absent, so each wrapper reports unavailable and the UI
  shows the empty/degraded state it was already designed for. **The iOS app builds
  and runs today** (UI + shared logic + Bridge transport); native features light
  up as their Swift adapters are added — incrementally, never a big-bang.
- **The Bridge works cross-platform now.** The relay + session server are
  platform-neutral Node/TS; the phone side needs only a `CryptoProvider` (see
  §3, the same native piece Android still needs). Nothing about the Bridge is
  Android-specific.

## 2. iOS build path (for a macOS/Xcode environment)

```sh
cd mobile
npm install
cd ios && pod install        # CocoaPods links RN + autolinked libs
# then: open ios/MiasMobile.xcworkspace in Xcode, or:
npx react-native run-ios
```

- New Architecture is already enabled (`newArchEnabled=true`); iOS Fabric/TurboModules
  come from the same flag.
- Autolinked libs (`react-native-screens`, `safe-area-context`,
  `image-picker`) ship iOS pods — `pod install` wires them.
- Signing/identifiers: mirror `io.mias.app` as the iOS bundle id; FaceID needs
  `NSFaceIDUsageDescription`, mic needs `NSMicrophoneUsageDescription`, camera
  `NSCameraUsageDescription`, speech `NSSpeechRecognitionUsageDescription` in
  `Info.plist`.
- Rename the template `MiasMobile` scheme/target to Mias when iOS work starts
  (kept as-is now to avoid churn on an unbuildable target).

## 3. Per-native-module iOS plan

Each Android Kotlin module (`mobile/android/.../bridge/*BridgeModule.kt`) needs a
Swift counterpart exposing the **same `NativeModules` name + method signatures**,
so the existing TS wrappers work unchanged. Effort is relative.

| Module (JS name) | Android today | iOS implementation | Effort |
|---|---|---|---|
| `MiasSecurity` | ZkVault (Keystore) + BiometricPrompt | **Keychain** + **LocalAuthentication** (FaceID/TouchID) | Low — see §4 reference |
| `MiasThermal` | TawsGovernor snapshot | `ProcessInfo.thermalState` + `UIDevice.batteryLevel` | Low |
| `MiasPrefs` | DataStore | `UserDefaults` (or the same TS-readable store) | Low |
| `MiasData` | Room + Hindsight + RAG | **New iOS persistence** behind the same JSON contract (SQLite via GRDB) + an embedding provider; RAG logic already lives in the shared session flow | High |
| `MiasSpeech` | Android SpeechRecognizer + TTS | `SFSpeechRecognizer` (STT) + `AVSpeechSynthesizer` (TTS) | Medium |
| `MiasModelHub` | model-hub (download/registry) | Port download/registry logic (URLSession + SHA-256); reuse the curated catalogue as shared data | Medium–High |
| `MiasInference` | llama.cpp JNI + AI Edge (NPU) | **llama.cpp with Metal** (upstream supports iOS/Metal); ONNX Runtime iOS; MediaPipe iOS for vision | High (the core compute port) |
| `MiasVision` | MediaPipe .task | MediaPipe Tasks iOS, or a vision model via the inference stack | High |
| `MiasEvolution` | WorkManager | `BGTaskScheduler` for background self-learning | Medium |
| Bridge `CryptoProvider` | **still needed on Android too** | Apple **CryptoKit** (X25519, AES-GCM, HKDF, HMAC) — trivial on iOS; on Android a Conscrypt/Keystore bridge | Low (iOS), the shared unblock for a live phone↔PC session |

**Sequence for iOS parity:** start with the low-effort, high-value adapters that
make the app feel real (`MiasSecurity` → cold-start FaceID gate; `MiasPrefs`;
`MiasThermal`; the `CryptoProvider` → live Bridge from an iPhone), then `MiasSpeech`,
then the heavy compute (`MiasInference`/`MiasVision`/`MiasData`). Until the compute
adapters land, iOS users can still **offload to a desktop via the Bridge/LAN** —
the whole point of the PC part — so a useful iOS app ships well before on-device
inference is ported.

## 4. Reference Swift module

`mobile/ios/MiasModules/` holds a compile-unverified reference implementation of
`MiasSecurity` (Keychain + LocalAuthentication) — the pattern every module
follows: a Swift class exposing the same method names the TS wrapper calls, plus
an `RCT_EXTERN_MODULE` bridge. It is the template for the table above, not a
finished, tested module.

## 5. Desktop

Two distinct desktop needs, and they're already largely met:

- **The PC offload host (the product's "PC part") — done in S5.** It's the VS Code
  extension + session server (`/extension`, `/session-server`), consuming the
  shared `@mias/*` packages. This is the recommended primary desktop surface: it
  lives where developers already are, and the session server is a plain sidecar.

- **A standalone desktop *app* (optional, beyond the extension).** If a
  chat/control desktop app is wanted outside an editor, the options:

  | Option | Reuse | Verdict |
  |---|---|---|
  | **react-native-macos / -windows** | The RN screens + `@mias/*` packages nearly verbatim; native adapters need macOS/Windows impls | Best fit — one UI codebase across mobile + desktop; macOS can even reuse the iOS Swift adapters. Recommended if a native desktop app is prioritized. |
  | **Electron shell around the TS packages** | `@mias/domain` + `@mias/bridge-protocol` directly (they're Node-friendly); a new web UI | Fastest to a cross-OS window, but a second UI to maintain and heavier runtime. Fallback, not preferred. |
  | **Tauri** | The TS packages via a webview; Rust host | Lightweight, but adds a Rust toolchain and a second UI. Not recommended given the RN investment. |

  **Recommendation:** keep the **VS Code extension as the PC offload surface**; if a
  standalone desktop app becomes a priority, use **react-native-windows/macos** to
  reuse the RN UI + shared packages (macOS reuses iOS adapters), rather than
  starting a separate Electron/web UI.

## 6. What remains (the honest gaps)

- Everything in §3 marked Medium/High is real platform engineering that needs a
  **macOS + Xcode** environment to write and verify — it cannot be done here.
- The Swift in `mobile/ios/MiasModules/` is **unverified** (never compiled).
- The shared `CryptoProvider` native module (iOS CryptoKit / Android Conscrypt) is
  the one piece gating a *live* phone↔PC Bridge session on either platform; the
  relay + session server already run the protocol (see `/session-server` tests).
