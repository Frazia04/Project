import { fileURLToPath, URL } from 'node:url';

import vue from '@vitejs/plugin-vue';
import { defineConfig, type LogLevel } from 'vite';

// https://vitejs.dev/config/
// export default defineConfig({
//   plugins: [
//     vue()
//   ],
//   resolve: {
//     alias: {
//       '@': fileURLToPath(new URL('./src', import.meta.url))
//     }
//   }
// })

export default defineConfig(() => {
  return {
    // Allow setting log level via environment variable
    logLevel: process.env.VITE_LOG_LEVEL as LogLevel | undefined ?? 'info',

    // Produce relative path to assets, for deployment in subfolder
    base: '',

    build: {
      // Set chunk size warning limit to 1 MB
      chunkSizeWarningLimit: 1 * 1024,
    },

    plugins: [
      vue(),
    ],

    define: {
      __APP_VERSION__: JSON.stringify(process.env.npm_package_version ?? '0.0.0'),
      __DEV__: false,
    },

    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
  }
});