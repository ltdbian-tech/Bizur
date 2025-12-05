import { FlatList, StyleSheet, Text } from 'react-native';
import { CallLogCard } from '@/components/CallLogCard';
import { Screen } from '@/components/Screen';
import { SectionHeader } from '@/components/SectionHeader';
import { useCallLogs, useContacts } from '@/store/useConversationsStore';
import { colors } from '@/theme/colors';

const CallHistoryScreen = () => {
  const callLogs = useCallLogs();
  const contacts = useContacts();

  const nameById = Object.fromEntries(contacts.map((contact) => [contact.id, contact.displayName]));

  return (
    <Screen>
      <SectionHeader>Call History</SectionHeader>
      <FlatList
        data={callLogs}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <CallLogCard call={item} displayName={nameById[item.contactId] ?? 'Unknown peer'} />
        )}
        ListEmptyComponent={<Text style={styles.empty}>No calls yet.</Text>}
      />
    </Screen>
  );
};

const styles = StyleSheet.create({
  empty: {
    color: colors.textMuted,
    textAlign: 'center',
    marginTop: 48,
  },
});

export default CallHistoryScreen;
