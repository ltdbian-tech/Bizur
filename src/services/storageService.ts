import * as SecureStore from 'expo-secure-store';
import * as FileSystem from 'expo-file-system';
import type { HydratePayload } from '@/services/types';
import type { Identity } from '@/types/messaging';

const IDENTITY_KEY = 'bizur.identity.v1';

const resolveStateFileUri = () => {
  try {
    return new FileSystem.File(FileSystem.Paths.document, 'bizur-state.json').uri;
  } catch {
    return new FileSystem.File(FileSystem.Paths.cache, 'bizur-state.json').uri;
  }
};

const STATE_FILE = resolveStateFileUri();

export const storageService = {
  async saveIdentity(identity: Identity) {
    await SecureStore.setItemAsync(IDENTITY_KEY, JSON.stringify(identity));
  },
  async loadIdentity(): Promise<Identity | null> {
    const raw = await SecureStore.getItemAsync(IDENTITY_KEY);
    return raw ? (JSON.parse(raw) as Identity) : null;
  },
  async clearIdentity() {
    await SecureStore.deleteItemAsync(IDENTITY_KEY);
  },
  async persistState(payload: HydratePayload) {
    await FileSystem.writeAsStringAsync(STATE_FILE, JSON.stringify(payload));
  },
  async loadState(): Promise<HydratePayload | null> {
    try {
      const raw = await FileSystem.readAsStringAsync(STATE_FILE);
      return raw ? (JSON.parse(raw) as HydratePayload) : null;
    } catch {
      return null;
    }
  },
};
