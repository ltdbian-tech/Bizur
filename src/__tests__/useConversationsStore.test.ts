import { act } from '@testing-library/react-native';
import { useConversationsStore } from '@/store/useConversationsStore';

const resetStore = () => useConversationsStore.getState().reset();

describe('useConversationsStore', () => {
  afterEach(() => {
    resetStore();
  });

  it('adds a message and updates conversation metadata', () => {
    act(() => {
      useConversationsStore.getState().hydrateWithSeed({
        conversations: [
          {
            id: 'peer-1',
            peerId: 'peer-1',
            title: 'Peer One',
            unreadCount: 0,
            lastMessagePreview: 'Hello',
            lastActivity: new Date(0).toISOString(),
            isSecure: true,
          },
        ],
      });
      useConversationsStore.getState().addMessage({
        id: 'msg-1',
        conversationId: 'peer-1',
        senderId: 'peer-1',
        body: 'Mesh ping',
        sentAt: new Date(1).toISOString(),
        status: 'delivered',
      });
    });

    const { conversations, messages } = useConversationsStore.getState();
    expect(messages['peer-1']).toHaveLength(1);
    expect(conversations['peer-1'].lastMessagePreview).toBe('Mesh ping');
    expect(conversations['peer-1'].unreadCount).toBe(1);
  });
});
