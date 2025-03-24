import type { Context } from '../context';
import { Precedence, reactiveRendering, Rendering, type RenderingRef } from '../rendering';
import type { Expression } from '.';
import { expressionParameter as e } from './construction';

export interface LeftRightExpression extends Expression {
  readonly left: Expression;
  readonly right: Expression;
}

export interface LeftRightExpressionConstructor {
  new (left: Expression, right: Expression, ctx: Context): LeftRightExpression;
  readonly constructorParams: readonly [typeof e, typeof e];
}

export function defineLeftRightExpr(op: string | Rendering, precedence: Precedence): LeftRightExpressionConstructor {
  return class implements LeftRightExpression {
    public static readonly constructorParams = [e, e] as const;
    public readonly rendered: RenderingRef;
    public readonly left: Expression;
    public readonly right: Expression;

    constructor(left: Expression, right: Expression, ctx: Context) {
      this.left = left;
      this.right = right;
      this.rendered = reactiveRendering(ctx, () => [precedence, left, op, precedence + 1, right]);
    }
  };
}
