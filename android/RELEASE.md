# Release APK Signing

`assembleRelease` requires local signing credentials. Do not commit these values.

Create or update `.quorvia.local.env`:

```properties
ANDROID_RELEASE_KEYSTORE_PATH=C:\Users\ryanc\Desktop\Quorvia\quorvia-release.jks
ANDROID_RELEASE_KEY_ALIAS=quorvia
ANDROID_RELEASE_KEY_PASSWORD=your-keystore-password
```

Then build:

```powershell
cd android
.\gradlew.bat assembleRelease
```

Output:

```text
android/app/build/outputs/apk/release/app-release.apk
```

For real-device testing, also set `QRNG_PROXY_BASE_URL` in `android/local.properties`
to your deployed HTTPS proxy URL before building a release APK.

Gradle also accepts `android/local.properties`, environment variables, Gradle properties,
or `android/signing.properties`. Local secret files are ignored by Git.
