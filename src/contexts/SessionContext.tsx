import type { PropsWithChildren } from 'react';
import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { identityService } from '@/services/identityService';
import type { Identity } from '@/types/messaging';

interface SessionContextValue {
  identity: Identity | null;
  isLoading: boolean;
  refreshIdentity: () => Promise<void>;
  setDisplayName: (displayName: string) => Promise<void>;
}

const SessionContext = createContext<SessionContextValue | undefined>(undefined);

export const SessionProvider = ({ children }: PropsWithChildren) => {
  const [identity, setIdentity] = useState<Identity | null>(null);
  const [isLoading, setLoading] = useState(true);

  useEffect(() => {
    const loadIdentity = async () => {
      const next = await identityService.loadOrCreate();
      setIdentity(next);
      setLoading(false);
    };

    void loadIdentity();
  }, []);

  const refreshIdentity = useCallback(async () => {
    const next = await identityService.loadOrCreate(identity?.displayName);
    setIdentity(next);
  }, [identity?.displayName]);

  const setDisplayName = useCallback(async (displayName: string) => {
    const updated = await identityService.updateDisplayName(displayName);
    if (updated) {
      setIdentity(updated);
    }
  }, []);

  return (
    <SessionContext.Provider value={{ identity, isLoading, refreshIdentity, setDisplayName }}>
      {children}
    </SessionContext.Provider>
  );
};

export const useSession = () => {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error('useSession must be used inside SessionProvider');
  }

  return context;
};
