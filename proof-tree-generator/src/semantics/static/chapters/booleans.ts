import type { Context } from '../../../context';
import { EQ, FalseLiteral, GEQ, GT, IfThenElse, LEQ, LT, NEQ, TrueLiteral } from '../../../syntax';
import type { ConstraintProvider } from '../../constraints';
import type { Rule } from '../../rules';
import { buildExprRule } from '../ruleBuilders';
import { StaticSemanticsExpressionStatement, type StaticSemanticsStatement } from '../statements';
import { boolType, typesMustMatch } from '../types';
import { checkLeftRightExpr } from '../utils';

function checkBoolLiteral(
  // Same arguments as in last parameter of `buildExprRule`
  expr: FalseLiteral | TrueLiteral,
  { type }: StaticSemanticsExpressionStatement,
  ctx: Context,
): [StaticSemanticsStatement[], ConstraintProvider[]] {
  return [
    [],
    typesMustMatch(type, boolType, ctx, expr),
  ];
}

// Nat -> Nat -> Bool
const checkNatNatBool = checkLeftRightExpr(boolType);

const rules: Rule<StaticSemanticsStatement>[] = [
  buildExprRule('False', 'False', FalseLiteral, checkBoolLiteral),
  buildExprRule('True', 'True', TrueLiteral, checkBoolLiteral),

  buildExprRule('IfThenElse', 'IfThenElse', IfThenElse, (expr, { sig, type }, ctx) => [
    [
      new StaticSemanticsExpressionStatement(sig, expr.condition, boolType, ctx),
      new StaticSemanticsExpressionStatement(sig, expr.thenExpr, type, ctx),
      new StaticSemanticsExpressionStatement(sig, expr.elseExpr, type, ctx),
    ],
    [],
  ]),

  buildExprRule('LT', 'LT', LT, checkNatNatBool),
  buildExprRule('LEQ', 'LEQ', LEQ, checkNatNatBool),
  buildExprRule('EQ', 'EQ', EQ, checkNatNatBool),
  buildExprRule('NEQ', 'NEQ', NEQ, checkNatNatBool),
  buildExprRule('GEQ', 'GEQ', GEQ, checkNatNatBool),
  buildExprRule('GT', 'GT', GT, checkNatNatBool),
];

export default rules;
