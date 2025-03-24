import { Precedence, Rendering, type RenderingRef, staticRendering } from '../../rendering';
import type { Expression } from '..';
import { natParameter } from '../construction';
import { defineLeftRightExpr } from '../utils';

export class NatLiteral implements Expression {
  public static readonly constructorParams = [natParameter] as const;
  public readonly rendered: RenderingRef;
  public readonly value: bigint;

  constructor(value: bigint) {
    // Due to a hack in the constructSyntaxElement function in ../construction.ts, we need to
    // make sure that passing a string instead of bigint also works fine for rendering purposes.
    if (__DEV__ && typeof value === 'bigint' && value < 0n) {
      throw new Error(`(Internal) Cannot construct NatLiteral of ${value}.`);
    }
    this.value = value;
    this.rendered = staticRendering(String(value));
  }
}

export const Addition = defineLeftRightExpr(' + ', Precedence.PlusMinus);
export const Monus = defineLeftRightExpr(new Rendering(' &dotminus; ', ' \\monus '), Precedence.PlusMinus);
export const Multiplication = defineLeftRightExpr(' * ', Precedence.MultDiv);
export const Division = defineLeftRightExpr(new Rendering(' &div; ', ' \\div '), Precedence.MultDiv);
export const Modulo = defineLeftRightExpr(new Rendering(' % ', ' \\% '), Precedence.MultDiv);
