// This module provides all common aspects for meta variables.

import { shallowRef } from 'vue';

import type { Context } from '../context';
import { htmlErrorMessagePrefix } from '../frontend/errors';
import { reactiveRendering, type Renderable, Rendering, type RenderingRef } from '../rendering';
import {
  objectHasPendingSimplification,
  reactiveSimplification,
  type Simplifiable,
  type SimplificationRef,
  SuccessfulSimplification,
} from '../simplify';
import type { RefLike, Snapshot } from './snapshots';

export class MetaVariable<T extends string | Renderable> implements Simplifiable<T> {
  public readonly rendered: RenderingRef;
  public readonly simplified: SimplificationRef<T>;
  private readonly _value = shallowRef<T>();

  protected constructor(symbol: string, ctx: Context, latexSymbol: string = symbol) {
    const index = ctx.metaVariables.getNextIndex(symbol, ctx);
    ctx.metaVariables.register(this, ctx.snapshot);
    this.rendered = reactiveRendering(ctx, () => {
      const value = this._value.value;
      return value === undefined
        ? new Rendering(
            `<span class="meta-variable">${symbol}<span class="sub">${index}</span></span>`,
            `\\mathit{${latexSymbol}_${index < 10 ? index : `{${index}}`}}`,
          )
        : value;
    });
    this.simplified = reactiveSimplification(ctx, () => {
      const value = this._value.value;
      return value === undefined ? objectHasPendingSimplification : new SuccessfulSimplification(value);
    });
  }

  public get value(): T | undefined {
    return this._value.value;
  }

  /**
   * Set this variable to the given value
   * @param value The value to set this variable to
   * @param ctx The context
   * @throws If this variable has already been set
   */
  public set(value: T, ctx: Context): void {
    if (__DEV__ && this._value.value !== undefined) {
      throw new Error(`${htmlErrorMessagePrefix}(Internal) Meta variable has already been set!`);
    }
    ctx.snapshot.setRef(this._value, value);
  }
}

export class MetaVariableContext {
  private readonly nextIndex = new Map<string, RefLike<number>>();
  private readonly _variables: RefLike<MetaVariable<string | Renderable>[]> = { value: [] };

  public getNextIndex(symbol: string, ctx: Context): number {
    let ref = this.nextIndex.get(symbol);
    if (ref === undefined) {
      ref = { value: 0 };
      this.nextIndex.set(symbol, ref);
    }
    const result = ref.value;
    ctx.snapshot.setRef(ref, result + 1);
    return result;
  }

  public register(v: MetaVariable<string | Renderable>, snapshot: Snapshot): void {
    snapshot.setRef(this._variables, this.variables.concat([v]));
  }

  public get variables(): MetaVariable<string | Renderable>[] {
    return this._variables.value;
  }
}
