// This module provides all aspects for simplifying objects, e.g. replacing objects by other (simpler, but equivalent
// objects), like resolving meta variables, evaluating arithmetic expressions or the comma operator.

import { computed, type Ref } from 'vue';

import type { Context } from './context';
import type { Renderable } from './rendering';

/**
 * Interface for objects that can be potentially be simplified.
 * Use this interface if one possible simplification is a string.
 * @typeParam T Type this object might be simplified to.
 */
export type StringOrSimplifiable<T extends StringOrSimplifiable<T>> =
  | string
  | (Renderable & {
      /**
       * If this object can be simplified, it must have a property
       * ```
       * public readonly simplified: SimplificationRef<...>;
       * ```
       * that is set as:
       * ```ts
       * constructor(..., ctx: Context) {
       *   this.simplified = reactiveSimplification(ctx, () => ...);
       * }
       * ```
       */
      readonly simplified?: SimplificationRef<T>;

      /**
       * Whether this `Simplifiable` object does not want a tooltip to be shown, regardless of any
       * `tooltip` entry in a `SuccessfulSimplification` returned by our `simplified` property.
       */
      readonly suppressTooltip?: boolean;
    });

/**
 * Interface for objects that can be potentially be simplified.
 * @typeParam T Type this object might be simplified to
 */
export type Simplifiable<T extends string | Renderable> = Exclude<StringOrSimplifiable<T>, string>;

/**
 * TypeScript treats types having the same structure as equal. To let it differentiate at compile-time between
 * different interfaces that extend `Simplifiable` but are otherwise empty, each extending interface must define the
 * same optional property with incompatible types. We use a symbol for the property key. It does not exist at runtime.
 */
export const typeDifferentiator = Symbol();

// Add a special symbol property to make sure using this module is the only way to create a SimplificationRef<T>.
// The property does not exist at runtime, it is only for type-checking.
declare const simplificationRefSymbol: unique symbol;
export type SimplificationRef<T> = Readonly<Ref<Simplification<T>>> & { [simplificationRefSymbol]: true };

export type Simplification<T> =
  | typeof objectIsAtomic
  | typeof objectHasPendingSimplification
  | SuccessfulSimplification<T>
  | FailedSimplification<T>;

/**
 * Denotes that the object cannot be further simplified, it already is atomic.
 */
export const objectIsAtomic = Symbol(__DEV__ ? 'atomic' : '');

/**
 * Denotes that the object currently cannot be simplified because it
 * is waiting for one of its parts to be simplified or resolved.
 */
export const objectHasPendingSimplification = Symbol(__DEV__ ? 'pending' : '');

/**
 * Denote that the object was successfully simplified. The result must be different from the original object!
 * The tooltip value optionally provides the object in a state before the last simplification,
 * that value is used by the tooltip function to render a tooltip showing the simplification.
 */
export class SuccessfulSimplification<T> {
  public readonly result: T;
  public readonly tooltip?: Renderable;

  constructor(result: T, tooltip?: Renderable) {
    this.result = result;
    this.tooltip = tooltip;
  }
}

/**
 * Denote that simplifying the object has failed, providing an error message and
 * optionally a partial simplification result to be shown instead of the original object.
 */
export class FailedSimplification<T> {
  public readonly errorMessage: string;
  public readonly partialResult?: T;

  constructor(errorMessage: string, partialResult?: T) {
    this.errorMessage = errorMessage;
    this.partialResult = partialResult;
  }
}

/**
 * Define a reactive simplification
 * @param ctx The context to get the effect scope from
 * @param getter Getter for the simplification result
 */
export function reactiveSimplification<T>(ctx: Context, getter: () => Simplification<T>): SimplificationRef<T> {
  return ctx.snapshot.effectScope.run(() => computed(getter))! as Omit<
    SimplificationRef<T>,
    typeof simplificationRefSymbol
  > as SimplificationRef<T>;
}

/**
 * Get the given object's simplification
 * @param x the object to simplify
 * @returns the object's simplification
 */
export function getSimplification<T extends StringOrSimplifiable<T>>(x: T): Simplification<T> {
  return typeof x === 'string' || !x.simplified ? objectIsAtomic : x.simplified.value;
}

/**
 * Simplify the given object as much as possible
 * @param x the object to simplify
 * @returns the final simplification or `x` if the object cannot be simplified
 */
export function simplify<T extends StringOrSimplifiable<T>>(x: T): T {
  let simplification: Simplification<T>;
  while ((simplification = getSimplification(x)) instanceof SuccessfulSimplification) {
    // per our specification, we have simplification.result !== x
    x = simplification.result; // eslint-disable-line no-param-reassign
  }
  return simplification instanceof FailedSimplification && simplification.partialResult !== undefined
    ? simplification.partialResult
    : x;
}

export function isAtomic<T extends StringOrSimplifiable<T>>(x: T): boolean {
  return getSimplification(x) === objectIsAtomic;
}
