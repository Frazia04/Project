import { Pair, Unit } from '../../../syntax';
import type { Rule } from '../../rules';
import { ComposedExternalEffect, externalEffectsMustMatch, ExternalEffectVariable, noExternalEffect } from '../externalEffects';
import { buildBuildInFunctionApplicationRule, buildExprRule } from '../ruleBuilders';
import { DynamicSemanticsExpressionStatement, type DynamicSemanticsStatement } from '../statements';
import { storesMustMatch, StoreVariable } from '../stores';
import { PairValue, unitValue, valuesMustMatch, ValueVariable } from '../values';

const rules: Rule<DynamicSemanticsStatement>[] = [
  buildExprRule('Unit', 'Unit', Unit, (expr, { value, store, effect, resultStore }, ctx) => [
    [],
    [
      ...valuesMustMatch(value, unitValue, ctx, expr),
      ...storesMustMatch(store, resultStore, ctx),
      ...externalEffectsMustMatch(effect, noExternalEffect, ctx, expr),
    ],
  ]),

  buildExprRule('Pair', 'Pair', Pair, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    const v1 = new ValueVariable(ctx);
    const v2 = new ValueVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.fst, effect1, v1, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.snd, effect2, v2, resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, new PairValue(v1, v2, ctx), ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildBuildInFunctionApplicationRule('Fst', 'Fst', 'fst', (expr, { value, env, store, effect, resultStore }, ctx) => [
    [
      new DynamicSemanticsExpressionStatement(env, store, expr.arg, effect, new PairValue(value, new ValueVariable(ctx), ctx), resultStore, ctx),
    ],
    externalEffectsMustMatch(effect, new ExternalEffectVariable(ctx), ctx, expr),
  ]),

  buildBuildInFunctionApplicationRule('Snd', 'Snd', 'snd', (expr, { value, env, store, effect, resultStore }, ctx) => [
    [
      new DynamicSemanticsExpressionStatement(env, store, expr.arg, effect, new PairValue(new ValueVariable(ctx), value, ctx), resultStore, ctx),
    ],
    externalEffectsMustMatch(effect, new ExternalEffectVariable(ctx), ctx, expr),
  ]),
];

export default rules;
