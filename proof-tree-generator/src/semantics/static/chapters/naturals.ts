import { Addition, Division, Modulo, Monus, Multiplication, NatLiteral } from '../../../syntax';
import type { Rule } from '../../rules';
import { buildExprRule } from '../ruleBuilders';
import type { StaticSemanticsStatement } from '../statements';
import { natType, typesMustMatch } from '../types';
import { checkLeftRightExpr } from '../utils';

// Nat -> Nat -> Nat
const checkNatNatNat = checkLeftRightExpr(natType);

const rules: Rule<StaticSemanticsStatement>[] = [
  buildExprRule('NatLiteral', 'NatLiteral', NatLiteral, (expr, { type }, ctx) => [
    [],
    typesMustMatch(type, natType, ctx, expr),
  ]),

  buildExprRule('Addition', 'Addition', Addition, checkNatNatNat),
  buildExprRule('Monus', 'Monus', Monus, checkNatNatNat),
  buildExprRule('Multiplication', 'Multiplication', Multiplication, checkNatNatNat),
  buildExprRule('Division', 'Division', Division, checkNatNatNat),
  buildExprRule('Modulo', 'Modulo', Modulo, checkNatNatNat),
];

export default rules;
