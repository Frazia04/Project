import type {
    Expression,
    Type,
    TupleType,
    Pattern,
    PrimitivePattern
} from './parser/ast';
import { expressionToString, flatArgs } from '../src/parser/utils';
import { ref, shallowRef, reactive } from 'vue';

export type Parameter = string;

/**
 * Class for function closures for functions with only one parameter. 
 * The closure contains the name, the {@link Parameter} and the body ({@link Expression}).
 */
export class FunctionClosure {
    readonly name: string;
    readonly parameters: Parameter[] | undefined;
    readonly expr: Expression;

    // readonly prettyExpr: string;

    constructor(name: string, parameters: Parameter[] | undefined, expr: Expression) {
        this.name = name;
        this.parameters = parameters;
        this.expr = expr;
        // this.prettyExpr = expressionToString(expr);
    }

    public toString = (): string => {
        return "<" + this.name + ", " + this.parameters?.toString() + ", " + expressionToString(this.expr) + ">" 
    }
}

/**
 * Class for the environment of an expression. 
 * The environment contains a Map of name-{@link Value} pairs and the name of the function that is to debug.
 * Each function sees every other function, i.e. all functions are implicitly recursively entangled. 
 */
export class Environment { 
    public functionsToDebug: [string, string][] = []; 
    public bindings: Map<string, Value | PendingValue> = new Map(); 

    public lookUpClosure (name: string): Value | PendingValue {
        let value = this.bindings.get(name);
        if (value != undefined) {
            return value
        } else {
            throw new Error('Identifier ' + name + ' not found in bindings.');
        } 
    }

    public getBindings (): Map<string, Value | PendingValue> {
        return this.bindings;
    }

    /**
     * Merges this.bindings with another binding map. 
     * If both maps contain same keys, the value of the this.bindings map will be overwritten by the passed value
     * @param right the right bindings map (which has priority over the left)
     */
    public commaOp(right: Map<string, Value>) {
        for (let [key, value] of right) {
            this.bindings.set(key, value) // set updates value the value if the key already exists
        }
    }

    public addOrUpdateBinding (name: string, value: Value | PendingValue, print = false) { 
        if (print) console.log("updating " + name + " to " + value);
        this.bindings.set(name, value);
    }

    public copyEnvironment (): Environment {
        const newEnv = new Environment();
        this.bindings.forEach((value, key) => {
            newEnv.addOrUpdateBinding(key, value); 
        })

        this.functionsToDebug.forEach(f => {
            newEnv.addFunctionToDebug(f[0], f[1]);
        })

        return newEnv;
    }

    public getFunctionsToDebug(): [string, string][] {
        return this.functionsToDebug
    }

    public addFunctionToDebug(functionToDebug: string, color: string) {
        this.functionsToDebug.push([functionToDebug, color]);
    }

    public setFunctionsToDebug(functionsToDebug: [string, string][]) {
        this.functionsToDebug.concat(functionsToDebug);
    }

    public getNumberOfFunctionsToDebug(): number {
        return this.functionsToDebug.length;
    }

    public isInFunctionsToDebug(functionToDebug: string): boolean {
        let found = false;
        this.functionsToDebug.forEach(f => {
            if (f[0] === functionToDebug) {
                found = true;
                return;
            }
        });
        return found;
    }

    public getBackgroundColor(name: string): string {
        let color = "";
        this.functionsToDebug.forEach(f => {
            if (f[0] === name) {
                color = f[1];
                return;
            }
        });
        return color;
    }
}

export class DataConstructorValue {
    readonly constructorname: string;
    readonly parameters: Value[] | undefined;

    constructor (constructorname: string, parameters: Value[] | undefined) {
        this.constructorname = constructorname;
        this.parameters = parameters;
    }

    private parametersToString() {
        return this.parameters?.join(", ") 
        // return "(" + this.parameters?.join(", ") + ")" 
    }

    public toString = (): string => {
        if (this.parameters === undefined) {
            return this.constructorname
        } else {
            return "(" + this.constructorname + " " + this.parametersToString() + ")"
        }
    }
}


export type PrimitiveValue = 
| boolean
| bigint
| string

// export type Tuple =
// | PrimitiveValue[]

export class Tuple {
    public tuple: PrimitiveValue[];

    constructor(tuple: PrimitiveValue[]) {
        this.tuple = tuple;
    }
    
    toString() {
        return "(" + this.tuple.join(", ") + ")"
    }
}

export type Value =
| PrimitiveValue
| FunctionClosure
| Tuple
| DataConstructorValue

// #######################################################################################
// #######################################################################################
// #######################################################################################

export interface DiagramNodeProp {
    id: number;
    parameter: Tuple | PrimitiveValue;
    env: Environment;
    expr: Expression;
    recCall: PrimitiveValue | PendingValue;
    funName: string | undefined;
    color: string;
}

export class PendingValue {
    pendingValue: (PrimitiveValue | PendingValue | String)[];

    constructor(pendingValue: (PrimitiveValue | PendingValue | String)[]) {
      this.pendingValue = pendingValue;
    }

    public toString = (): string => {
        return this.pendingValue.join(" ")
    }
}

export function createEvaluator(id: number) {
    const childEdges: (String | undefined)[] = [];
    const childProps = reactive<DiagramNodeProp[]>([]);

    function evaluateExpression(env: Environment, expr: Expression, stopAtRecCall = true): () => Value | PendingValue {
        __DEV__ && console.log (expr.kind, "expr:", expr, "env:", env);

        switch (expr.kind) {
            case 'LetExpr': {
                const valueExpr = evaluateExpression(env, expr.value, stopAtRecCall);
                
                return () => {
                    const valueValue = valueExpr();
                    
                    if (valueValue instanceof PendingValue) {
                        
                        // throw new Error("Identifier must be known!") //currently no recursive calls possible in let exprs...

                        const newEnv = env.copyEnvironment();
                        newEnv.addOrUpdateBinding(expr.identifier, valueValue);

                        // __DEV__ && console.log("env:", newEnv, "inExpr:", expr.in, "in:", evaluateExpression(newEnv, expr.in, stopAtRecCall)() as PendingValue);
                        return new PendingValue([evaluateExpression(newEnv, expr.in, stopAtRecCall)() as PendingValue])
                    } else {
                        const newEnv = env.copyEnvironment();
                        newEnv.addOrUpdateBinding(expr.identifier, valueValue);

                        return evaluateExpression(newEnv, expr.in, stopAtRecCall)();
                    }
                }
            }
            case 'FunAppExpr': {
                const fun = evaluateExpression(env, expr.fun, false)();
                if (fun instanceof PendingValue) {
                    throw new Error("Function must be known!");
                } 

                const pendingArgs: (() => (Value | PendingValue))[] = [];
                const args: (Value | PendingValue)[] = []
                expr.args.forEach(arg => {
                    const result = evaluateExpression(env, arg, stopAtRecCall);
                    pendingArgs.push(result)
                    args.push(result())
                })

                if (fun instanceof FunctionClosure) {
                    if (env.isInFunctionsToDebug(fun.name) && stopAtRecCall) {

                        args.map((arg, i) => {
                            if (arg instanceof PendingValue) throw new Error("All arguments must be known. Argument at index " + i + " is unknown.")
                        })

                        __DEV__ && console.log("RECCALL");

                        const newEnv = env.copyEnvironment();
                        if (fun.parameters != undefined) {

                            if (args.some(a => a instanceof Tuple)) {
                                const flatArguments = flatArgs(args);
                                fun.parameters.forEach((param, i) =>{
                                    newEnv.addOrUpdateBinding(param, flatArguments[i] as Value)
                                })
                            } else {
                                fun.parameters.forEach((param, i) =>{
                                    newEnv.addOrUpdateBinding(param, args[i] as Value)
                                })
                            }
                        }
                        
                        let nodeParam;
                        if (args.length > 1) nodeParam = new Tuple((args as PrimitiveValue[])).tuple.join(" ");
                        else nodeParam = args[0] as PrimitiveValue;

                        const prop: DiagramNodeProp = {
                            id: id,
                            parameter: nodeParam!!,
                            env: newEnv,
                            expr: fun.expr,
                            recCall: new PendingValue([fun.name, ' ', args.join(" ")]),
                            funName: fun.name, 
                            color: env.getBackgroundColor(fun.name)
                        }

                        const index = childProps.push(prop) - 1;
                        return () => childProps[index].recCall;
                    }

                    return () => {
                        const newEnv = env.copyEnvironment();
                        if (fun.parameters != undefined) {
                            fun.parameters.forEach((param, i) =>
                                newEnv.addOrUpdateBinding(param, pendingArgs[i]() as Value)
                            )
                        }
                        
                        // dont add new child
                        
                        if (pendingArgs.some(arg => (arg() instanceof PendingValue)))  {
                            const as:(String | PrimitiveValue | PendingValue)[] = [fun.name, " ("]
                            return new PendingValue(as.concat(evaluateExpression(newEnv, fun.expr, stopAtRecCall)() as PendingValue).concat(")"))
                        }
    
                        return evaluateExpression(newEnv, fun.expr, stopAtRecCall)();
                    }
                }
                throw new Error("Function must be a function closure!");
            }
            case "AdditionExpr": {
                const left = evaluateExpression(env, expr.left, stopAtRecCall);
                const right = evaluateExpression(env, expr.right, stopAtRecCall);

                return () => {
                const leftValue = left();
                const rightValue = right();

                return leftValue instanceof PendingValue || rightValue instanceof PendingValue
                    ? new PendingValue([leftValue as PrimitiveValue, ' + ', rightValue as PrimitiveValue])
                    : (leftValue as bigint) + (rightValue as bigint); 
                };
            }
            case "SubtractionExpr": {
                const left = evaluateExpression(env, expr.left, stopAtRecCall);
                const right = evaluateExpression(env, expr.right, stopAtRecCall);

                return () => {
                const leftValue = left();
                const rightValue = right();

                return leftValue instanceof PendingValue || rightValue instanceof PendingValue
                    ? new PendingValue([leftValue as PrimitiveValue, ' - ', rightValue as PrimitiveValue])
                    : (leftValue as bigint) - (rightValue as bigint);
                };
            }
            case "MultiplicationExpr": {
                const left = evaluateExpression(env, expr.left, stopAtRecCall);
                const right = evaluateExpression(env, expr.right, stopAtRecCall);

                return () => {
                    const leftValue = left();
                    const rightValue = right();

                    return leftValue instanceof PendingValue || rightValue instanceof PendingValue
                        ? new PendingValue([leftValue as PrimitiveValue, ' * ', rightValue as PrimitiveValue])
                        : (leftValue as bigint) * (rightValue as bigint);
                };
            }
            case "DivisionExpr": {
                const left = evaluateExpression(env, expr.left, stopAtRecCall);
                const right = evaluateExpression(env, expr.right, stopAtRecCall);

                return () => {
                    const leftValue = left();
                    const rightValue = right();

                    return leftValue instanceof PendingValue || rightValue instanceof PendingValue
                        ? new PendingValue([leftValue as PrimitiveValue, ' / ', rightValue as PrimitiveValue])
                        : (leftValue as bigint) / (rightValue as bigint);
                };
            }
            case "ModuloExpr": {
                const left = evaluateExpression(env, expr.left, stopAtRecCall);
                const right = evaluateExpression(env, expr.right, stopAtRecCall);

                return () => {
                    const leftValue = left();
                    const rightValue = right();

                    return leftValue instanceof PendingValue || rightValue instanceof PendingValue
                        ? new PendingValue([leftValue as PrimitiveValue, ' % ', rightValue as PrimitiveValue])
                        : (leftValue as bigint) % (rightValue as bigint);
                };
            }
            case 'IfThenElseExpr': {
                const condValue = evaluateExpression(env, expr.if, stopAtRecCall)();

                if (condValue instanceof PendingValue) {
                    return () => new PendingValue([condValue]);
                } else {
                    if (condValue) {
                    return evaluateExpression(env, expr.then, stopAtRecCall);
                    } else {
                    return evaluateExpression(env, expr.else, stopAtRecCall);
                    }
                } 

            }
            case 'EqualExpr': {
                const left = evaluateExpression(env, expr.left, stopAtRecCall);
                const right = evaluateExpression(env, expr.right, stopAtRecCall);

                return () => {
                    const leftValue = left();
                    const rightValue = right();

                    if (leftValue instanceof PendingValue || rightValue instanceof PendingValue) {
                        return new PendingValue([leftValue as PrimitiveValue, ' = ', rightValue as PrimitiveValue]);
                    } else {
                        return leftValue == rightValue;
                    }
                }
            }
            case 'NotEqualExpr': {
                const left = evaluateExpression(env, expr.left, stopAtRecCall);
                const right = evaluateExpression(env, expr.right, stopAtRecCall);

                return () => {
                    const leftValue = left();
                    const rightValue = right();

                    if (leftValue instanceof PendingValue || rightValue instanceof PendingValue) {
                        return new PendingValue([leftValue as PrimitiveValue, ' <> ', rightValue as PrimitiveValue]);
                    } else {
                        return leftValue != rightValue;
                    }
                }
            }
            case 'LTExpr': {
                const left = evaluateExpression(env, expr.left, stopAtRecCall);
                const right = evaluateExpression(env, expr.right, stopAtRecCall);

                return () => {
                    const leftValue = left();
                    const rightValue = right();

                    if (leftValue instanceof PendingValue || rightValue instanceof PendingValue) {
                        return new PendingValue([leftValue as PrimitiveValue, ' < ', rightValue as PrimitiveValue]);
                    } else {
                        return leftValue < rightValue;
                    }
                }
            }
            case 'LTEExpr': {
                const left = evaluateExpression(env, expr.left, stopAtRecCall);
                const right = evaluateExpression(env, expr.right, stopAtRecCall);

                return () => {
                    const leftValue = left();
                    const rightValue = right();

                    if (leftValue instanceof PendingValue || rightValue instanceof PendingValue) {
                        return new PendingValue([leftValue as PrimitiveValue, ' <= ', rightValue as PrimitiveValue]);
                    } else {
                        return leftValue <= rightValue;
                    }
                }
            }
            case 'GTExpr': {
                const left = evaluateExpression(env, expr.left, stopAtRecCall);
                const right = evaluateExpression(env, expr.right, stopAtRecCall);

                return () => {
                    const leftValue = left();
                    const rightValue = right();

                    if (leftValue instanceof PendingValue || rightValue instanceof PendingValue) {
                        return new PendingValue([leftValue as PrimitiveValue, ' > ', rightValue as PrimitiveValue]);
                    } else {
                        return leftValue > rightValue;
                    }
                }
            }
            case 'GTEExpr': {
                const left = evaluateExpression(env, expr.left, stopAtRecCall);
                const right = evaluateExpression(env, expr.right, stopAtRecCall);

                return () => {
                    const leftValue = left();
                    const rightValue = right();

                    if (leftValue instanceof PendingValue || rightValue instanceof PendingValue) {
                        return new PendingValue([leftValue as PrimitiveValue, ' >= ', rightValue as PrimitiveValue]);
                    } else {
                        return leftValue >= rightValue;
                    }
                }
            }
            case 'TupleExpr': {
                const vs: (() => Value | PendingValue)[] = []
                expr.values.forEach (v => {
                    vs.push(evaluateExpression(env, v, stopAtRecCall))
                })

                return () => {
                    const values: (Value | PendingValue)[] = []
                    vs.forEach (v => {
                        values.push(v())
                    })

                    if (values.some(v => v instanceof PendingValue)) {
                        const ps: (string | PrimitiveValue | PendingValue)[] = ['(']
                        values.forEach (v => {
                            ps.push(v as PrimitiveValue)
                            ps.push((", "))
                        })
                        return new PendingValue(ps.slice(0, ps.length - 1).concat([")"]));
                    } else {
                        return new Tuple(values as PrimitiveValue[])
                    }
                }
            }
            case 'MatchWithExpr': {
                const discriminatorValue = evaluateExpression(env, expr.discriminatorExpr, stopAtRecCall)()
                var matchedExpr: Expression | undefined

                expr.rules.forEach( rule => {
                    if (evaluatePattern(discriminatorValue as Value, rule.pattern, env)) {
                        matchedExpr = rule.expr
                    } 
                })
                if (matchedExpr === undefined) { // kein Match gefunden
                    throw new Error("Expression was not type correct or the pattern was incomplete.");
                } else {
                    const value = evaluateExpression(env, matchedExpr! as Expression, stopAtRecCall);
                    return () => { // match gefunden
                        return value()
                    }
                }
            }
            case 'NatLiteralExpr': {
                return () => expr.value;
            }
            case 'StringLiteralExpr': {
                return () => expr.value;
            }
            case 'FalseLiteralExpr': {
                return () => false;
            }
            case 'TrueLiteralExpr': {
                return () => true;
            }
            case 'IdentifierExpr': {
                return () => env.lookUpClosure(expr.id);
            }
            case 'DataconstructorExpr': {
                if (expr.parameters === undefined) {
                    return () => new DataConstructorValue(expr.constructorname, undefined)
                } else {

                    const params: (() => (Value | PendingValue))[] = expr.parameters.map(p => evaluateExpression(env, p, stopAtRecCall));

                    return () => {

                        if (params.some(p => (p() instanceof PendingValue)))  {
                            __DEV__ && console.log("pending value in data constructor parameters");
                            
                            const pendingParams: PendingValue[] = []
                            for (var p of params) {
                                pendingParams.push(p() as PendingValue);
                            }

                            var flatParams: (String | PrimitiveValue | PendingValue)[] = [expr.constructorname, " ("];
                            
                            pendingParams.forEach(p => {
                                flatParams = flatParams.concat(p.pendingValue);
                            })

                            flatParams = flatParams.concat([")"]);

                            return new PendingValue(flatParams);
                        }
    
                        const values = params.map(p => p() as Value);
    
                        return new DataConstructorValue(expr.constructorname, values);
                    }
                }
            }
        }
    }

    function evaluatePattern (discriminatorValue: Value, pattern: Pattern, env: Environment): boolean {
        switch (pattern.kind) {
            case 'ConstructorPattern': {
                const dataConstValue = (discriminatorValue as DataConstructorValue)
                if (dataConstValue.constructorname == pattern.constructorName) {
                    if (dataConstValue.parameters === undefined) {
                        return (pattern.parameters === undefined);
                    } else {
                        console.log(dataConstValue.parameters.length, pattern.parameters!.length)
                        if (dataConstValue.parameters.length != pattern.parameters!.length) return false;

                        for (let i = 0; i < pattern.parameters!.length; i++){
                            if (!evaluatePrimitivePattern(dataConstValue.parameters[i], pattern.parameters![i] as PrimitivePattern, env)) return false;
                        }

                        return true; // all patterns matched
                    }
                } else return false;
            }

            console.log("UNHANDELED PATTERN: " + pattern.kind);
            return false;
        }
    }

    function evaluatePrimitivePattern (dataConstValue: Value, pattern: PrimitivePattern, env: Environment): boolean {
        switch (pattern.kind) {
            case 'NamedPattern': {
                env.addOrUpdateBinding(pattern.name.id, dataConstValue, false)
                return true;
            }
            case 'ConstPattern': {
                switch (pattern.value.kind) {
                    case 'NatLiteralExpr': {
                        return (dataConstValue as bigint == pattern.value.value)
                    }
                    case 'TrueLiteralExpr': return dataConstValue as boolean
                    case 'FalseLiteralExpr': return !dataConstValue as boolean
                }
            }
            case 'WildcardPattern': {
                return true;
            }
            case 'TuplePattern': {
                const tuple = (dataConstValue as Tuple).tuple
                if (tuple.length != pattern.patterns.length) return false;

                for (let i = 0; i < pattern.patterns.length; i++){
                    if (!evaluatePrimitivePattern(tuple[i], pattern.patterns[i] as PrimitivePattern, env)) return false;
                }

                return true;
            }

            console.log("UNHANDELED PRIMITIVE PATTERN: " + pattern.kind)
            return false;
        }
    }

    return {
        childEdges, childProps, evaluateExpression
    };
}