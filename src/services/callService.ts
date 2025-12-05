import { createPeerTransport } from '@/services/p2pTransport';
import type { CallDirection, CallLog, CallStatus, Identity } from '@/types/messaging';

export type CallEventType = 'idle' | 'dialing' | 'ringing' | 'connected' | 'ended';

export interface CallEvent {
  type: CallEventType;
  callId?: string;
  contactId?: string;
  startedAt?: string;
  status?: CallStatus;
  durationSeconds?: number;
}

interface CallClientOptions {
  identity: Identity;
  onCallLogged?: (log: CallLog) => void;
}

export interface CallClient {
  startCall: (contactId: string) => Promise<void>;
  acceptCall: (contactId: string) => Promise<void>;
  endCall: (status?: CallStatus) => void;
  onEvent: (listener: (event: CallEvent) => void) => () => void;
}

export const createCallClient = ({ identity, onCallLogged }: CallClientOptions): CallClient => {
  const transport = createPeerTransport();
  const listeners = new Set<(event: CallEvent) => void>();
  let activeCallId: string | null = null;
  let currentContactId: string | null = null;
  let startedAt: string | null = null;
  let direction: CallDirection = 'outgoing';

  const emit = (event: CallEvent) => {
    listeners.forEach((listener) => listener(event));
  };

  const trackCall = (status: CallStatus) => {
    if (!onCallLogged || !startedAt || !activeCallId || !currentContactId) {
      return;
    }

    const durationSeconds = Math.max(
      0,
      Math.round((Date.now() - Date.parse(startedAt)) / 1000)
    );

    onCallLogged({
      id: activeCallId,
      contactId: currentContactId,
      direction,
      startedAt,
      durationSeconds,
      status,
    });
  };

  const startCall = async (contactId: string) => {
    currentContactId = contactId;
    activeCallId = `${identity.id}-${contactId}-${Date.now()}`;
    startedAt = new Date().toISOString();
    direction = 'outgoing';
    const startedAtIso = startedAt ?? undefined;
    emit({ type: 'dialing', callId: activeCallId, contactId, startedAt: startedAtIso });

    await transport.connectPeer(contactId);

    setTimeout(() => {
      emit({
        type: 'connected',
        callId: activeCallId ?? undefined,
        contactId,
        startedAt: startedAt ?? undefined,
      });
    }, 600);
  };

  const acceptCall = async (contactId: string) => {
    currentContactId = contactId;
    activeCallId = `${contactId}-${identity.id}-${Date.now()}`;
    startedAt = new Date().toISOString();
    direction = 'incoming';
    emit({
      type: 'ringing',
      callId: activeCallId,
      contactId,
      startedAt: startedAt ?? undefined,
    });

    await transport.connectPeer(contactId);

    emit({
      type: 'connected',
      callId: activeCallId,
      contactId,
      startedAt: startedAt ?? undefined,
    });
  };

  const endCall = (status: CallStatus = 'ended') => {
    if (!activeCallId) return;
    emit({ type: 'ended', callId: activeCallId, contactId: currentContactId ?? undefined, status });
    trackCall(status);
    transport.teardown();
    activeCallId = null;
    startedAt = null;
    currentContactId = null;
  };

  const onEvent = (listener: (event: CallEvent) => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  };

  return {
    startCall,
    acceptCall,
    endCall,
    onEvent,
  };
};
