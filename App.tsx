import 'react-native-gesture-handler';
import { StatusBar } from 'expo-status-bar';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import RootNavigator from '@/navigation/RootNavigator';
import { SessionProvider, useSession } from '@/contexts/SessionContext';
import { BizurClientProvider, useBizurClientContext } from '@/contexts/BizurClientContext';
import { colors } from '@/theme/colors';

const AppGate = () => {
  const { identity, isLoading } = useSession();
  const { isReady } = useBizurClientContext();

  if (isLoading || !identity || !isReady) {
    return (
      <View style={styles.loadingState}>
        <ActivityIndicator size="small" color={colors.accent} />
        <Text style={styles.loadingText}>Spinning up your secure mesh…</Text>
      </View>
    );
  }

  return <RootNavigator />;
};

export default function App() {
  return (
    <GestureHandlerRootView style={styles.root}>
      <SafeAreaProvider>
        <SessionProvider>
          <BizurClientProvider>
            <AppGate />
            <StatusBar style="light" />
          </BizurClientProvider>
        </SessionProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.background,
  },
  loadingState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.background,
    gap: 12,
  },
  loadingText: {
    color: colors.textSecondary,
    fontSize: 14,
  },
});
