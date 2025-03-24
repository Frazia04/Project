import type { Context } from '../../context';
import { Precedence, reactiveRendering, type Renderable, Rendering, type RenderingRef } from '../../rendering';
import type { Type } from '../../semantics/static';
import type { Declaration, Expression } from '..';
import { expressionParameter as e, typeParameter as t } from '../construction';

export class FunctionDefinition implements Declaration {
  public static readonly constructorParams = ['f', 'x', t, t, e] as const;
  public readonly rendered: RenderingRef;
  public readonly id: string;
  public readonly param: string;
  public readonly argType?: Type;
  public readonly resultType?: Type;
  public readonly body: Expression;

  constructor(
    id: string,
    param: string,
    argType: Type | undefined,
    resultType: Type | undefined,
    body: Expression,
    ctx: Context,
  ) {
    this.id = id;
    this.param = param;
    this.argType = argType;
    this.resultType = resultType;
    this.body = body;
    this.rendered = reactiveRendering(ctx, () => {
      const spec: [Precedence, ...(string | Renderable)[]] = [Precedence.Atom, 'let ', id, ' '];
      if (argType === undefined) {
        spec.push(param);
      } else {
        spec.push(' (', param, ': ', argType, ')');
      }
      if (resultType !== undefined) {
        spec.push(': ', resultType);
      }
      spec.push(' = ', body);
      return spec;
    });
  }
}

// Note: We use FunctionApplication also for executing built-in functions.
// Then the `fun` is an `IdentifierExpression` with one of: fst, snd, getchar, putchar, ref, raise
export class FunctionApplication implements Expression {
  public static readonly constructorParams = [e, e] as const;
  public readonly rendered: RenderingRef;
  public readonly fun: Expression;
  public readonly arg: Expression;

  constructor(fun: Expression, arg: Expression, ctx: Context) {
    this.fun = fun;
    this.arg = arg;
    this.rendered = reactiveRendering(ctx, () => [Precedence.Application, fun, ' ', Precedence.Atom, arg]);
  }
}

export class LambdaExpression implements Expression {
  public static readonly constructorParams = ['x', t, e] as const;
  public readonly rendered: RenderingRef;
  public readonly param: string;
  public readonly argType?: Type;
  public readonly body: Expression;

  constructor(param: string, argType: Type | undefined, body: Expression, ctx: Context) {
    this.param = param;
    this.argType = argType;
    this.body = body;
    this.rendered = reactiveRendering(ctx, () => {
      const spec: [Precedence, ...(string | Rendering | Renderable)[]] = [Precedence.Low, 'fun '];
      if (argType === undefined) {
        spec.push(param);
      } else {
        spec.push(' (', param, ': ', argType, ')');
      }
      spec.push(new Rendering(' &rarr; ', ' \\to '), body);
      return spec;
    });
  }
}

export class RecursiveFunctionDefinition implements Declaration {
  public static readonly constructorParams = ['f', 'x', t, t, e] as const;
  public readonly rendered: RenderingRef;
  public readonly id: string;
  public readonly param: string;
  public readonly argType?: Type;
  public readonly resultType?: Type;
  public readonly body: Expression;

  constructor(
    id: string,
    param: string,
    argType: Type | undefined,
    resultType: Type | undefined,
    body: Expression,
    ctx: Context,
  ) {
    this.id = id;
    this.param = param;
    this.argType = argType;
    this.resultType = resultType;
    this.body = body;
    this.rendered = reactiveRendering(ctx, () => {
      const spec: [Precedence, ...(string | Renderable)[]] = [Precedence.Atom, 'let rec ', id, ' '];
      if (argType === undefined) {
        spec.push(param);
      } else {
        spec.push(' (', param, ': ', argType, ')');
      }
      if (resultType !== undefined) {
        spec.push(': ', resultType);
      }
      spec.push(' = ', body);
      return spec;
    });
  }
}
