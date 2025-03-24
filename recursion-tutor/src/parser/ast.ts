// Types

export interface PrimitiveType<T> {
    kind: 'PrimitiveType';
    name: T;
}

export type UnitType = PrimitiveType<'unit'>;
export type NatType = PrimitiveType<'bigint'>;
//export type IntType = PrimitiveType<'bigint'>;
export type BoolType = PrimitiveType<'boolean'>;
export type CharType = PrimitiveType<'char'>;
export type StringType = PrimitiveType<'string'>;

export class TupleType implements PrimitiveType<'tuple'> {
    kind: 'PrimitiveType' = 'PrimitiveType';
    name: 'tuple' = 'tuple';
    fst: Type;
    snd: Type;

    constructor (fst: Type, snd: Type) {
        this.fst = fst;
        this.snd = snd;
    }
}

export class FunctionType implements PrimitiveType<'function'> {
    kind: 'PrimitiveType' = 'PrimitiveType';
    name: 'function' = 'function';
    paramType: Type;
    returnType: Type;

    constructor (paramType: Type, returnType: Type) {
        this.paramType = paramType;
        this.returnType = returnType;
    }
}

export interface ListType {
    kind: 'ListType';
    elementType: Type;
}

export type Type = string;

// export type Type =
// | UnitType
// | NatType
// | BoolType
// | CharType
// | TupleType
// | ListType
// | FunctionType
// ; 

// Expressions

export interface LetExpr {
    kind: 'LetExpr',
    identifier: string,
    value: Expression,
    in: Expression
}

export interface IdentifierExpr {
    kind: 'IdentifierExpr',
    id: string;
}

export interface TrueLiteralExpr {
    kind: 'TrueLiteralExpr',
    value: true;
}

export interface FalseLiteralExpr {
    kind: 'FalseLiteralExpr',
    value: false;
}

export interface NatLiteralExpr {
    kind: 'NatLiteralExpr',
    value: bigint; 
}

export interface StringLiteralExpr {
    kind: 'StringLiteralExpr',
    value: string
}

export interface FunAppExpr {
    kind: 'FunAppExpr';
    fun: Expression;
    args: Expression[];
}

export interface IfThenElseExpr {
    kind: 'IfThenElseExpr',
    if: Expression,
    then: Expression,
    else: Expression
}

export interface EqualExpr {
    kind: 'EqualExpr',
    left: Expression,
    right: Expression
}

export interface NotEqualExpr {
    kind: 'NotEqualExpr',
    left: Expression,
    right: Expression
}

export interface LTExpr {
    kind: 'LTExpr',
    left: Expression,
    right: Expression
}

export interface GTExpr {
    kind: 'GTExpr',
    left: Expression,
    right: Expression
}

export interface LTEExpr {
    kind: 'LTEExpr',
    left: Expression,
    right: Expression
}

export interface GTEExpr {
    kind: 'GTEExpr',
    left: Expression,
    right: Expression
}

export interface AdditionExpr {
    kind: 'AdditionExpr',
    left: Expression,
    right: Expression
}

export interface SubtractionExpr {
    kind: 'SubtractionExpr',
    left: Expression,
    right: Expression
}

export interface MultiplicationExpr {
    kind: 'MultiplicationExpr',
    left: Expression,
    right: Expression
}

export interface DivisionExpr {
    kind: 'DivisionExpr',
    left: Expression,
    right: Expression
}

export interface ModuloExpr {
    kind: 'ModuloExpr',
    left: Expression,
    right: Expression
}

export interface MatchWithExpr {
    kind: 'MatchWithExpr',
    discriminatorExpr: Expression,
    rules: Rule[];
}

export interface TupleExpr {
    kind: 'TupleExpr',
    values: Expression[]
}

export interface DataconstructorExpr {
    kind: 'DataconstructorExpr',
    constructorname: string,
    parameters: Expression[] | undefined
}

export type Expression = 
| LetExpr
| IdentifierExpr
| TrueLiteralExpr
| FalseLiteralExpr
| NatLiteralExpr
| StringLiteralExpr
| FunAppExpr
| IfThenElseExpr
| MatchWithExpr
| EqualExpr
| NotEqualExpr
| LTExpr
| LTEExpr
| GTExpr
| GTEExpr
| BinArExpr
| TupleExpr
| DataconstructorExpr
;

export type BinArExpr =
| AdditionExpr
| SubtractionExpr
| MultiplicationExpr
| DivisionExpr
| ModuloExpr

// Pattern matching

export type Rule = {
    kind: 'Rule',
    pattern: Pattern,
    expr: Expression
}

export interface ConstructorPattern {
    kind: 'ConstructorPattern',
    constructorName: string,
    parameters: PrimitivePattern[] | undefined 
}

export interface ConstPattern {
    kind: 'ConstPattern',
    value: NatLiteralExpr | TrueLiteralExpr | FalseLiteralExpr
}

export interface NamedPattern {
    kind: 'NamedPattern',
    name: IdentifierExpr,
}

export interface WildcardPattern {
    kind: 'WildcardPattern'
}

export interface TuplePattern {
    kind: 'TuplePattern',
    patterns: PrimitivePattern[]
}

export type Pattern = 
| ConstructorPattern
// | PrimitivePattern

export type PrimitivePattern =
| TuplePattern
| NamedPattern
| ConstPattern
| WildcardPattern

// Declarations

export interface ParameterDecl {
    kind: 'ParameterDecl';
    parameters: [string, Type][]
}

export interface TupleParameterDecl {
    kind: 'TupleParameterDecl';
    parameters: [string, Type][];
}

export type Parameter =
| ParameterDecl
| TupleParameterDecl
;

export interface VariableDecl {
    kind: 'VariableDecl';
    name: string;
    expression: Expression;
}

export interface FunctionDecl {
    kind: 'FunctionDecl';
    name: string;
    toDebug: boolean;
    returnType: Type | undefined;
    parameter: {
        name: string;
        paramType: Type;
    } | undefined
    /*params: {
        name: string;
        paramType: Type;
    }[];*/
    expr: Expression;
}

export interface MultiParamFunctionDecl {
    kind: 'MultiParamFunctionDecl';
    name: string;
    toDebug: boolean;
    returnType: Type | undefined;
    parameters: Parameter[] | undefined;
    expr: Expression;
}

export type Declaration =
//| VariableDecl
// | FunctionDecl
| MultiParamFunctionDecl
;

// Program

export interface Program {
    declarations: Declaration[];
}