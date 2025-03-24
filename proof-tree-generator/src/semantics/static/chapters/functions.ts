import { FunctionApplication, FunctionDefinition, LambdaExpression, RecursiveFunctionDefinition } from '../../../syntax';
import type { Rule } from '../../rules';
import { buildDeclRule, buildExprRule } from '../ruleBuilders';
import type { StaticSemanticsStatement } from '../statements';
import { StaticSemanticsExpressionStatement } from '../statements';
import { BasicSignature, CommaOperatorSignature, FunctionType, signaturesMustMatch, typesMustMatch, TypeVariable } from '../types';

const rules: Rule<StaticSemanticsStatement>[] = [
  buildDeclRule('FunctionDefinition', 'FunctionDefinition', FunctionDefinition, (decl, { sig, resultSig }, ctx) => [
    [
      new StaticSemanticsExpressionStatement(new CommaOperatorSignature(sig, new BasicSignature([[decl.param, decl.argType!]], ctx), ctx), decl.body, decl.resultType!, ctx),
    ],
    signaturesMustMatch(resultSig, new BasicSignature([[decl.id, new FunctionType(decl.argType!, decl.resultType!, ctx)]], ctx), ctx),
  ]),

  buildExprRule('FunctionApplication', 'FunctionApplication', FunctionApplication, (expr, { sig, type }, ctx) => {
    const argType = new TypeVariable(ctx);
    return [
      [
        new StaticSemanticsExpressionStatement(sig, expr.fun, new FunctionType(argType, type, ctx), ctx),
        new StaticSemanticsExpressionStatement(sig, expr.arg, argType, ctx),
      ],
      [],
    ];
  }),

  buildExprRule('LambdaExpression', 'LambdaExpression', LambdaExpression, (expr, { sig, type }, ctx) => {
    const resultType = new TypeVariable(ctx);
    return [
      [
        new StaticSemanticsExpressionStatement(new CommaOperatorSignature(sig, new BasicSignature([[expr.param, expr.argType!]], ctx), ctx), expr.body, resultType, ctx),
      ],
      typesMustMatch(type, new FunctionType(expr.argType!, resultType, ctx), ctx, expr),
    ];
  }),

  buildDeclRule('RecursiveFunctionDefinition', 'RecursiveFunctionDefinition', RecursiveFunctionDefinition, (decl, { sig, resultSig }, ctx) => {
    const funType = new FunctionType(decl.argType!, decl.resultType!, ctx);
    return [
      [
        new StaticSemanticsExpressionStatement(
          new CommaOperatorSignature(
            sig,
            new BasicSignature(
              [
                [decl.id, funType],
                [decl.param, decl.argType!],
              ],
              ctx,
            ),
            ctx,
          ),
          decl.body,
          decl.resultType!,
          ctx,
        ),
      ],
      signaturesMustMatch(resultSig, new BasicSignature([[decl.id, funType]], ctx), ctx),
    ];
  }),
];

export default rules;
