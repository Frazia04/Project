import fs from 'node:fs/promises';
import {
  dirname,
  join as joinPath,
  parse as parsePath,
  relative as relativePath,
  resolve as resolvePath,
  sep,
} from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import browserslist from 'browserslist';
import esbuild from 'esbuild';
import { compileStringAsync as compileSass } from 'sass-embedded';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const nodeModulesDir = resolvePath(__dirname, '..', 'node_modules');

const outputDirectory = joinPath(__dirname, 'dist');
const srcDirectory = joinPath(__dirname, 'src');
const stylesheetSource = joinPath(srcDirectory, 'css', 'site.scss');

// Clean dist dir before we start
await fs.rm(outputDirectory, { recursive: true, force: true });

// Esbuild options
const target = targetsFromBrowserslist();
const minify = true;

const highlightJsLanguages = ['bash', 'java', 'javascript', 'properties'];

await Promise.all([
  // Bundle src/js/* to dist/js/site.js
  (async () => {
    const resolveDir = joinPath(srcDirectory, 'js');
    await esbuild.build({
      stdin: {
        contents: (await fs.readdir(resolveDir)).map((file) => `import './${file}';\n`).join(''),
        resolveDir,
      },
      outfile: joinPath(outputDirectory, 'js', 'site.js'),
      bundle: true,
      target,
      minify,
    });
  })(),

  // Bundle highlight.js: core and languages we need, among with code registering the languages
  esbuild.build({
    stdin: {
      contents:
        `import hljs from 'highlight.js/lib/core';\n` +
        [...highlightJsLanguages, 'plaintext']
          .map((lang) => `import ${lang} from 'highlight.js/lib/languages/${lang}';\n`)
          .join('') +
        `import { mergeHTMLPlugin } from './src/highlightjs-merge-html-plugin';\n` +
        `hljs.addPlugin(mergeHTMLPlugin);\n` +
        highlightJsLanguages.map((lang) => `hljs.registerLanguage('${lang}', ${lang});\n`).join('') +
        `hljs.registerLanguage('none', plaintext);\n` +
        `hljs.configure({ cssSelector: 'pre code.hljs[data-lang]', ignoreUnescapedHTML: true });\n` +
        `hljs.highlightAll();\n`,
      resolveDir: __dirname,
    },
    outfile: joinPath(outputDirectory, 'js', 'vendor', 'highlight.js'),
    bundle: true,
    target,
    minify,
  }),

  // Bundle css from scss. Unfortunately, esbuild does not support sass natively. We therefore need
  // to call the sass compiler ourselves. Since it merges files from several locations, we need to
  // fix relative url(...) paths such that esbuild can find the files to bundle.
  (async () => {
    const resolveDir = dirname(stylesheetSource);
    await esbuild.build({
      stdin: {
        contents: (
          await compileSass(await fs.readFile(stylesheetSource, 'utf8'), {
            importer: {
              // Make all imports use absolute paths such that we have the full path in load(...)
              async canonicalize(url, { containingUrl }) {
                async function ifExists(path: string): Promise<string | null> {
                  try {
                    await fs.access(path);
                    return path;
                  } catch (ignored) {
                    return null;
                  }
                }

                async function resolveSassExt(path: string): Promise<string | null> {
                  let index: number;
                  return (
                    (await ifExists(path)) ??
                    (await ifExists(
                      (index = path.lastIndexOf(sep)) >= 0
                        ? path.slice(0, index) + sep + '_' + path.slice(index + 1)
                        : '_' + path,
                    ))
                  );
                }

                async function resolve(path: string): Promise<string | null> {
                  if ((await ifExists(dirname(path))) !== null) {
                    return (
                      (await resolveSassExt(`${path}.scss`)) ??
                      (await resolveSassExt(`${path}.css`)) ??
                      (await resolveSassExt(`${path}.sass`)) ??
                      (await resolve(joinPath(path, 'index')))
                    );
                  }
                  return null;
                }

                const basedir = containingUrl ? dirname(fileURLToPath(containingUrl)) : resolveDir;
                const path = url.startsWith('file://') ? fileURLToPath(url) : url;
                for (const baseCandidate of [basedir, nodeModulesDir]) {
                  const absolutePath = resolvePath(baseCandidate, path);
                  const resolved = await (parsePath(absolutePath).ext.match(/\.(?:sass|scss|css)/)
                    ? resolveSassExt(absolutePath)
                    : resolve(absolutePath));
                  if (resolved !== null) {
                    return pathToFileURL(resolved);
                  }
                }
                return null;
              },

              // Fix relative url(...) occurrences while loading the file contents.
              // We compute paths relative to `resolveDir` instead of absolute paths to avoid
              // problems in case the latter one contains weird characters.
              async load(canonicalUrl) {
                const path = fileURLToPath(canonicalUrl);
                let rel = relativePath(resolveDir, dirname(path));
                if (rel) {
                  // Normalize path segment separator to forward slash
                  if (sep !== '/') {
                    rel = rel.replaceAll(sep, '/');
                  }
                } else {
                  // Fix empty string
                  rel = '.';
                }
                return {
                  contents: (await fs.readFile(path, 'utf8')).replace(
                    /(url\(['"]?)(\.\.?\/)([^'")]+['"]?\))/g,
                    `$1${rel}/$2$3`,
                  ),
                  syntax: path.endsWith('.scss') ? 'scss' : path.endsWith('.css') ? 'css' : 'indented',
                };
              },
            },
          })
        ).css,
        resolveDir,
        loader: 'css',
      },
      outdir: outputDirectory,
      bundle: true,
      loader: {
        '.svg': 'copy',
        '.woff2': 'copy',
      },
      entryNames: 'css/site',
      assetNames: 'assets/[name]',
      target,
      minify,
    });
  })(),

  // Compile helpers
  (async () => {
    const dir = joinPath(srcDirectory, 'helpers');
    return esbuild.build({
      entryPoints: (await fs.readdir(dir)).map((file) => joinPath(dir, file)),
      outdir: joinPath(outputDirectory, 'helpers'),
      banner: {
        js: '"use strict";',
      },
      format: 'cjs',
    });
  })(),

  // Copy other files
  ...['assets', 'layouts', 'partials'].map((name) =>
    fs.cp(joinPath(srcDirectory, name), joinPath(outputDirectory, name), { recursive: true }),
  ),
]);

// Compute esbuild targets from browserslist
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
