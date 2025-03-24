import type { Context } from '../../context';
import { Precedence, reactiveRendering, Rendering, type RenderingRef } from '../../rendering';
import type { Declaration, Expression } from '../../syntax';
import { removeTooltip, tooltip } from '../../tooltips';
import { DeclarationStatement, ExpressionStatement } from '../statements';
import type { Signature, Type } from './types';

export type StaticSemanticsStatement = StaticSemanticsExpressionStatement | StaticSemanticsDeclarationStatement;

export class StaticSemanticsExpressionStatement extends ExpressionStatement {
  public readonly rendered: RenderingRef;
  public readonly sig: Signature;
  public readonly type: Type;

  constructor(sig: Signature, expr: Expression, type: Type, ctx: Context) {
    super(expr);

    // Properties that are typically passed upwards in the tree
    this.sig = removeTooltip(sig, ctx);

    // Properties that are typically passed downwards in the tree
    this.type = type = removeTooltip(type, ctx); // eslint-disable-line no-param-reassign

    this.rendered = reactiveRendering(ctx, () => [
      Precedence.Low,
      ...(ctx.features.decl && (ctx.displaySettings.showEmptySignature || !sig.isEmpty)
        ? [...tooltip(sig), new Rendering(' &vdash; ', ' \\vdash ')]
        : []),
      new Rendering('<span class="syntax">', '\\mathtt{'),
      expr,
      new Rendering('</span> : ', '} : '),
      ...tooltip(type),
    ]);
  }
}

export class StaticSemanticsDeclarationStatement extends DeclarationStatement {
  public readonly rendered: RenderingRef;
  public readonly sig: Signature;
  public readonly resultSig: Signature;

  constructor(sig: Signature, decl: Declaration, resultSig: Signature, ctx: Context) {
    super(decl);

    // Properties that are typically passed upwards in the tree
    this.sig = removeTooltip(sig, ctx);

    // Properties that are typically passed downwards in the tree
    this.resultSig = resultSig = removeTooltip(resultSig, ctx); // eslint-disable-line no-param-reassign

    this.rendered = reactiveRendering(ctx, () => [
      Precedence.Low,
      ...(ctx.features.decl && (ctx.displaySettings.showEmptySignature || !sig.isEmpty)
        ? [...tooltip(sig), new Rendering(' &vdash; ', ' \\vdash ')]
        : []),
      new Rendering('<span class="syntax">', '\\mathtt{'),
      decl,
      new Rendering('</span> : ', '} : '),
      ...tooltip(resultSig),
    ]);
  }
}
