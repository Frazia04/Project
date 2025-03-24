// This module contains example inputs to be shown to the user.
// In the future, we might also want to automatically test the solver using our valid examples.

import type { SupportedFeatures } from './context';
import { availableSemantics, type Semantics } from './semantics';

/**
 * A group of examples sharing the same selector
 */
export interface ExamplesGroup {
  readonly selector: ExampleSelector;
  readonly examples: readonly Example[];
}

/**
 * Properties that determine whether an example should be available
 */
export type ExampleSelector = Readonly<SupportedFeatures> & {
  readonly semantics: Semantics;
};

/**
 * A single example, providing values for all input fields
 */
export interface Example {
  readonly valid: boolean | undefined; // TODO: remove undefined once all examples have been categorized
  readonly exprOrDecl: 'expression' | 'declaration';
  readonly typeDefs: string;
  readonly sigEnv: string;
  readonly store: string;
  readonly exprDecl: string;
  readonly externalEffects: string;
  readonly result: string;
  readonly resultStore: string;
}

// ------------------------------------------------------------------------------------------------

// For writing down hundreds of examples below, we use a compacter tuple type
type ExamplesGroupInternal = SupportedFeatures &
  Record<
    Semantics,
    [
      // The order must be consistent with the `transform` function below!
      Example['valid'],
      keyof typeof abbrExprOrDecl, // 'e' | 'd' instead of 'expression' | 'declaration'
      Example['typeDefs'],
      Example['sigEnv'],
      Example['store'],
      Example['exprDecl'],
      Example['externalEffects'],
      Example['result'],
      Example['resultStore'],
    ][]
  >;
const abbrExprOrDecl = {
  e: 'expression',
  d: 'declaration',
} as const satisfies Record<string, Example['exprDecl']>;

function transform({ decl, pairs, io, store, exceptions, ...examples }: ExamplesGroupInternal): ExamplesGroup[] {
  return availableSemantics.flatMap((semantics) => {
    const semanticsExamples = examples[semantics];
    return semanticsExamples.length
      ? [
          {
            selector: { decl, pairs, io, store, exceptions, semantics },
            examples: semanticsExamples.map<Example>(
              (
                // The order must be consistent with the tuple type definition above!
                [valid, eOrD, typeDefs, sigEnv, store, exprDecl, externalEffects, result, resultStore],
              ) => ({
                valid,
                exprOrDecl: abbrExprOrDecl[eOrD],
                typeDefs,
                sigEnv,
                store,
                exprDecl,
                externalEffects,
                result,
                resultStore,
              }),
            ),
          },
        ]
      : [];
  });
}

// ------------------------------------------------------------------------------------------------

const examplesGroupsInternal: ExamplesGroupInternal[] = [
  // no features
  {
    decl: false,
    pairs: false,
    io: false,
    store: false,
    exceptions: false,
    static: [
      [true, 'e', '', '{}', '', 'if (7 - ?) < 5 then 6 * 7 else ?', '', 'Nat', ''],
      [true, 'e', '', '{}', '', 'if 4 > 5 then 4 * 6 else 42', '', 'Nat', ''],
      [true, 'e', '', '{}', '', '7 % 3', '', 'Nat', ''],
      [true, 'e', '', '{}', '', '5 - ?', '', 'Nat', ''],
      [true, 'e', '', '{}', '', '? + ?', '', 'Nat', ''],
      [true, 'e', '', '{}', '', 'if ? then ? else ?', '', 'Nat', ''],
      [false, 'e', '', '{}', '', '4 * 8', '', 'Bool', ''],
      [false, 'e', '', '{}', '', 'if 4 + 5 then true else 42', '', 'Bool', ''],
      [false, 'e', '', '{}', '', 'if true then false else 10', '', '', ''],
    ],
    dynamic: [
      [true, 'e', '', '{}', '', 'if 4 > ? then ? else 1', '', '1', ''],
      [true, 'e', '', '{}', '', 'if 7 - ? < 5 then 6 * 7 else 42', '', '42', ''],
      [true, 'e', '', '{}', '', 'if 4 > 5 then 4 * 6 else 42', '', '42', ''],
      [true, 'e', '', '{}', '', '7 % 3', '', '1', ''],
      [true, 'e', '', '{}', '', '5 - ?', '', '', ''],
      [false, 'e', '', '{}', '', 'true + 1', '', '2', ''],
      [false, 'e', '', '{}', '', '5', '', '7', ''],
      [false, 'e', '', '{}', '', '5 / 0', '', '', ''],
    ],
  },

  // decl
  {
    decl: true,
    pairs: false,
    io: false,
    store: false,
    exceptions: false,
    static: [
      [true, 'd', '', '{y -> Nat}', '', 'let f (x : Nat) : Nat = x + y', '', '{ f -> Nat -> Nat }', ''],
      [true, 'e', '', '{}', '', 'let f (x : Nat) : Nat = ? in f ?', '', 'Nat', ''],
      [true, 'e', '', '{y -> Nat}', '', 'let x = ? in x * y', '', 'Nat', ''],
      [true, 'e', '', '{}', '', 'let s = 42 in ?', '', 'Nat', ''],
      [true, 'e', '', '{y -> Nat}', '', 'let f (x : Nat) : Nat = x + y in f 1', '', '', ''],
      [true, 'e', '', '{}', '', 'let rec f (x: Nat): Bool = if x = 0 then true else (if (f (x-1)) then false else true) in f 10', '', '', ''],
      [true, 'e', '', '{}', '', '(fun (x: Nat) -> x > 4) 5', '', '', ''],
      [true, 'e', '', '{}', '', 'let f (x : Nat) : Nat = x + 2 in f 39', '', 'Nat', ''],
      [true, 'e', '', '{ f -> Nat -> Bool }', '', 'f 5', '', '', ''],
      [true, 'd', '', '{}', '', 'let f (x : Nat) : Bool = x % 2 = 0', '', '', ''],
      [true, 'e', '', '{}', '', 'let s = 42 in s * s', '', 'Nat', ''],
      [false, 'e', '', '{}', '', 'fun (x: Nat) -> x > true', '', '', ''],
      [false, 'e', '', '{}', '', 'let s = 42 in s * a', '', 'Nat', ''],
      //[undefined, 'e', '', '{ x -> Nat, y -> Bool}', '', 'x > 3 && y', '', 'Bool', ''],
      //[undefined, 'e', '', '{ x -> Nat, y ->Bool}', '', 'x && y', '', '', ''],
      //[undefined, 'e', '', '{}', '', '(let s = true in s) && (let s = 7 in s * s > 42) = true', '', '', ''],
    ],
    dynamic: [
      [true, 'e', '', '{}', '', 'let f (x : Nat) : Nat = ? in f 8', '', '', ''],
      [true, 'd', '', '{}', '', 'let f (x : Nat) : Nat = x * 19', '', '', ''],
      [true, 'e', '', '{y -> 9}', '', 'let x = ? in x * y', '', '', ''],
      [true, 'e', '', '{y -> 77}', '', 'let f (x : Nat) : Nat = x + y in f 1', '', '', ''],
      [true, 'e', '', '{}', '', 'let rec f (x: Nat): Bool = if x = 0 then 1 else (f (x-1) * 2) in f 2', '', '', ''],
      [true, 'e', '', '{}', '', '(fun (x: Nat) -> x > 4) 5', '', '', ''],
      [true, 'e', '', '{}', '', 'fun (x: Nat) -> x > 4', '', '', ''],
      [true, 'e', '', '{ f -> < {}, x, x % 2 = 0>}', '', 'f 5', '', 'false', ''],
      [true, 'd', '', '{}', '', 'let f (x : Nat) : Bool = x % 2 = 0', '', '', ''],
      [true, 'e', '', '{}', '', 'let s = 42 in s * s', '', '', ''],
      //[undefined, 'e', '', '{ x -> 5, y -> true}', '', 'x > 3 && y', '', 'true', ''],
      //[undefined, 'e', '', '{}', '', '(let s = true in s) && (let s = 7 in s * s > 42)', '', '', ''],
      [false, 'e', '', '{y -> true}', '', 'let x = 5 in x * y', '', '', ''],
      [false, 'e', '', '{}', '', 'let f (x : Nat) : Nat = x + 2 in f 39', '', '42', ''],
    ],
  },

  // pairs
  {
    decl: false,
    pairs: true,
    io: false,
    store: false,
    exceptions: false,
    static: [
      [true, 'e', '', '{}', '', 'fst (?, 42)', '', 'Bool', ''],
      [true, 'e', '', '{}', '', '(5, ?)', '', 'Nat * Nat', ''],
      [true, 'e', '', '{}', '', 'fst (true, 42)', '', '', ''],
      [true, 'e', '', '{}', '', '(5, true)', '', '', ''],
      [false, 'e', '', '{}', '', 'snd (4 > 5, 42)', '', 'Bool', ''],
    ],
    dynamic: [
      [true, 'e', '', '{}', '', '(5, ?)', '', '', ''],
      [true, 'e', '', '{}', '', 'fst (?, 42)', '', '', ''],
      [true, 'e', '', '{}', '', 'snd (4, 0)', '', '', ''],
      [true, 'e', '', '{}', '', '(5, true)', '', '', ''],
      [false, 'e', '', '{}', '', 'snd (4 > 5, 42)', '', 'false', ''],
    ],
  },

  // io
  {
    decl: false,
    pairs: false,
    io: true,
    store: false,
    exceptions: false,
    static: [
      [true, 'e', '', '{}', '', "putchar 'a'", '', '', ''],
      [false, 'e', '', '{}', '', 'putchar 10', '', 'Unit', ''],
    ],
    dynamic: [
      [true, 'e', '', '{}', '', "putchar 'a'", 'out(a)', '', ''],
    ],
  },

  // pairs, io
  {
    decl: false,
    pairs: true,
    io: true,
    store: false,
    exceptions: false,
    static: [
      [true, 'e', '', '{}', '', 'putchar (getchar ())', '', 'Unit', ''],
      [true, 'e', '', '{}', '', 'getchar ()', '', '', ''],
      [true, 'e', '', '{}', '', 'snd ?', '', 'Char', ''],
      [true, 'e', '', '{}', '', '(5, getchar () )', '', '', ''],
      [true, 'e', '', '{}', '', "putchar (fst ('a', true))", '', 'Unit', ''],
      [false, 'e', '', '{}', '', '(5, getchar () )', '', 'Nat*Unit', ''],
    ],
    dynamic: [
      [true, 'e', '', '{}', '', 'getchar ()', 'in(a)', '', ''],
      [true, 'e', '', '{}', '', 'putchar (getchar ())', 'in(a)out(a)', '()', ''],
      [true, 'e', '', '{}', '', '(5, getchar () )', 'in(a)', '', ''],
      [true, 'e', '', '{}', '', "putchar (fst ('a', true))", 'out(a)', '()', ''],
      [false, 'e', '', '{}', '', 'putchar (getchar ())', 'in(a)out(b)', '()', ''],
    ],
  },

  // store
  {
    decl: false,
    pairs: false,
    io: false,
    store: true,
    exceptions: false,
    static: [
      [true, 'e', '', '{}', '', 'ref ?', '', 'Ref<Nat>', ''],
      [true, 'e', '', '{}', '', 'ref (ref 5)', '', '', ''],
      [true, 'e', '', '{}', '', '((ref 0) := 9)', '', '', ''],
      [true, 'e', '', '{}', '', '!(ref ?)', '', 'Bool', ''],
      [true, 'e', '', '{}', '', 'ref 5', '', '', ''],
      [false, 'e', '', '{}', '', '!((ref 8) := 10)', '', 'Nat', ''],
    ],
    dynamic: [
      [true, 'e', '', '{}', '{}', '(ref 0) := 9', '', '', ''],
      [true, 'e', '', '{}', '{}', '!(ref 7)', '', '', ''],
      [true, 'e', '', '{}', '{}', 'ref (ref 5)', '', '', ''],
      [true, 'e', '', '{}', '{}', 'ref 5', '', '', ''],
      [false, 'e', '', '{}', '{}', '!((ref 0) := 9)', '', '', ''],
      [false, 'e', '', '{}', '', '!(ref ?)', '', '', ''],
    ],
  },

  // decl, pairs
  {
    decl: true,
    pairs: true,
    io: false,
    store: false,
    exceptions: false,
    static: [
      [true, 'e', '', '{}', '', 'let x = fst (true, 42) in ?', '', 'Bool', ''],
      [true, 'e', '', '{ p -> Nat * Bool}', '', 'fst p + ?', '', 'Nat', ''],
      [true, 'e', '', '{x -> Bool}', '', '(9, x)', '', '', ''],
      [true, 'e', '', '{ p -> Nat * Bool}', '', 'fst p', '', '', ''],
      [false, 'e', '', '{ p -> Bool * Bool}', '', 'fst p > 2', '', 'Bool', ''],
    ],
    dynamic: [
      [true, 'e', '', '{ p -> (7, false)}', '', 'snd p', '', '', ''],
      [true, 'e', '', '{x -> false}', '', '(x, 9)', '', '', ''],
      [true, 'e', '', '{x -> 99}', '', 'snd(true, 1) + x', '', '100', ''],
      [true, 'e', '', '{}', '', 'let x = snd(true, 42) in x >= 1', '', 'true', ''],
      [false, 'e', '', '{p -> (5,7)}', '', 'fst p = snd p', '', 'true', ''],
    ],
  },

  // decl, pairs, store
  {
    decl: true,
    pairs: true,
    io: false,
    store: true,
    exceptions: false,
    static: [
      [true, 'e', '', '{}', '', 'let x = (10, true) in ref x', '', '', ''],
      [true, 'e', '', '{}', '{} ', 'let x = snd (!(ref (10, false))) in ?', '', 'Bool', ''],
      [true, 'e', '', '{x -> Nat}', '', 'fst (!(ref (10, x)))', '', '', ''],
      [true, 'e', '', '{y -> Ref <Nat>}', '', 'let x = ! y in 2 + x', '', '', ''],
      [false, 'e', '', '{x -> Bool }', '', 'if x then ref (3, 4) else ref (true, 3)', '', '', ''],
    ],
    dynamic: [
      [true, 'e', '', '{}', '{}', 'let x = (10, true) in ref x', '', '', ''],
      [true, 'e', '', '{x ->  5 }', '{}', 'if x  > ? then ref (x, true) else ref (x, false)', '', '', ''],
      [true, 'e', '', '{x -> 5}', '{}', 'fst (!(ref (10, x)))', '', '', ''],
      [true, 'e', '', '{x -> 5}', '{a0 -> true}', 'fst (!(ref (10, x)))', '', '', ''],
      [false, 'e', '', '{x -> true}', '{}', 'if x then ref 10 else ref 9', '', '', '{a0 -> 9}'],
    ],
  },

  // pairs, io, store
  {
    decl: false,
    pairs: true,
    io: true,
    store: true,
    exceptions: false,
    static: [
      [true, 'e', '', '{}', '', "ref ('a', 4) := (getchar(), 7)", '', '', ''],
      [true, 'e', '', '{}', '', 'fst (!(ref 5), getchar ()) + ?', '', 'Nat', ''],
      [true, 'e', '', '{}', '', 'ref (getchar ())', '', '', ''],
      [true, 'e', '', '{}', '', 'fst (ref 5, getchar ())', '', '', ''],
      [false, 'e', '', '{}', '', "ref ('8', true) := (putchar (), 7)", '', '', ''],
    ],
    dynamic: [
      [true, 'e', '', '{}', '{}', 'fst (!(ref 5), getchar ()) + ?', '', '5', ''],
      [true, 'e', '', '{}', '{}', 'fst (ref 5, getchar ())', 'in(b)', '', ''],
      [true, 'e', '', '{}', '{}', 'ref (getchar ())', '', '', ''],
      [false, 'e', '', '{}', '{}', 'snd (!(ref 5), putchar ()) + 5', '', '5', ''],
    ],
  },

  // decl, io, store
  {
    decl: true,
    pairs: false,
    io: true,
    store: true,
    exceptions: false,
    static: [
      [true, 'e', '', '{}', '', "let f (x: Char): Unit = (!(ref (putchar x))) in f 'a'", '', '', ''],
      [true, 'e', '', '{x -> Bool}', '', "if x then putchar ('a') else (ref 5) := 6", '', '', ''],
      [true, 'e', '', '{}', '{}', "ref (putchar 'x')", '', '', ''],
      [false, 'e', '', '{}', '{}', "let x = putchar 'x' in ref x := true", '', '', ''],
    ],
    dynamic: [
      [true, 'e', '', '{}', '{}', "let f (x: Char): Char = (!(ref (putchar x))) in f 'a'", 'out(a)', '', ''],
      [true, 'e', '', "{x -> 'a'}", '{}', 'ref (putchar x)', '', '', ''],
      [false, 'e', '', '{x -> true}', '{}', "if x then putchar ('a') else (ref 5) := 6", "in('a')", '', ''],
    ],
  },

  // decl, pairs, io, store
  {
    decl: true,
    pairs: true,
    io: true,
    store: true,
    exceptions: false,
    static: [
      [true, 'e', '', '{}', '', 'let f (x : Char) : (Char*Ref<Nat>) = (x, ref 10) in f (getchar ())', '', '', ''],
      [true, 'e', '', '{x -> Nat}', '', 'let p = (!(ref x), getchar ()) in fst p', '', '', ''],
      [true, 'e', '', '{}', '', 'let x = getchar () in ref (putchar x)', '', '', ''],
      [true, 'e', '', '{x -> Nat} ', '', 'snd (!(ref x), getchar ())', '', '', ''],
      [false, 'e', '', '{x -> Nat} ', '', '!(ref x) + snd (getchar(), 4 > x)', '', '', ''],
    ],
    dynamic: [
      [true, 'e', '', '{}', '{}', 'let f (x : Char) : (Char*Ref<Nat>) = !(snd (x, ref 10)) > 10 in f (getchar ())', 'in(a)', '', ''],
      [true, 'e', '', '{}', '{}', 'let x = getchar () in ref (putchar x)', '', '', ''],
      [true, 'e', '', '{x -> 7}', '{}', 'let p = (!(ref x), getchar ()) in fst p', 'in(a)', '', ''],
      [true, 'e', '', '{x -> 6}', '{}', 'snd (!(ref x), getchar ())', '', '', ''],
      [true, 'e', '', '{}', '{}', 'let x = getchar () in ref (putchar x)', '', '', ''],
      [false, 'e', '', "{x -> '5'}", '{}', 'snd ( ! (ref (putchar x, x = 5)))', '', 'true', ''],
    ],
  },

  // decl, pairs, io
  {
    decl: true,
    pairs: true,
    io: true,
    store: false,
    exceptions: false,
    static: [
      [true, 'e', '', '{}', '', "let f ( x : Char): Unit = putchar (x) in f (fst ('a', 1))", '', '', ''],
      [true, 'e', '', '{}', '', 'let y = getchar () in putchar (y)', '', '', ''],
      [true, 'd', '', '{}', '', "let p = (putchar 'b' , 0)", '', '', ''],
      [true, 'd', '', '{}', '', 'let y = getchar ()', '', '', ''],
      [true, 'e', '', '{x -> Nat}', '', '(getchar () , x)', '', '', ''],
      [false, 'd', '', '{y -> Nat*Bool}', '', 'let f (x : Char):Nat = x + (fst y)', '', '', ''],
    ],
    dynamic: [
      [true, 'e', '', '{}', '', 'let f (x : Nat) : (Char * Nat) = (getchar (), ?) in f 5', '', '', ''],
      [true, 'd', '', '{}', '', "let p = (putchar 'b' , 0)", 'out(b)', '', ''],
      [true, 'e', '', '{x -> 5}', '', '(getchar () , x)', 'in(a)', '', ''],
      [true, 'd', '', '{}', '', 'let y = getchar ()', 'in(a)', '', ''],
      [false, 'e', '', "{x -> 'b'}", '', 'let y = getchar () in putchar x', 'out(b)in(a)', '', ''],
    ],
  },

  // io, store
  {
    decl: false,
    pairs: false,
    io: true,
    store: true,
    exceptions: false,
    static: [
      [true, 'e', '', '{}', '', "if !(ref true) then putchar('t') else putchar('f')", '', '', ''],
      [true, 'e', '', '{}', '', "putchar (!(ref ('a')))", '', 'Unit', ''],
      [false, 'e', '', '{}', '', "ref (putchar('a')) := 9", '', '', ''],
    ],
    dynamic: [
      [true, 'e', '', '{}', '{}', "if !(ref true) then putchar('t') else putchar('f')", '', '', ''],
      [true, 'e', '', '{}', '{}', "putchar (!(ref ('a')))", 'out(a)', '()', ''],
      [false, 'e', '', '{}', '{}', "ref (putchar('a')) := 'b'", 'out(b)', '()', ''],
    ],
  },

  // decl, io
  {
    decl: true,
    pairs: false,
    io: true,
    store: false,
    exceptions: false,
    static: [
      [true, 'e', '', '{x -> Char}', '', 'putchar x', '', '', ''],
      [true, 'e', '', '{}', '', "let f (x : Char): Unit = putchar (x) in f 'a'", 'out(a)', '', ''],
      [true, 'e', '', '{y -> Nat}', '', "if (y % 2) = 0 then putchar('t') else putchar('f')", '', '', ''],
      [false, 'e', '', '{x -> Nat}', '', 'if 4 > 3 then x else putchar (false)', '', '', ''],
    ],
    dynamic: [
      [true, 'e', '', "{x -> 'x'}", '', 'putchar x', 'out(x)', '', ''],
      [true, 'e', '', '{}', '', "let f (x : Char): Unit = putchar (x) in f 'a'", 'out(a)', '', ''],
      [true, 'e', '', '{y -> 5}', '', "if (y % 2) = 0 then putchar('t') else putchar('f')", '', '', ''],
      [false, 'e', '', "{x -> '5'}", '', "if 4 > 3 then x + 5 else putchar ('f')", '', '', ''],
    ],
  },

  // pairs, store
  {
    decl: false,
    pairs: true,
    io: false,
    store: true,
    exceptions: false,
    static: [
      [true, 'e', '', '{}', '', 'ref (4, 4) := (6, 7)', '', '', ''],
      [true, 'e', '', '{}', '', 'fst (!(ref 10), true)', '', '', ''],
      [true, 'e', '', '{}', '', 'ref (10, true)', '', '', ''],
      [false, 'e', '', '{}', '', 'ref (true, 4) := (6, false)', '', '', ''],
    ],
    dynamic: [
      [true, 'e', '', '{}', '{}', 'fst (!(ref (10, true)))', '', '', ''],
      [true, 'e', '', '{}', '{}', 'ref (4, 4) := (6, 7)', '', '', ''],
      [false, 'e', '', '{}', '{}', 'snd (4,5) + ref 3', '', '', ''],
    ],
  },

  // decl, store
  {
    decl: true,
    pairs: false,
    io: false,
    store: true,
    exceptions: false,
    static: [
      [true, 'e', '', '{ f -> Nat -> Bool }', '', 'ref (f 5)', '', '', ''],
      [true, 'e', '', '{}', '', '!(ref (let x = 10 in x + 1))', '', '', ''],
      [true, 'e', '', '{x -> Nat}', '', '!(ref 7) * x', '', '', ''],
      [true, 'e', '', '{x -> Nat}', '', 'ref x', '', '', ''],
      [false, 'e', '', '{x -> Bool}', '', '! (ref 9) + x', '', '', ''],
    ],
    dynamic: [
      [true, 'e', '', '{ f -> <{}, x, x < 5> }', '{}', 'ref (f 5)', '', '', ''],
      [true, 'e', '', '{x -> 6}', '{}', '!(ref 7) * x', '', '', ''],
      [true, 'e', '', '{x -> 42}', '{}', 'ref x', '', '', ''],
      [true, 'e', '', '{}', '{}', '!(ref (let x = 10 in x + 1))', '', '', ''],
      [false, 'e', '', '{x -> true}', '{}', '! (ref 9) + x', '', '', ''],
    ],
  },

  // exceptions
  {
    decl: false,
    pairs: false,
    io: false,
    store: false,
    exceptions: true,
    static: [
      [undefined, 'e', 'exception A;', '{}', '', 'if 3 < 4 then raise A else 7', '', '', ''],
    ],
    dynamic: [],
  },

  // decl, pairs, store, exceptions
  {
    decl: true,
    pairs: true,
    io: false,
    store: true,
    exceptions: true,
    static: [
      [undefined, 'e', 'exception A;', '{x -> Nat}', '', 'fst (ref (getchar x), raise A)', '', '', ''],
    ],
    dynamic: [],
  },

  // pairs, io, store, exceptions
  {
    decl: false,
    pairs: true,
    io: true,
    store: true,
    exceptions: true,
    static: [
      [undefined, 'e', 'exception A;', '{}', '', '(ref (getchar ()), raise A)', '', '', ''],
      [undefined, 'e', 'exception A;', '{}', '', "if getchar () = 'b' then raise A else ref 42", '', '', ''],
    ],
    dynamic: [],
  },

  // pairs, store, exceptions
  {
    decl: false,
    pairs: true,
    io: false,
    store: true,
    exceptions: true,
    static: [
      [undefined, 'e', 'exception A;', '{}', '', '(ref 22, raise A)', '', '', ''],
    ],
    dynamic: [],
  },

  // decl, store, exceptions
  {
    decl: true,
    pairs: false,
    io: false,
    store: true,
    exceptions: true,
    static: [
      [undefined, 'e', 'exception A;', '{x -> Bool}', '', 'let x = raise A in ref x', '', '', ''],
    ],
    dynamic: [],
  },

  // decl, pairs, io, exceptions
  {
    decl: true,
    pairs: true,
    io: true,
    store: false,
    exceptions: true,
    static: [
      [undefined, 'e', 'exception A;', '{x -> Char}', '', 'fst (putchar x, raise A)', '', '', ''],
      [undefined, 'e', 'exception A;', '{x -> Char}', '', "if  getchar () = x then putchar 'c' else raise A", '', '', ''],
    ],
    dynamic: [],
  },

  // decl, pairs, exceptions
  {
    decl: true,
    pairs: true,
    io: false,
    store: false,
    exceptions: true,
    static: [
      [undefined, 'e', 'exception A;', '{x -> Nat}', '', 'fst (x, raise A)', '', '', ''],
    ],
    dynamic: [],
  },

  // store, exceptions
  {
    decl: false,
    pairs: false,
    io: false,
    store: true,
    exceptions: true,
    static: [
      [undefined, 'e', 'exception A;', '{}', '', 'if (!(ref 5)) < 10 then 42 else raise A', '', '', ''],
    ],
    dynamic: [],
  },

  // pairs, io, exceptions
  {
    decl: false,
    pairs: true,
    io: true,
    store: false,
    exceptions: true,
    static: [
      [undefined, 'e', 'exception A;', '{}', '', '(getchar (), raise A)', '', '', ''],
      [undefined, 'e', 'exception A;', '{}', '', "if getchar () = 'b' then raise A else 42", '', '', ''],
    ],
    dynamic: [],
  },

  // pairs, exceptions
  {
    decl: false,
    pairs: true,
    io: false,
    store: false,
    exceptions: true,
    static: [
      [undefined, 'e', 'exception A;', '{}', '', '(true, raise A)', '', '', ''],
    ],
    dynamic: [],
  },

  // decl, exceptions
  {
    decl: true,
    pairs: false,
    io: false,
    store: false,
    exceptions: true,
    static: [
      [undefined, 'd', 'exception A;', '{}', '', 'let f (x :Nat) : Nat = if x = 0 then raise A else x / 2', '', '', ''],
      [undefined, 'd', 'exception A;', '{}', '', 'let f (x:Nat):Nat = if x < 4 then raise A else x - 4', '', '', ''],
    ],
    dynamic: [],
  },

  // decl, pairs, io, store, exceptions
  {
    decl: true,
    pairs: true,
    io: true,
    store: true,
    exceptions: true,
    static: [
      [undefined, 'e', 'exception A;', '{x -> Unit }', '', 'fst (ref (getchar x), raise A)', '', '', ''],
      [undefined, 'e', 'exception A;', '{}', '', "let x = if getchar () = 'b' then raise A else ref 42 in x", '', '', ''],
    ],
    dynamic: [],
  },
];

export const examplesGroups = examplesGroupsInternal.flatMap<ExamplesGroup>(transform);
