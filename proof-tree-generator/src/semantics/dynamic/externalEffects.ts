import type { Context } from '../../context';
import { htmlErrorMessagePrefix } from '../../frontend/errors';
import {
  centerDot,
  Precedence,
  reactiveRendering,
  type Renderable,
  Rendering,
  type RenderingRef,
  staticRendering,
} from '../../rendering';
import {
  objectHasPendingSimplification,
  reactiveSimplification,
  type Simplifiable,
  type SimplificationRef,
  simplify,
  SuccessfulSimplification,
  type typeDifferentiator,
} from '../../simplify';
import { MetaVariable } from '../../state/metaVariables';
import type { ConstraintProvider } from '../constraints';
import { buildMustMatchFunction, MismatchError, translateMismatchError } from '../matching';
import { type Value, valuesMustMatch } from './values';

// --------------------------------------------------------------------------------------------------------------------

export type Event = ReadEvent | WriteEvent;

export class ReadEvent implements Renderable {
  public readonly rendered: RenderingRef;
  public readonly char: Value;

  constructor(char: Value, ctx: Context) {
    this.char = char;
    this.rendered = reactiveRendering(ctx, () => [Precedence.Atom, Precedence.Low, 'in(', char, ')']);
  }
}

export class WriteEvent implements Renderable {
  public readonly rendered: RenderingRef;
  public readonly char: Value;

  constructor(char: Value, ctx: Context) {
    this.char = char;
    this.rendered = reactiveRendering(ctx, () => [Precedence.Atom, Precedence.Low, 'out(', char, ')']);
  }
}

// --------------------------------------------------------------------------------------------------------------------

export interface ExternalEffect extends Simplifiable<ExternalEffect> {
  readonly [typeDifferentiator]?: unique symbol;
}

export class ExternalEffectVariable extends MetaVariable<ExternalEffect> {
  constructor(ctx: Context) {
    super('t', ctx);
  }
}

export class EventSequence implements ExternalEffect {
  public readonly rendered: RenderingRef;
  public readonly events: readonly Event[];

  constructor();
  constructor(events: readonly Event[], ctx: Context);
  constructor(events?: readonly Event[], ctx?: Context) {
    if (events?.length) {
      this.events = events;
      this.rendered = reactiveRendering(ctx!, () => {
        const spec: [Precedence, ...(string | Rendering | Renderable)[]] = [Precedence.MultDiv];
        let needDot = false;
        events.forEach((event) => {
          if (needDot) {
            spec.push(centerDot);
          } else {
            needDot = true;
          }
          spec.push(event);
        });
        return spec;
      });
    } else {
      this.events = [];
      this.rendered = staticRendering('&epsilon;', '\\epsilon');
    }
  }
}

export const noExternalEffect = new EventSequence();

export class ComposedExternalEffect implements ExternalEffect {
  public readonly rendered: RenderingRef;
  public readonly simplified: SimplificationRef<ExternalEffect>;
  public readonly left: ExternalEffect;
  public readonly right: ExternalEffect;

  constructor(left: ExternalEffect, right: ExternalEffect, ctx: Context) {
    this.left = left;
    this.right = right;
    this.rendered = reactiveRendering(ctx, () => [Precedence.Low, left, centerDot, right]);
    this.simplified = reactiveSimplification(ctx, () => {
      const simpleLeft = simplify(left);
      const simpleRight = simplify(right);
      const tooltipValue = new ComposedExternalEffect(simpleLeft, simpleRight, ctx);
      if (isEmpty(simpleLeft)) {
        return new SuccessfulSimplification(simpleRight, tooltipValue);
      }
      if (isEmpty(simpleRight)) {
        return new SuccessfulSimplification(simpleLeft, tooltipValue);
      }
      if (simpleLeft instanceof EventSequence && simpleRight instanceof EventSequence) {
        return new SuccessfulSimplification<ExternalEffect>(
          new EventSequence([...simpleLeft.events, ...simpleRight.events], ctx),
          tooltipValue,
        );
      }
      return objectHasPendingSimplification;
    });
  }
}

function isEmpty(externalEffect: ExternalEffect): boolean {
  return externalEffect instanceof EventSequence && !externalEffect.events.length;
}

// --------------------------------------------------------------------------------------------------------------------

class EffectMismatchError extends MismatchError {
  constructor(expected: ExternalEffect, actual: ExternalEffect, object?: Renderable) {
    super(
      htmlErrorMessagePrefix +
        'Du kannst diese Regel nicht anwenden, da der externe Effekt ' +
        actual.rendered.value.html +
        (object ? ' von ' + object.rendered.value.html : '') +
        ' nicht mit dem erwarteten externen Effekt ' +
        expected.rendered.value.html +
        ' übereinstimmt.',
    );
    this.name = 'EffectMismatchError';
  }
}

export const externalEffectsMustMatch: (
  expected: ExternalEffect,
  actual: ExternalEffect,
  ctx: Context,
  object?: Renderable,
) => ConstraintProvider[] = buildMustMatchFunction(
  translateMismatchError<ExternalEffect>(EffectMismatchError, (expected, actual, ctx) => {
    if (expected instanceof EventSequence && actual instanceof EventSequence) {
      if (expected.events.length !== actual.events.length) {
        return false;
      }
      const constraints: ConstraintProvider[] = [];
      for (let i = 0; i < expected.events.length; i++) {
        const e = expected.events[i];
        const a = actual.events[i];
        if (
          (e instanceof ReadEvent && a instanceof ReadEvent) ||
          (e instanceof WriteEvent && a instanceof WriteEvent)
        ) {
          constraints.push(...valuesMustMatch(e.char, a.char, ctx));
        } else {
          return false;
        }
      }
      return constraints;
    }
    return false;
  }),
  translateMismatchError<ExternalEffect, undefined>(EffectMismatchError, (expected, actual, ctx) => {
    if (expected instanceof ComposedExternalEffect && actual instanceof EventSequence && !actual.events.length) {
      return [
        ...externalEffectsMustMatch(expected.left, noExternalEffect, ctx),
        ...externalEffectsMustMatch(expected.right, noExternalEffect, ctx),
      ];
    }
    if (expected instanceof EventSequence && !expected.events.length && actual instanceof ComposedExternalEffect) {
      return [
        ...externalEffectsMustMatch(noExternalEffect, actual.left, ctx),
        ...externalEffectsMustMatch(noExternalEffect, actual.right, ctx),
      ];
    }
    return undefined;
  }),
);
