/**
 * From `Tuple` take only the components that are a subtype of or equal to the desired `Filter` type
 */
export type FilterTuple<Filter, Tuple extends unknown[]> = Tuple extends readonly [infer Head, ...infer Tail]
  ? Head extends Filter
    ? readonly [Head, ...FilterTuple<Filter, Tail>]
    : FilterTuple<Filter, Tail>
  : readonly [];
