import type { Context } from '../../context';
import { Precedence, reactiveRendering, Rendering, type RenderingRef } from '../../rendering';
import type { Declaration, Expression } from '../../syntax';
import { removeTooltip, tooltip } from '../../tooltips';
import { DeclarationStatement, ExpressionStatement } from '../statements';
import type { ExternalEffect } from './externalEffects';
import type { Store } from './stores';
import type { Environment, Value } from './values';

export type DynamicSemanticsStatement = DynamicSemanticsExpressionStatement | DynamicSemanticsDeclarationStatement;

export class DynamicSemanticsExpressionStatement extends ExpressionStatement {
  public readonly rendered: RenderingRef;
  public readonly env: Environment;
  public readonly store: Store;
  public readonly effect: ExternalEffect;
  public readonly value: Value;
  public readonly resultStore: Store;

  constructor(
    env: Environment,
    store: Store,
    expr: Expression,
    effect: ExternalEffect,
    value: Value,
    resultStore: Store,
    ctx: Context,
  ) {
    super(expr);

    // Properties that are typically passed upwards in the tree
    this.env = removeTooltip(env, ctx);
    this.store = removeTooltip(store, ctx);

    // Properties that are typically passed downwards in the tree
    this.effect = effect = removeTooltip(effect, ctx); // eslint-disable-line no-param-reassign
    this.value = value = removeTooltip(value, ctx); // eslint-disable-line no-param-reassign
    this.resultStore = resultStore = removeTooltip(resultStore, ctx); // eslint-disable-line no-param-reassign

    this.rendered = reactiveRendering(ctx, () => {
      const showStore =
        ctx.features.store && (ctx.displaySettings.showEmptyStore || !store.isEmpty || !resultStore.isEmpty);
      return [
        Precedence.Low,
        ...(ctx.features.decl && (ctx.displaySettings.showEmptyEnvironment || !env.isEmpty)
          ? [...tooltip(env), new Rendering(' &vdash; ', ' \\vdash ')]
          : []),
        ...(showStore ? [...tooltip(store), new Rendering(' &Vert; ', ' \\mathrel{\\|} ')] : []),
        new Rendering('<span class="syntax">', '\\mathtt{'),
        expr,
        new Rendering('</span> &DoubleDownArrow;', '} \\Downarrow'),
        ...(ctx.features.io
          ? [new Rendering('<span class="sub">', '_{'), effect, new Rendering('</span> ', '} ')]
          : [' ']),
        ...tooltip(value),
        ...(showStore ? [new Rendering(' &Vert; ', ' \\mathrel{\\|} '), ...tooltip(resultStore)] : []),
      ];
    });
  }
}

export class DynamicSemanticsDeclarationStatement extends DeclarationStatement {
  public readonly rendered: RenderingRef;
  public readonly env: Environment;
  public readonly store: Store;
  public readonly effect: ExternalEffect;
  public readonly resultEnv: Environment;
  public readonly resultStore: Store;

  constructor(
    env: Environment,
    store: Store,
    decl: Declaration,
    effect: ExternalEffect,
    resultEnv: Environment,
    resultStore: Store,
    ctx: Context,
  ) {
    super(decl);

    // Properties that are typically passed upwards in the tree
    this.env = removeTooltip(env, ctx);
    this.store = removeTooltip(store, ctx);

    // Properties that are typically passed downwards in the tree
    this.effect = effect = removeTooltip(effect, ctx); // eslint-disable-line no-param-reassign
    this.resultEnv = resultEnv = removeTooltip(resultEnv, ctx); // eslint-disable-line no-param-reassign
    this.resultStore = resultStore = removeTooltip(resultStore, ctx); // eslint-disable-line no-param-reassign

    this.rendered = reactiveRendering(ctx, () => {
      const showStore =
        ctx.features.store && (ctx.displaySettings.showEmptyStore || !store.isEmpty || !resultStore.isEmpty);
      return [
        Precedence.Low,
        ...(ctx.features.decl && (ctx.displaySettings.showEmptyEnvironment || !env.isEmpty)
          ? [...tooltip(env), new Rendering(' &vdash; ', ' \\vdash ')]
          : []),
        ...(showStore ? [...tooltip(store), new Rendering(' &Vert; ', ' \\mathrel{\\|} ')] : []),
        new Rendering('<span class="syntax">', '\\mathtt{'),
        decl,
        new Rendering('</span> &DoubleDownArrow;', '} \\Downarrow'),
        ...(ctx.features.io
          ? [new Rendering('<span class="sub">', '_{'), effect, new Rendering('</span> ', '} ')]
          : [' ']),
        ...tooltip(resultEnv),
        ...(showStore ? [new Rendering(' &Vert; ', ' \\mathrel{\\|} '), ...tooltip(resultStore)] : []),
      ];
    });
  }
}
