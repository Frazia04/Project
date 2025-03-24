import type { Context } from '../../context';
import { Precedence, reactiveRendering, type RenderingRef } from '../../rendering';
import type { Expression } from '..';
import { expressionParameter as e } from '../construction';

// ref: represented by FunctionApplication with IdentifierExpression

export class Dereference implements Expression {
  public static readonly constructorParams = [e] as const;
  public readonly rendered: RenderingRef;
  public readonly addr: Expression;

  constructor(addr: Expression, ctx: Context) {
    this.addr = addr;
    this.rendered = reactiveRendering(ctx, () => [Precedence.Application, '!', Precedence.Atom, addr]);
  }
}

export class Assignment implements Expression {
  public static readonly constructorParams = [e, e] as const;
  public readonly rendered: RenderingRef;
  public readonly addr: Expression;
  public readonly expr: Expression;

  constructor(addr: Expression, expr: Expression, ctx: Context) {
    this.addr = addr;
    this.expr = expr;
    this.rendered = reactiveRendering(ctx, () => [Precedence.Low, addr, ' := ', expr]);
  }
}
