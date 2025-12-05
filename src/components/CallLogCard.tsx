import type { ComponentProps } from 'react';
import type { CallLog } from '@/types/messaging';
import { colors } from '@/theme/colors';
import { radius, spacing } from '@/theme/spacing';
import { Feather } from '@expo/vector-icons';
import { StyleSheet, Text, View } from 'react-native';

interface CallLogCardProps {
  call: CallLog;
  displayName: string;
}

type FeatherIcon = ComponentProps<typeof Feather>['name'];

const iconByStatus: Record<CallLog['status'], FeatherIcon> = {
  connected: 'phone-call',
  missed: 'phone-off',
  declined: 'x-circle',
  ended: 'phone-call',
};

export const CallLogCard = ({ call, displayName }: CallLogCardProps) => (
  <View style={styles.container}>
    <Feather name={iconByStatus[call.status]} size={18} color={colors.accent} />
    <View style={styles.meta}>
      <Text style={styles.title}>{displayName}</Text>
      <Text style={styles.subtitle}>
        {call.direction} • {Math.round(call.durationSeconds)}s • {new Date(call.startedAt).toLocaleTimeString()}
      </Text>
    </View>
  </View>
);

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    backgroundColor: colors.surface,
    padding: spacing.md,
    borderRadius: radius.lg,
    marginBottom: spacing.md,
  },
  meta: {
    flex: 1,
  },
  title: {
    color: colors.textPrimary,
    fontWeight: '600',
  },
  subtitle: {
    color: colors.textSecondary,
    fontSize: 12,
    textTransform: 'capitalize',
  },
});
