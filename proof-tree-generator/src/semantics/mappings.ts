// This module provides all common aspects for Signatures, Environments and Stores.

import type { Context } from '../context';
import { htmlErrorMessagePrefix } from '../frontend/errors';
import {
  Precedence,
  reactiveRendering,
  render,
  type Renderable,
  Rendering,
  type RenderingRef,
  staticRendering,
} from '../rendering';
import {
  isAtomic,
  objectHasPendingSimplification,
  objectIsAtomic,
  reactiveSimplification,
  type Simplifiable,
  type SimplificationRef,
  simplify,
  type StringOrSimplifiable,
  SuccessfulSimplification,
  typeDifferentiator,
} from '../simplify';
import { MetaVariable } from '../state/metaVariables';
import { unwrap } from '../tooltips';
import { capitalizeFirstLetter } from '../utils';
import { Constraint, type ConstraintProvider } from './constraints';
import { MismatchError, translateMismatchError } from './matching';

// Identifiers are the keys of Signatures and Environments. Stores use Values (addresses) as keys.
export type Identifier = string | IdentifierVariable;
export class IdentifierVariable extends MetaVariable<Identifier> {
  constructor(ctx: Context, symbol: 'x' | 'f' = 'x') {
    super(symbol, ctx);
  }
}

const pending = Symbol(__DEV__ ? 'pending' : '');
const invalidKey = Symbol(__DEV__ ? 'invalidKey' : '');

export interface Mapping<K extends StringOrSimplifiable<K>, V extends string | Renderable>
  extends Simplifiable<Mapping<K, V>> {
  readonly [typeDifferentiator]?: unique symbol;

  /**
   * Look up the given key in this mapping (to be used only in mappings.ts)
   * @param key The key to look up
   * @returns The value that the key maps to;
   *          `pending` if a mapping key is not yet atomic or a component of this mapping is an unset mapping variable;
   *          `invalidKey` if mapping does not contain the key
   */
  readonly _lookup: (key: K) => V | typeof pending | typeof invalidKey;

  /**
   * Check whether this mapping is empty
   * @returns Whether the mapping is empty;
   *          `undefined` if a component of this mapping is an unset mapping variable
   */
  readonly isEmpty: boolean | undefined;

  /**
   * Get all keys in this mapping
   * @returns all keys in this mapping (the list may contain duplicates);
   *          `undefined` if a component of this mapping is an unset mapping variable
   */
  readonly keys: readonly K[] | undefined;
}

export type MappingVariable<K extends StringOrSimplifiable<K>, V extends string | Renderable> = Mapping<K, V> &
  MetaVariable<Mapping<K, V>>;

export function defineMappingClasses<K extends StringOrSimplifiable<K>, V extends string | Renderable>(
  metaVariableSymbol: string,
  metaVariableLatexSymbol: string,
  theMapping: string,
  inMapping: string,
  theKey: string,
  mappingValuesMustMatch: (expected: V, actual: V, ctx: Context, object?: Renderable) => ConstraintProvider[],
  keyEquals?: (k1: K, k2: K) => boolean,
): {
  BasicMapping: (new () => Mapping<K, V>) &
    (new (entries: readonly (readonly [K, V])[], ctx: Context) => Mapping<K, V>);
  CommaOperatorMapping: new (left: Mapping<K, V>, right: Mapping<K, V>, ctx: Context) => Mapping<K, V>;
  MappingVariable: new (ctx: Context) => MappingVariable<K, V>;
  LookupResult: new (mapping: Mapping<K, V>, key: K, ctx: Context) => Simplifiable<V>;
  mappingMustContainKey: (mapping: Mapping<K, V>, key: K, ctx: Context) => ConstraintProvider[];
  mappingMustNotContainKey: (mapping: Mapping<K, V>, key: K, ctx: Context) => ConstraintProvider[];
  mappingsMustMatch: (expected: Mapping<K, V>, actual: Mapping<K, V>, ctx: Context) => ConstraintProvider[];
} {
  class BasicMapping implements Mapping<K, V> {
    public readonly rendered: RenderingRef;
    public readonly simplified?: SimplificationRef<never>;
    public readonly entries: readonly (readonly [K, V])[];

    constructor();
    constructor(entries: readonly (readonly [K, V])[], ctx: Context);
    constructor(entries?: readonly (readonly [K, V])[], ctx?: Context) {
      if (entries?.length) {
        this.entries = entries;
        this.rendered = reactiveRendering(ctx!, () => {
          const spec: [Precedence, Precedence, ...(string | Rendering | Renderable)[]] = [
            // The mapping itself is atomic (because of curly braces)
            Precedence.Atom,

            // Entries can have low precedence, because none of the objects
            // we put into mappings have a comma at outer level
            Precedence.Low,
          ];

          spec.push(new Rendering('{', '\\{'));
          let needComma = false;
          entries.forEach(([key, value]) => {
            if (needComma) {
              spec.push(', ');
            } else {
              needComma = true;
            }
            spec.push(key, new Rendering(' &RightTeeArrow; ', ' \\mapsto '), value);
          });
          spec.push(new Rendering('}', '\\}'));
          return spec;
        });

        // Check for duplicate keys (when constructing mapping from parser)
        const keys = new Set();
        for (let [key] of entries) {
          key = simplify(key);
          if (!(key instanceof MetaVariable)) {
            if (keys.has(key)) {
              throw new Error(
                htmlErrorMessagePrefix +
                  `${inMapping[0].toUpperCase() + inMapping.substring(1)} ${
                    this.rendered.value.html
                  } darf ${theKey} ${render(key)} nicht doppelt vorkommen.`,
              );
            } else {
              keys.add(key);
            }
          }
        }

        this.simplified = reactiveSimplification(ctx!, () => {
          // This basic mapping is atomic if all contained keys are atomic (values do not matter).
          for (const [key] of entries) {
            if (!isAtomic(simplify(key))) {
              return objectHasPendingSimplification;
            }
          }
          return objectIsAtomic;
        });
      } else {
        this.entries = [];
        this.rendered = staticRendering('&empty;', '\\emptyset');
      }
    }

    public _lookup(key: K): V | typeof pending | typeof invalidKey {
      key = unwrap(simplify(key)); // eslint-disable-line no-param-reassign
      let isPending = !isAtomic(key);
      for (let [k, v] of this.entries) {
        k = unwrap(simplify(k));
        if (k === key || keyEquals?.(k, key)) {
          return v;
        }
        isPending = isPending || !isAtomic(k);
      }
      return isPending ? pending : invalidKey;
    }

    public get isEmpty(): boolean {
      return this.entries.length === 0;
    }

    public get keys(): readonly K[] {
      return this.entries.map(([k]) => unwrap(simplify(k)));
    }
  }

  class CommaOperatorMapping implements Mapping<K, V> {
    public readonly rendered: RenderingRef;
    public readonly simplified: SimplificationRef<Mapping<K, V>>;
    private readonly left: Mapping<K, V>;
    private readonly right: Mapping<K, V>;

    constructor(left: Mapping<K, V>, right: Mapping<K, V>, ctx: Context) {
      this.left = left;
      this.right = right;
      this.rendered = reactiveRendering(ctx, () => [Precedence.Low, left, ' , ', right]);
      this.simplified = reactiveSimplification(ctx, () => {
        const simpleLeft = simplify(left);
        const simpleRight = simplify(right);
        const tooltipValue = new CommaOperatorMapping(simpleLeft, simpleRight, ctx);
        if (simpleLeft.isEmpty) {
          return new SuccessfulSimplification(simpleRight, tooltipValue);
        }
        if (simpleRight.isEmpty) {
          return new SuccessfulSimplification(simpleLeft, tooltipValue);
        }
        if (
          simpleLeft instanceof BasicMapping &&
          simpleRight instanceof BasicMapping &&
          isAtomic(simpleLeft) &&
          isAtomic(simpleRight)
        ) {
          const rightEntries = (simpleRight as BasicMapping).entries;
          const rightKeys = new Set(rightEntries.map(([k]) => unwrap(simplify(k))));
          return new SuccessfulSimplification<Mapping<K, V>>(
            new BasicMapping(
              Array.from((simpleLeft as BasicMapping).entries)
                .filter(([k]) => !rightKeys.has(unwrap(simplify(k))))
                .concat(rightEntries),
              ctx,
            ),
            tooltipValue,
          );
        }
        return objectHasPendingSimplification;
      });
    }

    public _lookup(key: K): V | typeof pending | typeof invalidKey {
      const result = this.right._lookup(key);
      return result === invalidKey ? this.left._lookup(key) : result;
    }

    public get isEmpty(): boolean | undefined {
      const leftEmpty = this.left.isEmpty;
      if (leftEmpty === false) return false;
      const rightEmpty = this.right.isEmpty;
      if (rightEmpty === false) return false;
      return leftEmpty && rightEmpty ? true : undefined;
    }

    public get keys(): readonly K[] | undefined {
      const leftKeys = this.left.keys;
      if (!leftKeys) return undefined;
      const rightKeys = this.right.keys;
      if (!rightKeys) return undefined;
      return leftKeys.concat(rightKeys);
    }
  }

  class MappingVariable extends MetaVariable<Mapping<K, V>> {
    constructor(ctx: Context) {
      super(metaVariableSymbol, ctx, metaVariableLatexSymbol);
    }

    public _lookup(key: K): V | typeof pending | typeof invalidKey {
      const mapping = this.value;
      return mapping ? mapping._lookup(key) : pending;
    }

    public get isEmpty(): boolean | undefined {
      return this.value?.isEmpty;
    }

    public get keys(): readonly K[] | undefined {
      return this.value?.keys;
    }
  }

  class LookupResult implements Simplifiable<V> {
    public readonly rendered: RenderingRef;
    public readonly simplified: SimplificationRef<V>;

    constructor(mapping: Mapping<K, V>, key: K, ctx: Context) {
      this.rendered = reactiveRendering(ctx, () => [Precedence.Application, Precedence.Atom, mapping, '(', key, ')']);
      this.simplified = reactiveSimplification(ctx, () => {
        const result = mapping._lookup(key);
        switch (result) {
          case pending:
          case invalidKey:
            return objectHasPendingSimplification;
          default:
            return new SuccessfulSimplification(result, new LookupResult(simplify(mapping), simplify(key), ctx));
        }
      });
    }
  }

  function mappingMustContainKey(mapping: Mapping<K, V>, key: K, ctx: Context): ConstraintProvider[] {
    function check(): boolean {
      switch (mapping._lookup(key)) {
        case pending:
          return false;
        case invalidKey:
          throw new Error(
            htmlErrorMessagePrefix +
              `${capitalizeFirstLetter(theKey)} ${render(simplify(key))} ist nicht ${inMapping} ${
                simplify(mapping).rendered.value.html
              } enthalten!`,
          );
        default:
          return true;
      }
    }

    return check()
      ? []
      : [
          () =>
            new Constraint(
              check,
              reactiveRendering(ctx, () => [
                Precedence.Low,
                Precedence.Atom,
                key,
                new Rendering(' &isin; ', ' \\in '),
                'dom ',
                mapping,
              ]),
            ),
        ];
  }

  function mappingMustNotContainKey(mapping: Mapping<K, V>, key: K, ctx: Context): ConstraintProvider[] {
    function check(): boolean {
      switch (mapping._lookup(key)) {
        case pending:
          return false;
        case invalidKey:
          return true;
        default:
          throw new Error(
            htmlErrorMessagePrefix +
              `${capitalizeFirstLetter(theKey)} ${render(simplify(key))} ist ${inMapping} ${
                simplify(mapping).rendered.value.html
              } bereits enthalten!`,
          );
      }
    }

    return check()
      ? []
      : [
          () =>
            new Constraint(
              check,
              reactiveRendering(ctx, () => [
                Precedence.Low,
                Precedence.Atom,
                key,
                new Rendering(' &notin; ', ' \\notin '),
                'dom ',
                mapping,
              ]),
            ),
        ];
  }

  class MappingMismatchError extends MismatchError {
    constructor(expected: Mapping<K, V>, actual: Mapping<K, V>) {
      super(
        htmlErrorMessagePrefix +
          `Du kannst diese Regel nicht anwenden, da ${theMapping} ${actual.rendered.value.html} nicht mit ${expected.rendered.value.html} übereinstimmt.`,
      );
      this.name = 'MappingMismatchError';
    }
  }

  const mappingsMustMatch = translateMismatchError<Mapping<K, V>>(
    MappingMismatchError,
    (expected: Mapping<K, V>, actual: Mapping<K, V>, ctx: Context): ConstraintProvider[] => {
      function check(ctx: Context): boolean {
        const simpleExpected = simplify(expected);
        const simpleActual = simplify(actual);

        // Identical
        if (simpleExpected === simpleActual) {
          return true;
        }

        // Meta variable (variable is unset if returned by simplify)
        if (simpleExpected instanceof MetaVariable) {
          simpleExpected.set(simpleActual, ctx);
          return true;
        }
        if (simpleActual instanceof MetaVariable) {
          simpleActual.set(simpleExpected, ctx);
          return true;
        }

        // Require keys for comparing the mappings
        const expectedKeys = simpleExpected.keys;
        if (!expectedKeys) {
          return false;
        }
        const actualKeys = simpleActual.keys;
        if (!actualKeys) {
          return false;
        }

        // Collect all keys from both mappings and use a Set to eliminate duplicates
        for (const key of new Set(expectedKeys.concat(actualKeys))) {
          // Lookup values in both mappings
          const expectedValue = simpleExpected._lookup(key);
          if (expectedValue === invalidKey) {
            throw new MappingMismatchError(simpleExpected, simpleActual);
          }
          const actualValue = simpleActual._lookup(key);
          if (actualValue === invalidKey) {
            throw new MappingMismatchError(simpleExpected, simpleActual);
          }

          if (
            // Check for pending lookup value
            expectedValue === pending ||
            actualValue === pending ||
            // Match the values (might throw) and check if it yields a constraint.
            // By the invariant of Constraint, any returned constraint will be pending.
            mappingValuesMustMatch(expectedValue, actualValue, ctx).length
          ) {
            return false;
          }
        }
        return true;
      }

      return check(ctx)
        ? []
        : [
            () =>
              new Constraint(
                check,
                reactiveRendering(ctx, () => [
                  Precedence.Comparison,
                  Precedence.Comparison + 1,
                  actual,
                  ' = ',
                  expected,
                ]),
              ),
          ];
    },
  );

  return {
    BasicMapping,
    CommaOperatorMapping,
    MappingVariable,
    LookupResult,
    mappingMustContainKey,
    mappingMustNotContainKey,
    mappingsMustMatch,
  };
}
