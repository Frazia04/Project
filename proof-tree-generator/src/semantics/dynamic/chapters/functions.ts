import { ExpressionVariable, FunctionApplication, FunctionDefinition, LambdaExpression, RecursiveFunctionDefinition } from '../../../syntax';
import { IdentifierVariable } from '../../mappings';
import type { Rule } from '../../rules';
import { ComposedExternalEffect, externalEffectsMustMatch, ExternalEffectVariable, noExternalEffect } from '../externalEffects';
import { buildDeclRule, buildExprRule } from '../ruleBuilders';
import { DynamicSemanticsExpressionStatement, type DynamicSemanticsStatement } from '../statements';
import { storesMustMatch, StoreVariable } from '../stores';
import { BasicEnvironment, Closure, CommaOperatorEnvironment, environmentsMustMatch, EnvironmentVariable, RecursiveClosure, valuesMustMatch, ValueVariable } from '../values';

const rules: Rule<DynamicSemanticsStatement>[] = [
  buildDeclRule('FunctionDefinition', 'FunctionDefinition', FunctionDefinition, (decl, { resultEnv, env, store, effect, resultStore }, ctx) => [
    [],
    [
      ...environmentsMustMatch(resultEnv, new BasicEnvironment([[decl.id, new Closure(env, decl.param, decl.body, ctx)]], ctx), ctx),
      ...storesMustMatch(resultStore, store, ctx),
      ...externalEffectsMustMatch(effect, noExternalEffect, ctx, decl),
    ],
  ]),

  buildExprRule('FunctionApplication', 'FunctionApplication', FunctionApplication, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const closureEnv = new EnvironmentVariable(ctx);
    const closureParamId = new IdentifierVariable(ctx);
    const closureBody = new ExpressionVariable(ctx);
    const arg = new ValueVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    const effect3 = new ExternalEffectVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const store2 = new StoreVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.fun, effect1, new Closure(closureEnv, closureParamId, closureBody, ctx), store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.arg, effect2, arg, store2, ctx),
        new DynamicSemanticsExpressionStatement(new CommaOperatorEnvironment(closureEnv, new BasicEnvironment([[closureParamId, arg]], ctx), ctx), store2, closureBody, effect3, value, resultStore, ctx),
      ],
      externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, new ComposedExternalEffect(effect2, effect3, ctx), ctx), ctx),
    ];
  }),

  buildExprRule('LambdaExpression', 'LambdaExpression', LambdaExpression, (expr, { value, env, store, effect, resultStore }, ctx) => [
    [],
    [
      ...valuesMustMatch(value, new Closure(env, expr.param, expr.body, ctx), ctx, expr),
      ...storesMustMatch(resultStore, store, ctx),
      ...externalEffectsMustMatch(effect, noExternalEffect, ctx, expr),
    ],
  ]),

  buildDeclRule('RecursiveFunctionDefinition', 'RecursiveFunctionDefinition', RecursiveFunctionDefinition, (decl, { resultEnv, env, store, effect, resultStore }, ctx) => [
    [],
    [
      ...environmentsMustMatch(resultEnv, new BasicEnvironment([[decl.id, new RecursiveClosure(env, decl.id, decl.param, decl.body, ctx)]], ctx), ctx),
      ...storesMustMatch(resultStore, store, ctx),
      ...externalEffectsMustMatch(effect, noExternalEffect, ctx, decl),
    ],
  ]),

  buildExprRule('RecursiveFunctionApplication', 'RecursiveFunctionApplication', FunctionApplication, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const closureEnv = new EnvironmentVariable(ctx);
    const closureFunctionId = new IdentifierVariable(ctx);
    const closureParamId = new IdentifierVariable(ctx);
    const closureBody = new ExpressionVariable(ctx);
    const closure = new RecursiveClosure(closureEnv, closureFunctionId, closureParamId, closureBody, ctx);
    const arg = new ValueVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    const effect3 = new ExternalEffectVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const store2 = new StoreVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.fun, effect1, closure, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.arg, effect2, arg, store2, ctx),
        new DynamicSemanticsExpressionStatement(
          new CommaOperatorEnvironment(
            closureEnv,
            new BasicEnvironment(
              [
                [closureFunctionId, closure],
                [closureParamId, arg],
              ],
              ctx,
            ),
            ctx,
          ),
          store2,
          closureBody,
          effect3,
          value,
          resultStore,
          ctx,
        ),
      ],
      externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, new ComposedExternalEffect(effect2, effect3, ctx), ctx), ctx),
    ];
  }),
];

export default rules;
