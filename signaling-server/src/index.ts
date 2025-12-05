import http from 'http';
import express from 'express';
import { WebSocketServer, WebSocket } from 'ws';
import { z } from 'zod';

const PORT = Number(process.env.PORT || 8787);

const app = express();
app.get('/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: Date.now() });
});

const server = http.createServer(app);
const wss = new WebSocketServer({ server });

const connections = new Map<string, WebSocket>();
const queues = new Map<string, QueuedMessage[]>();

const baseEnvelope = z.object({
  type: z.enum(['register', 'offer', 'answer', 'ice', 'ciphertext', 'pullQueue', 'pong']),
  from: z.string().min(8)
});
const routedEnvelope = baseEnvelope.extend({
  to: z.string().min(8),
  payload: z.unknown()
});
const registerEnvelope = baseEnvelope.extend({
  type: z.literal('register'),
  deviceId: z.number().int().nonnegative()
});
const pullEnvelope = baseEnvelope.extend({ type: z.literal('pullQueue') });

wss.on('connection', (socket) => {
  let identity: string | null = null;

  socket.on('message', (raw) => {
    try {
      const text = typeof raw === 'string' ? raw : raw.toString('utf-8');
      const parsed = JSON.parse(text);
      const { type } = parsed;

      if (type === 'register') {
        const data = registerEnvelope.parse(parsed);
        identity = data.from;
        connections.set(identity, socket);
        log(`registered ${identity}`);
        emit(socket, { type: 'registered', at: Date.now() });
        return;
      }

      if (type === 'pullQueue') {
        if (!identity) throw new Error('must register before pulling queue');
        pullEnvelope.parse(parsed);
        const queue = queues.get(identity) ?? [];
        queue.forEach((msg) => emit(socket, { type: 'queued', payload: msg }));
        queues.set(identity, []);
        emit(socket, { type: 'queueEnd' });
        return;
      }

      if (type === 'pong') {
        return; // ignore
      }

      const data = routedEnvelope.parse(parsed);
      routeMessage(data);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'unknown';
      emit(socket, { type: 'error', message });
    }
  });

  socket.on('close', () => {
    if (identity) {
      connections.delete(identity);
      log(`disconnected ${identity}`);
    }
  });
});

function routeMessage(envelope: z.infer<typeof routedEnvelope>) {
  const { to } = envelope;
  const target = connections.get(to);
  const payload: QueuedMessage = {
    from: envelope.from,
    type: envelope.type,
    payload: envelope.payload,
    sentAt: Date.now()
  };

  if (target && target.readyState === WebSocket.OPEN) {
    emit(target, payload);
    return;
  }

  const queue = queues.get(to) ?? [];
  queue.push(payload);
  queues.set(to, queue);
  log(`queued ${payload.type} for ${to}`);
}

function emit(socket: WebSocket, data: unknown) {
  try {
    socket.send(JSON.stringify(data));
  } catch (err) {
    log(`failed to send: ${err}`);
  }
}

setInterval(() => {
  for (const [identity, socket] of connections.entries()) {
    if (socket.readyState !== WebSocket.OPEN) {
      connections.delete(identity);
      continue;
    }
    emit(socket, { type: 'ping', at: Date.now() });
  }
}, 20_000);

server.listen(PORT, () => {
  console.log(`Bizur signaling server listening on http://0.0.0.0:${PORT}`);
});

interface QueuedMessage {
  from: string;
  type: 'offer' | 'answer' | 'ice' | 'ciphertext';
  payload: unknown;
  sentAt: number;
}

function log(message: string) {
  console.log(`[${new Date().toISOString()}] ${message}`);
}
