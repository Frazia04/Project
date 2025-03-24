import type { Context } from '../../context';
import type { LeftRightExpression } from '../../syntax';
import type { ConstraintProvider } from '../constraints';
import { StaticSemanticsExpressionStatement, type StaticSemanticsStatement } from './statements';
import { natType, type Type, typesMustMatch } from './types';

// Nat -> Nat -> *
export function checkLeftRightExpr(
  returnType: Type,
): (
  expr: LeftRightExpression,
  stmt: StaticSemanticsExpressionStatement,
  ctx: Context,
) => [StaticSemanticsStatement[], ConstraintProvider[]] {
  return (expr, { type, sig }, ctx) => [
    [
      new StaticSemanticsExpressionStatement(sig, expr.left, natType, ctx),
      new StaticSemanticsExpressionStatement(sig, expr.right, natType, ctx),
    ],
    typesMustMatch(type, returnType, ctx, expr),
  ];
}
