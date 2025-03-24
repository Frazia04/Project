import { simplify } from '../../../simplify';
import type { MetaVariable } from '../../../state/metaVariables';
import { Assignment, Dereference } from '../../../syntax';
import type { Rule } from '../../rules';
import { ComposedExternalEffect, externalEffectsMustMatch, ExternalEffectVariable } from '../externalEffects';
import { buildBuildInFunctionApplicationRule, buildExprRule } from '../ruleBuilders';
import { DynamicSemanticsExpressionStatement, type DynamicSemanticsStatement } from '../statements';
import { BasicStore, CommaOperatorStore, nextAddress, StoreLookupResult, storeMustContainAddress, storeMustNotContainAddress, storesMustMatch, StoreVariable } from '../stores';
import { Address, unitValue, valuesMustMatch, ValueVariable } from '../values';

const rules: Rule<DynamicSemanticsStatement>[] = [
  buildBuildInFunctionApplicationRule('Allocation', 'Allocation', 'ref', (expr, { env, store, effect, value, resultStore }, ctx) => {
    // Keep provided address, if any, otherwise chose fresh address
    const address = simplify(value);
    if (address instanceof ValueVariable) {
      address.set(nextAddress(ctx), ctx);
    }

    const storedValue = new ValueVariable(ctx);
    const newStore = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    return [
      [new DynamicSemanticsExpressionStatement(env, store, expr.arg, effect1, storedValue, newStore, ctx)],
      [
        ...storesMustMatch(resultStore, new CommaOperatorStore(newStore, new BasicStore([[address, storedValue]], ctx), ctx), ctx),
        ...externalEffectsMustMatch(effect, effect1, ctx, expr),
        ...storeMustNotContainAddress(newStore, address, ctx),
      ],
    ];
  }),

  buildExprRule('Dereference', 'Dereference', Dereference, (expr, { env, store, effect, value, resultStore }, ctx) => {
    const addressVar = new ValueVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    return [
      [new DynamicSemanticsExpressionStatement(env, store, expr.addr, effect1, addressVar, resultStore, ctx)],
      [
        ...valuesMustMatch(value, new StoreLookupResult(resultStore, addressVar, ctx), ctx, expr),
        ...externalEffectsMustMatch(effect, effect1, ctx, expr),
        ...storeMustContainAddress(resultStore, addressVar, ctx),
      ],
    ];
  }),

  buildExprRule('Assignment', 'Assignment', Assignment, (expr, { env, store, effect, value, resultStore }, ctx) => {
    const addressVar = new ValueVariable(ctx) as ValueVariable & MetaVariable<Address>;
    const valueVar = new ValueVariable(ctx);
    const store1 = new StoreVariable(ctx);
    const store2 = new StoreVariable(ctx);
    const effect1 = new ExternalEffectVariable(ctx);
    const effect2 = new ExternalEffectVariable(ctx);
    return [
      [
        new DynamicSemanticsExpressionStatement(env, store, expr.addr, effect1, addressVar, store1, ctx),
        new DynamicSemanticsExpressionStatement(env, store1, expr.expr, effect2, valueVar, store2, ctx),
      ],
      [
        ...valuesMustMatch(value, unitValue, ctx, expr),
        ...storesMustMatch(resultStore, new CommaOperatorStore(store2, new BasicStore([[addressVar, valueVar]], ctx), ctx), ctx),
        ...externalEffectsMustMatch(effect, new ComposedExternalEffect(effect1, effect2, ctx), ctx, expr),
      ],
    ];
  }),
];

export default rules;
