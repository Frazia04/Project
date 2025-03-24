import { describe, expect, test } from '@jest/globals';
import {
    FunctionClosure,
    Identifier,
    Environment,
    evaluate,
    Parameter
} from '../../src/evaluator';
import { applyFunAppExpr } from '../../src/parser/parser';

describe('Test Evaluator 4', () => {
    test('evaluate FunApp', () => {
        var expr = applyFunAppExpr({
                kind: 'IdentifierExpr',
                id: 'twoTimes'
            }, {
                kind: 'IdentifierExpr',
                id: 'n'
            }
        )


        var env = new Environment();
        env.addOrUpdateBinding(
            'twoTimes',
            new FunctionClosure(
                'twoTimes',
                new Parameter('x', { kind: 'PrimitiveType', name: 'bigint'}), 
                { 
                    kind: 'MultiplicationExpr', 
                    left: { 
                        kind: 'NatLiteralExpr', 
                        value: 2n
                    }, 
                    right: { 
                        kind: 'IdentifierExpr', 
                        id: 'x'
                    }
                }
            )
        )
        env.addOrUpdateBinding('n', 21n);
        expect(evaluate(expr, env, false)).toBe(42n);
    })
});