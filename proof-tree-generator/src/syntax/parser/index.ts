import './declarations';
import './io';
import './expressions';
import './mappings';
import './types';
import './values';

import { apply, expectEOF, expectSingleResult, type Parser } from 'typescript-parsec';

import type { Context } from '../../context';
import { lexer, Tok } from './lexer';
import {
  type ConstructWithContext,
  DeclarationParser,
  EnvironmentParser,
  ExpressionParser,
  ExternalEffectsParser,
  SignatureParser,
  StoreParser,
  TypeParser,
  ValueParser,
} from './rules';

export { parseIdentifier } from './identifiers';

function buildParserFunction<T>(parser: Parser<Tok, ConstructWithContext<T>>): (s: string, ctx: Context) => T {
  return (s: string, ctx: Context) => {
    const branches = expectEOF(apply(parser, (pf) => pf(ctx)).parse(lexer.parse(s.trim() || '?')));
    // TODO: Better error message
    if (!branches.successful) {
      throw new Error(
        branches.error.message +
          (branches.error.pos ? ` (ab Zeichen ${branches.error.pos.columnBegin} in der Eingabe)` : ''),
      );
    }
    if (__DEV__ && branches.candidates.length > 1) {
      console.log('Candidates:', branches.candidates);
    }
    return expectSingleResult(branches);
  };
}

export const parseExpression = buildParserFunction(ExpressionParser);
export const parseDeclaration = buildParserFunction(DeclarationParser);
export const parseType = buildParserFunction(TypeParser);
export const parseValue = buildParserFunction(ValueParser);
export const parseSignature = buildParserFunction(SignatureParser);
export const parseEnvironment = buildParserFunction(EnvironmentParser);
export const parseStore = buildParserFunction(StoreParser);
export const parseExternalEffects = buildParserFunction(ExternalEffectsParser);

export function parseTypeDefs(): void {
  // TODO: parser for record and exception definitions
}
