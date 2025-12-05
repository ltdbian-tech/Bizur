# Bizur Android Build Notes

The native module targets Android Studio Ladybug (or newer) with JDK 17. A Gradle wrapper
is committed, so you can build immediately without installing Gradle globally.

## Prerequisites

- Android Studio with SDK 34 (or newer) plus an emulator/device for launches.
- JDK 17 (bundled with current Android Studio releases).
- `local.properties` pointing at your SDK location.
- Allow Bizur to record audio and, on Android 14+, enable the Foreground Service (microphone) special access so active calls can keep streaming audio in the background.

### Configure `local.properties`

Inside the `android/` directory add (or edit) `local.properties` so it references your SDK:

```properties
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Without this file the Gradle task `:app:compileDebugJavaWithJavac` fails with
`SDK location not found`.

## Build from the CLI

```powershell
cd android
.\gradlew.bat assembleDebug     # Windows PowerShell
./gradlew assembleDebug          # macOS/Linux
.\gradlew.bat testDebugUnitTest  # Windows JVM unit tests
./gradlew testDebugUnitTest       # macOS/Linux JVM unit tests
```

Artifacts land in `android/app/build/outputs/apk/debug/`.

VS Code also ships a **"Gradle Assemble Debug"** task (`Terminal → Run Task`) which runs
the same command via `.vscode/tasks.json`.

## Release builds

1. Copy `keystore.properties.example` to `keystore.properties` and fill in the keystore path/passwords. Keep this file private.
2. Update `SIGNALING_URL` inside `gradle.properties` (or pass `-PSIGNALING_URL=`) so release builds point to your production relay endpoint.
3. Generate a signed bundle for Play Store uploads:

```powershell
cd android
./gradlew bundleRelease
```

Use `assembleRelease` instead if you specifically need a signed APK. When the keystore file is absent, Gradle will sign with the debug keystore so development builds continue working; Play Store submissions require the proper release keystore.

## Install & Launch on an emulator/device

```powershell
cd android
.\gradlew.bat installDebug
```

After the APK installs you can start the activity manually:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.bizur.android/.MainActivity
```

This mirrors what Android Studio does when you click **Run**.

## Foreground call service

Bizur keeps every ringing/connected call alive through `CallForegroundService`, which exposes the `Calls` notification channel. Accept the POST_NOTIFICATIONS prompt on Android 13+ and keep the Foreground Service (microphone) toggle enabled on Android 14+ so the OS allows microphone capture while the app is backgrounded.

## Offline data layer

- The native app uses Room for contacts, conversations, messages, and call logs. The database file (`bizur.db`) lives in the app sandbox and never leaves the device. It starts empty; add contacts from the Contacts tab to begin chatting.
- `DraftStore` (DataStore preferences) persists the in-progress message field so it survives process death without involving any server.
- `SeedData` is retained for wipes but currently returns an empty state so reset simply clears the tables.
