import { alt, apply, kmid, kright, opt_sc, type Parser, rep_sc, seq, tok } from 'typescript-parsec';

import type { Context } from '../../context';
import type { Renderable } from '../../rendering';
import { BasicStore, StoreVariable } from '../../semantics/dynamic/stores';
import { BasicEnvironment, EnvironmentVariable } from '../../semantics/dynamic/values';
import type { Mapping, MappingVariable } from '../../semantics/mappings';
import { BasicSignature, SignatureVariable } from '../../semantics/static/types';
import type { StringOrSimplifiable } from '../../simplify';
import { Tok } from './lexer';
import {
  AddressParser,
  type ConstructWithContext,
  EnvironmentParser,
  IdentifierParser,
  SignatureParser,
  StoreParser,
  TypeParser,
  ValueParser,
} from './rules';

// Helper function to parse mappings
function buildMappingParser<K extends StringOrSimplifiable<K>, V extends string | Renderable>(
  keyParser: Parser<Tok, ConstructWithContext<K>>,
  valueParser: Parser<Tok, ConstructWithContext<V>>,
  basicMappingConstructor: (new () => Mapping<K, V>) &
    (new (entries: readonly (readonly [K, V])[], ctx: Context) => Mapping<K, V>),
  mappingVariableConstructor: new (ctx: Context) => MappingVariable<K, V>,
): Parser<Tok, ConstructWithContext<Mapping<K, V>>> {
  const MappingEntryParser: Parser<Tok, ConstructWithContext<[K, V]>> = apply(
    seq(keyParser, kright(tok(Tok.Arrow), valueParser)),
    ([k, v]) =>
      (ctx) => [k(ctx), v(ctx)],
  );

  return alt(
    apply(tok(Tok.Hole), () => (ctx) => new mappingVariableConstructor(ctx)),
    apply(tok(Tok.EmptySet), () => () => new basicMappingConstructor()),
    apply(
      kmid(
        tok(Tok.LBrace),
        opt_sc(seq(MappingEntryParser, rep_sc(kright(tok(Tok.Comma), MappingEntryParser)))),
        tok(Tok.RBrace),
      ),
      (entries) => (ctx) =>
        entries === undefined
          ? new basicMappingConstructor()
          : new basicMappingConstructor([entries[0](ctx), ...entries[1].map((entry) => entry(ctx))], ctx),
    ),
  );
}

const identifierParserWithContext: Parser<Tok, ConstructWithContext<string>> = apply(IdentifierParser, (t) => () => t);

SignatureParser.setPattern(
  buildMappingParser(identifierParserWithContext, TypeParser, BasicSignature, SignatureVariable),
);
EnvironmentParser.setPattern(
  buildMappingParser(identifierParserWithContext, ValueParser, BasicEnvironment, EnvironmentVariable),
);
StoreParser.setPattern(buildMappingParser(AddressParser, ValueParser, BasicStore, StoreVariable));
