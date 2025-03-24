import path = require('node:path/posix');

module.exports = function relativize(to: string, options: any): string {
  // Check for empty destination path
  if (!to) {
    return '#';
  }

  // Check for destination path that is already relative
  if (to.charAt(0) !== '/') {
    return to;
  }

  // Split hash part away from destination path
  let hash = '';
  const hashIdx = to.indexOf('#');
  if (hashIdx !== -1) {
    hash = to.slice(hashIdx);
    to = to.slice(0, hashIdx);
  }

  // Extract path of current page
  const from: string = options.data.root.page.url;

  // Check whether we are linking the current page
  if (to === from) {
    if (hash) {
      return hash;
    } else if (isDir(to)) {
      return './';
    } else {
      return path.basename(to);
    }
  }

  // Compute relative path
  let relative = path.relative(path.dirname(from + '.'), to);
  if (relative) {
    if (isDir(to)) {
      relative += '/';
    }
  } else {
    relative = (isDir(to) ? './' : '../') + path.basename(to);
  }
  return relative + hash;
};

function isDir(str: string): boolean {
  return str.charAt(str.length - 1) === '/';
}
