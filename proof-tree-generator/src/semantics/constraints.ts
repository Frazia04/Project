// Constraints are additional premises of semantic rules. Unlike normal premises, constraints do
// not generate a proof tree. Constraints are checked after any change to the entire proof tree
// because the constraint's check function might depend on the value of meta variables that will be
// set by a future rule application.

import { shallowRef } from 'vue';

import type { Context } from '../context';
import type { Renderable, RenderingRef } from '../rendering';

export class Constraint implements Renderable {
  public readonly rendered: RenderingRef;

  /**
   * Check this constraint. Throws if the constraint is failed.
   */
  public readonly check: (ctx: Context) => void;

  private readonly _fulfilled = shallowRef(false);

  /**
   * Create a new constraint.
   *
   * **Invariant:** A freshly constructed constraint must not be fulfilled!
   *
   * @param check callback that checks the constraint;
   *              returning `true` if fulfilled and `false` if pending;
   *              throwing if the constraint has failed
   * @param rendered rendering for the constraint
   */
  constructor(check: (ctx: Context) => boolean, rendered: RenderingRef) {
    this.rendered = rendered;
    this.check = (ctx) => {
      if (!this._fulfilled.value) {
        if (check(ctx)) {
          ctx.snapshot.setRef(this._fulfilled, true);
        }
      }
    };
  }

  /**
   * Whether this constraint is fulfilled (`true`) or still pending (`false`)
   */
  public get fulfilled(): boolean {
    return this._fulfilled.value;
  }
}

// Constructing a constraint involves creating a reactive Vue reference, which comes with some
// cost. Sometimes we do not need the constraint itself, but only the information that there is a
// constraint. We use this type to lazily provide the actual constraint only when required.
export type ConstraintProvider = () => Constraint;
