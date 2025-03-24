import type { SupportedFeatures } from '../../context';
import type { Ruleset } from '../rules';
import chapterBool from './chapters/booleans';
import chapterDef from './chapters/definitions';
//import chapterExceptions from './chapters/exceptions';
import chapterFun from './chapters/functions';
import chapterIO from './chapters/io';
import chapterNat from './chapters/naturals';
import chapterPairs from './chapters/pairs';
import chapterState from './chapters/state';
import type { DynamicSemanticsStatement } from './statements';

export default function getRuleset(features: SupportedFeatures): Ruleset<DynamicSemanticsStatement> {
  return [
    ['Natürliche Zahlen', chapterNat],
    ['Boolesche Werte', chapterBool],
    ...(features.decl
      ? ([
          ['Wertedefinitionen', chapterDef],
          ['Funktionen', chapterFun],
        ] as const)
      : []),
    ...(features.pairs ? ([['Paare', chapterPairs]] as const) : []),
    ...(features.io ? ([['Ein- und Ausgabe', chapterIO]] as const) : []),
    ...(features.store ? ([['Zustand', chapterState]] as const) : []),
    //...(features.exceptions ? ([['Ausnahmen', chapterExceptions]] as const) : []),
  ];
}

export type { Value, Environment } from './values';
export type { Store } from './stores';
