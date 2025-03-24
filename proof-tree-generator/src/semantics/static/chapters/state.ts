import { Assignment, Dereference } from '../../../syntax';
import type { Rule } from '../../rules';
import { buildBuildInFunctionApplicationRule, buildExprRule } from '../ruleBuilders';
import type { StaticSemanticsStatement } from '../statements';
import { StaticSemanticsExpressionStatement } from '../statements';
import { RefType, typesMustMatch, TypeVariable, unitType } from '../types';

const rules: Rule<StaticSemanticsStatement>[] = [
  buildBuildInFunctionApplicationRule('Allocation', 'Allocation', 'ref', (expr, { sig, type }, ctx) => {
    const elemType = new TypeVariable(ctx);
    return [
      [new StaticSemanticsExpressionStatement(sig, expr.arg, elemType, ctx)],
      typesMustMatch(type, new RefType(elemType, ctx), ctx, expr),
    ];
  }),

  buildExprRule('Dereference', 'Dereference', Dereference, (expr, { sig, type }, ctx) => [
    [new StaticSemanticsExpressionStatement(sig, expr.addr, new RefType(type, ctx), ctx)],
    [],
  ]),

  buildExprRule('Assignment', 'Assignment', Assignment, (expr, { sig, type }, ctx) => {
    const elemType = new TypeVariable(ctx);
    return [
      [
        new StaticSemanticsExpressionStatement(sig, expr.addr, new RefType(elemType, ctx), ctx),
        new StaticSemanticsExpressionStatement(sig, expr.expr, elemType, ctx),
      ],
      typesMustMatch(type, unitType, ctx, expr),
    ];
  }),
];

export default rules;
