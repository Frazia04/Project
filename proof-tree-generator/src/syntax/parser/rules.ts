import { rule } from 'typescript-parsec';

import type { Context } from '../../context';
import type { Environment, Store, Value } from '../../semantics/dynamic';
import type { ExternalEffect } from '../../semantics/dynamic/externalEffects';
import type { Address } from '../../semantics/dynamic/values';
import type { Signature, Type } from '../../semantics/static';
import type { Declaration, Expression } from '..';
import type { Tok } from './lexer';

// The IdentifierParser validates that the lexer creates a single Identifier or Address token.
// Address names can also be used as identifiers, the parser allows both token kinds.
export const IdentifierParser = rule<Tok, string>();

// Other parsers need access to the Context (e.g. to construct meta variables when seeing a hole)
export type ConstructWithContext<T> = (ctx: Context) => T;

// A parser that only accepts addresses, but not other identifiers.
// This parser is required when an address should be parsed as atomic value.
export const AddressParser = rule<Tok, ConstructWithContext<Address>>();

export const ExpressionParser = rule<Tok, ConstructWithContext<Expression>>();
export const DeclarationParser = rule<Tok, ConstructWithContext<Declaration>>();
export const TypeParser = rule<Tok, ConstructWithContext<Type>>();
export const ValueParser = rule<Tok, ConstructWithContext<Value>>();
export const SignatureParser = rule<Tok, ConstructWithContext<Signature>>();
export const EnvironmentParser = rule<Tok, ConstructWithContext<Environment>>();
export const StoreParser = rule<Tok, ConstructWithContext<Store>>();
export const ExternalEffectsParser = rule<Tok, ConstructWithContext<ExternalEffect>>();

// Guards for features
export interface Guard {
  (ctx: Context): void;
  <T>(ctx: Context, cb: () => T): T;
}
class FeatureNotSupportedError extends Error {
  constructor(featureName: string) {
    super(`Das Sprachfeature ${featureName} ist nicht aktiviert!`);
    this.name = 'FeatureNotSupportedError';
  }
}

function buildGuard(featureName: string, check: (ctx: Context) => boolean): Guard {
  return <T>(ctx: Context, cb?: () => T) => {
    if (check(ctx)) {
      return cb?.();
    }
    throw new FeatureNotSupportedError(featureName);
  };
}

export const guardDecl = buildGuard('Wertedefinitionen & Funktionen', (ctx) => ctx.features.decl);
export const guardPairs = buildGuard('Paare', (ctx) => ctx.features.pairs);
export const guardIO = buildGuard('Ein- und Ausgabe', (ctx) => ctx.features.io);
export const guardStore = buildGuard('Zustand', (ctx) => ctx.features.store);
export const guardExceptions = buildGuard('Ausnahmen', (ctx) => ctx.features.exceptions);
