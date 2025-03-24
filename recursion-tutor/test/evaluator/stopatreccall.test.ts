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
    test.skip('evaluate RecFunApp', () => {
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

        var expectedOutput = new Environment();
        // maybe change Result to a different kind of Context, one that has a field "parameterOfRecCall" because thats what we need for the diagram

        evaluate(expr, env, true)
        //expect(evaluate(expr, context, true)).toBe(120);
    })
});