import type { PropsWithChildren } from 'react';
import { createContext, useContext } from 'react';
import { useBizurClient, type UseBizurClientResult } from '@/hooks/useBizurClient';

const BizurClientContext = createContext<UseBizurClientResult | null>(null);

export const BizurClientProvider = ({ children }: PropsWithChildren) => {
  const client = useBizurClient();
  return <BizurClientContext.Provider value={client}>{children}</BizurClientContext.Provider>;
};

export const useBizurClientContext = () => {
  const value = useContext(BizurClientContext);
  if (!value) {
    throw new Error('useBizurClientContext must be used inside BizurClientProvider');
  }
  return value;
};
