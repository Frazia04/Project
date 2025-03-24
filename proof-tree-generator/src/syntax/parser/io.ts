import { alt, apply, kmid, kright, list_sc, lrec_sc, opt_sc, type Parser, seq, str, tok } from 'typescript-parsec';

import type { Context } from '../../context';
import {
  ComposedExternalEffect,
  type Event,
  EventSequence,
  type ExternalEffect,
  ExternalEffectVariable,
  ReadEvent,
  WriteEvent,
} from '../../semantics/dynamic/externalEffects';
import { CharValue, type Value, ValueVariable } from '../../semantics/dynamic/values';
import { CharLiteralParser, Tok } from './lexer';
import { type ConstructWithContext, ExternalEffectsParser } from './rules';

// Special parser that recognizes (potentially unquoted) character literals.
// We cannot just use alt(...) because that would produce ambiguous results.
const CharParser: Parser<Tok, ConstructWithContext<Value>> = {
  parse(token) {
    return (
      token?.kind === Tok.Hole
        ? // Hole token => produce a meta variable
          apply(tok(Tok.Hole), () => (ctx: Context) => new ValueVariable(ctx))
        : // No Hole token => check the token's length to decide
        token?.text.length === 1
        ? // Token with exactly one character => treat it as unquoted char
          apply(tok(token.kind), () => () => new CharValue(token.text))
        : // Otherwise => expect a normal quoted char literal (might fail)
          apply(CharLiteralParser, (char) => () => new CharValue(char))
    ).parse(token);
  },
};

const EventParser: Parser<Tok, ConstructWithContext<Event>> = alt(
  apply(
    kmid(seq(str('in'), tok(Tok.LParen)), CharParser, tok(Tok.RParen)),
    (charValue) => (ctx) => new ReadEvent(charValue(ctx), ctx),
  ),
  apply(
    kmid(seq(str('out'), tok(Tok.LParen)), CharParser, tok(Tok.RParen)),
    (charValue) => (ctx) => new WriteEvent(charValue(ctx), ctx),
  ),
);

const AtomEffectParser: Parser<Tok, ConstructWithContext<ExternalEffect>> = alt(
  apply(tok(Tok.Hole), () => (ctx) => new ExternalEffectVariable(ctx)),
  apply(
    list_sc(EventParser, opt_sc(tok(Tok.Dot))),
    (events) => (ctx) =>
      new EventSequence(
        events.map((e) => e(ctx)),
        ctx,
      ),
  ),
);

ExternalEffectsParser.setPattern(
  lrec_sc(
    AtomEffectParser,
    kright(tok(Tok.Dot), AtomEffectParser),
    (e1, e2) => (ctx) => new ComposedExternalEffect(e1(ctx), e2(ctx), ctx),
  ),
);
