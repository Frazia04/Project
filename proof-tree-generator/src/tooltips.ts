// This module is for rendering tooltips.

import type { Context } from './context';
import { stripHtmlPrefixOrEscape } from './frontend/errors';
import { type Renderable, Rendering } from './rendering';
import {
  FailedSimplification,
  getSimplification,
  objectHasPendingSimplification,
  reactiveSimplification,
  type Simplifiable,
  type Simplification,
  SuccessfulSimplification,
} from './simplify';
import { MetaVariable } from './state/metaVariables';

/**
 * Render a tooltip for the given object:
 * - using the explicitly provided error message, if present,
 * - otherwise showing the last simplification step, if the simplification array has two elements,
 * - or no tooltip at all
 * @param x the object to render
 * @returns rendering for the object, including a tooltip if provided explicitly or the simplification array contains two values
 */
export function tooltip(x: Renderable): (string | Rendering | Renderable)[] {
  let simplification: Simplification<Renderable>;
  let suppressTooltip = false;
  let tooltip: Renderable | undefined;
  while ((simplification = getSimplification(x)) instanceof SuccessfulSimplification) {
    if (
      // Update the local suppressTooltip variable to true as soon as a simplification layer has the flag set.
      // If the local suppressTooltip variable is still false (i.e. tooltips are not suppressed)...
      !(suppressTooltip = suppressTooltip || !!(x as Partial<Simplifiable<any>>).suppressTooltip) &&
      // ... and the current simplification layer has a tooltip available...
      simplification.tooltip
    ) {
      // ... then use that tooltip (potentially overwriting a tooltip from an outer simplification layer).
      tooltip = simplification.tooltip;
    }
    x = simplification.result; // eslint-disable-line no-param-reassign
  }
  if (simplification instanceof FailedSimplification) {
    return [
      new Rendering('<div class="tooltip runtime-error">', '', ''),
      x,
      new Rendering(
        `<span class="tooltiptext">${stripHtmlPrefixOrEscape(simplification.errorMessage)}</span></div>`,
        '',
        '',
      ),
    ];
  }
  if (tooltip) {
    return [
      new Rendering('<div class="tooltip">', '', ''),
      x,
      new Rendering(`<span class="tooltiptext">${tooltip.rendered.value.htmlNoTooltip}</span></div>`, '', ''),
    ];
  }

  // No tooltip
  return [x];
}

// A symbol property that will be added to wrapped values created with `Object.create(...)`
const wrappedMarker = Symbol(__DEV__ ? 'wrappedMarker' : '');

/**
 * Remove the tooltip information from an objects simplification, such that no tooltip is shown.
 * @param x The object to remove the tooltip from
 * @param ctx The context to get the effect scope from
 * @returns a new object that won't render a tooltip
 */
export function removeTooltip<T extends Simplifiable<T>>(x: T, ctx: Context): T {
  // Meta variable: Show the tooltip only if the value is set by the same wrapped meta variable
  if (x instanceof MetaVariable) {
    // A symbol property that will be added to values that are set to the returned meta variable's
    // instance. The symbol must be local to this function to distinguish different instances.
    const layerMarker = Symbol(__DEV__ ? 'layerMarker' : '');

    return Object.create(x satisfies T & MetaVariable<T>, {
      [wrappedMarker]: {},

      // Wrap values set to this meta variable
      ['set' satisfies keyof MetaVariable<T>]: {
        value: ((value, ctx) =>
          x.set(
            Object.create(value satisfies T, {
              [wrappedMarker]: {},
              // Add a marker that we added a layer
              [layerMarker]: {},
              // Mark value as suppressing tooltips
              ['suppressTooltip' satisfies keyof T]: {
                value: true satisfies T['suppressTooltip'],
              },
            }) as T,
            ctx,
          )) satisfies MetaVariable<T>['set'],
      },

      // Unwrap the value when accessing it through the `value` property of the meta variable
      ['value' satisfies keyof MetaVariable<T>]: {
        get(): MetaVariable<T>['value'] {
          const value = x.value;
          return value && Object.prototype.hasOwnProperty.call(value, layerMarker)
            ? (Object.getPrototypeOf(value satisfies T) as T)
            : value;
        },
      },

      // When accessing this meta variable, then we unwrap the layers added above
      ['simplified' satisfies keyof MetaVariable<T>]: {
        value: reactiveSimplification(ctx, () => {
          const value = x.value;
          return value
            ? // Meta variable has been set -> we can simplify to that value
              new SuccessfulSimplification(
                Object.prototype.hasOwnProperty.call(value, layerMarker)
                  ? // Value was wrapped by our `.set(...)` method -> remove the layer
                    (Object.getPrototypeOf(value satisfies T) as T)
                  : // Otherwise it was not set by us -> suppress the tooltip
                    addSuppressTooltipMarker(value),
              )
            : // An unset meta variable has pending simplification
              objectHasPendingSimplification;
        }) satisfies MetaVariable<T>['simplified'],
      },
    }) as T;
  }

  return addSuppressTooltipMarker(x);
}

function addSuppressTooltipMarker<T extends Simplifiable<T>>(x: T): T {
  return x.suppressTooltip || !x.simplified
    ? // Object already suppresses the tooltip or has no simplifications -> nothing to do
      x
    : // Otherwise wrap the object and mark it as suppressing tooltips
      (Object.create(x satisfies T, {
        [wrappedMarker]: {},
        ['suppressTooltip' satisfies keyof T]: {
          value: true satisfies T['suppressTooltip'],
        },
      }) as T);
}

export function unwrap<T>(value: T): T {
  while (Object.prototype.hasOwnProperty.call(value, wrappedMarker)) {
    value = Object.getPrototypeOf(value); // eslint-disable-line no-param-reassign
  }
  return value;
}
