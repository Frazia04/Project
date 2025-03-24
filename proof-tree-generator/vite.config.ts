import vue from '@vitejs/plugin-vue';
import autoprefixer from 'autoprefixer';
import browserslist from 'browserslist';
import { defineConfig, type LogLevel } from 'vite';
import { ViteMinifyPlugin } from 'vite-plugin-minify';
import { viteSingleFile } from 'vite-plugin-singlefile';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // Whether this is a development build
  const isDev = mode === 'development';

  // Allow bypassing minification by executing: MINIFY=false npm run build
  const minify = parseBooleanFlag(process.env.MINIFY) ?? !isDev;

  // Generate single file by executing: SINGLE_FILE=true npm run build
  const singleFile = parseBooleanFlag(process.env.SINGLE_FILE) ?? false;

  return {
    // Allow setting log level via environment variable
    logLevel: (process.env.VITE_LOG_LEVEL as LogLevel | undefined) ?? 'info',

    // Produce relative path to assets, for deployment in subfolder
    base: '',

    build: {
      // Load supported browsers from .browserslistrc
      target: targetsFromBrowserslist(),

      // We do not use dynamic imports, so no need for module preload
      modulePreload: { polyfill: false },

      minify,
    },

    plugins: [
      // Vue plugin
      vue(),

      // Minify generated HTML page
      ...(minify ? [ViteMinifyPlugin()] : []),

      // Generate single HTML file (inline all assets)
      ...(singleFile ? [viteSingleFile()] : []),
    ],

    // Use Autoprefixer to add prefixes in generated css. Uses Browserslist.
    css: { postcss: { plugins: [autoprefixer()] } },

    // Define global constants. Keep them in sync with env.d.ts
    define: {
      // Whether this is a development build
      __DEV__: isDev,

      // The version (git commit)
      __APP_VERSION__: JSON.stringify(process.env.npm_package_version ?? '0.0.0'),

      // Allow to disable unfinished features
      __FEATURE_EXCEPTIONS__: parseBooleanFlag(process.env.FEATURE_EXCEPTIONS) ?? isDev,

      // Set the expiration time for the "time bomb" in frontend/main.ts
      __TIME_BOMB_ENABLED__: false,
      __TIME_BOMB_EXPIRATION_TIME__: ((): number => {
        const daysUntilExpiration = 31;
        const expiration = new Date();
        expiration.setDate(expiration.getDate() + daysUntilExpiration);

        // For obfuscation, we divide the milliseconds timestamp by 123456789.
        // Round the result to three decimal places. Precision then is 2 minutes.
        return Math.ceil(expiration.getTime() / 123456.789) / 1000;
      })(),
    },
  };
});

// Helper for parsing environment variables
function parseBooleanFlag(value: string | undefined): boolean | undefined {
  if (value !== undefined) {
    const s = value.trim();
    if (/^(?:y|yes|true|1|on)$/i.test(s)) {
      return true;
    }
    if (/^(?:n|no|false|0|off)$/i.test(s)) {
      return false;
    }
  }
  return undefined;
}

// Add Browserslist support to Vite
// https://github.com/vitejs/vite/issues/11489
// https://github.com/evanw/esbuild/issues/121
function targetsFromBrowserslist(): string[] {
  // Collect lowest supported version for each browser. The browserslist result is sorted, lowest
  // version number comes last. We therefore can simply overwrite the map entry in each iteration.
  const browsers = new Map<string, string>();
  browserslist().forEach((entry) => {
    const [browser, rawVersion] = entry.split(' ');
    const version = rawVersion
      // Version range: take lower bound
      .split('-')[0]
      // All versions: use version 1
      .replace(/^all$/, '1')
      // Remove trailing .0
      .replace(/(?:\.0)+$/, '');

    // Only accept numeric versions (ignores "safari TP")
    if (/^\d+(?:\.\d+)*$/.test(version)) {
      browsers.set(browser, version);
    }
  });

  // Mapping from esbuild target environment name to browserslist browser name
  const map = new Map<string, string>([['ios', 'ios_saf']]);
  ['chrome', 'edge', 'firefox', 'ie', 'opera', 'safari'].forEach((name) => map.set(name, name));

  // Construct result
  const result: string[] = [];
  for (const [esbuildName, browserslistName] of map.entries()) {
    const version = browsers.get(browserslistName);
    if (version) {
      result.push(esbuildName + version);
    }
  }
  return result;
}
