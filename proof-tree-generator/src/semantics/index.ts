import type { Ruleset } from './rules';
import type { Statement } from './statements';

export class ProofObligation<T extends Statement> {
  public readonly ruleset: Ruleset<T>;
  public readonly stmt: T;

  constructor(ruleset: Ruleset<T>, stmt: T) {
    this.ruleset = ruleset;
    this.stmt = stmt;
  }
}

export const availableSemantics = ['static', 'dynamic'] as const;
export type Semantics = (typeof availableSemantics)[number];

export type { Statement } from './statements';
export type { Rule, Ruleset } from './rules';
