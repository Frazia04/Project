import { Pair, Unit } from '../../../syntax';
import type { Rule } from '../../rules';
import { buildBuildInFunctionApplicationRule, buildExprRule } from '../ruleBuilders';
import { StaticSemanticsExpressionStatement, type StaticSemanticsStatement } from '../statements';
import { PairType, typesMustMatch, TypeVariable, unitType } from '../types';

const rules: Rule<StaticSemanticsStatement>[] = [
  buildExprRule('Unit', 'Unit', Unit, (expr, { type }, ctx) => [[], typesMustMatch(type, unitType, ctx, expr)]),

  buildExprRule('Pair', 'Pair', Pair, (expr, { sig, type }, ctx) => {
    const fstType = new TypeVariable(ctx);
    const sndType = new TypeVariable(ctx);
    return [
      [
        new StaticSemanticsExpressionStatement(sig, expr.fst, fstType, ctx),
        new StaticSemanticsExpressionStatement(sig, expr.snd, sndType, ctx),
      ],
      typesMustMatch(type, new PairType(fstType, sndType, ctx), ctx, expr),
    ];
  }),

  buildBuildInFunctionApplicationRule('Fst', 'Fst', 'fst', (expr, { sig, type }, ctx) => [
    [new StaticSemanticsExpressionStatement(sig, expr.arg, new PairType(type, new TypeVariable(ctx), ctx), ctx)],
    [],
  ]),

  buildBuildInFunctionApplicationRule('Snd', 'Snd', 'snd', (expr, { sig, type }, ctx) => [
    [new StaticSemanticsExpressionStatement(sig, expr.arg, new PairType(new TypeVariable(ctx), type, ctx), ctx)],
    [],
  ]),
];

export default rules;
