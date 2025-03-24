import { CharLiteral } from '../../../syntax';
import type { Rule } from '../../rules';
import { ComposedExternalEffect, EventSequence, externalEffectsMustMatch, ExternalEffectVariable, noExternalEffect, ReadEvent, WriteEvent } from '../externalEffects';
import { buildBuildInFunctionApplicationRule, buildExprRule } from '../ruleBuilders';
import type { DynamicSemanticsStatement } from '../statements';
import { DynamicSemanticsExpressionStatement } from '../statements';
import { storesMustMatch } from '../stores';
import { CharValue, unitValue, valuesMustMatch, ValueVariable } from '../values';

const rules: Rule<DynamicSemanticsStatement>[] = [
  buildExprRule('CharLiteral', 'CharLiteral', CharLiteral, (expr, { value, store, effect, resultStore }, ctx) => [
    [],
    [
      ...valuesMustMatch(value, new CharValue(expr.char), ctx, expr),
      ...storesMustMatch(store, resultStore, ctx),
      ...externalEffectsMustMatch(effect, noExternalEffect, ctx, expr),
    ],
  ]),

  buildBuildInFunctionApplicationRule('GetChar', 'GetChar', 'getchar', (expr, { value, env, store, effect, resultStore }, ctx) => {
    const char = new ValueVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    return [
      [new DynamicSemanticsExpressionStatement(env, store, expr.arg, effect1, unitValue, resultStore, ctx)],
      [
        ...valuesMustMatch(value, char, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, new EventSequence([new ReadEvent(char, ctx)], ctx), ctx), ctx, expr),
      ],
    ];
  }),

  buildBuildInFunctionApplicationRule('PutChar', 'PutChar', 'putchar', (expr, { value, env, store, effect, resultStore }, ctx) => {
    const char = new ValueVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    return [
      [new DynamicSemanticsExpressionStatement(env, store, expr.arg, effect1, char, resultStore, ctx)],
      [
        ...valuesMustMatch(value, unitValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, new EventSequence([new WriteEvent(char, ctx)], ctx), ctx), ctx, expr),
      ],
    ];
  }),
];

export default rules;
