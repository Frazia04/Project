import { describe, expect, test } from '@jest/globals';
import {
    Environment,
    evaluate
} from '../../src/evaluator';
import { assert } from 'console';
import { applyEqualExpr, applyIdentifier, applyIdentifierExpr, applyIfThenElseExpr, applySubtractionExpr } from '../../src/parser/parser';

describe('Test Evaluator 3', () => {
    test('evaluate IfThenElse, Equal, Subtraction, Identifier, NatLiteral', () => {
        var expr = applyIfThenElseExpr([
            applyEqualExpr(
                applySubtractionExpr(
                    applyIdentifierExpr('n'), { 
                        kind: 'NatLiteralExpr',
                        value: 2n
                    }
                ), {
                    kind: 'IdentifierExpr',
                    id: 'x'
                }
            ), {
                kind: 'NatLiteralExpr',
                value: 1n
            },{
                kind: 'NatLiteralExpr',
                value: 0n
            }
        ])

        var env = new Environment();
        env.addOrUpdateBinding('n', 44n);
        env.addOrUpdateBinding('x', 42n);
        expect(evaluate(expr, env, false)).toBe(1n);
    })
});