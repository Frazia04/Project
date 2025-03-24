import ruleBuilders from '../ruleBuilders';
import { ExternalEffectVariable } from './externalEffects';
import { DynamicSemanticsDeclarationStatement, DynamicSemanticsExpressionStatement } from './statements';
import { StoreVariable } from './stores';
import { EnvironmentVariable, ValueVariable } from './values';

export const { buildExprRule, buildDeclRule, buildBuildInFunctionApplicationRule } = ruleBuilders(
  DynamicSemanticsExpressionStatement,
  DynamicSemanticsDeclarationStatement,
  (expr, ctx) =>
    new DynamicSemanticsExpressionStatement(
      new EnvironmentVariable(ctx),
      new StoreVariable(ctx),
      expr,
      new ExternalEffectVariable(ctx),
      new ValueVariable(ctx),
      new StoreVariable(ctx),
      ctx,
    ),
  (decl, ctx) =>
    new DynamicSemanticsDeclarationStatement(
      new EnvironmentVariable(ctx),
      new StoreVariable(ctx),
      decl,
      new ExternalEffectVariable(ctx),
      new EnvironmentVariable(ctx),
      new StoreVariable(ctx),
      ctx,
    ),
);
