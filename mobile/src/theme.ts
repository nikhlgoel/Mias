/**
 * Eye-comfort theme tokens (bridge/docs/10 section 2): never pure #000/#FFF;
 * off-white / near-black extremes; semantic slots. Grows into
 * packages/ui-tokens at stage S3+.
 */
export interface Theme {
  bg: string;
  bgRaised: string;
  text: string;
  textMuted: string;
  accent: string;
  userBubble: string;
  userText: string;
  assistantBubble: string;
  danger: string;
  border: string;
}

export const darkTheme: Theme = {
  bg: '#0E0F12',
  bgRaised: '#1A1C21',
  text: '#E6E7EA',
  textMuted: '#9BA0A8',
  accent: '#D9A05B',
  userBubble: '#2A2E36',
  userText: '#E6E7EA',
  assistantBubble: '#16181D',
  danger: '#C96A5E',
  border: '#2A2D33',
};

export const lightTheme: Theme = {
  bg: '#F5F5F4',
  bgRaised: '#FFFFFF',
  text: '#1A1B1E',
  textMuted: '#6B6F76',
  accent: '#B07830',
  userBubble: '#E7E2D9',
  userText: '#1A1B1E',
  assistantBubble: '#EDECEA',
  danger: '#A94438',
  border: '#DDDBD6',
};
