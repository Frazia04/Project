import { describe, expect, test } from '@jest/globals';
import { expectEOF, expectSingleResult } from 'typescript-parsec';
import type * as ast from '../src/parser/ast';
import { DECL, EXPR, PROGRAM, TYPE } from '../src/parser/rules';
import { lexer } from '../src/parser/tok';

describe('Test Parser', () => {
    test.skip(`Sum`, () => {
        const input = `
    let rec sum (xs: List<Nat>): Nat = 
        match xs with
        | []    -> 0
        | y::ys -> y + sum ys
    `;
    
        const output: ast.Program = {
            declarations: 
            [
                {
                    kind: 'FunctionDecl',
                    name: 'sum',
                    toDebug: false,
                    returnType: {
                        kind: 'PrimitiveType',
                        name: 'bigint'
                    },
                    parameter: {
                        name: 'xs',
                        paramType: {
                            kind: 'ListType',
                            elementType: {
                                kind: 'PrimitiveType',
                                name: 'bigint'
                            }
                        }
                    },
                    expr: {
                        kind: 'MatchWithExpr',
                        expr: {
                            kind: 'IdentifierExpr',
                            id: 'xs'
                        },
                        rules: 
                        [
                            {
                                kind: 'Rule',
                                pattern: {
                                    kind: 'ListPattern',
                                    emptyList: true,
                                    elements: []
                                },
                                expr: {
                                    kind: 'NatLiteralExpr',
                                    value: 0n
                                }
                            },{
                                kind: 'Rule',
                                pattern: {
                                    kind: 'ColonColonPattern',
                                    elem: {
                                        kind: 'NamedPattern',
                                        name: {
                                            kind: 'IdentifierExpr',
                                            id: 'y'
                                        }
                                    },
                                    rest: {
                                        kind: 'NamedPattern',
                                        name: {
                                            kind: 'IdentifierExpr',
                                            id: 'ys'
                                        }
                                    }
                                },
                                expr: {
                                    kind: 'AdditionExpr',
                                    left: {
                                        kind: 'IdentifierExpr',
                                        id: 'y'
                                    },
                                    right: {
                                        kind: 'FunAppExpr',
                                        fun: {
                                            kind: 'IdentifierExpr',
                                            id: 'sum'
                                        },
                                        arg: {
                                            kind: 'IdentifierExpr',
                                            id: 'ys'
                                        }
                                    }
                                }
                            }
                        ]
                    }
                }
            ]
        }
    
        expect(expectSingleResult(expectEOF(PROGRAM.parse(lexer.parse(input))))).toStrictEqual(output);
    });
})


