import { describe, expect, test } from '@jest/globals';
import { expectEOF, expectSingleResult } from 'typescript-parsec';
import type * as ast from '../src/parser/ast';
import { DECL, EXPR, PROGRAM, TYPE } from '../src/parser/rules';
import { lexer } from '../src/parser/tok';

describe('Test Parser', () => {
    test(`Fibonacci`, () => {
        const input = `
    let rec fibonacci (n: Nat): Nat = 
        match n with
        | 0 -> n
        | 1 -> n
        | n -> fibonacci (n - 1) + fibonacci (n - 2)
    `;
    
        const output: ast.Program = {
            declarations: 
            [
                {
                    kind: 'FunctionDecl',
                    name: 'fibonacci',
                    toDebug: false,
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
                        kind: 'MatchWithExpr',
                        expr: {
                            kind: 'IdentifierExpr',
                            id: 'n'
                        },
                        rules: 
                        [
                            {
                                kind: 'Rule',
                                pattern: {
                                    kind: 'ConstPattern',
                                    value: {
                                        kind: 'NatLiteralExpr',
                                        value: 0n
                                    }
                                },
                                expr: {
                                    kind: 'IdentifierExpr',
                                    id: 'n'
                                }
                            },
                            {
                                kind: 'Rule',
                                pattern: {
                                    kind: 'ConstPattern',
                                    value: {
                                        kind: 'NatLiteralExpr',
                                        value: 1n
                                    }
                                },
                                expr: {
                                    kind: 'IdentifierExpr',
                                    id: 'n'
                                }
                            },
                            {
                                kind: 'Rule',
                                pattern: {
                                    kind: 'NamedPattern',
                                    name: {
                                        kind: 'IdentifierExpr',
                                        id: 'n'
                                    }
                                },
                                expr: {
                                    kind: 'AdditionExpr',
                                    left: {
                                        kind: 'FunAppExpr',
                                        fun: {
                                            kind: 'IdentifierExpr',
                                            id: 'fibonacci'
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
                                    },
                                    right: {
                                        kind: 'FunAppExpr',
                                        fun: {
                                            kind: 'IdentifierExpr',
                                            id: 'fibonacci'
                                        },
                                        arg: {
                                            kind: 'SubtractionExpr',
                                            left: {
                                                kind: 'IdentifierExpr',
                                                id: 'n'
                                            },
                                            right: {
                                                kind: 'NatLiteralExpr',
                                                value: 2n
                                            }
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


