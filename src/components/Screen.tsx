import type { PropsWithChildren, ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import { colors } from '@/theme/colors';
import { spacing } from '@/theme/spacing';

interface ScreenProps extends PropsWithChildren {
  header?: ReactNode;
  footer?: ReactNode;
}

export const Screen = ({ children, header, footer }: ScreenProps) => (
  <View style={styles.root}>
    {header}
    <View style={styles.content}>{children}</View>
    {footer}
  </View>
);

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.background,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.lg,
  },
  content: {
    flex: 1,
  },
});
