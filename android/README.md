# Quorvia Android

Modern Android client for Quorvia.

## Stack

- Kotlin
- Jetpack Compose
- Material 3
- Package name: `com.quorvia.app`
- Min SDK: 23
- Compile SDK: 36
- Target SDK: 36

## Local configuration

Create `local.properties` after installing the Android SDK:

```properties
sdk.dir=C\:\\Users\\ryanc\\AppData\\Local\\Android\\Sdk
AMAP_ANDROID_KEY=your-amap-android-key
```

`local.properties` is ignored by Git.

## Current note

The machine currently has Android Studio installed but no Android SDK path was
found from the command line. Open Android Studio once and install:

- Android SDK Platform 36
- Android SDK Build-Tools
- Android SDK Platform-Tools
- Android SDK Command-line Tools

Lint currently reports newer Android 37 / AndroidX versions, but `sdkmanager`
did not expose `platforms;android-37` in this environment. Keep the project on
the latest compileable Android 36 dependency line until Android 37 is available
locally.
