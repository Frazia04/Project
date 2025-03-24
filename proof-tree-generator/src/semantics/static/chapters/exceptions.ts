import type { Rule } from '../../rules';
import { buildBuildInFunctionApplicationRule } from '../ruleBuilders';
import { StaticSemanticsExpressionStatement, type StaticSemanticsStatement } from '../statements';
import { exceptionType } from '../types';

const rules: Rule<StaticSemanticsStatement>[] = [
  buildBuildInFunctionApplicationRule('Raise', 'Raise', 'raise', (expr, { sig }, ctx) => [
    [new StaticSemanticsExpressionStatement(sig, expr.arg, exceptionType, ctx)],
    [],
  ]),
];

export default rules;
