module.exports = {
  preset: 'react-native',
  // Transform our linked TS packages (@mias/*) too; RN's default ignore pattern
  // would skip them and fail on TS syntax.
  transformIgnorePatterns: [
    'node_modules/(?!((jest-)?react-native|@react-native(-community)?|@mias)/)',
  ],
  // Linked packages resolve helpers (@babel/runtime) from their real path
  // outside this app dir — fall back to this app's node_modules.
  moduleDirectories: ['node_modules', '<rootDir>/node_modules'],
};
