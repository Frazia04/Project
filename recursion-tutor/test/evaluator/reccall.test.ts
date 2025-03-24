import { describe, expect, test } from '@jest/globals';
import {
    FunctionClosure,
    Environment,
    evaluate,
    Parameter
} from '../../src/evaluator';
import { applyFunAppExpr } from '../../src/parser/parser';
import { logExpr } from '../../src/parser/utils'

describe('Test Evaluator 5', () => {
    test('evaluate RecFunApp', () => {
        var expr = applyFunAppExpr({
                kind: 'IdentifierExpr',
                id: 'factorial'
            }, {
                kind: 'NatLiteralExpr',
                value: 5n
            }
        )

        //logExpr(expr);

        var env = new Environment();
        env.setFunctionToDebug('factorial');

        env.addOrUpdateBinding(
            'factorial',
            new FunctionClosure(
                'factorial',
                new Parameter('n', { kind: 'PrimitiveType', name: 'bigint'}), 
                {
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
            )
        )

        expect(evaluate(expr, env, false)).toBe(120n);
    })
});