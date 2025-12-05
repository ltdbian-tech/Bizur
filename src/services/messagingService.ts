import { io, Socket } from 'socket.io-client';
import type { Identity, Message } from '@/types/messaging';

interface MessagingOptions {
  identity: Identity;
  signalServerUrl?: string;
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
  signalServerUrl,
}: MessagingOptions): MessagingClient => {
  const listeners = new Set<MessageListener>();
  let socket: Socket | null = null;
  let connected = false;

  const emit = (message: Message) => {
    listeners.forEach((listener) => listener(message));
  };

  const relayListener: MessageListener = (message) => emit(message);

  const connect = () => {
    if (connected) return;
    if (signalServerUrl) {
      socket = io(signalServerUrl, {
        autoConnect: false,
        transports: ['websocket'],
      });
      socket.on('connect', () => {
        connected = true;
      });
      socket.on('message', (message: Message) => {
        emit(message);
      });
      socket.connect();
    } else {
      inMemoryRelay.add(relayListener);
      connected = true;
    }
  };

  const disconnect = () => {
    if (!connected) return;
    socket?.disconnect();
    socket = null;
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

    if (socket && connected) {
      socket.emit('message', payload);
    } else {
      notifyRelay(payload);
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
