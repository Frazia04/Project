import { alt, apply, kmid, kright, opt, seq, tok } from 'typescript-parsec';

import { htmlErrorMessagePrefix } from '../../frontend/errors';
import {
  Address,
  CharValue,
  Closure,
  falseValue,
  NatValue,
  PairValue,
  RecursiveClosure,
  trueValue,
  unitValue,
  ValueVariable,
} from '../../semantics/dynamic/values';
import { CharLiteralParser, NumberLiteralParser, Tok } from './lexer';
import {
  AddressParser,
  EnvironmentParser,
  ExpressionParser,
  guardDecl,
  guardIO,
  guardStore,
  IdentifierParser,
  ValueParser,
} from './rules';

AddressParser.setPattern(
  apply(IdentifierParser, (id) => (ctx) => {
    if (/^a[0-9]+$/.test(id)) {
      const index = BigInt(id.substring(1));
      if (index >= ctx.nextAddressIndex.value) {
        ctx.snapshot.setRef(ctx.nextAddressIndex, index + 1n);
      }
      return new Address(index);
    }
    throw new Error(
      htmlErrorMessagePrefix +
        `'${id}' is keine gültige Speicher-Adresse! Adressen müssen die Form a<span class="sub">n</span> haben.`,
    );
  }),
);

ValueParser.setPattern(
  alt(
    apply(tok(Tok.Hole), () => (ctx) => new ValueVariable(ctx)),
    apply(tok(Tok.True), () => () => trueValue),
    apply(tok(Tok.False), () => () => falseValue),
    apply(NumberLiteralParser, (n) => () => new NatValue(n)),
    apply(CharLiteralParser, (char) => (ctx) => guardIO(ctx, () => new CharValue(char))),
    apply(seq(tok(Tok.LParen), tok(Tok.RParen)), () => () => unitValue),
    apply(
      seq(kright(tok(Tok.LParen), ValueParser), kmid(tok(Tok.Comma), ValueParser, tok(Tok.RParen))),
      ([fst, snd]) =>
        (ctx) =>
          new PairValue(fst(ctx), snd(ctx), ctx),
    ),
    apply(AddressParser, (a) => (ctx) => guardStore(ctx, () => a(ctx))),
    apply(
      seq(
        kright(alt(tok(Tok.LAngle), tok(Tok.Lt)), EnvironmentParser),
        opt(kright(tok(Tok.Comma), IdentifierParser)),
        kright(tok(Tok.Comma), IdentifierParser),
        kmid(tok(Tok.Comma), ExpressionParser, alt(tok(Tok.RAngle), tok(Tok.Gt))),
      ),
      ([env, functionId, paramId, body]) =>
        (ctx) =>
          guardDecl(ctx, () =>
            functionId === undefined
              ? new Closure(env(ctx), paramId, body(ctx), ctx)
              : new RecursiveClosure(env(ctx), functionId, paramId, body(ctx), ctx),
          ),
    ),
  ),
);
