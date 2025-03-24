//import { Address } from 'cluster';
import { rule, type Parser, rep } from 'typescript-parsec';
import { alt, amb, apply, kleft, kmid, kright, list_sc, rep_sc, lrec_sc, opt_sc, seq, str, tok } from 'typescript-parsec';
import { Tok } from './tok';
import type { Type, Expression, Rule, Pattern, Parameter, Declaration, Program, PrimitivePattern } from './ast';
import * as p from './parser';

export const IDENTIFIER = rule<Tok, string>();

export const TYPE = rule<Tok, Type>();
export const PARAM = rule<Tok, Parameter>();

export const EXPR = rule<Tok, Expression>();

export const RULE = rule<Tok, Rule>();
export const DATACONSTRUCTOR = rule<Tok, string>();
export const PATTERN = rule<Tok, Pattern>();
export const PRIM_PATTERN = rule<Tok, PrimitivePattern>();
export const TUPLE_PATTERN = rule<Tok, PrimitivePattern>();

export const FUN = rule<Tok, Declaration>();
export const DEBUGFUN = rule<Tok, Declaration>();

export const PROGRAM = rule<Tok, Program>();

PROGRAM.setPattern(
    apply(
        rep_sc(
            alt(
                DEBUGFUN, 
                FUN
            )
        ),
        p.applyProgram
    )
)

FUN.setPattern(
    apply ( // multiple (also zero) parameters, opt. return type
    seq(
        kright(seq(tok(Tok.Let), opt_sc(tok(Tok.Rec))), IDENTIFIER), // Function name
        opt_sc(rep_sc(PARAM)), // Parameters
        opt_sc(kright(tok(Tok.Colon), TYPE)), // Function Type
        kright(tok(Tok.Eq), EXPR) // Function body
    ),
    p.applyMultiParamFunctionDecl
)
)

DEBUGFUN.setPattern(
    apply ( // multiple (also zero) parameters, opt. return type
        seq(
            kright(seq(tok(Tok.Let), tok(Tok.TraceRec)), IDENTIFIER), // Function name
            opt_sc(rep_sc(PARAM)), // Parameters
            opt_sc(kright(tok(Tok.Colon), TYPE)), // Function Type
            kright(tok(Tok.Eq), EXPR) // Function body
        ),
        p.applyMultiParamFunctionToDebugDecl
    )
)   

export const DATACONSTRUCTOR_EXPR: Parser<Tok, Expression> =
    apply(
        seq(
            DATACONSTRUCTOR,
            opt_sc(EXPR)
        ), 
        p.applyDataconstructorExpr
    )

export const TUPLE_EXPR: Parser<Tok, Expression> =
    apply(
        seq(
            kmid(tok(Tok.LParen), EXPR, tok(Tok.Comma)),
            kleft(list_sc(EXPR, tok(Tok.Comma)), tok(Tok.RParen))
        ),
        p.applyTupleExpr
    )

export const ATOM_EXPR: Parser<Tok, Expression> =
    alt(
        apply(IDENTIFIER, p.applyIdentifierExpr),
        apply(tok(Tok.True), p.applyTrueLiteralExpr),
        apply(tok(Tok.False), p.applyFalseLiteralExpr),
        apply(tok(Tok.NumberLiteral), p.applyNatLiteralExpr), //ein N würde als FunApp geparst werden    
        kmid(tok(Tok.LParen), EXPR, tok(Tok.RParen)),
        apply(tok(Tok.StringLiteral), p.applyStringLiteralExpr),
        DATACONSTRUCTOR_EXPR,
        TUPLE_EXPR
    )

export const FUNAPP_EXPR: Parser<Tok, Expression> = 
    lrec_sc(
        ATOM_EXPR,
        rep_sc(ATOM_EXPR),
        p.applyFunAppExpr
    )

export const MULTDIV_EXPR: Parser<Tok, Expression> =
    lrec_sc(
        FUNAPP_EXPR,
        seq(
            alt(
                tok(Tok.Star), 
                tok(Tok.Div), 
                tok(Tok.Mod)
            ), 
            FUNAPP_EXPR
        ),
        (left, [op, right]) => {
            switch (op.kind) {
                case Tok.Star:
                    return p.applyMultiplicationExpr(left, right);
                case Tok.Div:
                    return p.applyDivisionExpr(left, right);
                case Tok.Mod:
                    return p.applyModuloExpr(left, right);
            }
        }
    )

export const PLUSMINUS_EXPR: Parser<Tok, Expression> =
    lrec_sc(
        MULTDIV_EXPR,
        seq(
            alt(
                tok(Tok.Plus), 
                tok(Tok.Minus)
            ), 
            MULTDIV_EXPR
        ),
        (left, [op, right]) => {
            switch (op.kind) {
                case Tok.Plus:
                    return p.applyAdditionExpr(left, right);
                case Tok.Minus:
                    return p.applySubtractionExpr(left, right);
            }
        }
    )

export const COMP_EXPR: Parser<Tok, Expression> =
    lrec_sc(
        PLUSMINUS_EXPR,
        seq(
            alt(
                tok(Tok.Eq), 
                tok(Tok.NEq), 
                tok(Tok.Lt), 
                tok(Tok.Gt), 
                tok(Tok.Leq), 
                tok(Tok.Geq)
            ), 
            PLUSMINUS_EXPR
        ),
        (left, [op, right]) => {
            switch (op.kind) {
                case Tok.Eq:
                    return p.applyEqualExpr(left, right);
                case Tok.NEq:
                    return p.applyNotEqualExpr(left, right);
                case Tok.Lt:
                    return p.applyLTExpr(left, right);
                case Tok.Gt:
                    return p.applyGTExpr(left, right);
                case Tok.Leq:
                    return p.applyLTEExpr(left, right);
                case Tok.Geq:
                    return p.applyGTEExpr(left, right);
            }
        }
    )

export const LET_EXPR: Parser<Tok, Expression> =
    alt(COMP_EXPR,
        apply(
            seq(
                kright(tok(Tok.Let), IDENTIFIER),
                kright(tok(Tok.Eq), EXPR),
                kright(tok(Tok.In), EXPR)
            ),
            p.applyLetExpr
        )    
    )

EXPR.setPattern( 
    alt(
        LET_EXPR, 
        apply( // if then else
            seq(
              kright(tok(Tok.If), EXPR),
              kright(tok(Tok.Then), EXPR),
              kright(tok(Tok.Else), EXPR)
            ),
            p.applyIfThenElseExpr
        ),
        apply( // match with
            seq(
                kright(tok(Tok.Match), EXPR),
                kright(tok(Tok.With), rep_sc(RULE))
            ),
            p.applyMatchWithExpr
        )
    )
)

IDENTIFIER.setPattern(
    apply(
        tok(Tok.Identifier),
        p.applyIdentifier
    )
)

TYPE.setPattern(
    apply(
        tok(Tok.Dataconstructor),
        p.applyType
    )
)

PARAM.setPattern(
    alt(
        apply(
            seq(
                kright(tok(Tok.LParen), IDENTIFIER),
                kmid(tok(Tok.Colon), TYPE, tok(Tok.RParen))
            ),
            p.applyParameter
        ),
        apply(
            seq(
                kright(tok(Tok.LParen), IDENTIFIER),
                kmid(tok(Tok.Colon), TYPE, tok(Tok.Comma)),
                kleft(list_sc(seq(IDENTIFIER, kright(tok(Tok.Colon), TYPE)), tok(Tok.Comma)), tok(Tok.RParen))
            ),
            p.applyTupleParameter
        )
    )
)


// Pattern matching
RULE.setPattern(
    apply(
        seq(
            kmid(tok(Tok.Pat), PATTERN, tok(Tok.Arrow)),
            EXPR
        ),
        p.applyRule
    )
)

DATACONSTRUCTOR.setPattern(
    apply(
        tok(Tok.Dataconstructor),
        p.applyDataconstructor
    )
)

PATTERN.setPattern(
    // alt (
        apply(
            seq(
                DATACONSTRUCTOR,
                opt_sc(PRIM_PATTERN)
            ),
            p.applyConstructorPattern
        // ),
        // apply(
        //     seq(
        //         DATACONSTRUCTOR,
        //         kmid(tok(Tok.LParen), PRIM_PATTERN, tok(Tok.Comma)),
        //         kleft(list_sc(PRIM_PATTERN, tok(Tok.Comma)), tok(Tok.RParen))
        //     ),
        //     p.applyConstructorPatternTupel
        // )
    )
)

PRIM_PATTERN.setPattern(
    alt(
        apply(IDENTIFIER, p.applyNamedPattern),
        apply(tok(Tok.True), p.applyConstPatternTrue),
        apply(tok(Tok.False), p.applyConstPatternFalse),
        apply(tok(Tok.NumberLiteral), p.applyConstPatternNat),
        apply(tok(Tok.Wildcard), p.applyWildcardPattern),
        apply(
            seq(
                kmid(tok(Tok.LParen), PRIM_PATTERN, tok(Tok.Comma)),
                kleft(list_sc(PRIM_PATTERN, tok(Tok.Comma)), tok(Tok.RParen))
            ),
            p.applyTuplePattern
        )
    )
)