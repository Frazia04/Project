import { IdentifierExpression, InExpression, LetBinding } from '../../../syntax';
import type { Rule } from '../../rules';
import { buildDeclRule, buildExprRule } from '../ruleBuilders';
import type { StaticSemanticsStatement } from '../statements';
import { StaticSemanticsDeclarationStatement, StaticSemanticsExpressionStatement } from '../statements';
import { BasicSignature, CommaOperatorSignature, SignatureLookupResult, signatureMustContainId, signaturesMustMatch, SignatureVariable, typesMustMatch, TypeVariable } from '../types';

const rules: Rule<StaticSemanticsStatement>[] = [
  buildDeclRule('LetBinding', 'LetBinding', LetBinding, (decl, { sig, resultSig }, ctx) => {
    const type = new TypeVariable(ctx);
    return [
      [new StaticSemanticsExpressionStatement(sig, decl.expr, type, ctx)],
      signaturesMustMatch(resultSig, new BasicSignature([[decl.id, type]], ctx), ctx),
    ];
  }),

  buildExprRule('IdentifierExpression', 'IdentifierExpression', IdentifierExpression, (expr, { type, sig }, ctx) => [
    [],
    [
      ...typesMustMatch(type, new SignatureLookupResult(sig, expr.id, ctx), ctx),
      ...signatureMustContainId(sig, expr.id, ctx),
    ],
  ]),

  buildExprRule('InExpression', 'InExpression', InExpression, (expr, { sig, type }, ctx) => {
    const sig2 = new SignatureVariable(ctx);
    return [
      [
        new StaticSemanticsDeclarationStatement(sig, expr.decl, sig2, ctx),
        new StaticSemanticsExpressionStatement(new CommaOperatorSignature(sig, sig2, ctx), expr.body, type, ctx),
      ],
      [],
    ];
  }),
];

export default rules;
