import type { Context } from '../../../context';
import { htmlErrorMessagePrefix } from '../../../frontend/errors';
import { centerDot, Precedence, reactiveRendering, Rendering } from '../../../rendering';
import { simplify } from '../../../simplify';
import { Addition, Division, Modulo, Monus, Multiplication, NatLiteral } from '../../../syntax';
import { Constraint, type ConstraintProvider } from '../../constraints';
import type { Rule } from '../../rules';
import { ComposedExternalEffect, externalEffectsMustMatch, ExternalEffectVariable, noExternalEffect } from '../externalEffects';
import { buildExprRule } from '../ruleBuilders';
import type { DynamicSemanticsStatement } from '../statements';
import { DynamicSemanticsExpressionStatement } from '../statements';
import { storesMustMatch, StoreVariable } from '../stores';
import { AdditionValue, MultiplicationValue, NatValue, type Value, valuesMustMatch, ValueVariable, zeroValue } from '../values';

function naturalDivisionConstraint(dividend: Value, divisor: Value, quotient: Value, remainder: Value, ctx: Context): ConstraintProvider {
  return () =>
    new Constraint(
      (ctx: Context) => {
        // TODO: Check types (maybe use wrapper value for same solution here and in defineNatNatNatValue)
        const simpleDivisor = simplify(divisor);
        if (simpleDivisor instanceof NatValue) {
          if (simpleDivisor.value === 0n) {
            throw new Error(htmlErrorMessagePrefix + `Division durch Null! Verletzte Nebenbedingung ${remainder.rendered.value.html} < 0`);
          }
          const simpleDividend = simplify(dividend);
          if (simpleDividend instanceof NatValue) {
            return !(valuesMustMatch(new NatValue(simpleDividend.value / simpleDivisor.value), quotient, ctx).length || valuesMustMatch(new NatValue(simpleDividend.value % simpleDivisor.value), remainder, ctx).length);
          }
        }
        return false;
      },
      reactiveRendering(ctx, () => [
        Precedence.Low,
        Precedence.Comparison + 1,
        dividend,
        ' = ',
        Precedence.MultDiv,
        quotient,
        centerDot,
        Precedence.MultDiv + 1,
        divisor,
        ' + ',
        Precedence.PlusMinus + 1,
        remainder,
        new Rendering(' und ', '\\quad\\mathrm{und}\\quad '),
        Precedence.Comparison + 1,
        remainder,
        new Rendering(' &lt; ', ' < '),
        divisor,
      ]),
    );
}

const rules: Rule<DynamicSemanticsStatement>[] = [
  buildExprRule('NatLiteral', 'NatLiteral', NatLiteral, (expr, { value, store, effect, resultStore }, ctx) => [
    [],
    [
      ...valuesMustMatch(value, new NatValue(expr.value), ctx, expr),
      ...storesMustMatch(store, resultStore, ctx),
      ...externalEffectsMustMatch(effect, noExternalEffect, ctx, expr),
    ],
  ]),

  buildExprRule('Addition', 'Addition', Addition, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n1 = new ValueVariable(ctx);
    const n2 = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, n1, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, n2, resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, new AdditionValue(n1, n2, ctx), ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('Multiplication', 'Multiplication', Multiplication, (expr, { value, env, store, effect, resultStore }, ctx) => {
    const n1 = new ValueVariable(ctx);
    const n2 = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, n1, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, n2, resultStore, ctx),
      ],
      [
        ...valuesMustMatch(value, new MultiplicationValue(n1, n2, ctx), ctx, expr),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('Monus1', 'Monus1', Monus, (expr, { value: k, env, store, effect, resultStore }, ctx) => {
    const n = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, new AdditionValue(n, k, ctx), store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, n, resultStore, ctx),
      ],
      [...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr)],
    ];
  }),

  buildExprRule('Monus2', 'Monus2', Monus, (expr, { value, env, store, effect, resultStore }, ctx) => {
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
        ...valuesMustMatch(value, zeroValue, ctx),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('Division', 'Division', Division, (expr, { value: q, env, store, effect, resultStore }, ctx) => {
    const n1 = new ValueVariable(ctx);
    const n2 = new ValueVariable(ctx);
    const r = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, n1, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, n2, resultStore, ctx),
      ],
      [
        naturalDivisionConstraint(n1, n2, q, r, ctx),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),

  buildExprRule('Modulo', 'Modulo', Modulo, (expr, { value: r, env, store, effect, resultStore }, ctx) => {
    const n1 = new ValueVariable(ctx);
    const n2 = new ValueVariable(ctx);
    const q = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.left, effect1, n1, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.right, effect2, n2, resultStore, ctx),
      ],
      [
        naturalDivisionConstraint(n1, n2, q, r, ctx),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),
];

export default rules;
