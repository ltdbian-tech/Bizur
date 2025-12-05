import type { Contact } from '@/types/messaging';
import { colors } from '@/theme/colors';
import { radius, spacing } from '@/theme/spacing';
import { Feather } from '@expo/vector-icons';
import { Pressable, StyleSheet, Text, View } from 'react-native';

interface ContactCardProps {
  contact: Contact;
  onPress?: (contact: Contact) => void;
  onCallPress?: (contact: Contact) => void;
}

const presenceColor: Record<Contact['presence'], string> = {
  online: colors.success,
  connecting: colors.warning,
  offline: colors.textMuted,
};

export const ContactCard = ({ contact, onPress, onCallPress }: ContactCardProps) => (
  <Pressable style={({ pressed }) => [styles.root, pressed && styles.pressed]} onPress={() => onPress?.(contact)}>
    <View style={[styles.avatar, { backgroundColor: contact.avatarColor }]}>
      <Text style={styles.avatarLabel}>{contact.displayName.at(0)}</Text>
    </View>
    <View style={styles.meta}>
      <Text style={styles.name}>{contact.displayName}</Text>
      <View style={styles.presenceRow}>
        <View style={[styles.presenceDot, { backgroundColor: presenceColor[contact.presence] }]} />
        <Text style={styles.presenceText}>{contact.presence}</Text>
      </View>
    </View>
    <Pressable style={styles.callButton} onPress={() => onCallPress?.(contact)}>
      <Feather name="phone" size={16} color={colors.background} />
    </Pressable>
  </Pressable>
);

const styles = StyleSheet.create({
  root: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: spacing.md,
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    marginBottom: spacing.md,
    gap: spacing.md,
  },
  pressed: {
    opacity: 0.85,
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarLabel: {
    color: colors.background,
    fontWeight: '600',
  },
  meta: {
    flex: 1,
    gap: spacing.xs,
  },
  name: {
    color: colors.textPrimary,
    fontSize: 16,
    fontWeight: '600',
  },
  presenceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
  },
  presenceDot: {
    width: 8,
    height: 8,
    borderRadius: radius.pill,
  },
  presenceText: {
    color: colors.textSecondary,
    fontSize: 12,
    textTransform: 'capitalize',
  },
  callButton: {
    backgroundColor: colors.accent,
    borderRadius: radius.pill,
    padding: spacing.sm,
  },
});
