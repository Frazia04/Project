import { alt, apply, kmid, kright, seq, tok } from 'typescript-parsec';

import { DeclarationVariable, FunctionDefinition, LetBinding, RecursiveFunctionDefinition } from '..';
import { Tok } from './lexer';
import { DeclarationParser, ExpressionParser, guardDecl, IdentifierParser, TypeParser } from './rules';

DeclarationParser.setPattern(
  alt(
    apply(tok(Tok.Hole), () => (ctx) => guardDecl(ctx, () => new DeclarationVariable(ctx))),
    apply(
      seq(kright(tok(Tok.Let), IdentifierParser), kright(tok(Tok.Eq), ExpressionParser)),
      ([x, e]) =>
        (ctx) =>
          guardDecl(ctx, () => new LetBinding(x, e(ctx), ctx)),
    ),
    apply(
      seq(
        kright(tok(Tok.Let), IdentifierParser),
        kright(tok(Tok.LParen), IdentifierParser),
        kright(tok(Tok.Colon), TypeParser),
        kright(seq(tok(Tok.RParen), tok(Tok.Colon)), TypeParser),
        kright(tok(Tok.Eq), ExpressionParser),
      ),
      ([f, x, t1, t2, e]) =>
        (ctx) =>
          guardDecl(ctx, () => new FunctionDefinition(f, x, t1(ctx), t2(ctx), e(ctx), ctx)),
    ),
    apply(
      seq(
        kright(tok(Tok.Let), IdentifierParser),
        kright(tok(Tok.LParen), IdentifierParser),
        kmid(tok(Tok.Colon), TypeParser, tok(Tok.RParen)),
        kright(tok(Tok.Eq), ExpressionParser),
      ),
      ([f, x, t1, e]) =>
        (ctx) =>
          guardDecl(ctx, () => {
            if (ctx.requireTypes) {
              throw new Error(`Der Rückgabetyp in Funktionsdefinition ${f} fehlt.`);
            }
            return new FunctionDefinition(f, x, t1(ctx), undefined, e(ctx), ctx);
          }),
    ),
    apply(
      seq(kright(tok(Tok.Let), IdentifierParser), IdentifierParser, kright(tok(Tok.Eq), ExpressionParser)),
      ([f, x, e]) =>
        (ctx) =>
          guardDecl(ctx, () => {
            if (ctx.requireTypes) {
              throw new Error(`Der Parametertyp in Funktionsdefinition ${f} fehlt.`);
            }
            return new FunctionDefinition(f, x, undefined, undefined, e(ctx), ctx);
          }),
    ),
    apply(
      seq(
        kright(seq(tok(Tok.Let), tok(Tok.Rec)), IdentifierParser),
        kright(tok(Tok.LParen), IdentifierParser),
        kright(tok(Tok.Colon), TypeParser),
        kright(seq(tok(Tok.RParen), tok(Tok.Colon)), TypeParser),
        kright(tok(Tok.Eq), ExpressionParser),
      ),
      ([f, x, t1, t2, e]) =>
        (ctx) =>
          guardDecl(ctx, () => new RecursiveFunctionDefinition(f, x, t1(ctx), t2(ctx), e(ctx), ctx)),
    ),
    apply(
      seq(
        kright(seq(tok(Tok.Let), tok(Tok.Rec)), IdentifierParser),
        kright(tok(Tok.LParen), IdentifierParser),
        kmid(tok(Tok.Colon), TypeParser, tok(Tok.RParen)),
        kright(tok(Tok.Eq), ExpressionParser),
      ),
      ([f, x, t1, e]) =>
        (ctx) =>
          guardDecl(ctx, () => {
            if (ctx.requireTypes) {
              throw new Error(`Der Rückgabetyp in Funktionsdefinition ${f} fehlt.`);
            }
            return new RecursiveFunctionDefinition(f, x, t1(ctx), undefined, e(ctx), ctx);
          }),
    ),
    apply(
      seq(
        kright(seq(tok(Tok.Let), tok(Tok.Rec)), IdentifierParser),
        IdentifierParser,
        kright(tok(Tok.Eq), ExpressionParser),
      ),
      ([f, x, e]) =>
        (ctx) =>
          guardDecl(ctx, () => {
            if (ctx.requireTypes) {
              throw new Error(`Der Parametertyp in Funktionsdefinition ${f} fehlt.`);
            }
            return new RecursiveFunctionDefinition(f, x, undefined, undefined, e(ctx), ctx);
          }),
    ),
  ),
);
