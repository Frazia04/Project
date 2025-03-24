import type { Expression } from "./ast";
import { expectEOF, expectSingleResult } from 'typescript-parsec';
import { EXPR } from './rules';
import { lexer } from './tok';
import { Environment, PendingValue, type Value, Tuple } from "@/evaluator";

export function expressionToString (expr: Expression): string {

    switch (expr.kind) {
        case 'FunAppExpr':
            return expressionToString(expr.fun) + "(" + expr.args.map(arg => expressionToString(arg)).join(", ") + ")";
        case 'IfThenElseExpr':
            return "if " + expressionToString(expr.if) + " then " + expressionToString(expr.then) + " else " + expressionToString(expr.else);
        case 'AdditionExpr':
            return expressionToString(expr.left) + " + " + expressionToString(expr.right);
        case 'SubtractionExpr':
            return expressionToString(expr.left) + " - " + expressionToString(expr.right);
        case 'MultiplicationExpr':
            return expressionToString(expr.left) + " * " + expressionToString(expr.right);
        case 'DivisionExpr':
            return expressionToString(expr.left) + " / " + expressionToString(expr.right);
        case 'ModuloExpr':
            return expressionToString(expr.left) + " % " + expressionToString(expr.right);
        case 'EqualExpr':
            return expressionToString(expr.left) + " = " + expressionToString(expr.right);
        case 'NatLiteralExpr':
            return expr.value.toString();
        case 'TrueLiteralExpr':
            return "true";
        case 'FalseLiteralExpr':
            return "false";
        case 'IdentifierExpr':
            return expr.id;
        default:
            return "";
    }
}

export function logExpr (expr: Expression) {
    console.log(expressionToString(expr));
}

export function stringToExpression (s: string): Expression {
    return expectSingleResult(expectEOF(EXPR.parse(lexer.parse(s))));
}

export function replaceExprBackwards (oldExpr: string, expr: string, newExpr: string): Expression {
    return stringToExpression(oldExpr.replace(expr, newExpr));
}

export function printResult (result: Value | PendingValue | String): String {
    var res = "";
    if (result instanceof PendingValue) {
      result.pendingValue.forEach(pendingValue => {
        res += printResult(pendingValue);
      });
    } else {
      res += result 
    }
    return res;
}

export function longestChild (childEdges: (String | undefined)[]): String | undefined {
    let longestChild: String = "";
    childEdges.forEach(child => {
        if (child != undefined) {
            if (child.length > longestChild.length) longestChild = child;
        }
    })
    return longestChild;
}

export function getTextWidth(text: String | undefined): string {
    if (text === undefined) return "-1";
    let span = document.createElement("span");
    document.body.appendChild(span);
    
    span.style.fontSize = getComputedStyle(document.documentElement).getPropertyValue("--font-size");
    span.style.position = 'absolute';
    span.style.whiteSpace = 'no-wrap';
    span.innerHTML = text.toString();
    
    let width = Math.ceil(span.clientWidth);
    let formattedWidth = width + "px";
    document.body.removeChild(span);

    return formattedWidth;
}

export function flatArgs(arr: (Value | PendingValue)[]) {
    const res: (Value | PendingValue)[] = []
    arr.forEach(a => {
        if (a instanceof Tuple) {
            a.tuple.forEach(aa => {
                res.push(aa)
            })
        } else {
            res.push(a)
        }
    })
    return res;
}

export function getDataStructureDepth(expr: Expression): number {

    switch (expr.kind) {
        case 'DataconstructorExpr': {
            if (expr.parameters == undefined) return 0
            else return 1 + Math.max(...expr.parameters!!.map(getDataStructureDepth))
        }
        default: 
            return 0;
    }

}