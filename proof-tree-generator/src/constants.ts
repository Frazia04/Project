export const appVersion = __APP_VERSION__;
export const featureFlagExceptions = __FEATURE_EXCEPTIONS__;

export const isMobileBrowser: boolean =
  // If present, use the value from navigator.userAgentData.mobile (experimental spec)
  // https://developer.mozilla.org/en-US/docs/Web/API/Navigator/userAgentData
  // https://developer.mozilla.org/en-US/docs/Web/API/NavigatorUAData/mobile
  navigator.userAgentData?.mobile ??
  // otherwise use a regex to analyze the user agent string
  /Android|iPhone|iPad|iPod|\bMobile\b/i.test(navigator.userAgent);
