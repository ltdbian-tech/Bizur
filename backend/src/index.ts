import 'dotenv/config';
import express from 'express';
import { createServer } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import Database from 'better-sqlite3';
import crypto from 'crypto';
import { initFirebase, sendPush } from './push';
import pkg from '../package.json';

const PORT = parseInt(process.env.PORT || '8787', 10);
const AUTH_TOKEN = process.env.AUTH_TOKEN || '';
const VERSION = pkg.version;
const COMMIT = process.env.RENDER_GIT_COMMIT || process.env.GIT_SHA || '';
const START_TIME = Date.now();

const requireAuth = (req: express.Request, res: express.Response, next: express.NextFunction) => {
  if (!AUTH_TOKEN) return res.status(500).json({ error: 'server auth not configured' });
  const header = req.header('x-api-key');
  if (header !== AUTH_TOKEN) return res.status(401).json({ error: 'unauthorized' });
  return next();
};

const rateLimitBuckets = new Map<string, { count: number; resetAt: number }>();
const RATE_LIMIT = 100; // requests per 5 minutes per key
const RATE_WINDOW_MS = 5 * 60 * 1000;
const checkRate = (key: string) => {
  const now = Date.now();
  const bucket = rateLimitBuckets.get(key);
  if (!bucket || bucket.resetAt < now) {
    rateLimitBuckets.set(key, { count: 1, resetAt: now + RATE_WINDOW_MS });
    return true;
  }
  if (bucket.count >= RATE_LIMIT) return false;
  bucket.count += 1;
  return true;
};

// ─── Database Setup ──────────────────────────────────────────────────────────
const db = new Database('bizur.db');
db.pragma('journal_mode = WAL');

db.exec(`
  CREATE TABLE IF NOT EXISTS prekeys (
    identity TEXT PRIMARY KEY,
    bundle TEXT NOT NULL,
    updated_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS push_tokens (
    identity TEXT PRIMARY KEY,
    token TEXT NOT NULL,
    updated_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS api_keys (
    identity TEXT PRIMARY KEY,
    token TEXT NOT NULL,
    updated_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS message_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recipient TEXT NOT NULL,
    payload TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    message_id TEXT
  );
  CREATE INDEX IF NOT EXISTS idx_queue_recipient ON message_queue(recipient);
  CREATE UNIQUE INDEX IF NOT EXISTS idx_queue_recipient_msg ON message_queue(recipient, message_id);
  CREATE TABLE IF NOT EXISTS seen_message_ids (
    recipient TEXT NOT NULL,
    msg_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE(recipient, msg_id)
  );
  CREATE INDEX IF NOT EXISTS idx_seen_recipient ON seen_message_ids(recipient);
`);

// Best-effort migration to add message_id column if missing
try {
  db.exec(`ALTER TABLE message_queue ADD COLUMN message_id TEXT`);
} catch {
  // ignore if already exists
}

const stmtUpsertPreKey = db.prepare(`
  INSERT INTO prekeys (identity, bundle, updated_at) VALUES (?, ?, ?)
  ON CONFLICT(identity) DO UPDATE SET bundle=excluded.bundle, updated_at=excluded.updated_at
`);
const stmtGetPreKey = db.prepare(`SELECT bundle FROM prekeys WHERE identity = ?`);
const stmtUpsertPushToken = db.prepare(`
  INSERT INTO push_tokens (identity, token, updated_at) VALUES (?, ?, ?)
  ON CONFLICT(identity) DO UPDATE SET token=excluded.token, updated_at=excluded.updated_at
`);
const stmtGetPushToken = db.prepare(`SELECT token FROM push_tokens WHERE identity = ?`);
const stmtEnqueue = db.prepare(`INSERT INTO message_queue (recipient, payload, created_at, message_id) VALUES (?, ?, ?, ?)`);
const stmtDequeue = db.prepare(`SELECT id, payload FROM message_queue WHERE recipient = ? ORDER BY id`);
const stmtDeleteQueued = db.prepare(`DELETE FROM message_queue WHERE id = ?`);
const stmtGetApiKey = db.prepare(`SELECT token FROM api_keys WHERE identity = ?`);
const stmtUpsertApiKey = db.prepare(`
  INSERT INTO api_keys (identity, token, updated_at) VALUES (?, ?, ?)
  ON CONFLICT(identity) DO UPDATE SET token=excluded.token, updated_at=excluded.updated_at
`);
const stmtInsertSeen = db.prepare(`INSERT OR IGNORE INTO seen_message_ids (recipient, msg_id, created_at) VALUES (?, ?, ?)`);
const stmtPruneSeen = db.prepare(`DELETE FROM seen_message_ids WHERE created_at < ?`);
const stmtPruneQueue = db.prepare(`
  DELETE FROM message_queue
  WHERE id IN (
    SELECT id FROM message_queue WHERE recipient = ? ORDER BY id DESC LIMIT -1 OFFSET 200
  )
`);

// ─── Express REST API ────────────────────────────────────────────────────────
const app = express();
app.use(express.json({ limit: '1mb' }));

app.use((req, res, next) => {
  if (!checkRate(req.ip || 'unknown')) return res.status(429).json({ error: 'rate limit' });
  next();
});

// Health check
app.get('/', (_req, res) => res.json({ status: 'ok' }));
app.get('/health', (_req, res) => {
  res.json({ status: 'ok', uptimeMs: Date.now() - START_TIME, now: new Date().toISOString() });
});
app.get('/version', (_req, res) => {
  res.json({ version: VERSION, commit: COMMIT || undefined });
});

// Pre-key upload
app.post('/prekeys/:identity', requireAuth, (req, res) => {
  const { identity } = req.params;
  if (!identity || typeof identity !== 'string' || identity.length > 128) {
    return res.status(400).json({ error: 'bad identity' });
  }
  const bundle = JSON.stringify(req.body);
  stmtUpsertPreKey.run(identity, bundle, Date.now());
  res.json({ success: true });
});

// Pre-key fetch
app.get('/prekeys/:identity', requireAuth, (req, res) => {
  const { identity } = req.params;
  if (!identity || typeof identity !== 'string' || identity.length > 128) {
    return res.status(400).json({ error: 'bad identity' });
  }
  const row = stmtGetPreKey.get(identity) as { bundle: string } | undefined;
  if (!row) return res.status(404).json({ error: 'not found' });
  res.json(JSON.parse(row.bundle));
});

// Push token registration
app.post('/push/register', requireAuth, (req, res) => {
  const { identity, token } = req.body;
  if (!identity || typeof identity !== 'string' || identity.length > 128) {
    return res.status(400).json({ error: 'bad identity' });
  }
  if (!token || typeof token !== 'string') return res.status(400).json({ error: 'missing token' });
  stmtUpsertPushToken.run(identity, token, Date.now());
  res.json({ success: true });
});

// Issue per-identity API keys (protected by master AUTH_TOKEN)
app.post('/auth/register', requireAuth, (req, res) => {
  const { identity } = req.body as { identity?: string };
  if (!identity || typeof identity !== 'string' || identity.length > 128) {
    return res.status(400).json({ error: 'bad identity' });
  }
  const token = crypto.randomBytes(24).toString('hex');
  stmtUpsertApiKey.run(identity, token, Date.now());
  res.json({ identity, apiKey: token });
});

const httpServer = createServer(app);

// ─── WebSocket Signaling ─────────────────────────────────────────────────────
interface Client {
  ws: WebSocket;
  identity: string;
  deviceId: number;
}

const clients = new Map<string, Client>();

const isFreshTimestamp = (ts: number, skewMs = 5 * 60 * 1000) => {
  const now = Date.now();
  return Math.abs(now - ts) <= skewMs;
};

const wss = new WebSocketServer({
  server: httpServer,
  path: '/',
  verifyClient: (info, done) => {
    if (!AUTH_TOKEN) return done(false, 500, 'server auth not configured');
    try {
      const url = new URL(info.req.url || '/', 'http://localhost');
      const identity = url.searchParams.get('identity');
      const legacyToken = url.searchParams.get('token');
      const apiKey = url.searchParams.get('apiKey');
      const ts = parseInt(url.searchParams.get('ts') || '0', 10);
      const nonce = url.searchParams.get('nonce') || '';
      const sig = url.searchParams.get('sig') || '';

      if (!identity || identity.length > 128) return done(false, 400, 'bad identity');

      // Preferred path: per-identity apiKey + signed challenge
      if (apiKey && sig && ts && nonce) {
        if (!isFreshTimestamp(ts)) return done(false, 401, 'stale timestamp');
        const stored = stmtGetApiKey.get(identity) as { token: string } | undefined;
        if (!stored || stored.token !== apiKey) return done(false, 401, 'bad apiKey');
        const expected = crypto
          .createHmac('sha256', apiKey)
          .update(`${identity}:${ts}:${nonce}`)
          .digest('hex');
        if (sig !== expected) return done(false, 401, 'bad signature');
        (info.req as any).identity = identity;
        return done(true);
      }

      // Legacy fallback: shared AUTH_TOKEN (deprecated)
      if (legacyToken === AUTH_TOKEN) {
        console.warn('[ws] legacy auth fallback used; add apiKey+sig');
        (info.req as any).identity = identity;
        return done(true);
      }

      return done(false, 401, 'unauthorized');
    } catch (err) {
      console.error('[ws] verify error', err);
      return done(false, 400, 'bad request');
    }
  },
});

wss.on('connection', (ws, request) => {
  const identity = (request as any).identity as string;
  let client: Client | null = { ws, identity, deviceId: 0 };
  clients.set(identity, client);
  ws.send(JSON.stringify({ type: 'registered' }));

  ws.on('message', async (data) => {
    let msg: any;
    try {
      msg = JSON.parse(data.toString());
    } catch {
      return;
    }

    const type = msg.type as string;

    if (!client) {
      ws.send(JSON.stringify({ type: 'error', message: 'not registered' }));
      return;
    }

    if (type === 'pullQueue') {
      const rows = stmtDequeue.all(client.identity) as { id: number; payload: string }[];
      for (const row of rows) {
        ws.send(JSON.stringify({ type: 'queued', payload: JSON.parse(row.payload) }));
        stmtDeleteQueued.run(row.id);
      }
      ws.send(JSON.stringify({ type: 'queueEnd' }));
      return;
    }

    // Lookup a peer code to check if it exists
    if (type === 'lookup') {
      const target = msg.target as string;
      if (!target || typeof target !== 'string') return;
      const normalizedTarget = target.toUpperCase();
      // Check if target is registered (has prekeys or is currently connected)
      const hasPrekeys = stmtGetPreKey.get(normalizedTarget);
      const isOnline = clients.has(normalizedTarget);
      ws.send(JSON.stringify({
        type: 'lookup_result',
        target: normalizedTarget,
        found: !!(hasPrekeys || isOnline)
      }));
      return;
    }

    // Routed messages: offer, answer, ice, ciphertext, contact_request, contact_response
    if (['offer', 'answer', 'ice', 'ciphertext', 'contact_request', 'contact_response'].includes(type)) {
      const to = msg.to as string;
      const msgId = msg.msgId as string;
      if (!to || typeof to !== 'string' || to.length > 128) return;
      if (!msgId || typeof msgId !== 'string' || msgId.length > 128) return;

      // Drop replays for this recipient
      const seenInserted = stmtInsertSeen.run(to, msgId, Date.now());
      if (seenInserted.changes === 0) {
        return; // already processed
      }

      const envelope = JSON.stringify({ ...msg, from: client.identity });
      const target = clients.get(to);
      if (target && target.ws.readyState === WebSocket.OPEN) {
        target.ws.send(envelope);
      } else {
        // Queue for later + push notification
        stmtEnqueue.run(to, envelope, Date.now(), msgId);
        stmtPruneQueue.run(to);
        const tokenRow = stmtGetPushToken.get(to) as { token: string } | undefined;
        if (tokenRow) {
          sendPush(tokenRow.token, {
            title: '',
            body: '',
            data: { type: 'queue' }
          }).catch((err) => console.error('[push] failed', err));
        }
      }
      return;
    }

    if (type === 'pong') {
      // Keep-alive response, ignore
      return;
    }
  });

  ws.on('close', () => {
    if (client) {
      clients.delete(client.identity);
      console.log(`[ws] disconnected: ${client.identity}`);
    }
  });

  // Ping every 30s to keep connection alive
  const pingInterval = setInterval(() => {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'ping' }));
    }
  }, 30_000);
  ws.on('close', () => clearInterval(pingInterval));
});

// ─── Start ───────────────────────────────────────────────────────────────────
initFirebase();
// Prune seen IDs older than 7 days on startup
stmtPruneSeen.run(Date.now() - 7 * 24 * 60 * 60 * 1000);
httpServer.listen(PORT, () => {
  console.log(`Bizur backend listening on http://0.0.0.0:${PORT}`);
});
