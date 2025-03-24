import { type Ref, shallowRef, type ToRef, watchSyncEffect } from 'vue';

/**
 * Inverse of `UnwrapRef<T>`. Similar to `ToRefs<T>`, but also allows for plain values.
 */
export type ToMaybeRefs<T> = {
  [P in keyof T]: T[P] | ToRef<T[P]>;
};

/**
 * Eager variant of `computed(...)` that synchronously re-computes the value when triggered by a
 * dependency, but only triggers dependents if the computed value changes.
 * @see https://dev.to/linusborg/vue-when-a-computed-property-can-be-the-wrong-tool-195j
 */
export function computedEager<T>(fn: () => T): Readonly<Ref<T>> {
  const result = shallowRef<T>(undefined as T);
  watchSyncEffect(() => {
    result.value = fn();
  });
  return result;
}
