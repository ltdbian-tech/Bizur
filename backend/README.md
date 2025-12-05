# Bizur Backend

Minimal signaling, pre-key store, and push dispatcher for Bizur P2P messaging.

## Features

| Component | Endpoint | Purpose |
|-----------|----------|---------|
| WebSocket relay | `ws://host:8787/` | Real-time signaling (SDP, ICE, messages) |
| Pre-key API | `POST/GET /prekeys/:identity` | Upload/fetch Signal pre-key bundles |
| Push registration | `POST /push/register` | Store FCM tokens |
| Message queue | Automatic | Store-and-forward when recipient offline |

## Local Development

```bash
cd backend
npm install
cp .env.example .env
# Edit .env with your Firebase service account JSON

npm run dev
```

Server runs at `http://localhost:8787`.

## Deploy to Fly.io (Free Tier)

1. Install Fly CLI: https://fly.io/docs/hands-on/install-flyctl/

2. Login:
   ```bash
   flyctl auth login
   ```

3. Create app and volume:
   ```bash
   cd backend
   flyctl apps create bizur-backend
   flyctl volumes create bizur_data --region iad --size 1
   ```

4. Set secrets (paste your Firebase JSON):
   ```bash
   flyctl secrets set FIREBASE_SERVICE_ACCOUNT_JSON='{ ... your JSON ... }'
   ```

5. Deploy:
   ```bash
   flyctl deploy
   ```

6. Get your URL:
   ```bash
   flyctl info
   ```
   It will be something like `https://bizur-backend.fly.dev`

   ## Deploy to Render (Free Tier, no card)

   1. Push this repo to GitHub (or any git host Render can pull).
   2. In Render, create a **Blueprint** pointing to this repo. The `render.yaml` in `/backend` defines the service.
   3. Set env vars in the service:
      - `FIREBASE_SERVICE_ACCOUNT_JSON` (secret) = your Firebase service account JSON as one line.
      - `PORT` is set to `10000` in `render.yaml`; leave as-is.
   4. Deploy. Render will build the Dockerfile and expose a URL like `https://<your-app>.onrender.com`.

   Notes:
   - Render free tier has no persistent disk; SQLite data resets on redeploy/cold start. For durability, switch to Postgres or a paid disk.
   - The server sleeps on inactivity; first request after idle may cold-start.
   - Update Android URLs to the Render host: `wss://<your-app>.onrender.com` for signaling, `https://<your-app>.onrender.com` for pre-key and push.

## Update Android App

In `android/app/build.gradle.kts`, update the URLs:

```kotlin
val defaultSignalingUrl = "wss://bizur-backend.fly.dev"
val defaultPreKeyUrl = "https://bizur-backend.fly.dev"
val defaultPushUrl = "https://bizur-backend.fly.dev"
```

Or pass via Gradle properties:
```bash
./gradlew :app:assembleDebug -PSIGNALING_URL=wss://bizur-backend.fly.dev -PPREKEY_URL=https://bizur-backend.fly.dev
```

## API Reference

### WebSocket Protocol

Connect to `ws(s)://host:8787/` and send JSON messages:

**Register:**
```json
{ "type": "register", "from": "identity-string", "deviceId": 0 }
```

**Send offer/answer/ice/ciphertext:**
```json
{ "type": "offer", "from": "alice", "to": "bob", "payload": { ... } }
```

**Pull queued messages:**
```json
{ "type": "pullQueue", "from": "identity-string" }
```

### REST Endpoints

**Upload pre-key bundle:**
```
POST /prekeys/:identity
Body: { registrationId, deviceId, identityKey, signedPreKey, preKeys }
```

**Fetch pre-key bundle:**
```
GET /prekeys/:identity
```

**Register push token:**
```
POST /push/register
Body: { identity: "...", token: "fcm-token" }
```
