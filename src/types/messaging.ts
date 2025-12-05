export type PresenceStatus = 'online' | 'offline' | 'connecting';
export type MessageStatus = 'pending' | 'delivered' | 'read';
export type CallDirection = 'incoming' | 'outgoing' | 'missed';
export type CallStatus = 'connected' | 'missed' | 'declined' | 'ended';

export interface Identity {
  id: string;
  displayName: string;
  publicKey: string;
  deviceLabel: string;
  createdAt: string;
}

export interface Contact {
  id: string;
  displayName: string;
  avatarColor: string;
  presence: PresenceStatus;
  lastSeen?: string;
  trustLevel: 'verified' | 'new' | 'blocked';
}

export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  body: string;
  sentAt: string;
  status: MessageStatus;
  encryptedPayload?: string;
}

export interface Conversation {
  id: string;
  peerId: string;
  title: string;
  unreadCount: number;
  lastMessagePreview: string;
  lastActivity: string;
  isSecure: boolean;
}

export interface CallLog {
  id: string;
  contactId: string;
  direction: CallDirection;
  startedAt: string;
  durationSeconds: number;
  status: CallStatus;
}

export interface PeerSession {
  id: string;
  channelHint?: string;
  presence: PresenceStatus;
  lastNegotiatedAt?: string;
}
