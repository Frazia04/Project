import { fork } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

import vue from '@vitejs/plugin-vue';
import { type AttributeNode, NodeTypes, type SourceLocation } from '@vue/compiler-core';
import autoprefixer from 'autoprefixer';
import browserslist from 'browserslist';
import { defineConfig, type LogLevel, normalizePath } from 'vite';
import { ViteMinifyPlugin } from 'vite-plugin-minify';
import * as vueSFCCompiler from 'vue/compiler-sfc';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // Whether this is a development build
  const isDev = mode === 'development';

  // Allow bypassing minification by executing: MINIFY=false npm run build
  const minify = parseBooleanFlag(process.env.MINIFY) ?? !isDev;

  // Get the URL of the backend server we should proxy to in development mode
  const backendServer = (() => {
    if (isDev) {
      let url = process.env.BACKEND_SERVER?.trim();
      if (url?.length) {
        if (!/^https?:\/\//.test(url)) {
          url = 'http://' + url;
        }
        if (url.endsWith('/')) {
          url = url.substring(0, url.length - 1);
        }
        return url;
      }
    }
  })();
  if (isDev) {
    console.log(
      backendServer
        ? `Will proxy backend API requests to ${backendServer}`
        : 'Will mock the backend API using Mirage JS',
    );
  }

  // Normalized absolute paths to directories from where we dynamically import modules and want all
  // such imports end up in a chunk named after the importer's file name.
  const chunkGroupDynamicImporterLocations = new Set([
    normalizePath(path.resolve(__dirname, 'src', 'router', 'lazy')),
    normalizePath(path.resolve(__dirname, 'src', 'i18n', 'translations', 'lazy')),
  ]);

  // To generate chunk names from module ids, we need to convert absolute to relative paths
  const projectPath = normalizePath(__dirname) + '/';
  function stripProjectPathPrefix(p: string): string {
    // Virtual modules start with a NULL character. Remove it if present.
    if (p.startsWith('\0')) {
      p = p.substring(1); // eslint-disable-line no-param-reassign
    }
    return p.startsWith(projectPath) ? p.substring(projectPath.length) : p;
  }

  return {
    // Allow setting log level via environment variable
    logLevel: (process.env.VITE_LOG_LEVEL as LogLevel | undefined) ?? 'info',

    // Produce relative path to assets, for deployment in subfolder
    base: '',

    build: {
      // Load supported browsers from .browserslistrc
      target: targetsFromBrowserslist(),

      // We need the manifest file for the backend
      manifest: 'frontend-manifest.json',

      minify,

      // Configure our chunk splitting strategy
      rollupOptions: {
        output: {
          manualChunks(id, { getModuleInfo }) {
            // The export helper module is required by multiple chunks.
            // Since it is very small we add it to the `index` chunk.
            if (id === '\0plugin-vue:export-helper') {
              return 'index';
            }

            // Walk all parent modules that (transitively) import this module to see which chunks require it
            const chunks = new Set<string>();
            const worklist = new Set([id]);
            for (const parentId of worklist) {
              const moduleInfo = getModuleInfo(parentId);
              if (moduleInfo) {
                // Synchronous import chain from entry module -> include it in `index` chunk
                if (moduleInfo.isEntry) {
                  return 'index';
                }

                // Modules that synchronously import this module are parents that we need to walk as well
                moduleInfo.importers.forEach((importer) => worklist.add(importer));

                // Check whether the module is asynchronously imported
                const { dynamicImporters } = moduleInfo;
                if (dynamicImporters.length) {
                  chunks.add(
                    dynamicImporters.length === 1 &&
                      chunkGroupDynamicImporterLocations.has(dynamicImporters[0].replace(/\/[^/]+$/, ''))
                      ? // Module is async-imported by router/i18n -> chunk name is the importer file name.
                        // Using that strategy, we put related views + their translations into the same chunk.
                        dynamicImporters[0].replace(/^.*\//, '').replace(/\.[^.]+$/, '')
                      : // Non-router/i18n dynamic import -> derive chunk name from module id
                        stripProjectPathPrefix(parentId)
                          .replace(/^(?:src|node_modules)\//, '') // remove src and node_modules prefix
                          .replace(/^\W+(?!$)/, '') // remove leading non-word characters (preventing empty string)
                          .replace(/(?<!^)\.\w+$/, '') // remove file type suffix (preventing empty string)
                          .replace(/\W+/g, '-'), // replace non-word characters by a dash
                  );
                }
              }
            }

            // Use the calculated single chunk name. If the module is required by multiple chunks,
            // then create a separate common chunk concatenating the names separate by underscore.
            // If we do not have a chunk name for any reason, put the module into the `index` chunk.
            if (chunks.size) {
              return Array.from(chunks).sort().join('_');
            } else {
              console.warn(`Could not determine chunk for module ${stripProjectPathPrefix(id)}.`);
              return 'index';
            }
          },
        },
      },

      // Set chunk size warning limit to 4 MB
      chunkSizeWarningLimit: 4 * 1024,
    },

    plugins: [
      // Vue plugin
      vue({ compiler: vueSFCCompiler }),

      // Minify generated HTML page
      ...(minify ? [ViteMinifyPlugin()] : []),

      // Inline svg files as vue components
      {
        name: 'svg-to-component-loader',
        enforce: 'pre',
        async load(id) {
          if (id.endsWith('.svg')) {
            const { code } = vueSFCCompiler.compileTemplate({
              id: JSON.stringify(id),
              filename: id,
              transformAssetUrls: false,
              source: await fs.promises.readFile(id, 'utf-8'),
              compilerOptions: {
                nodeTransforms: [
                  // A transformer that enhances the root <svg> element
                  (node) => {
                    if (node.type === NodeTypes.ROOT) {
                      for (const childNode of node.children) {
                        if (childNode.type === NodeTypes.ELEMENT && childNode.tag === 'svg') {
                          // Add role="img"
                          childNode.props.push(createAttributeNode('role', 'img'));

                          // For material symbols, also add class="material-symbol"
                          if (id.includes('/@material-symbols/')) {
                            childNode.props.push(createAttributeNode('class', 'material-symbol'));
                          }
                        }
                      }
                    }
                  },
                ],
              },
            });
            return `${code}\nexport default render`;
          }
        },
      },

      // Build a regular expression matching vue-router routes and write it to a file
      // such that the webserver can serve index.html for these paths.
      {
        name: 'build-routes-regexp',
        apply: 'build',
        generateBundle() {
          return new Promise((resolve, reject) => {
            const child = fork('./build-routes-regexp.ts', { execArgv: ['--import', 'tsx'] });
            child.on('message', (message) => {
              this.emitFile({
                fileName: 'frontend-routes.regexp',
                type: 'asset',
                source: message as string,
              });
              resolve();
            });
            child.on('error', reject);
          });
        },
      },
    ],

    // Use Autoprefixer to add prefixes in generated css. Uses Browserslist.
    css: { postcss: { plugins: [autoprefixer()] } },

    // Define global constants. Keep them in sync with env.d.ts
    define: {
      // Whether this is a development build
      __DEV__: JSON.stringify(isDev),

      // The version (from package.json)
      __APP_VERSION__: JSON.stringify(process.env.npm_package_version ?? '0.0.0'),

      // The backend server that we proxy API requests to
      __BACKEND_SERVER__: JSON.stringify(backendServer),

      // Whether to mock the backend server using Mirage JS
      __MIRAGE__: JSON.stringify(isDev && !backendServer),

      // Remove Vue's options API (we only use composition API)
      __VUE_OPTIONS_API__: JSON.stringify(false),
    },

    // Proxy to the configured backend server
    server: {
      proxy: backendServer
        ? {
            '/api': backendServer,
          }
        : undefined,
    },

    // Do not clear the screen, such that we can see console.log output
    clearScreen: false,
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

// Helper function used in svg-to-component-loader
function createAttributeNode(name: string, value: string): AttributeNode {
  function createLoc(source: string): SourceLocation {
    return {
      start: { column: 0, line: 0, offset: 0 },
      end: { column: 0, line: 0, offset: 0 },
      source,
    };
  }
  return {
    type: NodeTypes.ATTRIBUTE,
    name,
    nameLoc: createLoc(name),
    value: {
      type: NodeTypes.TEXT,
      content: value,
      loc: createLoc(`"${value}"`),
    },
    loc: createLoc(`${name}="${value}"`),
  };
}
