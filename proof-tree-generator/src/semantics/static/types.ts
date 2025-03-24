import type { Context } from '../../context';
import { htmlErrorMessagePrefix } from '../../frontend/errors';
import {
  Precedence,
  reactiveRendering,
  type Renderable,
  Rendering,
  type RenderingRef,
  staticRendering,
} from '../../rendering';
import { type Simplifiable, typeDifferentiator } from '../../simplify';
import { MetaVariable } from '../../state/metaVariables';
import type { ConstraintProvider } from '../constraints';
import { defineMappingClasses, type Identifier, type Mapping, type MappingVariable } from '../mappings';
import { buildMustMatchFunction, MismatchError, translateMismatchError } from '../matching';

// --------------------------------------------------------------------------------------------------------------------

/**
 * Representation of an F# type
 */
export interface Type extends Simplifiable<Type> {
  readonly [typeDifferentiator]?: unique symbol;
}

export class TypeVariable extends MetaVariable<Type> implements Type {
  constructor(ctx: Context) {
    super('t', ctx);
  }
}

// --------------------------------------------------------------------------------------------------------------------

/**
 * Signatures map Identifiers to Types
 */
export type Signature = Mapping<Identifier, Type>;
export type SignatureVariable = MappingVariable<Identifier, Type>;

// --------------------------------------------------------------------------------------------------------------------

function createBasicType(typeName: string): Type {
  return {
    rendered: staticRendering(new Rendering(typeName, `\\mathrm{${typeName}}`)),
  };
}
export const natType = createBasicType('Nat');
export const boolType = createBasicType('Bool');
export const unitType = createBasicType('Unit');
export const charType = createBasicType('Char');
export const exceptionType = createBasicType('Exception');

// --------------------------------------------------------------------------------------------------------------------

export class FunctionType implements Type {
  public readonly rendered: RenderingRef;
  public readonly paramType: Type;
  public readonly returnType: Type;

  constructor(paramType: Type, returnType: Type, ctx: Context) {
    this.paramType = paramType;
    this.returnType = returnType;
    this.rendered = reactiveRendering(ctx, () => [
      Precedence.Application,
      Precedence.Atom,
      paramType,
      new Rendering(' &rarr; ', ' \\to '),
      Precedence.Application,
      returnType,
    ]);
  }
}

export class RefType implements Type {
  public readonly rendered: RenderingRef;
  public readonly type: Type;

  constructor(type: Type, ctx: Context) {
    this.type = type;
    this.rendered = reactiveRendering(ctx, () => [
      Precedence.Low,
      new Rendering('Ref &#x2329;', 'Ref\\langle '),
      type,
      new Rendering('&#x232A;', '\\rangle '),
    ]);
  }
}

export class PairType implements Type {
  public readonly rendered: RenderingRef;
  public readonly fst: Type;
  public readonly snd: Type;

  constructor(fst: Type, snd: Type, ctx: Context) {
    this.fst = fst;
    this.snd = snd;
    this.rendered = reactiveRendering(ctx, () => [Precedence.Application, Precedence.Atom, fst, ' * ', snd]);
  }
}

// --------------------------------------------------------------------------------------------------------------------

class TypeMismatchError extends MismatchError {
  constructor(expected: Type, actual: Type, object?: Renderable) {
    super(
      htmlErrorMessagePrefix +
        'Du kannst diese Regel nicht anwenden, da der Typ ' +
        actual.rendered.value.html +
        (object ? ' von ' + object.rendered.value.html : '') +
        ' nicht mit dem erwarteten Typ ' +
        expected.rendered.value.html +
        ' übereinstimmt.',
    );
    this.name = 'TypeMismatchError';
  }
}

/**
 * Check that the two given types are equal, thereby setting `TypeVariable`s as needed
 * @param expected the expected type (passed from below)
 * @param actual the actual type (generated from the rule application)
 * @param object the object being checked
 * @param ctx the context
 * @returns constraints that must be fulfilled for the types to match
 * @throws TypeMismatchError if the given types are not equal
 */
export const typesMustMatch: (expected: Type, actual: Type, ctx: Context, object?: Renderable) => ConstraintProvider[] =
  buildMustMatchFunction(
    translateMismatchError<Type>(TypeMismatchError, (expected, actual, ctx) => {
      if (expected instanceof FunctionType && actual instanceof FunctionType) {
        return [
          ...typesMustMatch(expected.paramType, actual.paramType, ctx),
          ...typesMustMatch(expected.returnType, actual.returnType, ctx),
        ];
      }
      if (expected instanceof RefType && actual instanceof RefType) {
        return typesMustMatch(expected.type, actual.type, ctx);
      }
      if (expected instanceof PairType && actual instanceof PairType) {
        return [...typesMustMatch(expected.fst, actual.fst, ctx), ...typesMustMatch(expected.snd, actual.snd, ctx)];
      }
      return false;
    }),
  );

export const {
  BasicMapping: BasicSignature,
  CommaOperatorMapping: CommaOperatorSignature,
  MappingVariable: SignatureVariable,
  LookupResult: SignatureLookupResult,
  mappingMustContainKey: signatureMustContainId,
  mappingsMustMatch: signaturesMustMatch,
} = defineMappingClasses<Identifier, Type>(
  '&Sigma;',
  '\\Sigma',
  'die Signatur',
  'in der Signatur',
  'der Bezeichner',
  typesMustMatch,
);
