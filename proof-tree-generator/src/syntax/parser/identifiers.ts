import { apply, expectEOF, expectSingleResult, tok } from 'typescript-parsec';

import { lexer, Tok } from './lexer';
import { IdentifierParser } from './rules';

IdentifierParser.setPattern(apply(tok(Tok.Identifier), (t) => t.text));

/**
 * Parse an identifier in the given string.
 * @param s the string to parse
 * @returns the parsed identifier
 * @throws if the string does not contain a valid identifier
 */
export function parseIdentifier(s: string): string {
  return expectSingleResult(expectEOF(IdentifierParser.parse(lexer.parse(s))));
}
