import { CallLog, Contact, Conversation, Message } from '@/types/messaging';

const now = Date.now();

export const demoContacts: Contact[] = [
  {
    id: 'contact-aurora',
    displayName: 'Aurora Lane',
    avatarColor: '#f97316',
    presence: 'online',
    lastSeen: new Date(now - 2 * 60 * 1000).toISOString(),
    trustLevel: 'verified',
  },
  {
    id: 'contact-niko',
    displayName: 'Niko Reyes',
    avatarColor: '#0ea5e9',
    presence: 'connecting',
    lastSeen: new Date(now - 15 * 60 * 1000).toISOString(),
    trustLevel: 'verified',
  },
  {
    id: 'contact-mira',
    displayName: 'Mira Chen',
    avatarColor: '#f472b6',
    presence: 'offline',
    lastSeen: new Date(now - 3 * 60 * 60 * 1000).toISOString(),
    trustLevel: 'new',
  },
];

export const demoConversations: Conversation[] = [
  {
    id: 'contact-aurora',
    peerId: 'contact-aurora',
    title: 'Aurora Lane',
    unreadCount: 2,
    lastMessagePreview: 'Let me know when you are online again.',
    lastActivity: new Date(now - 60 * 1000).toISOString(),
    isSecure: true,
  },
  {
    id: 'contact-niko',
    peerId: 'contact-niko',
    title: 'Mesh Team',
    unreadCount: 0,
    lastMessagePreview: 'Shared new mesh relay fingerprint.',
    lastActivity: new Date(now - 20 * 60 * 1000).toISOString(),
    isSecure: true,
  },
];

export const demoMessages: Record<string, Message[]> = {
  'contact-aurora': [
    {
      id: 'm-1',
      conversationId: 'contact-aurora',
      senderId: 'contact-aurora',
      body: 'Ping me when your relay wakes up.',
      sentAt: new Date(now - 5 * 60 * 1000).toISOString(),
      status: 'delivered',
    },
    {
      id: 'm-2',
      conversationId: 'contact-aurora',
      senderId: 'self',
      body: 'Running diagnostics now, standby.',
      sentAt: new Date(now - 4 * 60 * 1000).toISOString(),
      status: 'read',
    },
  ],
};

export const demoCalls: CallLog[] = [
  {
    id: 'call-1',
    contactId: 'contact-aurora',
    direction: 'outgoing',
    startedAt: new Date(now - 60 * 60 * 1000).toISOString(),
    durationSeconds: 420,
    status: 'connected',
  },
  {
    id: 'call-2',
    contactId: 'contact-mira',
    direction: 'incoming',
    startedAt: new Date(now - 3 * 60 * 60 * 1000).toISOString(),
    durationSeconds: 0,
    status: 'missed',
  },
];
