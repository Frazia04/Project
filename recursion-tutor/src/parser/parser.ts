import type * as parsec from 'typescript-parsec';
import type { Parser, rep_sc, rule } from 'typescript-parsec';
import { alt, apply, kleft, kmid, kright, list_sc, lrec_sc, opt_sc, seq, str, tok } from 'typescript-parsec';
import type { Tok } from './tok';
import type { Type, Expression, NatLiteralExpr, TrueLiteralExpr, FalseLiteralExpr, IdentifierExpr, Rule, Pattern, Parameter, Declaration, Program, PrimitivePattern, TupleParameterDecl } from './ast';


type Token = parsec.Token<Tok>;

// Types

// export function applyUnit(value: Token): Type {
//     return { kind: 'PrimitiveType', name: 'unit' };
// }

// export function applyNat(value: Token): Type {
//     return { kind: 'PrimitiveType', name: 'bigint' };
// }

/*export function applyInt(value: Token): Type {
    return { kind: 'PrimitiveType', name: 'number' };
}*/

// export function applyBoolean(value: Token): Type {
//     return { kind: 'PrimitiveType', name: 'boolean' };
// }

// export function applyChar(value: Token): Type {
//     return { kind: 'PrimitiveType', name: 'char' };
// }

/*export function applyString(value: Token): Type {
    return { kind: 'PrimitiveType', name: 'string' };
}*/

// export function applyTupleType (value: [Type, Type]): Type {
//     return {
//         kind: 'PrimitiveType',
//         name: 'tuple',
//         fst: value[0],
//         snd: value[1]
//     }
// }

// export function applyListType (value: Type): Type {
//     return {
//         kind: 'ListType',
//         elementType: value
//     }
// }

// export function applyFunctionType (value: [Type, Type]): Type {
//     return {
//         kind: 'PrimitiveType',
//         name: 'function',
//         paramType: value[0],
//         returnType: value[1]
//     }
// }

// Expressions

export function applyLetExpr (value: [string, Expression, Expression]): Expression {
    return {
        kind: 'LetExpr',
        identifier: value[0],
        value: value[1],
        in: value[2]
    }
}

export function applyIdentifier (value: Token): string {
    return value.text;
}

export function applyType (value: Token): string {
    return value.text;
}

export function applyIdentifierExpr (value: string): Expression {
    return {
        kind: 'IdentifierExpr',
        id: value
    };
}

export function applyTrueLiteralExpr (): Expression {
    return {
        kind: 'TrueLiteralExpr',
        value: true
    };
}

export function applyFalseLiteralExpr (): Expression {
    return {
        kind: 'FalseLiteralExpr',
        value: false
    };
}

export function applyNatLiteralExpr (value: Token): Expression {
    return {
        kind: 'NatLiteralExpr',
        value: BigInt(value.text)
    };
}

export function applyStringLiteralExpr (value: Token): Expression {
    return {
        kind: 'StringLiteralExpr',
        value: value.text.slice(1,-1)
    };
}

export function applyFunAppExpr (fun: Expression, args: Expression[]): Expression {
    return {
        kind: 'FunAppExpr',
        fun: fun,
        args: args
    }
}

export function applyIfThenElseExpr (value: [Expression, Expression, Expression]): Expression {
    return {
        kind: 'IfThenElseExpr',
        if: value[0],
        then: value[1],
        else: value[2]
    }
}

export function applyMatchWithExpr (value: [Expression, Rule[]]): Expression {
    return {
        kind: 'MatchWithExpr',
        discriminatorExpr: value[0],
        rules: value[1]
    }
}

export function applyEqualExpr (left: Expression, right: Expression): Expression {
    return {
        kind: 'EqualExpr',
        left: left,
        right: right
    }
}
export function applyNotEqualExpr (left: Expression, right: Expression): Expression {
    return {
        kind: 'NotEqualExpr',
        left: left,
        right: right
    }
}

export function applyLTExpr (left: Expression, right: Expression): Expression {
    return {
        kind: 'LTExpr',
        left: left,
        right: right
    }
}

export function applyGTExpr (left: Expression, right: Expression): Expression {
    return {
        kind: 'GTExpr',
        left: left,
        right: right
    }
}

export function applyLTEExpr (left: Expression, right: Expression): Expression {
    return {
        kind: 'LTEExpr',
        left: left,
        right: right
    }
}

export function applyGTEExpr (left: Expression, right: Expression): Expression {
    return {
        kind: 'GTEExpr',
        left: left,
        right: right
    }
}

export function applyAdditionExpr (left: Expression, right: Expression): Expression {
    return {
        kind: 'AdditionExpr',
        left: left,
        right: right
    }
}

export function applySubtractionExpr (left: Expression, right: Expression): Expression {
    return {
        kind: 'SubtractionExpr',
        left: left,
        right: right
    }
}

export function applyMultiplicationExpr (left: Expression, right: Expression): Expression {
    return {
        kind: 'MultiplicationExpr',
        left: left,
        right: right
    }
}

export function applyDivisionExpr (left: Expression, right: Expression): Expression {
    return {
        kind: 'DivisionExpr',
        left: left,
        right: right
    }
}

export function applyModuloExpr (left: Expression, right: Expression): Expression {
    return {
        kind: 'ModuloExpr',
        left: left,
        right: right
    }
}

export function applyTupleExpr (value: [Expression, Expression[]]): Expression {
    return {
       kind: 'TupleExpr',
       values: [value[0]].concat(value[1])
    }
}

export function applyDataconstructorExpr (value: [string, Expression?]): Expression {
    return {
        kind: 'DataconstructorExpr',
        constructorname: value[0],
        parameters: (value[1] === undefined) ? undefined : [value[1]]
    }
}

export function applyDataconstructorTupleExpr (value: [string, Expression, Expression[]]): Expression {
    return {
        kind: 'DataconstructorExpr',
        constructorname: value[0],
        parameters: [value[1]].concat(value[2])
    }
}



// Pattern matching

export function applyRule (value: [Pattern, Expression]): Rule {
    return {
        kind: 'Rule',
        pattern: value[0],
        expr: value[1]
    }
}

export function applyDataconstructor (value: Token): string {
    return value.text;
}

export function applyConstructorPattern (value: [string, PrimitivePattern?]): Pattern {
    return {
        kind: 'ConstructorPattern',
        constructorName: value[0],
        parameters: (value[1] === undefined) ? undefined : [value[1]]
    }
}

export function applyConstructorPatternTupel (value: [string, PrimitivePattern, PrimitivePattern[]]): Pattern {
    return {
        kind: 'ConstructorPattern',
        constructorName: value[0],
        parameters: [value[1]].concat(value[2])
    }
}

export function applyTuplePattern (value: [PrimitivePattern, PrimitivePattern[]]): PrimitivePattern {
    return {
        kind: 'TuplePattern',
        patterns: [value[0]].concat(value[1])
    }
}

export function applyConstPatternTrue (): PrimitivePattern {
    return {
        kind: 'ConstPattern',
        value: {
            kind: 'TrueLiteralExpr',
            value: true
        }
    }
}

export function applyConstPatternFalse (): PrimitivePattern {
    return {
        kind: 'ConstPattern',
        value: {
            kind: 'FalseLiteralExpr',
            value: false
        }
    }
}

export function applyConstPatternNat (value: Token): PrimitivePattern {
    return {
        kind: 'ConstPattern',
        value: {
            kind: 'NatLiteralExpr',
            value: BigInt(value.text)
        }
    }
}

export function applyNamedPattern (value: string): PrimitivePattern {
    return {
        kind: 'NamedPattern',
        name: {
            kind: 'IdentifierExpr',
            id: value
        }
    }
}

export function applyWildcardPattern (): PrimitivePattern {
    return {
        kind: 'WildcardPattern'
    }
}

// Declarations

export function applyMultiParamFunctionDecl (value: [string, Parameter[] | undefined, Type | undefined, Expression]): Declaration {
    return {
        kind: 'MultiParamFunctionDecl',
        name: value[0],
        toDebug: false,
        returnType: value[2],
        parameters: value[1],
        expr: value[3]
    };
}

export function applyMultiParamFunctionToDebugDecl (value: [string, Parameter[] | undefined, Type | undefined, Expression]): Declaration {
    return {
        kind: 'MultiParamFunctionDecl',
        name: value[0],
        toDebug: true,
        returnType: value[2],
        parameters: value[1],
        expr: value[3]
    };
}

export function applyParameter (value: [string, Type]): Parameter {
    return {
        kind: 'ParameterDecl',
        parameters: [value]
    };
}

export function applyTupleParameter (value: [string, Type, [string, Type][]]): Parameter {
    const p: [string, Type][] = [[value[0], value[1]]]
    return {
        kind: 'TupleParameterDecl',
        parameters: p.concat(value[2])
    };
}

// Program

export function applyProgram (value: Declaration[]): Program {
    return {
        declarations: value
    };
}