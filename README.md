# Bizur

Bizur now ships with two code paths:

1. **`src/` – Expo/React Native (TypeScript)**: the original cross-platform prototype with Zustand state, navigation, and service stubs.
2. **`android/` – Android Studio + Jetpack Compose (Kotlin)**: a native implementation that mirrors the peer-to-peer concept with Compose UI, ViewModels, and repository stubs you can evolve into a production Android app.

The Expo app remains intact for potential future cross-platform work, while the Android module is the current focus.

## Quick start (no coding)

- Open PowerShell, go to the repo: `cd "c:\Users\adith\OneDrive\Documents\app idea\Bizur"`.
- Run the helper: `powershell -ExecutionPolicy Bypass -File .\signaling-setup.ps1 -BackendUrl "https://your-backend" -Identity "your-name" -AuthToken "<AUTH_TOKEN>" -Install`
- It will: (1) call `/auth/register` to get your `apiKey`, (2) build the Android APK, and (3) install it if an emulator/device is connected.
- After install, open the app and use the same identity you registered.

## Android (Jetpack Compose) Highlights

- 📱 Kotlin + Jetpack Compose UI with Material 3 styling and a bottom navigation shell.
- 🧠 `BizurRepository` + `BizurViewModel` replicate the contact/conversation/message/call log flows from the Expo store.
- 🔁 State flows power chats, contacts, call logs, and message drafts with demo seed data.
- 💾 Offline-first data layer: Room + DataStore persist everything locally so no server or cloud storage is required.
- ✏️ Screens for Chats, Contacts, Calls, and Settings, each backed by composables/components.
- ⚙️ Plain Kotlin data models (`model/`) align with the TypeScript types for easier parity.
- ☎ Contacts expose Ping + Call actions, and an always-on-top call banner keeps the active session visible so you can hang up from any tab.
- 🔐 Every install now gets a shareable pairing code (format `BZ-XXXXXXXX`). You’ll see it on the Contacts tab and in Settings—share it, paste someone else’s code, and Bizur creates the linked contact + conversation instantly.
- 🚫 Contacts now have **Block** and **Mute** toggles: blocked peers disappear from Chats/Calls and their traffic is dropped locally, while muted peers still sync silently without notifications.
- 🛡️ Chats, call signals, and queue fallbacks are now end-to-end encrypted with the Signal Double Ratchet, so plaintext never leaves the device even when relayed through the store-and-forward queue.
- 📣 When peers go fully offline the relay now pings Firebase Cloud Messaging; Bizur wakes up, drains the queue, and delivers the encrypted payload plus a local notification.
- 🔏 Signaling relay registration now carries a signed proof (pairing code + timestamp + identity key signature) so the server can reject spoofed devices that try to hijack your code.

> The Room database now boots completely empty. Use the **Contacts** tab to add peers; each contact automatically gets a matching conversation so you can start chatting without any preloaded demo rows.

### Signaling & relay service

- The `signaling-server/` directory contains a lightweight Node + WebSocket relay that exchanges WebRTC offers/answers/ICE and temporarily stores encrypted blobs for offline peers.
- Start it locally with `npm install` followed by `npm run dev` (development) or `npm run build && npm start` for production.
- The Android client will connect to this relay to perform QR-based pairing, exchange Signal prekeys, and wake dormant peers without ever exposing plaintext messages.
- A background WorkManager task (`QueueDrainWorker`) periodically wakes the relay even if WebRTC data channels are down, drains queued ciphertext packets, and surfaces system notifications so contacts still receive store-and-forward deliveries.
- Audio calling now reuses the same PeerConnection: tapping a call button negotiates an audio track, enforces the `RECORD_AUDIO` permission, and shows a system-wide call banner so you can hang up from any tab.
- `CallForegroundService` pins a lightweight notification with channel `Calls`, satisfying Android 14's background limits so ringing/connected calls continue streaming microphone audio even if the UI is backgrounded.
- Production relay (Render) now enforces an `AUTH_TOKEN` shared secret. Connect via `wss://<host>/?token=AUTH_TOKEN&identity=<peerCode>`; the server ignores any client-supplied `from` and binds the session to `identity`. REST calls must send `x-api-key: AUTH_TOKEN`.
- Health endpoints: `GET /health` returns status + uptime, and `GET /version` returns the deployed version/commit.

### Pairing codes (contact linking)

1. Open the **Contacts** tab to view your device’s code (format `BZ-XXXXXXXX`).
2. Share that code out-of-band; your peer enters it plus a display name in the **Add a contact by code** form.
3. Do the same with their code. Bizur creates the contact and its `chat-<code>` conversation immediately, so messages/calls route through the matching peer ID.

### Native transport stack

- `android/app/src/main/java/com/bizur/android/transport/WebRtcTransport.kt` initializes a full WebRTC data-channel stack (125.x SDK) with TURN/STUN fallbacks and Ktor-based signaling.
- `BizurRepository` now streams outgoing chat messages through this transport. When the peer is offline, payloads fall back to the relay's `ciphertext` queue so nothing is lost.
- `SignalStore` teams up with the new `SignalCipher` and `PreKeyService` helpers to run full X3DH + Double Ratchet handshakes. Outgoing payloads are wrapped in encrypted envelopes before they ever touch WebRTC data channels or the relay queue.
- Relay registration now includes a `auth` block signed by your identity key, giving the server enough material to verify that the pairing code being claimed actually belongs to the connecting device.
- Configure the relay endpoint through `BuildConfig.SIGNALING_URL` (set in `android/app/build.gradle.kts`). The default points at `ws://10.0.2.2:8787` for local emulator testing.

### Push fallback (Firebase Cloud Messaging)

- `BizurMessagingService` listens for FCM data pings (`{"type":"queue"}`) and immediately asks `WebRtcTransport` to drain the relay backlog so encrypted messages land even if the UI is backgrounded or killed.
- `PushRegistrar` bootstraps an FCM token at startup, signs `{token|peerCode|deviceId|timestamp}` with the device's Signal identity key, and POSTs it to `/push/register`. The relay can then wake that peer whenever new ciphertext is buffered.
- A placeholder `android/app/google-services.json` keeps Gradle happy. Replace it with your real Firebase config before distributing the APK.
- Self-hosted relays should validate the signature with the stored identity key, persist `{peerCode, deviceId, token}`, and issue a high-priority data message whenever ciphertext is added to that peer's queue.

### Prerequisites

- Android Studio Ladybug (or newer) with Android SDK 34 installed.
- JDK 17 (bundled with recent Android Studio builds).
- (Optional) Standalone Gradle if you prefer CLI workflows before Android Studio generates the wrapper.
- Grant Bizur microphone permission on first launch before placing or accepting calls.
- Android 14+ exposes the Foreground Service (microphone) toggle under App Info → Special app access; leave it enabled so Bizur can keep audio calls alive while in the background.
- Android 13+ users will see a POST_NOTIFICATIONS prompt the first time Bizur requests to show system notifications; approve it so store-forward alerts appear.

### Opening the Android project

1. Launch Android Studio → **Open** → select the `android` folder in this repo.
2. Let Android Studio download dependencies and (if prompted) generate the Gradle wrapper. You can also run `gradle wrapper --gradle-version 8.7` inside `android/` if you have Gradle installed.
3. Once sync completes, pick a device/emulator and run the `app` configuration.

### CLI build (after wrapper exists)

```powershell
cd android
gradlew.bat assembleDebug    # Windows
gradlew.bat testDebugUnitTest    # Windows JVM unit tests
./gradlew assembleDebug      # macOS/Linux
./gradlew testDebugUnitTest      # macOS/Linux JVM unit tests
```

Artifacts land in `android/app/build/outputs/apk/debug/`. If the wrapper is missing, see `android/BUILD.md` for quick generation steps.

> Configure `android/local.properties` with `sdk.dir=...` (see `android/BUILD.md`) before running the CLI commands, otherwise Gradle cannot find the Android SDK.

### Release builds & signing

1. Duplicate `android/keystore.properties.example` → `android/keystore.properties`, then update it with your real keystore path, passwords, and alias. The file is git-ignored—do not commit secrets.
2. Set `SIGNALING_URL` in `android/gradle.properties` (or via `-PSIGNALING_URL=wss://relay.example.com`) so release builds point at your production relay.
3. Run `./gradlew bundleRelease` (preferred for Play Store uploads) or `./gradlew assembleRelease` if you need a signed APK for sideloading.

Release builds now enable R8 shrinking/resource shrinking. If `keystore.properties` is missing, Gradle falls back to the debug keystore so you can still produce unsigned artifacts during development.

### Testing

- `gradlew.bat testDebugUnitTest` / `./gradlew testDebugUnitTest` executes JVM unit tests such as `CallSignalTest`, covering invite/accept/end signaling plus unknown-field handling.
- Expo unit tests remain available via `npm run test` from the repo root.

### VS Code task shortcuts

- `Terminal → Run Task → Gradle Assemble Debug` runs `.\android\gradlew.bat -p android assembleDebug` without leaving VS Code.
- `Terminal → Run Task → npm: validate` verifies the Expo toolchain (lint + typecheck + tests).

### Offline storage design

- Room tables (`contacts`, `conversations`, `messages`, `call_logs`) start empty and live only inside the app sandbox. Adding a contact immediately creates the matching conversation entry.
- `BizurRepository` streams Room + DataStore changes so Compose screens stay in sync without a backend.
- Draft text is stored via DataStore preferences, and `reset` wipes the database + draft so no messages ever leave the device.

### Android module layout

```
android/
   app/build.gradle.kts   # Compose + Material3 + Navigation dependencies
   app/src/main/
      AndroidManifest.xml
      java/com/bizur/android/
         BizurApplication.kt       # Simple DI container with BizurRepository
         MainActivity.kt           # Hosts Compose + provides ViewModel factory
         data/                     # Repository + seed data
         model/                    # Kotlin data classes for contacts/messages
         ui/
            BizurApp.kt             # NavHost + Scaffold for bottom tabs
            components/             # Conversation/Contact/Call cards, headers
            screens/                # Chats, Contacts, Calls, Settings composables
         viewmodel/                # BizurViewModel + factory + UI state mapping
```

## Expo (React Native) Recap

The managed Expo project is unchanged. Key traits:

- React Navigation (stack + tabs) with dark UI skin.
- Zustand store plus services for identity, storage, messaging, P2P transport, and call simulation.
- Jest + React Native Testing Library and ESLint with the RN community config.
- The Expo code path still uses simulated transports; it does not yet implement the WebRTC, Signal, or audio calling stack that ships in the Android module.

### Expo commands

```bash
npm install
npm run android   # Expo Go / emulator
npm run lint
npm run typecheck
npm run test
npm run validate
```

> Expo SDK 54 prefers Node.js ≥ 20.19.4. Older Node versions warn during install.

## Next Steps

1. Flesh out the native `BizurRepository` with real cryptographic identity + persistence (Room/Datastore/SecureStorage).
2. Replace the placeholder `sendMessage`/`placeCall` methods with actual P2P transport hooks (Nearby, Bluetooth, WebRTC data channels, libp2p, etc.).
3. Mirror production-ready encryption by integrating libsodium/Signal protocol libraries on both the Expo and native paths.
4. Once the Android app stabilizes, consider reusing its data layer concepts inside the Expo project or vice versa for feature parity.
