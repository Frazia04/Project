import { describe, expect, test } from '@jest/globals';
import {
    evaluate,
    Environment
} from '../../src/evaluator';
import { applyEqualExpr } from '../../src/parser/parser';

describe('Test Evaluator 2', () => {
    test('evaluateEqual', () => {
        var expr = applyEqualExpr({
            kind: 'IdentifierExpr',
            id: 'n'
        }, {
            kind: 'NatLiteralExpr',
            value: 42n
        })

        var env = new Environment();
        env.addOrUpdateBinding('n', 42n);
        expect(evaluate(expr, env, false)).toBe(true);
    })
});