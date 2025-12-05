import 'dotenv/config';
import express from 'express';
import { createServer } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import Database from 'better-sqlite3';
import { initFirebase, sendPush } from './push';

const PORT = parseInt(process.env.PORT || '8787', 10);

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
  CREATE TABLE IF NOT EXISTS message_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recipient TEXT NOT NULL,
    payload TEXT NOT NULL,
    created_at INTEGER NOT NULL
  );
  CREATE INDEX IF NOT EXISTS idx_queue_recipient ON message_queue(recipient);
`);

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
const stmtEnqueue = db.prepare(`INSERT INTO message_queue (recipient, payload, created_at) VALUES (?, ?, ?)`);
const stmtDequeue = db.prepare(`SELECT id, payload FROM message_queue WHERE recipient = ? ORDER BY id`);
const stmtDeleteQueued = db.prepare(`DELETE FROM message_queue WHERE id = ?`);

// ─── Express REST API ────────────────────────────────────────────────────────
const app = express();
app.use(express.json({ limit: '1mb' }));

// Health check
app.get('/', (_req, res) => res.json({ status: 'ok' }));

// Pre-key upload
app.post('/prekeys/:identity', (req, res) => {
  const { identity } = req.params;
  const bundle = JSON.stringify(req.body);
  stmtUpsertPreKey.run(identity, bundle, Date.now());
  res.json({ success: true });
});

// Pre-key fetch
app.get('/prekeys/:identity', (req, res) => {
  const row = stmtGetPreKey.get(req.params.identity) as { bundle: string } | undefined;
  if (!row) return res.status(404).json({ error: 'not found' });
  res.json(JSON.parse(row.bundle));
});

// Push token registration
app.post('/push/register', (req, res) => {
  const { identity, token } = req.body;
  if (!identity || !token) return res.status(400).json({ error: 'missing fields' });
  stmtUpsertPushToken.run(identity, token, Date.now());
  res.json({ success: true });
});

const httpServer = createServer(app);

// ─── WebSocket Signaling ─────────────────────────────────────────────────────
interface Client {
  ws: WebSocket;
  identity: string;
  deviceId: number;
}

const clients = new Map<string, Client>();

const wss = new WebSocketServer({ server: httpServer, path: '/' });

wss.on('connection', (ws) => {
  let client: Client | null = null;

  ws.on('message', async (data) => {
    let msg: any;
    try {
      msg = JSON.parse(data.toString());
    } catch {
      return;
    }

    const type = msg.type as string;

    if (type === 'register') {
      const identity = msg.from as string;
      const deviceId = (msg.deviceId as number) ?? 0;
      client = { ws, identity, deviceId };
      clients.set(identity, client);
      ws.send(JSON.stringify({ type: 'registered' }));
      console.log(`[ws] registered: ${identity}`);
      return;
    }

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

    // Routed messages: offer, answer, ice, ciphertext
    if (['offer', 'answer', 'ice', 'ciphertext'].includes(type)) {
      const to = msg.to as string;
      const envelope = JSON.stringify(msg);
      const target = clients.get(to);
      if (target && target.ws.readyState === WebSocket.OPEN) {
        target.ws.send(envelope);
      } else {
        // Queue for later + push notification
        stmtEnqueue.run(to, envelope, Date.now());
        const tokenRow = stmtGetPushToken.get(to) as { token: string } | undefined;
        if (tokenRow) {
          sendPush(tokenRow.token, {
            title: 'New message',
            body: 'You have a new Bizur message',
            data: { from: client.identity, type }
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
httpServer.listen(PORT, () => {
  console.log(`Bizur backend listening on http://0.0.0.0:${PORT}`);
});
