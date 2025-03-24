import { IdentifierExpression, InExpression, LetBinding } from '../../../syntax';
import type { Rule } from '../../rules';
import { ComposedExternalEffect, externalEffectsMustMatch, ExternalEffectVariable, noExternalEffect } from '../externalEffects';
import { buildDeclRule, buildExprRule } from '../ruleBuilders';
import { DynamicSemanticsDeclarationStatement, DynamicSemanticsExpressionStatement, type DynamicSemanticsStatement } from '../statements';
import { storesMustMatch, StoreVariable } from '../stores';
import { BasicEnvironment, CommaOperatorEnvironment, EnvironmentLookupResult, environmentMustContainId, environmentsMustMatch, EnvironmentVariable, valuesMustMatch, ValueVariable } from '../values';

const rules: Rule<DynamicSemanticsStatement>[] = [
  buildDeclRule('LetBinding', 'LetBinding', LetBinding, (decl, { resultEnv, env, store, effect, resultStore }, ctx) => {
    const value = new ValueVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    return [
      [new DynamicSemanticsExpressionStatement(env, store, decl.expr, effect1, value, resultStore, ctx)],
      [
        ...environmentsMustMatch(resultEnv, new BasicEnvironment([[decl.id, value]], ctx), ctx),
        ...externalEffectsMustMatch(effect, effect1, ctx, decl),
      ],
    ];
  }),

  buildExprRule('IdentifierExpression', 'IdentifierExpression', IdentifierExpression, (expr, { value, env, store, effect, resultStore }, ctx) => [
    [],
    [
      ...valuesMustMatch(value, new EnvironmentLookupResult(env, expr.id, ctx), ctx),
      ...storesMustMatch(resultStore, store, ctx),
      ...externalEffectsMustMatch(effect, noExternalEffect, ctx, expr),
      ...environmentMustContainId(env, expr.id, ctx),
    ],
  ]),

  buildExprRule('InExpression', 'InExpression', InExpression, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const defEnv = new EnvironmentVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsDeclarationStatement(env, store, expr.decl, effect1, defEnv, store1, ctx),
        new DynamicSemanticsExpressionStatement(new CommaOperatorEnvironment(env, defEnv, ctx), store1, expr.body, effect2, value, resultStore, ctx),
      ],
      externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
    ];
  }),
];

export default rules;
