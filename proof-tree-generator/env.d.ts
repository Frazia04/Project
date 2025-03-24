// vite.config.ts: build.define
declare const __DEV__: boolean;
declare const __APP_VERSION__: string;
declare const __FEATURE_EXCEPTIONS__: boolean;
declare const __TIME_BOMB_ENABLED__: boolean;
declare const __TIME_BOMB_EXPIRATION_TIME__: number;

interface Navigator {
  // Add navigator.userAgentData (experimental spec)
  // https://developer.mozilla.org/en-US/docs/Web/API/Navigator/userAgentData
  // This excerpt only contains what we use in our codebase.
  // TODO: Remove it once it is stable and ships with typescript.
  readonly userAgentData?: {
    // https://developer.mozilla.org/en-US/docs/Web/API/NavigatorUAData/mobile
    readonly mobile: boolean;
  };
}
