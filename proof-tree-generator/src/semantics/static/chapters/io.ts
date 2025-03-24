import { CharLiteral } from '../../../syntax';
import type { Rule } from '../../rules';
import { buildBuildInFunctionApplicationRule, buildExprRule } from '../ruleBuilders';
import { StaticSemanticsExpressionStatement, type StaticSemanticsStatement } from '../statements';
import { charType, typesMustMatch, unitType } from '../types';

const rules: Rule<StaticSemanticsStatement>[] = [
  buildExprRule('CharLiteral', 'CharLiteral', CharLiteral, (expr, { type }, ctx) => [
    [],
    typesMustMatch(type, charType, ctx, expr),
  ]),

  buildBuildInFunctionApplicationRule('GetChar', 'GetChar', 'getchar', (expr, { sig, type }, ctx) => [
    [new StaticSemanticsExpressionStatement(sig, expr.arg, unitType, ctx)],
    typesMustMatch(type, charType, ctx, expr),
  ]),

  buildBuildInFunctionApplicationRule('PutChar', 'PutChar', 'putchar', (expr, { sig, type }, ctx) => [
    [new StaticSemanticsExpressionStatement(sig, expr.arg, charType, ctx)],
    typesMustMatch(type, unitType, ctx, expr),
  ]),
];

export default rules;
