import { NavigationContainer, DefaultTheme, Theme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import MainTabs from '@/navigation/MainTabs';
import type { RootStackParamList } from '@/navigation/types';
import { colors } from '@/theme/colors';

const Stack = createNativeStackNavigator<RootStackParamList>();

const navigationTheme: Theme = {
  ...DefaultTheme,
  dark: true,
  colors: {
    ...DefaultTheme.colors,
    primary: colors.accent,
    background: colors.background,
    text: colors.textPrimary,
    border: colors.border,
    card: colors.surface,
    notification: colors.accent,
  },
};

const RootNavigator = () => (
  <NavigationContainer theme={navigationTheme}>
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="MainTabs" component={MainTabs} />
    </Stack.Navigator>
  </NavigationContainer>
);

export default RootNavigator;
