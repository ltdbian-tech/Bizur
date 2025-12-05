import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { Screen } from '@/components/Screen';
import { SectionHeader } from '@/components/SectionHeader';
import { useSession } from '@/contexts/SessionContext';
import { colors } from '@/theme/colors';
import { radius, spacing } from '@/theme/spacing';

const SettingsScreen = () => {
  const { identity, setDisplayName, refreshIdentity } = useSession();
  const [displayName, setName] = useState(identity?.displayName ?? '');

  const handleSave = async () => {
    if (!displayName.trim()) return;
    await setDisplayName(displayName.trim());
  };

  return (
    <Screen>
      <SectionHeader>Node Identity</SectionHeader>
      <View style={styles.card}>
        <Text style={styles.label}>Display Name</Text>
        <TextInput
          value={displayName}
          onChangeText={setName}
          placeholder="Mesh operator"
          placeholderTextColor={colors.textMuted}
          style={styles.input}
        />
        <Pressable style={styles.primaryButton} onPress={handleSave}>
          <Text style={styles.primaryLabel}>Save Alias</Text>
        </Pressable>
      </View>
      <View style={styles.card}>
        <Text style={styles.label}>Public Key</Text>
        <Text selectable style={styles.value}>
          {identity?.publicKey}
        </Text>
        <Text style={styles.label}>Device</Text>
        <Text style={styles.value}>{identity?.deviceLabel}</Text>
        <Pressable style={styles.secondaryButton} onPress={refreshIdentity}>
          <Text style={styles.secondaryLabel}>Regenerate Identity</Text>
        </Pressable>
      </View>
    </Screen>
  );
};

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: spacing.lg,
    marginBottom: spacing.lg,
    gap: spacing.md,
  },
  label: {
    color: colors.textSecondary,
    fontSize: 12,
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  input: {
    backgroundColor: colors.surfaceMuted,
    borderRadius: radius.md,
    padding: spacing.md,
    color: colors.textPrimary,
  },
  value: {
    color: colors.textPrimary,
    fontFamily: 'Courier',
  },
  primaryButton: {
    backgroundColor: colors.accent,
    borderRadius: radius.md,
    paddingVertical: spacing.sm,
    alignItems: 'center',
  },
  primaryLabel: {
    color: colors.background,
    fontWeight: '600',
  },
  secondaryButton: {
    borderColor: colors.accent,
    borderWidth: 1,
    borderRadius: radius.md,
    paddingVertical: spacing.sm,
    alignItems: 'center',
  },
  secondaryLabel: {
    color: colors.accent,
    fontWeight: '600',
  },
});

export default SettingsScreen;
