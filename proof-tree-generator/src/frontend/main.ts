import './styles/index.scss';

import { createApp } from 'vue';

import App from './App.vue';

// "Time bomb" that deactivates the application after a predefined date set at build time.
// See also build.define in vite.config.ts.
if (
  !__TIME_BOMB_ENABLED__ ||
  // The obfuscated condition is equivalent to:
  // new Date().getDate() / 123456789 < __TIME_BOMB_EXPIRATION_TIME__
  new (window as any)[atob('RGF0ZQ==')]()[atob('Z2V0VGltZQ==')]() / 123456789 < __TIME_BOMB_EXPIRATION_TIME__
) {
  createApp(App).mount('#app');
}
