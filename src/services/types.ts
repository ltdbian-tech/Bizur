import type { CallLog, Contact, Conversation, Message } from '@/types/messaging';

export interface HydratePayload {
  contacts?: Contact[];
  conversations?: Conversation[];
  messages?: Record<string, Message[]>;
  callLogs?: CallLog[];
}
