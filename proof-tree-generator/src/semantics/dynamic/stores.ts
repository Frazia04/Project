import type { Context } from '../../context';
import { defineMappingClasses, type Mapping, type MappingVariable } from '../mappings';
import { Address, type Value, valuesMustMatch } from './values';

export type Store = Mapping<Value, Value>;
export type StoreVariable = MappingVariable<Value, Value>;

export const {
  BasicMapping: BasicStore,
  CommaOperatorMapping: CommaOperatorStore,
  MappingVariable: StoreVariable,
  LookupResult: StoreLookupResult,
  mappingMustContainKey: storeMustContainAddress,
  mappingMustNotContainKey: storeMustNotContainAddress,
  mappingsMustMatch: storesMustMatch,
} = defineMappingClasses<Value, Value>(
  '&sigma;',
  '\\sigma',
  'der Speicher',
  'im Speicher',
  'die Adresse',
  valuesMustMatch,
  (a1, a2) => a1 instanceof Address && a2 instanceof Address && a1.index === a2.index,
);

export function nextAddress(ctx: Context): Address {
  const index = ctx.nextAddressIndex.value;
  ctx.snapshot.setRef(ctx.nextAddressIndex, index + 1n);
  return new Address(index);
}
