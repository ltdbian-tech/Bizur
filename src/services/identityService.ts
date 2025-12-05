import * as Crypto from 'expo-crypto';
import { storageService } from '@/services/storageService';
import type { Identity } from '@/types/messaging';

const DEVICE_LABEL = 'Android Mesh Node';

const generateId = () => Crypto.randomUUID?.() ?? `peer-${Date.now()}`;

const generatePublicKey = () =>
  Crypto.getRandomBytes(32)
    .reduce((acc, byte) => acc + byte.toString(16).padStart(2, '0'), '')
    .slice(0, 64);

export const identityService = {
  async loadOrCreate(displayName = 'Bizur Operator'): Promise<Identity> {
    const existing = await storageService.loadIdentity();
    if (existing) {
      return existing;
    }

    const next: Identity = {
      id: generateId(),
      displayName,
      publicKey: generatePublicKey(),
      deviceLabel: DEVICE_LABEL,
      createdAt: new Date().toISOString(),
    };

    await storageService.saveIdentity(next);
    return next;
  },
  async updateDisplayName(displayName: string) {
    const identity = await storageService.loadIdentity();
    if (!identity) return null;

    const updated = { ...identity, displayName };
    await storageService.saveIdentity(updated);
    return updated;
  },
};
