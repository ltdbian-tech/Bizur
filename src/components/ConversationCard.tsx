import type { Conversation } from '@/types/messaging';
import { colors } from '@/theme/colors';
import { spacing, radius } from '@/theme/spacing';
import { Feather } from '@expo/vector-icons';
import { Pressable, StyleSheet, Text, View } from 'react-native';

interface ConversationCardProps {
  conversation: Conversation;
  onPress?: (conversation: Conversation) => void;
}

export const ConversationCard = ({ conversation, onPress }: ConversationCardProps) => (
  <Pressable style={({ pressed }) => [styles.card, pressed && styles.cardPressed]} onPress={() => onPress?.(conversation)}>
    <View style={styles.avatar}>
      <Text style={styles.avatarText}>{conversation.title.slice(0, 2).toUpperCase()}</Text>
    </View>
    <View style={styles.meta}>
      <View style={styles.titleRow}>
        <Text style={styles.title}>{conversation.title}</Text>
        {conversation.isSecure && <Feather name="shield" size={14} color={colors.accent} />}
      </View>
      <Text numberOfLines={1} style={styles.preview}>
        {conversation.lastMessagePreview}
      </Text>
    </View>
    {conversation.unreadCount > 0 && (
      <View style={styles.badge}>
        <Text style={styles.badgeText}>{conversation.unreadCount}</Text>
      </View>
    )}
  </Pressable>
);

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.surface,
    padding: spacing.md,
    borderRadius: radius.lg,
    marginBottom: spacing.md,
    gap: spacing.md,
  },
  cardPressed: {
    opacity: 0.8,
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: radius.lg,
    backgroundColor: colors.surfaceMuted,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: {
    color: colors.textPrimary,
    fontWeight: '600',
  },
  meta: {
    flex: 1,
    gap: spacing.xs,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
  },
  title: {
    color: colors.textPrimary,
    fontSize: 16,
    fontWeight: '600',
    flex: 1,
  },
  preview: {
    color: colors.textSecondary,
    fontSize: 13,
  },
  badge: {
    backgroundColor: colors.accent,
    borderRadius: radius.pill,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  badgeText: {
    color: colors.background,
    fontWeight: '700',
  },
});
