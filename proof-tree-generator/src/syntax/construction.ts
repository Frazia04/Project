/**
 * This module provides a way to construct expressions/declarations. We need this in two situations:
 * - when applying a semantics rule to an unknown ExpressionVariable/DeclarationVariable, and
 * - when rendering a semantics rule.
 *
 * We expect Expression and Declaration classes to provide a static property `constructorParams` describing the
 * constructor parameter types such that we can build suitable arguments to call the constructor at runtime.
 */

import type { Context } from '../context';
import { type Type, TypeVariable } from '../semantics/static/types';
import type { FilterTuple } from '../utils';
import type { Declaration, Expression } from '.';
import { DeclarationVariable, ExpressionVariable } from './metaVariables';
import { parseIdentifier } from './parser/identifiers';

// Descriptors for each supported parameter type
export const expressionParameter = Symbol(__DEV__ ? 'expressionParameter' : ''); // -> use an ExpressionVariable
export const declarationParameter = Symbol(__DEV__ ? 'declarationParameter' : ''); // -> use a DeclarationVariable
export const typeParameter = Symbol(__DEV__ ? 'typeParameter' : ''); // -> use a TypeVariable
export const natParameter = Symbol(__DEV__ ? 'natParameter' : ''); // -> ask for the value
export const charParameter = Symbol(__DEV__ ? 'charParameter' : ''); // -> ask for the value
// Identifier strings are described using a string that holds a placeholder-name (f, x, ...).

// Descriptors that can be fulfilled with a meta variable (automatic, no need to query the user)
// prettier-ignore
type MetaVariableTypeDescriptor =
  | typeof expressionParameter
  | typeof declarationParameter
  | typeof typeParameter
  ;

// Descriptors that need input from the user
// prettier-ignore
type QueriedTypeDescriptor =
  | typeof natParameter
  | typeof charParameter
  | string
  ;

// All possible type descriptors
type TypeDescriptor = MetaVariableTypeDescriptor | QueriedTypeDescriptor;

// All supported types
type SupportedTypes = Expression | Declaration | (Type | undefined) | bigint | string;

// Determine the corresponding type descriptor type for a given constructor parameter type
// prettier-ignore
type TypeDescriptorFor<T extends SupportedTypes> =
  | (Expression         extends T ? typeof expressionParameter    : never)
  | (Declaration        extends T ? typeof declarationParameter   : never)
  | ((Type | undefined) extends T ? typeof typeParameter          : never)
  | (bigint             extends T ? typeof natParameter           : never)
  | (string             extends T ? typeof charParameter | string : never)
  ;

// Inverse of `TypeDescriptorFor<...>`: Get the allowed type for a given type descriptor
// prettier-ignore
type ValueForTypeDescriptor<T extends TypeDescriptor> =
  | (T extends typeof expressionParameter  ? Expression       : never)
  | (T extends typeof declarationParameter ? Declaration      : never)
  | (T extends typeof typeParameter        ? Type | undefined : never)
  | (T extends typeof natParameter         ? bigint           : never)
  | (T extends typeof charParameter        ? string           : never)
  | (T extends string                      ? string           : never)
  ;

// Map `TypeDescriptorFor<...>` to a tuple
type TypeDescriptorsFor<T extends readonly SupportedTypes[]> = T extends readonly [
  infer Head extends SupportedTypes,
  ...infer Tail extends SupportedTypes[],
]
  ? readonly [TypeDescriptorFor<Head>, ...TypeDescriptorsFor<Tail>]
  : readonly [];

// Map `ValueForTypeDescriptor<...>` to a tuple
type ValuesForTypeDescriptors<T extends readonly TypeDescriptor[]> = T extends readonly [
  infer Head extends TypeDescriptor,
  ...infer Tail extends TypeDescriptor[],
]
  ? readonly [ValueForTypeDescriptor<Head>, ...ValuesForTypeDescriptors<Tail>]
  : readonly [];

/**
 * Constructor parameters (as tuple!) that are supported by the "construction" module
 */
export type SupportedParams = SupportedTypes[] | [...SupportedTypes[], Context];

// Remove the last element if it is `Context`
type OmitContextAtEnd<T extends SupportedParams> = T extends [...infer Init, Context] ? Init : T;

/**
 * A constructor that describes its parameter types in a `constructorParams` field
 */
export type ConstructorWithParameterTypeDescriptors<P extends SupportedParams, R> =
  // Check whether the property is optional
  TypeDescriptorsFor<OmitContextAtEnd<P>> extends readonly []
    ? {
        new (...args: P): R;
        readonly constructorParams?: readonly [];
      }
    : {
        new (...args: P): R;
        readonly constructorParams: TypeDescriptorsFor<OmitContextAtEnd<P>>;
      };

/**
 * Tuple of constructor parameters that need to be queried from the user
 */
export type QueriedConstructorParameters<
  T extends ConstructorWithParameterTypeDescriptors<P, R>,
  P extends SupportedParams = ConstructorParameters<T>,
  R = InstanceType<T>,
> = T['constructorParams'] extends readonly [...infer Descriptors]
  ? ValuesForTypeDescriptors<FilterTuple<QueriedTypeDescriptor, Descriptors>>
  : readonly [];

/**
 * Construct a given syntax element
 * @param ctor the constructor to call
 * @param ctx the context
 * @param queriedArgs values for constructor parameters that cannot be filled with meta variables
 */
export function constructSyntaxElement<
  T extends ConstructorWithParameterTypeDescriptors<P, R>,
  P extends SupportedParams = ConstructorParameters<T>,
  R = InstanceType<T>,
>(ctor: T, ctx: Context, queriedArgs: QueriedConstructorParameters<T, P, R>): R {
  const args: unknown[] = [];
  // Convince Typescript that the array might be non-empty
  if ((ctor.constructorParams as readonly TypeDescriptor[] | undefined)?.length) {
    const remainingQueriedArgs = [...queriedArgs];
    for (const p of ctor.constructorParams as readonly TypeDescriptor[]) {
      args.push(
        p === expressionParameter
          ? new ExpressionVariable(ctx)
          : p === declarationParameter
          ? new DeclarationVariable(ctx)
          : p === typeParameter
          ? ctx.requireTypes
            ? new TypeVariable(ctx)
            : undefined
          : remainingQueriedArgs.shift(),
      );
    }
  }
  args.push(ctx);
  return new ctor(...(args as P));
}

/**
 * Collect the "queried arguments" that are required to call the given constructor.
 * Ask the user for any number or character literals and identifiers.
 * @param ctor the constructor to read the `constructorParams` property from
 */
export async function interactiveQueryConstructorArguments<
  T extends ConstructorWithParameterTypeDescriptors<P, R>,
  P extends SupportedParams = ConstructorParameters<T>,
  R = InstanceType<T>,
>(ctor: T): Promise<QueriedConstructorParameters<T, P, R>> {
  const args: unknown[] = [];
  if (ctor.constructorParams) {
    // Convince Typescript that the array might be non-empty
    for (const p of ctor.constructorParams as readonly TypeDescriptor[]) {
      if (!(p === expressionParameter || p === declarationParameter || p === typeParameter)) {
        args.push(
          p === natParameter ? await queryNat() : p === charParameter ? await queryChar() : await queryIdentifier(p),
        );
      }
    }
  }
  return args as unknown as QueriedConstructorParameters<T, P, R>;
}

/**
 * Collect the "queried arguments" that are required to call the given constructor by providing placeholder values.
 * @param ctor the constructor to read the `constructorParams` property from
 */
export function buildPlaceholderQueriedArgs<
  T extends ConstructorWithParameterTypeDescriptors<P, R>,
  P extends SupportedParams = ConstructorParameters<T>,
  R = InstanceType<T>,
>(ctor: T): QueriedConstructorParameters<T, P, R> {
  const args: unknown[] = [];
  if (ctor.constructorParams) {
    // Convince Typescript that the array might be non-empty
    for (const p of ctor.constructorParams as readonly TypeDescriptor[]) {
      if (!(p === expressionParameter || p === declarationParameter || p === typeParameter)) {
        args.push(
          p === natParameter
            ? // string instead of bigint here violates the type system,
              // but that is fine when used for rendering purposes only.
              `<span class="meta-variable">n</span>`
            : p === charParameter
            ? `<span class="meta-variable">c</span>`
            : // string here is the identifier name
              `<span class="meta-variable">${p}</span>`,
        );
      }
    }
  }
  return args as unknown as QueriedConstructorParameters<T, P, R>;
}

// TODO: Better interaction, making use of async
// eslint-disable-next-line @typescript-eslint/require-await
async function queryIdentifier(placeholder: string): Promise<string> {
  const id = window.prompt(`Bezeichner ${placeholder}:`);
  if (id !== null) {
    try {
      return parseIdentifier(id);
    } catch (err) {
      // ignore, throw below
    }
  }
  throw new Error('Ungültiger Bezeichner!');
}

// TODO: Better interaction, making use of async
// eslint-disable-next-line @typescript-eslint/require-await
async function queryNat(): Promise<bigint> {
  const n = window.prompt('Natürliche Zahl:');
  if (n !== null) {
    try {
      return BigInt(n);
    } catch (err) {
      // ignore, throw below
    }
  }
  throw new Error('Ungültige natürliche Zahl!');
}

// TODO: Better interaction, making use of async
// eslint-disable-next-line @typescript-eslint/require-await
async function queryChar(): Promise<string> {
  const char = window.prompt('Zeichen:');
  if (char === null || char.length !== 1) {
    throw new Error('Ungültiges Zeichen!');
  }
  return char;
}
