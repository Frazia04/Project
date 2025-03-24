// Utility to check that two types/values/mappings match.
// Will set unset meta variables and calculate constraints for pending parts.
// Throws if inputs cannot match.

import type { Context } from '../context';
import { Precedence, reactiveRendering, type Renderable } from '../rendering';
import { isAtomic, simplify, type StringOrSimplifiable } from '../simplify';
import { MetaVariable } from '../state/metaVariables';
import { unwrap } from '../tooltips';
import { Constraint, type ConstraintProvider } from './constraints';

export class MismatchError extends Error {}

type MustMatchFunction<T extends StringOrSimplifiable<T>, R> = (
  expected: T,
  actual: T,
  ctx: Context,
  object?: Renderable,
) => ConstraintProvider[] | R;

type SimpleMustMatchFunction<T extends StringOrSimplifiable<T>> = MustMatchFunction<T, never>;

export function buildMustMatchFunction<T extends StringOrSimplifiable<T>>(
  whenAtomic: SimpleMustMatchFunction<T>,
  whenPending?: MustMatchFunction<T, null | undefined>,
): SimpleMustMatchFunction<T> {
  // Invariant: Arguments of mustMatch have been passed through simplify
  const mustMatch: SimpleMustMatchFunction<T> = (expected, actual, ctx, object) => {
    // Identical
    if (unwrap(expected) === unwrap(actual)) {
      return [];
    }

    // Meta variable (variable is unset if returned by simplify)
    if (expected instanceof MetaVariable) {
      expected.set(actual, ctx);
      return [];
    }
    if (actual instanceof MetaVariable) {
      actual.set(expected, ctx);
      return [];
    }

    // Check which callback to use
    return isAtomic(expected) && isAtomic(actual)
      ? // use callback for atomic inputs
        whenAtomic(expected, actual, ctx, object)
      : // try callback for pending inputs
        whenPending?.(expected, actual, ctx, object) ?? [
          // if result is unknown right now, create a constraint to check later
          () =>
            new Constraint(
              // Simplify inputs again (required for invariant), then check if recursive call yields a constraint.
              // By the invariant of Constraint, any returned constraint will be pending.
              (ctx) => !mustMatch(simplify(expected), simplify(actual), ctx, object).length,
              reactiveRendering(ctx, () => [Precedence.Comparison, Precedence.Comparison + 1, actual, ' = ', expected]),
            ),
        ];
  };

  return (expected, actual, ctx, object) => mustMatch(simplify(expected), simplify(actual), ctx, object);
}

export function translateMismatchError<T extends StringOrSimplifiable<T>, U extends undefined = never>(
  mismatchErrorCtor: new (expected: T, actual: T, object?: Renderable) => MismatchError,
  cb: MustMatchFunction<T, U | boolean>,
  symmetric?: undefined extends U ? true : never,
): MustMatchFunction<T, U> {
  return (expected, actual, ctx, object) => {
    try {
      let constraints = cb(expected, actual, ctx);
      if (symmetric && constraints === undefined) {
        constraints = cb(actual, expected, ctx);
      }
      if (constraints === true) {
        return [];
      }
      if (constraints !== false) {
        return constraints;
      }
    } catch (err) {
      if (!(err instanceof MismatchError)) {
        throw err;
      }
    }
    throw new mismatchErrorCtor(expected, actual, object);
  };
}
