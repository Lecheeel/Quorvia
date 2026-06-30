# Quorvia Architecture

Quorvia is an Android-first exploration app.

## Android

- Kotlin
- Jetpack Compose
- Material 3
- Package name: `com.quorvia.app`
- Compile/target SDK 36 for the current local SDK repository
- AMap Android SDK will provide map rendering, location, route planning, and
  navigation handoff.

## Backend

- Node.js 24 LTS
- TypeScript
- Fastify
- Deployed on the user's Debian 13 Alibaba Cloud server
- Protects the AQN API key
- Fails closed when ANU/AQN is unavailable

## Randomness Rule

The app must not use local PRNG/TRNG fallback for route generation. If the
proxy or ANU/AQN source is unavailable, the app should show an unavailable state
and stop generation.

## Verification

Run the root verification script from PowerShell:

```powershell
.\scripts\verify.ps1
```

Android Gradle tasks are intentionally run serially because parallel Gradle
invocations can contend for Kotlin compiler caches on Windows.
