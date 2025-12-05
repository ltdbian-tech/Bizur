import { useCallback, useEffect, useRef, useState } from 'react';
import { demoCalls, demoContacts, demoConversations, demoMessages } from '@/constants/sampleData';
import { createCallClient } from '@/services/callService';
import { createMessagingClient } from '@/services/messagingService';
import { storageService } from '@/services/storageService';
import { useConversationsStore } from '@/store/useConversationsStore';
import { useSession } from '@/contexts/SessionContext';
import type { Message } from '@/types/messaging';

export interface UseBizurClientResult {
  isReady: boolean;
  sendMessage: (conversationId: string, body: string) => Promise<void>;
  startCall: (contactId: string) => Promise<void>;
  endCall: () => void;
}

export const useBizurClient = (): UseBizurClientResult => {
  const { identity } = useSession();
  const hydrateWithSeed = useConversationsStore((state) => state.hydrateWithSeed);
  const addMessage = useConversationsStore((state) => state.addMessage);
  const addCallLog = useConversationsStore((state) => state.addCallLog);
  const [isReady, setReady] = useState(false);

  const messagingRef = useRef<ReturnType<typeof createMessagingClient> | null>(null);
  const callRef = useRef<ReturnType<typeof createCallClient> | null>(null);

  useEffect(() => {
    const boot = async () => {
      const persisted = await storageService.loadState();
      if (persisted) {
        hydrateWithSeed(persisted);
      } else {
        hydrateWithSeed({
          contacts: demoContacts,
          conversations: demoConversations,
          messages: demoMessages,
          callLogs: demoCalls,
        });
      }
      setReady(true);
    };

    void boot();
  }, [hydrateWithSeed]);

  useEffect(() => {
    const unsubscribe = useConversationsStore.subscribe((state) => {
      void storageService.persistState({
        contacts: state.contacts,
        conversations: Object.values(state.conversations),
        messages: state.messages,
        callLogs: state.callLogs,
      });
    });

    return unsubscribe;
  }, []);

  useEffect(() => {
    if (!identity) return;

    const messaging = createMessagingClient({ identity });
    messaging.connect();
    const handleMessage = (incoming: Message) => {
      const normalized: Message =
        incoming.senderId === identity.id
          ? { ...incoming, senderId: 'self' }
          : incoming;
      addMessage(normalized);
    };
    messaging.onMessage(handleMessage);
    messagingRef.current = messaging;

    const callClient = createCallClient({ identity, onCallLogged: addCallLog });
    callRef.current = callClient;

    return () => {
      messagingRef.current?.offMessage(handleMessage);
      messagingRef.current?.disconnect();
      messagingRef.current = null;
      callRef.current?.endCall('ended');
      callRef.current = null;
    };
  }, [identity, addMessage, addCallLog]);

  const sendMessage = useCallback(
    async (conversationId: string, body: string) => {
      if (!messagingRef.current || !identity) return;
      await messagingRef.current.sendMessage(conversationId, body);
    },
    [identity]
  );

  const startCall = useCallback(
    async (contactId: string) => {
      if (!callRef.current) return;
      await callRef.current.startCall(contactId);
    },
    []
  );

  const endCall = useCallback(() => {
    callRef.current?.endCall();
  }, []);

  return {
    isReady,
    sendMessage,
    startCall,
    endCall,
  };
};
