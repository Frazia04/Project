import { EQ, FalseLiteral, GEQ, GT, IfThenElse, LEQ, LT, NEQ, TrueLiteral } from '../../../syntax';
import type { Rule } from '../../rules';
import { ComposedExternalEffect, externalEffectsMustMatch, ExternalEffectVariable, noExternalEffect } from '../externalEffects';
import { buildExprRule } from '../ruleBuilders';
import { DynamicSemanticsExpressionStatement, type DynamicSemanticsStatement } from '../statements';
import { storesMustMatch, StoreVariable } from '../stores';
import { AdditionValue, falseValue, oneValue, trueValue, valuesMustMatch, ValueVariable } from '../values';

const rules: Rule<DynamicSemanticsStatement>[] = [
  buildExprRule('False', 'False', FalseLiteral, (expr, { value, store, effect, resultStore }, ctx) => [
    [],
    [
      ...valuesMustMatch(value, falseValue, ctx, expr),
      ...storesMustMatch(store, resultStore, ctx),
      ...externalEffectsMustMatch(effect, noExternalEffect, ctx, expr),
    ],
  ]),

  buildExprRule('True', 'True', TrueLiteral, (expr, { value, store, effect, resultStore }, ctx) => [
    [],
    [
      ...valuesMustMatch(value, trueValue, ctx, expr),
      ...storesMustMatch(store, resultStore, ctx),
      ...externalEffectsMustMatch(effect, noExternalEffect, ctx, expr),
    ],
  ]),

  buildExprRule('IfThenElseTrue', 'IfThenElseTrue', IfThenElse, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.condition, effect1, trueValue, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.thenExpr, effect2, value, resultStore, ctx),
      ],
      externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
    ];
  }),

  buildExprRule('IfThenElseFalse', 'IfThenElseFalse', IfThenElse, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.condition, effect1, falseValue, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.elseExpr, effect2, value, resultStore, ctx),
      ],
      externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
    ];
  }),

  buildExprRule('LTFalse', 'LTFalse', LT, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const k = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, new AdditionValue(n, k, ctx), store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, n, resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, falseValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('LTTrue', 'LTTrue', LT, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const k = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, n, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, new AdditionValue(new AdditionValue(n, k, ctx), oneValue, ctx), resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, trueValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('LEQFalse', 'LEQFalse', LEQ, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const k = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, new AdditionValue(new AdditionValue(n, k, ctx), oneValue, ctx), store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, n, resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, falseValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('LEQTrue', 'LEQTrue', LEQ, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const k = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, n, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, new AdditionValue(n, k, ctx), resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, trueValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('EQFalse1', 'EQFalse1', EQ, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const k = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, new AdditionValue(new AdditionValue(n, k, ctx), oneValue, ctx), store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, n, resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, falseValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('EQFalse2', 'EQFalse2', EQ, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const k = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, n, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, new AdditionValue(new AdditionValue(n, k, ctx), oneValue, ctx), resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, falseValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('EQTrue', 'EQTrue', EQ, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, n, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, n, resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, trueValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('NEQFalse', 'NEQFalse', NEQ, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, n, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, n, resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, falseValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('NEQTrue1', 'NEQTrue1', NEQ, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const k = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, new AdditionValue(new AdditionValue(n, k, ctx), oneValue, ctx), store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, n, resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, trueValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('NEQTrue2', 'NEQTrue2', NEQ, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const k = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, n, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, new AdditionValue(new AdditionValue(n, k, ctx), oneValue, ctx), resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, trueValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('GEQFalse', 'GEQFalse', GEQ, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const k = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, n, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, new AdditionValue(new AdditionValue(n, k, ctx), oneValue, ctx), resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, falseValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('GEQTrue', 'GEQTrue', GEQ, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const k = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, new AdditionValue(n, k, ctx), store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, n, resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, trueValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('GTFalse', 'GTFalse', GT, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const k = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, n, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, new AdditionValue(n, k, ctx), resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, falseValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('GTTrue', 'GTTrue', GT, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const k = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, new AdditionValue(new AdditionValue(n, k, ctx), oneValue, ctx), store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, n, resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, trueValue, ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),
];

export default rules;
