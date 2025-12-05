import { useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { ContactCard } from '@/components/ContactCard';
import { Screen } from '@/components/Screen';
import { SectionHeader } from '@/components/SectionHeader';
import { useBizurClientContext } from '@/contexts/BizurClientContext';
import { useConversationsStore, useContacts } from '@/store/useConversationsStore';
import { colors } from '@/theme/colors';
import { radius, spacing } from '@/theme/spacing';
import { useShallow } from 'zustand/react/shallow';

const accentPalette = ['#7c5dff', '#f97316', '#0ea5e9', '#34d399', '#f472b6'];

const ContactsScreen = () => {
  const contacts = useContacts();
  const { startCall } = useBizurClientContext();
  const { upsertContact, upsertConversation } = useConversationsStore(
    useShallow((state) => ({
      upsertContact: state.upsertContact,
      upsertConversation: state.upsertConversation,
    }))
  );
  const [displayName, setDisplayName] = useState('');
  const [peerCode, setPeerCode] = useState('');
  const [feedback, setFeedback] = useState('');

  const canAdd = useMemo(() => displayName.trim().length >= 2 && peerCode.trim().length >= 3, [displayName, peerCode]);

  const handleAddContact = () => {
    if (!canAdd) {
      setFeedback('Enter a name and peer code.');
      return;
    }

    const normalizedId = peerCode.trim().toLowerCase();
    const now = new Date().toISOString();
    const avatarColor = accentPalette[Math.floor(Math.random() * accentPalette.length)];

    upsertContact({
      id: normalizedId,
      displayName: displayName.trim(),
      avatarColor,
      presence: 'connecting',
      lastSeen: now,
      trustLevel: 'new',
    });

    upsertConversation({
      id: normalizedId,
      peerId: normalizedId,
      title: displayName.trim(),
      unreadCount: 0,
      lastMessagePreview: 'Secure channel primed.',
      lastActivity: now,
      isSecure: true,
    });

    setDisplayName('');
    setPeerCode('');
    setFeedback('Contact stored locally. Share your key to finish pairing.');
  };

  return (
    <Screen>
      <View style={styles.composer}>
        <Text style={styles.composerTitle}>Add a peer</Text>
        <TextInput
          placeholder="Display name"
          placeholderTextColor={colors.textMuted}
          value={displayName}
          onChangeText={setDisplayName}
          style={styles.input}
        />
        <TextInput
          placeholder="Peer Bizur code"
          placeholderTextColor={colors.textMuted}
          autoCapitalize="none"
          autoCorrect={false}
          value={peerCode}
          onChangeText={setPeerCode}
          style={styles.input}
        />
        <Pressable
          style={({ pressed }) => [styles.addButton, (!canAdd || pressed) && styles.addButtonDisabled]}
          onPress={handleAddContact}
          disabled={!canAdd}
        >
          <Text style={styles.addButtonLabel}>Save contact</Text>
        </Pressable>
        {!!feedback && <Text style={styles.feedback}>{feedback}</Text>}
      </View>
      <SectionHeader>Trusted Peers</SectionHeader>
      <FlatList
        data={contacts}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <ContactCard contact={item} onCallPress={() => startCall(item.id)} />
        )}
        ListEmptyComponent={<Text style={styles.empty}>Broadcast your Bizur key to find peers.</Text>}
      />
    </Screen>
  );
};

const styles = StyleSheet.create({
  composer: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: spacing.md,
    gap: spacing.sm,
    marginBottom: spacing.lg,
  },
  composerTitle: {
    color: colors.textSecondary,
    fontSize: 12,
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  input: {
    backgroundColor: colors.surfaceMuted,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    color: colors.textPrimary,
  },
  addButton: {
    backgroundColor: colors.accent,
    borderRadius: radius.md,
    paddingVertical: spacing.sm,
    alignItems: 'center',
  },
  addButtonDisabled: {
    opacity: 0.6,
  },
  addButtonLabel: {
    color: colors.background,
    fontWeight: '600',
  },
  feedback: {
    color: colors.textSecondary,
    fontSize: 12,
  },
  empty: {
    color: colors.textMuted,
    textAlign: 'center',
    marginTop: 48,
  },
});

export default ContactsScreen;
