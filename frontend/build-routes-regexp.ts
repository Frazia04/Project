// NodeJS script that builds a regular expression matching vue-router routes

import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

import { JSDOM } from 'jsdom';
import Storage from 'node-storage-shim';
import { createRouterMatcher, type Router } from 'vue-router';

// Populate global scope
Object.defineProperties(global, {
  ...Object.getOwnPropertyDescriptors(getWindow()),
  // constants that are normally defined by Vite
  __DEV__: { value: false },
  __APP_VERSION__: { value: '0.0.0' },
  __MIRAGE__: { value: false },
});

// Allow imports for .vue modules (providing an empty module)
const require = createRequire(fileURLToPath(import.meta.url));
require.extensions['.vue'] = () => '';

// Collect route paths (as regular expression) from vue-router
const { options }: Router = require('./src/router').default();
const regexp = createRouterMatcher(options.routes, options)
  .getRoutes()
  .map((matcher) => `(?:${matcher.re.source})`)
  .join('|');

// Send them to the parent node process (vite build) or dump them to stdout
if (process.send) {
  process.send(regexp);
} else {
  console.log(regexp);
}

// Helper function to prepare a window object
function getWindow(): Window {
  const window: any = {};

  // Get window from JSDOM
  const jsdom = new JSDOM();
  for (const key in jsdom.window) {
    if (key !== 'window') {
      try {
        window[key] = jsdom.window[key];
      } catch (e) {
        // ignored
      }
    }
  }

  // Set appropriate self-reference
  window.window = window;

  // TypeError: 'addEventListener' called on an object that is not a valid instance of EventTarget
  window.addEventListener = () => {};

  // SecurityError: replaceState cannot update history to a URL which differs in components other than in path, query, or fragment.
  window.history.replaceState = () => {};

  // Add localStorage and sessionStorage
  window.localStorage = new Storage();
  window.sessionStorage = new Storage();

  return window;
}
