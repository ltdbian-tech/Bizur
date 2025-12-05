import { create } from 'zustand';
import type { HydratePayload } from '@/services/types';
import type {
  CallLog,
  Contact,
  Conversation,
  Message,
  PeerSession,
  PresenceStatus,
} from '@/types/messaging';

interface ConversationState {
  contacts: Contact[];
  conversations: Record<string, Conversation>;
  messages: Record<string, Message[]>;
  callLogs: CallLog[];
  peers: Record<string, PeerSession>;
  hydrateWithSeed: (payload: HydratePayload) => void;
  upsertContact: (contact: Contact) => void;
  upsertConversation: (conversation: Conversation) => void;
  addMessage: (message: Message) => void;
  updateMessageStatus: (messageId: string, status: Message['status']) => void;
  addCallLog: (call: CallLog) => void;
  updatePresence: (contactId: string, presence: PresenceStatus) => void;
  setPeerSession: (peer: PeerSession) => void;
  reset: () => void;
}

const byMostRecent = (a: Conversation, b: Conversation) =>
  new Date(b.lastActivity).getTime() - new Date(a.lastActivity).getTime();

export const useConversationsStore = create<ConversationState>((set, _get) => ({
  contacts: [],
  conversations: {},
  messages: {},
  callLogs: [],
  peers: {},
  hydrateWithSeed: ({ contacts, conversations, messages, callLogs }) => {
    set((state) => ({
      contacts: contacts ?? state.contacts,
      conversations:
        conversations?.reduce<Record<string, Conversation>>((acc, convo) => {
          acc[convo.id] = convo;
          return acc;
        }, {}) ?? state.conversations,
      messages: messages ?? state.messages,
      callLogs: callLogs ?? state.callLogs,
    }));
  },
  upsertContact: (contact) =>
    set((state) => {
      const exists = state.contacts.some((item) => item.id === contact.id);
      return {
        contacts: exists
          ? state.contacts.map((item) => (item.id === contact.id ? contact : item))
          : [...state.contacts, contact],
      };
    }),
  upsertConversation: (conversation) =>
    set((state) => ({
      conversations: { ...state.conversations, [conversation.id]: conversation },
    })),
  addMessage: (message) =>
    set((state) => {
      const conversation = state.conversations[message.conversationId];
      const updatedConversation: Conversation = conversation
        ? {
            ...conversation,
            lastMessagePreview: message.body,
            lastActivity: message.sentAt,
            unreadCount:
              message.senderId === 'self'
                ? conversation.unreadCount
                : conversation.unreadCount + 1,
          }
        : {
            id: message.conversationId,
            peerId: message.senderId,
            title: message.senderId,
            unreadCount: message.senderId === 'self' ? 0 : 1,
            lastMessagePreview: message.body,
            lastActivity: message.sentAt,
            isSecure: true,
          };

      return {
        messages: {
          ...state.messages,
          [message.conversationId]: [
            ...(state.messages[message.conversationId] ?? []),
            message,
          ],
        },
        conversations: {
          ...state.conversations,
          [message.conversationId]: updatedConversation,
        },
      };
    }),
  updateMessageStatus: (messageId, status) =>
    set((state) => {
      const nextMessages: Record<string, Message[]> = {};
      let updatedConversation: Conversation | undefined;

      Object.entries(state.messages).forEach(([conversationId, list]) => {
        nextMessages[conversationId] = list.map((message) => {
          if (message.id !== messageId) {
            return message;
          }

          const next = { ...message, status };
          updatedConversation = state.conversations[conversationId]
            ? {
                ...state.conversations[conversationId],
                lastMessagePreview:
                  conversationId === message.conversationId
                    ? next.body
                    : state.conversations[conversationId].lastMessagePreview,
              }
            : undefined;

          return next;
        });
      });

      return {
        messages: nextMessages,
        conversations: updatedConversation
          ? { ...state.conversations, [updatedConversation.id]: updatedConversation }
          : state.conversations,
      };
    }),
  addCallLog: (call) =>
    set((state) => ({
      callLogs: [call, ...state.callLogs].slice(0, 25),
    })),
  updatePresence: (contactId, presence) =>
    set((state) => ({
      contacts: state.contacts.map((contact) =>
        contact.id === contactId ? { ...contact, presence } : contact
      ),
    })),
  setPeerSession: (peer) =>
    set((state) => ({
      peers: { ...state.peers, [peer.id]: peer },
    })),
  reset: () =>
    set({
      contacts: [],
      conversations: {},
      messages: {},
      callLogs: [],
      peers: {},
    }),
}));

export const useConversationList = () =>
  useConversationsStore((state) =>
    Object.values(state.conversations).sort(byMostRecent)
  );

export const useContacts = () => useConversationsStore((state) => state.contacts);
export const useCallLogs = () => useConversationsStore((state) => state.callLogs);
export const useMessages = (conversationId: string) =>
  useConversationsStore((state) => state.messages[conversationId] ?? []);
