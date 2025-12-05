import type { ComponentProps } from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import type { BottomTabNavigationOptions } from '@react-navigation/bottom-tabs';
import { Feather } from '@expo/vector-icons';
import { colors } from '@/theme/colors';
import { spacing } from '@/theme/spacing';
import ChatsScreen from '@/screens/ChatsScreen';
import ContactsScreen from '@/screens/ContactsScreen';
import CallHistoryScreen from '@/screens/CallHistoryScreen';
import SettingsScreen from '@/screens/SettingsScreen';
import type { MainTabsParamList } from '@/navigation/types';

const Tab = createBottomTabNavigator<MainTabsParamList>();

type FeatherIcon = ComponentProps<typeof Feather>['name'];

const iconMap: Record<keyof MainTabsParamList, FeatherIcon> = {
  Chats: 'message-circle',
  Contacts: 'users',
  Calls: 'phone-call',
  Settings: 'settings',
};

const createTabBarIcon = (
  routeName: keyof MainTabsParamList
): NonNullable<BottomTabNavigationOptions['tabBarIcon']> =>
  ({ color, size }) => (
    <Feather name={iconMap[routeName]} size={size} color={color} />
  );

const buildScreenOptions = ({ route }: { route: { name: keyof MainTabsParamList } }) => ({
  headerShown: false,
  tabBarActiveTintColor: colors.accent,
  tabBarInactiveTintColor: colors.textMuted,
  tabBarStyle: {
    backgroundColor: colors.surface,
    borderTopColor: colors.border,
    paddingBottom: spacing.sm,
    paddingTop: spacing.xs,
    height: 72,
  },
  tabBarIcon: createTabBarIcon(route.name),
});

const MainTabs = () => (
  <Tab.Navigator screenOptions={buildScreenOptions}>
    <Tab.Screen name="Chats" component={ChatsScreen} />
    <Tab.Screen name="Contacts" component={ContactsScreen} />
    <Tab.Screen name="Calls" component={CallHistoryScreen} options={{ title: 'Calls' }} />
    <Tab.Screen name="Settings" component={SettingsScreen} />
  </Tab.Navigator>
);

export default MainTabs;
