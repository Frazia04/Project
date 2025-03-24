import type { Context } from '../context';
import { simplify } from '../simplify';
import {
  type Declaration,
  DeclarationVariable,
  type Expression,
  ExpressionVariable,
  FunctionApplication,
  IdentifierExpression,
} from '../syntax';
import {
  buildPlaceholderQueriedArgs,
  type ConstructorWithParameterTypeDescriptors,
  constructSyntaxElement,
  interactiveQueryConstructorArguments,
  type QueriedConstructorParameters,
  type SupportedParams,
} from '../syntax/construction';
import type { ConstraintProvider } from './constraints';
import {
  DeclarationRuleNotApplicableError,
  ExpressionRuleNotApplicableError,
  type Rule,
  type RuleApplicationResult,
  type RuleRenderResult,
} from './rules';
import type { DeclarationStatement, ExpressionStatement } from './statements';

export type ExprRuleBuilder<ES extends ExpressionStatement, DS extends DeclarationStatement> = <
  T extends ConstructorWithParameterTypeDescriptors<P, R>,
  P extends SupportedParams = ConstructorParameters<T>,
  R extends Expression = InstanceType<T>,
>(
  ruleId: string,
  ruleName: string,
  exprCtor: T,
  cb: (expr: R, stmt: ES, ctx: Context) => [(ES | DS)[], ConstraintProvider[]],
) => Rule<ES | DS>;

export type DeclRuleBuilder<ES extends ExpressionStatement, DS extends DeclarationStatement> = <
  T extends ConstructorWithParameterTypeDescriptors<P, R>,
  P extends SupportedParams = ConstructorParameters<T>,
  R extends Declaration = InstanceType<T>,
>(
  ruleId: string,
  ruleName: string,
  declCtor: T,
  cb: (decl: R, stmt: DS, ctx: Context) => [(ES | DS)[], ConstraintProvider[]],
) => Rule<ES | DS>;

export type BuiltInFunctionApplicationRuleBuilder<ES extends ExpressionStatement, DS extends DeclarationStatement> = (
  ruleId: string,
  ruleName: string,
  functionName: string,
  cb: (expr: FunctionApplication, stmt: ES, ctx: Context) => [(ES | DS)[], ConstraintProvider[]],
) => Rule<ES | DS>;

export default function ruleBuilders<ES extends ExpressionStatement, DS extends DeclarationStatement>(
  exprStmtCtor: new (...args: any[]) => ES,
  declStmtCtor: new (...args: any[]) => DS,
  constructExprStmt: (expr: Expression, ctx: Context) => ES,
  constructDeclStmt: (decl: Declaration, ctx: Context) => DS,
): {
  buildExprRule: ExprRuleBuilder<ES, DS>;
  buildDeclRule: DeclRuleBuilder<ES, DS>;
  buildBuildInFunctionApplicationRule: BuiltInFunctionApplicationRuleBuilder<ES, DS>;
} {
  return {
    buildExprRule: <P extends SupportedParams, R extends Expression>(
      ruleId: string,
      ruleName: string,
      exprCtor: ConstructorWithParameterTypeDescriptors<P, R>,
      cb: (expr: R, stmt: ES, ctx: Context) => [(ES | DS)[], ConstraintProvider[]],
    ) => ({
      ruleId,
      ruleName,
      supportsExpr: [exprCtor],
      supportsDecl: [],
      apply: async function (
        stmt: ES | DS,
        ctx: Context,
        previousArgs?: unknown[],
        argsUntrusted = false,
      ): Promise<RuleApplicationResult<ES | DS>> {
        if (stmt instanceof exprStmtCtor) {
          const expr = simplify(stmt.expr);
          if (expr instanceof exprCtor) {
            return ruleApplicationResult(...cb(expr, stmt, ctx));
          }
          if (expr instanceof ExpressionVariable) {
            const args = await getArgs(exprCtor, previousArgs, argsUntrusted);
            const createdExpr = constructSyntaxElement(exprCtor, ctx, args);
            expr.set(createdExpr, ctx);
            return ruleApplicationResult(...cb(createdExpr, stmt, ctx), args as unknown as unknown[]);
          }
          throw new ExpressionRuleNotApplicableError(stmt.expr);
        }
        throw new DeclarationRuleNotApplicableError((stmt as DS).decl);
      },
      render: (ctx: Context): RuleRenderResult<ES | DS> => {
        const expr = constructSyntaxElement(
          exprCtor,
          ctx,
          buildPlaceholderQueriedArgs<typeof exprCtor, P, R>(exprCtor),
        );
        const stmt = constructExprStmt(expr, ctx);
        return ruleRenderResult(stmt, ...cb(expr, stmt, ctx));
      },
    }),

    buildDeclRule: <P extends SupportedParams, R extends Declaration>(
      ruleId: string,
      ruleName: string,
      declCtor: ConstructorWithParameterTypeDescriptors<P, R>,
      cb: (decl: R, stmt: DS, ctx: Context) => [(ES | DS)[], ConstraintProvider[]],
    ) => ({
      ruleId,
      ruleName,
      supportsExpr: [],
      supportsDecl: [declCtor],
      apply: async function apply(
        stmt: ES | DS,
        ctx: Context,
        previousArgs?: unknown[],
        argsUntrusted = false,
      ): Promise<RuleApplicationResult<ES | DS>> {
        if (stmt instanceof declStmtCtor) {
          const decl = simplify(stmt.decl);
          if (decl instanceof declCtor) {
            return ruleApplicationResult(...cb(decl, stmt, ctx));
          }
          if (decl instanceof DeclarationVariable) {
            const args = await getArgs(declCtor, previousArgs, argsUntrusted);
            const createdDecl = constructSyntaxElement(declCtor, ctx, args);
            decl.set(createdDecl, ctx);
            return ruleApplicationResult(...cb(createdDecl, stmt, ctx), args as unknown as unknown[]);
          }
          throw new DeclarationRuleNotApplicableError(stmt.decl);
        }
        throw new ExpressionRuleNotApplicableError((stmt as ES).expr);
      },
      render: (ctx: Context): RuleRenderResult<ES | DS> => {
        const decl = constructSyntaxElement(
          declCtor,
          ctx,
          buildPlaceholderQueriedArgs<typeof declCtor, P, R>(declCtor),
        );
        const stmt = constructDeclStmt(decl, ctx);
        return ruleRenderResult(stmt, ...cb(decl, stmt, ctx));
      },
    }),

    buildBuildInFunctionApplicationRule: (
      ruleId: string,
      ruleName: string,
      functionName: string,
      cb: (expr: FunctionApplication, stmt: ES, ctx: Context) => [(ES | DS)[], ConstraintProvider[]],
    ) => ({
      ruleId,
      ruleName,
      supportsExpr: [FunctionApplication],
      supportsDecl: [],
      // eslint-disable-next-line @typescript-eslint/require-await
      apply: async function (stmt: ES | DS, ctx: Context): Promise<RuleApplicationResult<ES | DS>> {
        if (stmt instanceof exprStmtCtor) {
          const expr = simplify(stmt.expr);
          if (expr instanceof FunctionApplication) {
            const fun = simplify(expr.fun);
            if (fun instanceof ExpressionVariable) {
              fun.set(new IdentifierExpression(functionName), ctx);
            } else if (!(fun instanceof IdentifierExpression && fun.id === functionName)) {
              throw new ExpressionRuleNotApplicableError(expr);
            }
            return ruleApplicationResult(...cb(expr, stmt, ctx));
          }
          if (expr instanceof ExpressionVariable) {
            const createdExpr = new FunctionApplication(
              new IdentifierExpression(functionName),
              new ExpressionVariable(ctx),
              ctx,
            );
            expr.set(createdExpr, ctx);
            return ruleApplicationResult(...cb(createdExpr, stmt, ctx));
          }
          throw new ExpressionRuleNotApplicableError(stmt.expr);
        }
        throw new DeclarationRuleNotApplicableError((stmt as DS).decl);
      },
      render: (ctx: Context): RuleRenderResult<ES | DS> => {
        const expr = new FunctionApplication(new IdentifierExpression(functionName), new ExpressionVariable(ctx), ctx);
        const stmt = constructExprStmt(expr, ctx);
        return ruleRenderResult(stmt, ...cb(expr, stmt, ctx));
      },
    }),
  };
}

function ruleApplicationResult<T>(
  premises: T[],
  constraintProviders: ConstraintProvider[],
  args?: unknown[],
): RuleApplicationResult<T> {
  return {
    premises,
    constraints: constraintProviders.map((provider) => provider()),
    args,
  };
}

function ruleRenderResult<T>(
  conclusion: T,
  premises: T[],
  constraintProviders: ConstraintProvider[],
): RuleRenderResult<T> {
  return {
    conclusion,
    premises,
    constraints: constraintProviders.map((provider) => provider()),
  };
}

async function getArgs<P extends SupportedParams, R>(
  ctor: ConstructorWithParameterTypeDescriptors<P, R>,
  previousArgs: unknown[] | undefined,
  argsUntrusted?: boolean,
): Promise<QueriedConstructorParameters<ConstructorWithParameterTypeDescriptors<P, R>, P, R>> {
  if (previousArgs) {
    if (argsUntrusted) {
      // TODO: verify args
    }
    return previousArgs as unknown as QueriedConstructorParameters<ConstructorWithParameterTypeDescriptors<P, R>, P, R>;
  }
  return await interactiveQueryConstructorArguments<typeof ctor, P, R>(ctor);
}
