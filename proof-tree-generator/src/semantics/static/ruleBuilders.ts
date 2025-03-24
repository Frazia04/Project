import ruleBuilders from '../ruleBuilders';
import { StaticSemanticsDeclarationStatement, StaticSemanticsExpressionStatement } from './statements';
import { SignatureVariable, TypeVariable } from './types';

export const { buildExprRule, buildDeclRule, buildBuildInFunctionApplicationRule } = ruleBuilders(
  StaticSemanticsExpressionStatement,
  StaticSemanticsDeclarationStatement,
  (expr, ctx) => new StaticSemanticsExpressionStatement(new SignatureVariable(ctx), expr, new TypeVariable(ctx), ctx),
  (decl, ctx) =>
    new StaticSemanticsDeclarationStatement(new SignatureVariable(ctx), decl, new SignatureVariable(ctx), ctx),
);
