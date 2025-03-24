import type { Renderable, RenderingRef } from '../rendering';
import type { Declaration, Expression } from '../syntax';

export abstract class Statement implements Renderable {
  public abstract readonly rendered: RenderingRef;
}

export abstract class ExpressionStatement extends Statement {
  public readonly expr: Expression;

  constructor(expr: Expression) {
    super();
    this.expr = expr;
  }
}

export abstract class DeclarationStatement extends Statement {
  public readonly decl: Declaration;

  constructor(decl: Declaration) {
    super();
    this.decl = decl;
  }
}
