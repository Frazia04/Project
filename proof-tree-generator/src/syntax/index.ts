// This module provides classes for the AST of our language.

import type { Simplifiable, typeDifferentiator } from '../simplify';

export interface Expression extends Simplifiable<Expression> {
  readonly [typeDifferentiator]?: unique symbol;
}

export interface Declaration extends Simplifiable<Declaration> {
  readonly [typeDifferentiator]?: unique symbol;
}

export * from './metaVariables';
export * from './utils';
export * from './chapters/booleans';
export * from './chapters/definitions';
export * from './chapters/functions';
export * from './chapters/io';
export * from './chapters/naturals';
export * from './chapters/pairs';
export * from './chapters/state';
