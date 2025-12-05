import { useCallback, useEffect, useMemo, useState } from 'react';
import { FlatList, ListRenderItem, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { ConversationCard } from '@/components/ConversationCard';
import { Screen } from '@/components/Screen';
import { SectionHeader } from '@/components/SectionHeader';
import { useBizurClientContext } from '@/contexts/BizurClientContext';
import { useConversationList, useMessages } from '@/store/useConversationsStore';
import { colors } from '@/theme/colors';
import { radius, spacing } from '@/theme/spacing';
import type { Conversation } from '@/types/messaging';

const ChatsScreen = () => {
  const conversations = useConversationList();
  const [selectedConversationId, setSelectedConversationId] = useState<string | null>(null);
  const messages = useMessages(selectedConversationId ?? '');
  const { sendMessage } = useBizurClientContext();
  const [draft, setDraft] = useState('');

  useEffect(() => {
    if (!selectedConversationId && conversations.length > 0) {
      setSelectedConversationId(conversations[0].id);
    }
  }, [conversations, selectedConversationId]);

  const currentConversationTitle = useMemo(() => {
    if (!selectedConversationId) return 'No conversation';
    return conversations.find((conversation) => conversation.id === selectedConversationId)?.title ?? 'Chat';
  }, [conversations, selectedConversationId]);

  const handleSend = async () => {
    if (!selectedConversationId || !draft.trim()) return;
    await sendMessage(selectedConversationId, draft.trim());
    setDraft('');
  };

  const renderConversation: ListRenderItem<Conversation> = useCallback(
    ({ item }) => (
      <ConversationCard conversation={item} onPress={() => setSelectedConversationId(item.id)} />
    ),
    [setSelectedConversationId]
  );

  return (
    <Screen>
      <SectionHeader>Secure Chats</SectionHeader>
      <FlatList
        data={conversations}
        keyExtractor={(item) => item.id}
        renderItem={renderConversation}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={<Text style={styles.emptyText}>No peers yet. Share your Bizur code to begin.</Text>}
      />
      <View style={styles.composer}>
        <Text style={styles.composerTitle}>{currentConversationTitle}</Text>
        <View style={styles.composerRow}>
          <TextInput
            placeholder="Type an encrypted whisper"
            placeholderTextColor={colors.textMuted}
            style={styles.input}
            value={draft}
            onChangeText={setDraft}
            multiline
          />
          <Pressable style={({ pressed }) => [styles.sendButton, pressed && styles.sendButtonPressed]} onPress={handleSend}>
            <Text style={styles.sendLabel}>Send</Text>
          </Pressable>
        </View>
        <View style={styles.messageLog}>
          {messages.slice(-3).map((message) => (
            <Text key={message.id} style={styles.messageLine} numberOfLines={2}>
              {message.senderId === 'self' ? 'You' : message.senderId}: {message.body}
            </Text>
          ))}
        </View>
      </View>
    </Screen>
  );
};

const styles = StyleSheet.create({
  listContent: {
    paddingBottom: spacing.xl,
  },
  emptyText: {
    color: colors.textMuted,
    textAlign: 'center',
    paddingVertical: spacing.xl,
  },
  composer: {
    gap: spacing.sm,
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: spacing.md,
  },
  composerTitle: {
    color: colors.textSecondary,
    fontSize: 12,
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  composerRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: spacing.sm,
  },
  input: {
    flex: 1,
    minHeight: 48,
    maxHeight: 96,
    color: colors.textPrimary,
    padding: spacing.sm,
    backgroundColor: colors.surfaceMuted,
    borderRadius: radius.md,
  },
  sendButton: {
    backgroundColor: colors.accent,
    borderRadius: radius.md,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
  },
  sendButtonPressed: {
    opacity: 0.85,
  },
  sendLabel: {
    color: colors.background,
    fontWeight: '600',
  },
  messageLog: {
    gap: spacing.xs,
  },
  messageLine: {
    color: colors.textSecondary,
    fontSize: 12,
  },
});

export default ChatsScreen;
