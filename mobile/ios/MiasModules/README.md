# iOS native modules (Mias)

Swift counterparts of the Android Kotlin bridge modules
(`mobile/android/app/src/main/java/dev/mias/mobile/bridge/*`). Each exposes the
**same `NativeModules` name and method signatures** the shared TS wrappers
(`mobile/src/native/*.ts`) call, so nothing in the JS/TS layer changes per
platform.

> ⚠️ **Unverified — no macOS/Xcode in the environment that authored these.** The
> files here are a **reference/starting point**, not compiled or tested. Adding
> them to the Xcode target, wiring `pod install`, and testing on a device is the
> remaining iOS engineering (see `docs/IOS_AND_DESKTOP.md`).

## Status

- `MiasSecurity` (`.swift` + `.m`) — reference implementation: Keychain
  (↔ Android ZkVault) + LocalAuthentication FaceID/TouchID (↔ BiometricPrompt).
  This is the pattern the rest follow.
- Everything else — planned; see the per-module table in
  `docs/IOS_AND_DESKTOP.md`. Until a module's Swift lands, its TS wrapper reports
  `isAvailable === false` and the app degrades gracefully (it still builds/runs).

## The pattern

1. A Swift `@objc(MiasX)` class with `@objc(method:...)` methods matching the TS
   wrapper's calls (promise resolve/reject, or `RCTEventEmitter` for streams).
2. An `.m` file with `RCT_EXTERN_MODULE` + `RCT_EXTERN_METHOD` exporting it.
3. A bridging header entry if the target has one.
4. For streaming modules (inference/speech/vision/model-hub), subclass
   `RCTEventEmitter` and emit the same event names (e.g. `MiasInference.step`).

The New-Architecture-native path is a TurboModule codegen spec (typed); the
`RCT_EXTERN` form above runs via the interop layer and is the quickest way to
bring each module up first.
