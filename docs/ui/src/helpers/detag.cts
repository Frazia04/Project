const TAG_ALL_RX = /<[^>]+>/g;

module.exports = function detag(html?: string): string | undefined {
  return html && html.replace(TAG_ALL_RX, '');
};
