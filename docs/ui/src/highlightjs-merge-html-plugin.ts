// The mergeHTMLPlugin has been removed in highlight.js version 11.
// See https://github.com/highlightjs/highlight.js/issues/2889
// The plugin, however, is required for Asciidoctor/Antora. We manually add back the plugin such
// that we can use the latest version og highlight.js.
// See also: https://github.com/asciidoctor/asciidoctor/issues/3976

// eslint-disable-next-line @typescript-eslint/triple-slash-reference
/// <reference path="../../node_modules/highlight.js/types/index.d.ts" />

import type { HLJSPlugin } from 'highlight.js';

let originalStream: Event[];

export const mergeHTMLPlugin: HLJSPlugin = {
  // preserve the original HTML token stream
  'before:highlightElement': ({ el }) => {
    originalStream = nodeStream(el);
  },
  // merge it afterwards with the highlighted token stream
  'after:highlightElement': ({ el, result, text }) => {
    if (!originalStream.length) return;

    const resultNode = document.createElement('div');
    resultNode.innerHTML = result.value;
    result.value = mergeStreams(originalStream, nodeStream(resultNode), text);
    el.innerHTML = result.value;
  },
};

/* Stream merging support functions */

type Event = {
  event: 'start' | 'stop';
  offset: number;
  node: Node;
};

function tag(node: Node): string {
  return node.nodeName.toLowerCase();
}

export function nodeStream(node: Node): Event[] {
  const result: Event[] = [];
  (function _nodeStream(node, offset) {
    for (let child = node.firstChild; child; child = child.nextSibling) {
      if (child.nodeType === 3) {
        offset += child.nodeValue?.length ?? 0;
      } else if (child.nodeType === 1) {
        result.push({
          event: 'start',
          offset: offset,
          node: child,
        });
        offset = _nodeStream(child, offset);
        // Prevent void elements from having an end tag that would actually
        // double them in the output. There are more void elements in HTML
        // but we list only those realistically expected in code display.
        if (!tag(child).match(/br|hr|img|input/)) {
          result.push({
            event: 'stop',
            offset: offset,
            node: child,
          });
        }
      }
    }
    return offset;
  })(node, 0);
  return result;
}

export function mergeStreams(original: Event[], highlighted: Event[], value: string): string {
  let processed = 0;
  let result = '';
  const nodeStack = [];

  function selectStream(): Event[] {
    if (!original.length || !highlighted.length) {
      return original.length ? original : highlighted;
    }
    if (original[0].offset !== highlighted[0].offset) {
      return original[0].offset < highlighted[0].offset ? original : highlighted;
    }

    /*
    To avoid starting the stream just before it should stop the order is
    ensured that original always starts first and closes last:

    if (event1 == 'start' && event2 == 'start')
      return original;
    if (event1 == 'start' && event2 == 'stop')
      return highlighted;
    if (event1 == 'stop' && event2 == 'start')
      return original;
    if (event1 == 'stop' && event2 == 'stop')
      return highlighted;

    ... which is collapsed to:
    */
    return highlighted[0].event === 'start' ? original : highlighted;
  }

  function open(node: Node): void {
    function attributeString(attr: Attr): string {
      return ' ' + attr.nodeName + '="' + escapeHTML(attr.value) + '"';
    }
    result += '<' + tag(node) + [].map.call((node as Element).attributes, attributeString).join('') + '>';
  }

  function close(node: Node): void {
    result += '</' + tag(node) + '>';
  }

  function render(event: Event): void {
    (event.event === 'start' ? open : close)(event.node);
  }

  while (original.length || highlighted.length) {
    let stream = selectStream();
    result += escapeHTML(value.substring(processed, stream[0].offset));
    processed = stream[0].offset;
    if (stream === original) {
      /*
      On any opening or closing tag of the original markup we first close
      the entire highlighted node stack, then render the original tag along
      with all the following original tags at the same offset and then
      reopen all the tags on the highlighted stack.
      */
      nodeStack.reverse().forEach(close);
      do {
        render(stream.splice(0, 1)[0]);
        stream = selectStream();
      } while (stream === original && stream.length && stream[0].offset === processed);
      nodeStack.reverse().forEach(open);
    } else {
      if (stream[0].event === 'start') {
        nodeStack.push(stream[0].node);
      } else {
        nodeStack.pop();
      }
      render(stream.splice(0, 1)[0]);
    }
  }
  return result + escapeHTML(value.substr(processed));
}

function escapeHTML(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;');
}
