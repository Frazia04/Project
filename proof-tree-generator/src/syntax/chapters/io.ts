import { type RenderingRef, renderQuotedChar, staticRendering } from '../../rendering';
import { charParameter } from '../construction';
import type { Expression } from '../index';

export class CharLiteral implements Expression {
  public static readonly constructorParams = [charParameter] as const;
  public readonly rendered: RenderingRef;
  public readonly char: string;

  constructor(char: string) {
    this.char = char;
    this.rendered = staticRendering(renderQuotedChar(char));
  }
}

// getchar, putchar: represented by FunctionApplication with IdentifierExpression
