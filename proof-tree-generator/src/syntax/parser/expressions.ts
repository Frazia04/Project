import { alt, apply, kmid, kright, lrec_sc, type Parser, seq, tok } from 'typescript-parsec';

import {
  Addition,
  Assignment,
  Dereference,
  Division,
  EQ,
  type Expression,
  ExpressionVariable,
  FalseLiteral,
  FunctionApplication,
  GEQ,
  GT,
  IdentifierExpression,
  IfThenElse,
  InExpression,
  LambdaExpression,
  LEQ,
  LT,
  Modulo,
  Monus,
  Multiplication,
  NatLiteral,
  NEQ,
  TrueLiteral,
} from '..';
import { CharLiteral, Pair, Unit } from '..';
import { CharLiteralParser, NumberLiteralParser, Tok } from './lexer';
import {
  type ConstructWithContext,
  DeclarationParser,
  ExpressionParser,
  guardDecl,
  guardExceptions,
  guardIO,
  guardPairs,
  guardStore,
  IdentifierParser,
  TypeParser,
} from './rules';

const AtomExprParser: Parser<Tok, ConstructWithContext<Expression>> = alt(
  apply(tok(Tok.Hole), () => (ctx) => new ExpressionVariable(ctx)),
  apply(IdentifierParser, (id) => () => new IdentifierExpression(id)),
  apply(tok(Tok.True), () => () => new TrueLiteral()),
  apply(tok(Tok.False), () => () => new FalseLiteral()),
  apply(NumberLiteralParser, (n) => () => new NatLiteral(n)),
  apply(CharLiteralParser, (char) => (ctx) => guardIO(ctx, () => new CharLiteral(char))),
  kmid(tok(Tok.LParen), ExpressionParser, tok(Tok.RParen)),
  apply(seq(tok(Tok.LParen), tok(Tok.RParen)), () => (ctx) => guardPairs(ctx, () => new Unit())),
  apply(
    seq(kright(tok(Tok.LParen), ExpressionParser), kmid(tok(Tok.Comma), ExpressionParser, tok(Tok.RParen))),
    ([e1, e2]) =>
      (ctx) =>
        guardPairs(ctx, () => new Pair(e1(ctx), e2(ctx), ctx)),
  ),
);

const FunAppExprParser: Parser<Tok, ConstructWithContext<Expression>> = alt(
  lrec_sc(AtomExprParser, AtomExprParser, (e1, e2) => (ctx) => {
    const fun = e1(ctx);
    if (!ctx.features.decl) {
      // If the functions and declarations feature is disabled, then we can still have
      // FunctionApplication for built-in functions, if the corresponding feature is enabled.
      if (fun instanceof IdentifierExpression) {
        switch (fun.id) {
          case 'fst':
          case 'snd':
            guardPairs(ctx);
            break;
          case 'getchar':
          case 'putchar':
            guardIO(ctx);
            break;
          case 'ref':
            guardStore(ctx);
            break;
          case 'raise':
            guardExceptions(ctx);
            break;
          default:
            guardDecl(ctx);
        }
      } else {
        guardDecl(ctx);
      }
    }
    return new FunctionApplication(fun, e2(ctx), ctx);
  }),
  apply(kright(tok(Tok.Bang), AtomExprParser), (e) => (ctx) => guardStore(ctx, () => new Dereference(e(ctx), ctx))),
);

const MultDivExprParser: Parser<Tok, ConstructWithContext<Expression>> = lrec_sc(
  FunAppExprParser,
  seq(alt(tok(Tok.Star), tok(Tok.Div), tok(Tok.Mod), tok(Tok.Assignment)), FunAppExprParser),
  (left, [op, right]) =>
    (ctx) => {
      switch (op.kind) {
        case Tok.Star:
          return new Multiplication(left(ctx), right(ctx), ctx);
        case Tok.Div:
          return new Division(left(ctx), right(ctx), ctx);
        case Tok.Mod:
          return new Modulo(left(ctx), right(ctx), ctx);
        case Tok.Assignment:
          return new Assignment(left(ctx), right(ctx), ctx);
      }
    },
);

const PlusMinusExprParser: Parser<Tok, ConstructWithContext<Expression>> = lrec_sc(
  MultDivExprParser,
  seq(alt(tok(Tok.Plus), tok(Tok.Minus)), MultDivExprParser),
  (left, [op, right]) =>
    (ctx) => {
      switch (op.kind) {
        case Tok.Plus:
          return new Addition(left(ctx), right(ctx), ctx);
        case Tok.Minus:
          return new Monus(left(ctx), right(ctx), ctx);
      }
    },
);

const ComparisonExprParser: Parser<Tok, ConstructWithContext<Expression>> = lrec_sc(
  PlusMinusExprParser,
  seq(alt(tok(Tok.Eq), tok(Tok.NEq), tok(Tok.Lt), tok(Tok.Gt), tok(Tok.Leq), tok(Tok.Geq)), PlusMinusExprParser),
  (left, [op, right]) =>
    (ctx) => {
      switch (op.kind) {
        case Tok.Eq:
          return new EQ(left(ctx), right(ctx), ctx);
        case Tok.NEq:
          return new NEQ(left(ctx), right(ctx), ctx);
        case Tok.Lt:
          return new LT(left(ctx), right(ctx), ctx);
        case Tok.Gt:
          return new GT(left(ctx), right(ctx), ctx);
        case Tok.Leq:
          return new LEQ(left(ctx), right(ctx), ctx);
        case Tok.Geq:
          return new GEQ(left(ctx), right(ctx), ctx);
      }
    },
);

ExpressionParser.setPattern(
  alt(
    ComparisonExprParser,
    apply(
      seq(
        kright(tok(Tok.If), ExpressionParser),
        kright(tok(Tok.Then), ExpressionParser),
        kright(tok(Tok.Else), ExpressionParser),
      ),
      ([e1, e2, e3]) =>
        (ctx) =>
          new IfThenElse(e1(ctx), e2(ctx), e3(ctx), ctx),
    ),
    apply(
      seq(DeclarationParser, kright(tok(Tok.In), ExpressionParser)),
      ([decl, expr]) =>
        (ctx) =>
          guardDecl(ctx, () => new InExpression(decl(ctx), expr(ctx), ctx)),
    ),
    apply(
      seq(
        kright(seq(tok(Tok.Fun), tok(Tok.LParen)), IdentifierParser),
        kright(tok(Tok.Colon), TypeParser),
        kright(seq(tok(Tok.RParen), tok(Tok.Arrow)), ExpressionParser),
      ),
      ([x, t, e]) =>
        (ctx) =>
          guardDecl(ctx, () => new LambdaExpression(x, t(ctx), e(ctx), ctx)),
    ),
    apply(
      seq(tok(Tok.Fun), IdentifierParser, tok(Tok.Arrow), ExpressionParser),
      () => (ctx) =>
        guardDecl(ctx, () => {
          throw new Error('Der Parametertyp im Funktionsausdruck fehlt.');
        }),
    ),
  ),
);
