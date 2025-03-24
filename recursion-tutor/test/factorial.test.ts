import { describe, expect, test } from '@jest/globals';
import { expectEOF, expectSingleResult } from 'typescript-parsec';
import type * as ast from '../src/parser/ast';
import { DECL, EXPR, PROGRAM, TYPE } from '../src/parser/rules';
import { lexer } from '../src/parser/tok';

describe('Test Parser', () => {
    test(`Factorial`, () => {
        const input = `
    let tracerec factorial (n: Nat): Nat =
        if n = 0 then 1
        else n * factorial (n - 1)
    `;
    
        const output: ast.Program = {
            declarations: 
            [
                {
                    kind: 'FunctionDecl',
                    name: 'factorial',
                    toDebug: true,
                    returnType: {
                        kind: 'PrimitiveType',
                        name: 'bigint'
                    },
                    parameter: {
                        name: 'n',
                        paramType: {
                            kind: 'PrimitiveType',
                            name: 'bigint'
                        }
                    },
                    expr: {
                        kind: 'IfThenElseExpr',
                        if: {
                            kind: 'EqualExpr',
                            left: {
                                kind: 'IdentifierExpr',
                                id: 'n'
                            },
                            right: {
                                kind: 'NatLiteralExpr',
                                value: 0n
                            }
                        },
                        then: {
                            kind: 'NatLiteralExpr',
                            value: 1n
                        },
                        else: {
                            kind: 'MultiplicationExpr',
                            left: {
                                kind: 'IdentifierExpr',
                                id: 'n'
                            },
                            right: {
                                kind: 'FunAppExpr',
                                fun: {
                                    kind: 'IdentifierExpr',
                                    id: 'factorial'
                                },
                                arg: {
                                    kind: 'SubtractionExpr',
                                    left: {
                                        kind: 'IdentifierExpr',
                                        id: 'n'
                                    },
                                    right: {
                                        kind: 'NatLiteralExpr',
                                        value: 1n
                                    } 
                                }
                            }
                        }   
                    }
                }
            ]
        }
    
        expect(expectSingleResult(expectEOF(PROGRAM.parse(lexer.parse(input))))).toStrictEqual(output);
    });
})


