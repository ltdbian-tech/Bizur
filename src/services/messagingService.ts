import type { Identity, Message } from '@/types/messaging';

const BACKEND_WS_URL = 'wss://bizur-backend.onrender.com/';
const AUTH_TOKEN = process.env.EXPO_PUBLIC_AUTH_TOKEN || '';

interface MessagingOptions {
  identity: Identity;
}

type MessageListener = (message: Message) => void;

const inMemoryRelay = new Set<MessageListener>();

const notifyRelay = (message: Message) => {
  inMemoryRelay.forEach((listener) => listener(message));
};

export interface MessagingClient {
  connect: () => void;
  disconnect: () => void;
  sendMessage: (conversationId: string, body: string) => Promise<Message>;
  onMessage: (listener: MessageListener) => void;
  offMessage: (listener: MessageListener) => void;
}

export const createMessagingClient = ({
  identity,
}: MessagingOptions): MessagingClient => {
  const listeners = new Set<MessageListener>();
  let socket: WebSocket | null = null;
  let connected = false;
  const outbox: Message[] = [];

  const emit = (message: Message) => {
    listeners.forEach((listener) => listener(message));
  };

  const relayListener: MessageListener = (message) => emit(message);

  const connect = () => {
    if (connected || socket?.readyState === WebSocket.OPEN) return;
    if (!AUTH_TOKEN) {
      console.error('AUTH_TOKEN is missing; set EXPO_PUBLIC_AUTH_TOKEN');
      return;
    }
    try {
      const url = `${BACKEND_WS_URL}?token=${AUTH_TOKEN}&identity=${encodeURIComponent(identity.id)}`;
      socket = new WebSocket(url);

      socket.onopen = () => {
        connected = true;
        socket?.send(JSON.stringify({ type: 'pullQueue' }));
        while (outbox.length) {
          const pending = outbox.shift();
          if (pending) {
            socket?.send(
              JSON.stringify({
                type: 'ciphertext',
                to: pending.conversationId,
                from: identity.id,
                payload: pending,
              })
            );
          }
        }
      };

      socket.onmessage = (event) => {
        let raw: any;
        try {
          raw = JSON.parse(event.data as string);
        } catch {
          return;
        }

        if (raw.type === 'queued' && raw.payload) {
          emit(raw.payload as Message);
          return;
        }

        if (raw.type === 'ciphertext' && raw.payload) {
          emit(raw.payload as Message);
          return;
        }

        if (raw.type === 'queueEnd') {
          return;
        }
      };

      socket.onclose = () => {
        connected = false;
        socket = null;
        // Attempt a simple reconnect after a short delay
        setTimeout(connect, 2000);
      };
    } catch {
      inMemoryRelay.add(relayListener);
      connected = true;
    }
  };

  const disconnect = () => {
    if (!connected) return;
    if (socket) {
      socket.close();
      socket = null;
    }
    inMemoryRelay.delete(relayListener);
    listeners.clear();
    connected = false;
  };

  const onMessage = (listener: MessageListener) => {
    listeners.add(listener);
  };

  const offMessage = (listener: MessageListener) => {
    listeners.delete(listener);
  };

  const sendMessage = async (conversationId: string, body: string) => {
    const payload: Message = {
      id: `${conversationId}-${Date.now()}`,
      conversationId,
      senderId: identity.id,
      body,
      sentAt: new Date().toISOString(),
      status: 'pending',
    };

    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(
        JSON.stringify({
          type: 'ciphertext',
          to: conversationId,
          from: identity.id,
          msgId: payload.id,
          payload,
        })
      );
    } else {
      outbox.push(payload);
      connect();
    }

    emit(payload);
    return payload;
  };

  return {
    connect,
    disconnect,
    sendMessage,
    onMessage,
    offMessage,
  };
};
