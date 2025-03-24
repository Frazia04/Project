import type { Context } from '../../context';
import { Precedence, reactiveRendering, type RenderingRef, staticRendering } from '../../rendering';
import { expressionParameter as e } from '../construction';
import type { Expression } from '../index';

export class Unit implements Expression {
  public readonly rendered = staticRendering('()');
}

export class Pair implements Expression {
  public static readonly constructorParams = [e, e] as const;
  public readonly rendered: RenderingRef;
  public readonly fst: Expression;
  public readonly snd: Expression;

  constructor(fst: Expression, snd: Expression, ctx: Context) {
    this.fst = fst;
    this.snd = snd;
    this.rendered = reactiveRendering(ctx, () => [
      Precedence.Atom,
      '(',
      Precedence.Low,
      fst,
      ', ',
      Precedence.Low,
      snd,
      ')',
    ]);
  }
}

// fst, snd: represented by FunctionApplication with IdentifierExpression
