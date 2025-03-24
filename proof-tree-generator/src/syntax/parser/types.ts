import { alt, apply, kmid, kright, type Parser, rule, seq, str, tok } from 'typescript-parsec';

import {
  boolType,
  charType,
  FunctionType,
  natType,
  PairType,
  RefType,
  type Type,
  TypeVariable,
  unitType,
} from '../../semantics/static/types';
import { Tok } from './lexer';
import { type ConstructWithContext, guardDecl, guardIO, guardPairs, guardStore, TypeParser } from './rules';

const AtomTypeParser: Parser<Tok, ConstructWithContext<Type>> = alt(
  apply(tok(Tok.Hole), () => (ctx) => new TypeVariable(ctx)),
  apply(str('Nat'), () => () => natType),
  apply(str('Bool'), () => () => boolType),
  apply(str('Unit'), () => () => unitType),
  apply(str('Char'), () => (ctx) => guardIO(ctx, () => charType)),
  apply(
    kmid(seq(str('Ref'), alt(tok(Tok.LAngle), tok(Tok.Lt))), TypeParser, alt(tok(Tok.RAngle), tok(Tok.Gt))),
    (t) => (ctx) => guardStore(ctx, () => new RefType(t(ctx), ctx)),
  ),
  kmid(tok(Tok.LParen), TypeParser, tok(Tok.RParen)),
);

const PairTypeParser = rule<Tok, ConstructWithContext<Type>>();
PairTypeParser.setPattern(
  alt(
    AtomTypeParser,
    apply(
      seq(AtomTypeParser, kright(tok(Tok.Star), PairTypeParser)),
      ([t1, t2]) =>
        (ctx) =>
          guardPairs(ctx, () => new PairType(t1(ctx), t2(ctx), ctx)),
    ),
  ),
);

TypeParser.setPattern(
  alt(
    PairTypeParser,
    apply(
      seq(PairTypeParser, kright(tok(Tok.Arrow), TypeParser)),
      ([t1, t2]) =>
        (ctx) =>
          guardDecl(ctx, () => new FunctionType(t1(ctx), t2(ctx), ctx)),
    ),
  ),
);
