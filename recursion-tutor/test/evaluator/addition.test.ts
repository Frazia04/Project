import { describe, expect, test } from '@jest/globals';
import {
    Environment,
    evaluate
} from '../../src/evaluator';
import { applyAdditionExpr, applyIdentifierExpr } from '../../src/parser/parser';

describe('Test Evaluator', () => {
    test('evaluateAddition', () => {
        var expr = applyAdditionExpr(
            applyIdentifierExpr('n'), 
            {
                kind: 'NatLiteralExpr',
                value: 2n
            }
        )

        var env = new Environment();
        env.addOrUpdateBinding('n', 40n);
        expect(evaluate(expr, env, false)).toBe(42n)
    })
});

