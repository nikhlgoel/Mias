# Mias — React Native app (`/mobile`)

The React Native (TypeScript, New Architecture) shell of Mias, being built up
during the Kotlin→RN migration (see `docs/RN_MIGRATION_PROMPT.md` and the phased
plan R0–R7). The heavy native logic stays in the Kotlin `core/*` Gradle libraries
and is **wrapped**, not rewritten — this folder owns only the UI layer and thin
TS view-state.

## How this builds (differs from a stock RN app)

There is **no standalone Android build here**. The Android module
(`android/app`) is folded into the repo's **single root Gradle build** as
`:mobile`, next to the legacy `:app` and the `:core:*` libraries
(see `/settings.gradle.kts`). The stock `android/settings.gradle`,
`android/build.gradle`, and Gradle wrapper were intentionally removed.

```sh
# 1) JS deps (first time / after package.json changes)
cd mobile && npm install

# 2) Build the APK — from the REPO ROOT, not from /mobile
./gradlew :mobile:assembleDebug
#    → mobile/android/app/build/outputs/apk/debug/mobile-debug.apk

# Run on a device/emulator with live JS (Metro):
cd mobile && npm start        # Metro dev server
# install the APK, launch — debug builds load JS from Metro
```

- Debug builds install as **`io.mias.app.rn`** so they sit side-by-side with the
  legacy Kotlin app (`io.mias.app`) until the R6 cutover.
- **arm64-v8a only**, 16 KB page alignment, signing/version come from the repo's
  `keystore.properties` / `version.properties` — all inherited from the root build.
- JVM targets: RN modules build at 17; the first-party `:app`/`:core:*` stay at 21
  (see the alignment notes in `/build.gradle.kts` and `/gradle.properties`).

## Cold-start biometric gate

`BiometricGateActivity` is the launcher: a strong (Class 3) biometric prompt runs
**before** the React root (`MainActivity`, `exported=false`) mounts. It is
standalone `androidx.biometric` for now; R1/R2 replace it with the `:core`
SecurityModule backed by a `CryptoObject`-bound Keystore key.

## Privacy invariants (do not violate)

- No analytics, crash reporters, or cloud AI SDKs — ever.
- Outbound network stays on the host allowlist (HuggingFace + GitHub CDNs).
- No secrets in JS-accessible storage (never AsyncStorage) — secrets stay in the
  native `ZkVault` / Keystore behind typed native modules.

## iOS

`ios/` is the untouched template shell, kept for the later iOS phase (R7 plans
per-module iOS implementations). It still carries template naming
(`MiasMobile`) — renamed when iOS work starts. Don't build it on Windows.

## Tests

```sh
cd mobile
npx tsc --noEmit   # typecheck
npm test           # jest
```
