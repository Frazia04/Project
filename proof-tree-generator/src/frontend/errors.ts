import { escapeHtml } from '../utils';

export const htmlErrorMessagePrefix = 'HTML-ERROR:';

/**
 * Check whether a message starts with `htmlErrorMessagePrefix`
 * @param message the message to check
 */
export function isHtmlMessage(message: string): boolean {
  return message.startsWith(htmlErrorMessagePrefix);
}

/**
 * Remove the first `htmlErrorMessagePrefix.length` characters from the given message.
 * Does not check whether the message actually starts with `htmlErrorMessagePrefix`!
 * @param message the message to strip characters from
 */
export function stripHtmlPrefix(message: string): string {
  return message.substring(htmlErrorMessagePrefix.length);
}

/**
 * Check whether a message starts with `htmlErrorMessagePrefix`. If so, remove the prefix
 * and return the remaining part unchanged. Otherwise, escape the whole message.
 * @param message the message to check
 */
export function stripHtmlPrefixOrEscape(message: string): string {
  return isHtmlMessage(message) ? stripHtmlPrefix(message) : escapeHtml(message);
}
