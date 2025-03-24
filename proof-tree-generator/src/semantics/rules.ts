import type { Context } from '../context';
import { htmlErrorMessagePrefix } from '../frontend/errors';
import type { Declaration, Expression } from '../syntax';
import type { Constraint } from './constraints';
import type { Statement } from './statements';

export interface Rule<T> {
  readonly ruleId: string;
  readonly ruleName: string;
  readonly supportsExpr: (new (...args: any) => Expression)[];
  readonly supportsDecl: (new (...args: any) => Declaration)[];
  readonly apply: (
    stmt: T,
    ctx: Context,
    args?: unknown[],
    argsUntrusted?: boolean,
  ) => Promise<RuleApplicationResult<T>>;
  readonly render: (ctx: Context) => RuleRenderResult<T>;
}

export interface RuleApplicationResult<T> {
  readonly premises: T[];
  readonly constraints: Constraint[];
  readonly args?: unknown[];
}

export interface RuleRenderResult<T> {
  readonly conclusion: T;
  readonly premises: T[];
  readonly constraints: Constraint[];
}

export class ExpressionRuleNotApplicableError extends Error {
  constructor(expr: Expression) {
    super(`${htmlErrorMessagePrefix}Diese Regel passt nicht zum Ausdruck ${expr.rendered.value.html}.`);
    this.name = 'ExpressionRuleNotApplicableError';
  }
}

export class DeclarationRuleNotApplicableError extends Error {
  constructor(decl: Declaration) {
    super(`${htmlErrorMessagePrefix}Diese Regel passt nicht zur Deklaration ${decl.rendered.value.html}.`);
    this.name = 'DeclarationRuleNotApplicableError';
  }
}

// string = chapter name
export type Ruleset<T extends Statement> = (readonly [string, Rule<T>[]])[];
