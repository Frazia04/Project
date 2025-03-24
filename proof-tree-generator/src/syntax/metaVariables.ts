import type { Context } from '../context';
import { MetaVariable } from '../state/metaVariables';
import type { Declaration, Expression } from '.';

export class ExpressionVariable extends MetaVariable<Expression> implements Expression {
  constructor(ctx: Context) {
    super('e', ctx);
  }
}

export class DeclarationVariable extends MetaVariable<Declaration> implements Declaration {
  constructor(ctx: Context) {
    super('d', ctx);
  }
}
