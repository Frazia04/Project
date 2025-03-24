// This module provides change-tracking, such that changes can be undone.

import { type EffectScope, effectScope, type Ref } from 'vue';

/**
 * Like Ref<T> from vue, but does not necessarily have reactivity.
 */
export type RefLike<T> = Pick<Ref<T>, 'value'>;

/**
 * A snapshot tracks changes, in particular changes to meta variables. The changes can later be undone.
 * Furthermore, it manages a Vue effect scope that collects all reactive effects created within that snapshot.
 */
export class Snapshot {
  public readonly effectScope: EffectScope;
  private readonly changes: Change<unknown>[] = [];

  constructor(parentEffectScope: EffectScope) {
    this.effectScope = parentEffectScope.run(effectScope)!;
  }

  /**
   * Set a reference and track the mutation in this snapshot
   * @param ref The reference to set
   * @param value The value to set the reference to
   */
  public setRef<T>(ref: RefLike<T>, value: T): void {
    this.changes.push(new Change(ref, ref.value, value));
    ref.value = value;
  }

  /**
   * Undo all changes performed in this snapshot
   */
  public undo(): void {
    let change: Change<unknown> | undefined;
    while ((change = this.changes.pop()) !== undefined) {
      change.undo();
    }
    this.effectScope.stop();
  }
}

class Change<T> {
  public readonly ref: RefLike<T>;
  public readonly oldValue: T;
  public readonly newValue: T;

  constructor(ref: RefLike<T>, oldValue: T, newValue: T) {
    this.ref = ref;
    this.oldValue = oldValue;
    this.newValue = newValue;
  }

  public undo(): void {
    if (__DEV__ && this.ref.value !== this.newValue) {
      console.error('Unexpected current value for undo operation', this);
    }
    this.ref.value = this.oldValue;
  }
}
