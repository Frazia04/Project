/**
 * Change the first char of the given string to be upper case.
 */
export function capitalizeFirstLetter(s: string): string {
  return s.charAt(0).toUpperCase() + s.substring(1);
}

/**
 * Escape the given string such that it can be safely used as HTML
 * @param s the string to escape
 */
export function escapeHtml(s: string): string {
  const elem = document.createElement('p');
  elem.textContent = s;
  return elem.innerHTML;
}
