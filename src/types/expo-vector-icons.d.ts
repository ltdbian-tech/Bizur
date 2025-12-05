declare module '@expo/vector-icons' {
  import type { ComponentType } from 'react';
  import type { TextProps } from 'react-native';

  export interface ExpoIconProps extends TextProps {
    name: string;
    size?: number;
    color?: string;
  }

  export const Feather: ComponentType<ExpoIconProps>;
}
