const esModules = [
  'react-native',
  '@react-native',
  '@react-navigation',
  'expo',
  'expo-modules-core',
  'expo-status-bar',
  'expo-secure-store',
  'expo-crypto',
  'expo-file-system',
  'expo-haptics',
  'react-native-reanimated',
  'react-native-gesture-handler',
  'react-native-safe-area-context',
  'react-native-screens',
  '@react-native-async-storage/async-storage',
].join('|');

module.exports = {
  preset: 'jest-expo',
  setupFilesAfterEnv: ['<rootDir>/jest.setup.ts'],
  transformIgnorePatterns: [`node_modules/(?!(${esModules})/)`],
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
  },
  testPathIgnorePatterns: ['<rootDir>/node_modules/', '<rootDir>/.expo/'],
};
