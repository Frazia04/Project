import type { Context } from '../../context';
import { htmlErrorMessagePrefix } from '../../frontend/errors';
import {
  centerDot,
  Precedence,
  reactiveRendering,
  render,
  type Renderable,
  Rendering,
  type RenderingRef,
  renderQuotedChar,
  staticRendering,
} from '../../rendering';
import {
  FailedSimplification,
  isAtomic,
  objectHasPendingSimplification,
  reactiveSimplification,
  type Simplifiable,
  type SimplificationRef,
  simplify,
  SuccessfulSimplification,
  typeDifferentiator,
} from '../../simplify';
import { MetaVariable } from '../../state/metaVariables';
import {
  Addition,
  Assignment,
  CharLiteral,
  type Declaration,
  Dereference,
  Division,
  EQ,
  type Expression,
  FalseLiteral,
  FunctionApplication,
  FunctionDefinition,
  GEQ,
  GT,
  IdentifierExpression,
  IfThenElse,
  InExpression,
  LambdaExpression,
  LEQ,
  LetBinding,
  LT,
  Modulo,
  Monus,
  Multiplication,
  NatLiteral,
  NEQ,
  Pair,
  RecursiveFunctionDefinition,
  TrueLiteral,
  Unit,
} from '../../syntax';
import type { ConstraintProvider } from '../constraints';
import { defineMappingClasses, type Identifier, type Mapping, type MappingVariable } from '../mappings';
import { buildMustMatchFunction, MismatchError, translateMismatchError } from '../matching';

// --------------------------------------------------------------------------------------------------------------------

/**
 * Representation of an F# value
 */
export interface Value extends Simplifiable<Value> {
  readonly [typeDifferentiator]?: unique symbol;
}

export class ValueVariable extends MetaVariable<Value> implements Value {
  constructor(ctx: Context) {
    super('v', ctx);
  }
}

// --------------------------------------------------------------------------------------------------------------------

/**
 * Environments map Identifiers to Values
 */
export type Environment = Mapping<Identifier, Value>;
export type EnvironmentVariable = MappingVariable<Identifier, Value>;

// --------------------------------------------------------------------------------------------------------------------

export const unitValue: Value = {
  rendered: staticRendering('()'),
};

// --------------------------------------------------------------------------------------------------------------------

class BasicValue<T> implements Value {
  public readonly value: T;
  public readonly rendered: RenderingRef;

  constructor(value: T) {
    this.value = value;
    this.rendered = staticRendering(String(value));
  }
}

export class NatValue extends BasicValue<bigint> {
  constructor(value: bigint) {
    if (__DEV__ && value < 0n) {
      throw new Error(`(Internal) Cannot construct NatValue of ${value}.`);
    }
    super(value);
  }
}
export const zeroValue = new NatValue(0n);
export const oneValue = new NatValue(1n);

class BoolValue extends BasicValue<boolean> {}
export const falseValue = new BoolValue(false);
export const trueValue = new BoolValue(true);

// --------------------------------------------------------------------------------------------------------------------

export class CharValue implements Value {
  public readonly char: string;
  public readonly rendered: RenderingRef;

  constructor(char: string) {
    this.char = char;
    this.rendered = staticRendering(renderQuotedChar(char));
  }
}

export class Address implements Value {
  public readonly index: bigint;
  public readonly rendered: RenderingRef;

  constructor(index: bigint) {
    this.index = index;
    this.rendered = staticRendering(`a<span class="sub">${index}</span>`, `a_{${index}}`);
  }
}

export class PairValue implements Value {
  public readonly fst: Value;
  public readonly snd: Value;
  public readonly rendered: RenderingRef;

  constructor(fst: Value, snd: Value, ctx: Context) {
    this.fst = fst;
    this.snd = snd;
    this.rendered = reactiveRendering(ctx, () => [
      Precedence.Atom,
      '(',
      Precedence.Low,
      fst,
      ', ',
      Precedence.Low,
      snd,
      ')',
    ]);
  }
}

export class Closure implements Value {
  public readonly rendered: RenderingRef;
  public readonly env: Environment;
  public readonly paramId: Identifier;
  public readonly body: Expression;

  constructor(env: Environment, paramId: Identifier, body: Expression, ctx: Context) {
    this.env = env;
    this.paramId = paramId;
    this.body = body;
    this.rendered = reactiveRendering(ctx, () => [
      Precedence.Atom,
      new Rendering('&#x2329;', '\\langle '),
      env,
      ', ',
      paramId,
      ', ',
      body,
      new Rendering('&#x232A;', '\\rangle '),
    ]);
  }
}

export class RecursiveClosure implements Value {
  public readonly rendered: RenderingRef;
  public readonly env: Environment;
  public readonly functionId: Identifier;
  public readonly paramId: Identifier;
  public readonly body: Expression;

  constructor(env: Environment, functionId: Identifier, paramId: Identifier, body: Expression, ctx: Context) {
    this.env = env;
    this.functionId = functionId;
    this.paramId = paramId;
    this.body = body;
    this.rendered = reactiveRendering(ctx, () => [
      Precedence.Atom,
      new Rendering('&#x2329;', '\\langle '),
      env,
      ', ',
      functionId,
      ', ',
      paramId,
      ', ',
      body,
      new Rendering('&#x232A;', '\\rangle '),
    ]);
    if (functionId === paramId) {
      throw new Error(
        `${htmlErrorMessagePrefix}Im rekursiven Funktionsabschluss ${
          this.rendered.value.html
        } dürfen Funktion und Parameter nicht beide ${render(functionId)} heißen.`,
      );
    }
  }
}

// --------------------------------------------------------------------------------------------------------------------
// Values representing the result of addition and multiplication

interface LeftRightValue extends Value {
  readonly left: Value;
  readonly right: Value;
}

function defineNatNatNatValue(
  op: string | Rendering,
  precedence: Precedence,
  fn: (left: bigint, right: bigint) => bigint,
): new (left: Value, right: Value, ctx: Context) => LeftRightValue {
  return class V implements LeftRightValue {
    public readonly left: Value;
    public readonly right: Value;
    public readonly rendered: RenderingRef;
    public readonly simplified: SimplificationRef<Value>;

    constructor(left: Value, right: Value, ctx: Context) {
      this.left = left;
      this.right = right;
      this.rendered = reactiveRendering(ctx, () => [precedence, left, op, precedence + 1, right]);
      this.simplified = reactiveSimplification(ctx, () => {
        const simpleLeft = simplify(left);
        const simpleRight = simplify(right);
        return simpleLeft instanceof NatValue && simpleRight instanceof NatValue
          ? new SuccessfulSimplification<Value>(
              new NatValue(fn(simpleLeft.value, simpleRight.value)),
              new V(simpleLeft, simpleRight, ctx),
            )
          : (isAtomic(simpleLeft) && !(simpleLeft instanceof NatValue)) ||
            (isAtomic(simpleRight) && !(simpleRight instanceof NatValue))
          ? new FailedSimplification(
              htmlErrorMessagePrefix + `Typfehler: Operation '${render(op)}' erwartet zwei natürliche Zahlen!`,
              new V(simpleLeft, simpleRight, ctx),
            )
          : objectHasPendingSimplification;
      });
    }
  };
}
export const AdditionValue = defineNatNatNatValue(' + ', Precedence.PlusMinus, (l, r) => l + r);
export const MultiplicationValue = defineNatNatNatValue(centerDot, Precedence.MultDiv, (l, r) => l * r);

// --------------------------------------------------------------------------------------------------------------------

class ValueMismatchError extends MismatchError {
  constructor(expected: Value, actual: Value, object?: Renderable) {
    super(
      htmlErrorMessagePrefix +
        'Du kannst diese Regel nicht anwenden, da der Wert ' +
        actual.rendered.value.html +
        (object ? ' von ' + object.rendered.value.html : '') +
        ' nicht mit dem erwarteten Wert ' +
        expected.rendered.value.html +
        ' übereinstimmt.',
    );
    this.name = 'ValueMismatchError';
  }
}

/**
 * Check that the two given values are equal, thereby setting `ValueVariable`s as needed
 * @param expected the expected value (passed from below)
 * @param actual the actual value (generated from the rule application)
 * @param object the object being checked
 * @param ctx the context
 * @returns constraints that must be fulfilled for the values to match
 * @throws ValueMismatchError if the given values are not equal
 */
export const valuesMustMatch: (
  expected: Value,
  actual: Value,
  ctx: Context,
  object?: Renderable,
) => ConstraintProvider[] = buildMustMatchFunction(
  translateMismatchError<Value>(ValueMismatchError, (expected, actual, ctx) => {
    if (expected instanceof BasicValue && actual instanceof BasicValue) {
      return expected.value === actual.value;
    }
    if (expected instanceof CharValue && actual instanceof CharValue) {
      return expected.char === actual.char;
    }
    if (expected instanceof Address && actual instanceof Address) {
      return expected.index === actual.index;
    }
    if (expected instanceof PairValue && actual instanceof PairValue) {
      return [...valuesMustMatch(expected.fst, actual.fst, ctx), ...valuesMustMatch(expected.snd, actual.snd, ctx)];
    }
    if (expected instanceof Closure && actual instanceof Closure) {
      return [
        ...identifiersMustMatch(expected.paramId, actual.paramId, ctx),
        ...environmentsMustMatch(expected.env, actual.env, ctx),
        ...expressionsMustMatch(expected.body, actual.body, ctx),
      ];
    }
    if (expected instanceof RecursiveClosure && actual instanceof RecursiveClosure) {
      return [
        ...identifiersMustMatch(expected.functionId, actual.functionId, ctx),
        ...identifiersMustMatch(expected.paramId, actual.paramId, ctx),
        ...environmentsMustMatch(expected.env, actual.env, ctx),
        ...expressionsMustMatch(expected.body, actual.body, ctx),
      ];
    }
    return false;
  }),
  translateMismatchError<Value, undefined>(
    ValueMismatchError,
    (v1, v2, ctx) => {
      if (v1 instanceof NatValue && v2 instanceof AdditionValue) {
        if (v1.value === 0n) {
          return [...valuesMustMatch(v2.left, zeroValue, ctx), ...valuesMustMatch(v2.right, zeroValue, ctx)];
        }
        for (let [x, y] of [
          [v2.left, v2.right],
          [v2.right, v2.left],
        ]) {
          x = simplify(x);
          if (x instanceof NatValue) {
            return v1.value < x.value ? false : valuesMustMatch(y, new NatValue(v1.value - x.value), ctx);
          }
        }
      }
      return undefined;
    },
    true,
  ),
);

const identifiersMustMatch: (expected: Identifier, actual: Identifier, ctx: Context) => ConstraintProvider[] =
  buildMustMatchFunction(() => {
    throw new MismatchError();
  });

const expressionsMustMatch: (expected: Expression, actual: Expression, ctx: Context) => ConstraintProvider[] =
  buildMustMatchFunction((expected, actual, ctx) => {
    if (
      (expected instanceof FalseLiteral && actual instanceof FalseLiteral) ||
      (expected instanceof TrueLiteral && actual instanceof TrueLiteral) ||
      (expected instanceof Unit && actual instanceof Unit) ||
      (expected instanceof NatLiteral && actual instanceof NatLiteral && expected.value === actual.value) ||
      (expected instanceof CharLiteral && actual instanceof CharLiteral && expected.char === actual.char) ||
      (expected instanceof IdentifierExpression && actual instanceof IdentifierExpression && expected.id === actual.id)
    ) {
      return [];
    }
    if (
      (expected instanceof LT && actual instanceof LT) ||
      (expected instanceof LEQ && actual instanceof LEQ) ||
      (expected instanceof EQ && actual instanceof EQ) ||
      (expected instanceof NEQ && actual instanceof NEQ) ||
      (expected instanceof GEQ && actual instanceof GEQ) ||
      (expected instanceof GT && actual instanceof GT) ||
      (expected instanceof Addition && actual instanceof Addition) ||
      (expected instanceof Monus && actual instanceof Monus) ||
      (expected instanceof Multiplication && actual instanceof Multiplication) ||
      (expected instanceof Division && actual instanceof Division) ||
      (expected instanceof Modulo && actual instanceof Modulo)
    ) {
      return [
        ...expressionsMustMatch(expected.left, actual.left, ctx),
        ...expressionsMustMatch(expected.right, actual.right, ctx),
      ];
    }
    if (expected instanceof IfThenElse && actual instanceof IfThenElse) {
      return [
        ...expressionsMustMatch(expected.condition, actual.condition, ctx),
        ...expressionsMustMatch(expected.thenExpr, actual.thenExpr, ctx),
        ...expressionsMustMatch(expected.elseExpr, actual.elseExpr, ctx),
      ];
    }
    if (expected instanceof InExpression && actual instanceof InExpression) {
      return [
        ...declarationsMustMatch(expected.decl, actual.decl, ctx),
        ...expressionsMustMatch(expected.body, actual.body, ctx),
      ];
    }
    if (expected instanceof FunctionApplication && actual instanceof FunctionApplication) {
      return [
        ...expressionsMustMatch(expected.fun, actual.fun, ctx),
        ...expressionsMustMatch(expected.arg, actual.arg, ctx),
      ];
    }
    if (expected instanceof LambdaExpression && actual instanceof LambdaExpression && expected.param === actual.param) {
      return expressionsMustMatch(expected.body, actual.body, ctx);
    }
    if (expected instanceof Pair && actual instanceof Pair) {
      return [
        ...expressionsMustMatch(expected.fst, actual.fst, ctx),
        ...expressionsMustMatch(expected.snd, actual.snd, ctx),
      ];
    }
    if (expected instanceof Dereference && actual instanceof Dereference) {
      return expressionsMustMatch(expected.addr, actual.addr, ctx);
    }
    if (expected instanceof Assignment && actual instanceof Assignment) {
      return [
        ...expressionsMustMatch(expected.addr, actual.addr, ctx),
        ...expressionsMustMatch(expected.expr, actual.expr, ctx),
      ];
    }
    throw new MismatchError();
  });

const declarationsMustMatch: (expected: Declaration, actual: Declaration, ctx: Context) => ConstraintProvider[] =
  buildMustMatchFunction((expected, actual, ctx) => {
    if (expected instanceof LetBinding && actual instanceof LetBinding && expected.id === actual.id) {
      return expressionsMustMatch(expected.expr, actual.expr, ctx);
    }
    if (
      ((expected instanceof FunctionDefinition && actual instanceof FunctionDefinition) ||
        (expected instanceof RecursiveFunctionDefinition && actual instanceof RecursiveFunctionDefinition)) &&
      expected.id === actual.id &&
      expected.param === actual.param
    ) {
      return expressionsMustMatch(expected.body, actual.body, ctx);
    }
    throw new MismatchError();
  });

export const {
  BasicMapping: BasicEnvironment,
  CommaOperatorMapping: CommaOperatorEnvironment,
  MappingVariable: EnvironmentVariable,
  LookupResult: EnvironmentLookupResult,
  mappingMustContainKey: environmentMustContainId,
  mappingsMustMatch: environmentsMustMatch,
} = defineMappingClasses<Identifier, Value>(
  '&delta;',
  '\\delta',
  'die Umgebung',
  'in der Umgebung',
  'der Bezeichner',
  valuesMustMatch,
);
