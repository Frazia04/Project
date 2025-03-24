import type { Context } from '../../context';
import { Precedence, reactiveRendering, Rendering, type RenderingRef, staticRendering } from '../../rendering';
import type { Expression } from '..';
import { expressionParameter as e } from '../construction';
import { defineLeftRightExpr } from '../utils';

export class FalseLiteral implements Expression {
  public readonly rendered = staticRendering('false');
}

export class TrueLiteral implements Expression {
  public readonly rendered = staticRendering('true');
}

export class IfThenElse implements Expression {
  public static readonly constructorParams = [e, e, e] as const;
  public readonly rendered: RenderingRef;
  public readonly condition: Expression;
  public readonly thenExpr: Expression;
  public readonly elseExpr: Expression;

  constructor(condition: Expression, thenExpr: Expression, elseExpr: Expression, ctx: Context) {
    this.condition = condition;
    this.thenExpr = thenExpr;
    this.elseExpr = elseExpr;
    this.rendered = reactiveRendering(ctx, () => [
      Precedence.Low,
      'if ',
      condition,
      ' then ',
      thenExpr,
      ' else ',
      elseExpr,
    ]);
  }
}

export const LT = defineLeftRightExpr(new Rendering(' &lt; ', ' < '), Precedence.Comparison);
export const LEQ = defineLeftRightExpr(new Rendering(' &le; ', '  \\leq '), Precedence.Comparison);
export const EQ = defineLeftRightExpr(' = ', Precedence.Comparison);
export const NEQ = defineLeftRightExpr(new Rendering(' &lt;&gt; ', ' <> '), Precedence.Comparison);
export const GEQ = defineLeftRightExpr(new Rendering(' &ge; ', ' \\geq '), Precedence.Comparison);
export const GT = defineLeftRightExpr(new Rendering(' &gt; ', ' > '), Precedence.Comparison);
