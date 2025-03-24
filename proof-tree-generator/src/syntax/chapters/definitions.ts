import type { Context } from '../../context';
import { Precedence, reactiveRendering, type RenderingRef, staticRendering } from '../../rendering';
import type { Declaration, Expression } from '..';
import { declarationParameter as d, expressionParameter as e } from '../construction';

export class LetBinding implements Declaration {
  public static readonly constructorParams = ['x', e] as const;
  public readonly rendered: RenderingRef;
  public readonly id: string;
  public readonly expr: Expression;

  constructor(id: string, expr: Expression, ctx: Context) {
    this.id = id;
    this.expr = expr;
    this.rendered = reactiveRendering(ctx, () => [Precedence.Low, 'let ', id, ' = ', expr]);
  }
}

export class IdentifierExpression implements Expression {
  public static readonly constructorParams = ['x'] as const;
  public readonly rendered: RenderingRef;
  public readonly id: string;

  constructor(id: string) {
    this.id = id;
    this.rendered = staticRendering(id);
  }
}

export class InExpression implements Expression {
  public static readonly constructorParams = [d, e] as const;
  public readonly rendered: RenderingRef;
  public readonly decl: Declaration;
  public readonly body: Expression;

  constructor(decl: Declaration, body: Expression, ctx: Context) {
    this.decl = decl;
    this.body = body;
    this.rendered = reactiveRendering(ctx, () => [Precedence.Low, decl, ' in ', body]);
  }
}
