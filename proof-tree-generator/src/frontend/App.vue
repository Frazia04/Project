<script setup lang="ts">
import {
  computed,
  getCurrentScope,
  markRaw,
  reactive,
  type Ref,
  shallowReactive,
  shallowRef,
  toRaw,
  toRef,
  watch,
} from 'vue';

import { featureFlagExceptions } from '../constants';
import type { Context, DisplaySettings, SupportedFeatures } from '../context';
import type { Example } from '../examples';
import type { Semantics } from '../semantics';
import getDynamicSemanticsRuleset from '../semantics/dynamic';
import {
  DynamicSemanticsDeclarationStatement,
  DynamicSemanticsExpressionStatement,
  type DynamicSemanticsStatement,
} from '../semantics/dynamic/statements';
import { BasicStore, type Store } from '../semantics/dynamic/stores';
import { BasicEnvironment } from '../semantics/dynamic/values';
import getStaticSemanticsRuleset from '../semantics/static';
import {
  StaticSemanticsDeclarationStatement,
  StaticSemanticsExpressionStatement,
  type StaticSemanticsStatement,
} from '../semantics/static/statements';
import { BasicSignature } from '../semantics/static/types';
import { MetaVariableContext } from '../state/metaVariables';
import { Snapshot } from '../state/snapshots';
import {
  parseDeclaration,
  parseEnvironment,
  parseExpression,
  parseExternalEffects,
  parseSignature,
  parseStore,
  parseType,
  parseTypeDefs,
  parseValue,
} from '../syntax/parser';
import type { ToMaybeRefs } from '../utils';
import { computedEager } from '../utils';
import ErrorMessage from './ErrorMessage.vue';
import ExamplesList from './ExamplesList.vue';
import ProofTree, { type ProofTreeProp } from './ProofTree.vue';

// --------------------------------------------------------------------------------------------------------------------

// Get this component's effect scope
const componentEffectScope = getCurrentScope()!;

// State for feature selection
const features: SupportedFeatures = shallowReactive({
  decl: false,
  pairs: false,
  io: false,
  store: false,
  exceptions: false,
});

// State for display settings
const displaySettings: DisplaySettings = shallowReactive({
  showEmptySignature: false,
  showEmptyEnvironment: false,
  showEmptyStore: false,
});

// State for semantics selection
const semantics = shallowRef<Semantics>('static');
const exprOrDecl = shallowRef<'expression' | 'declaration'>('expression');

// Maintain consistency between `features.decl` and `exprOrDecl`
watch(
  () => features.decl,
  (enabled) => {
    if (!enabled) {
      exprOrDecl.value = 'expression';
    }
  },
);
watch(
  () => exprOrDecl.value,
  (mode) => {
    if (mode === 'declaration') {
      features.decl = true;
    }
  },
);

// --------------------------------------------------------------------------------------------------------------------
// State and validation for inputs

interface Input {
  // html id for the input
  readonly id: string;

  // whether the input is visible
  readonly visible: boolean;

  // label text
  readonly label: string;

  // value (bound to the html input value)
  value: string;

  // error message, if any
  error: string | undefined;

  // whether to apply the 'shake' css class to the error message (animation)
  shakeError: boolean;

  // validation function for this input (throws an error if input is invalid)
  readonly validator: (s: string, ctx: Context) => void;

  // value and error message of last validation
  readonly lastValidation: {
    value: string;
    error: string | undefined;
  };
}

function createInput(
  id: string,
  label: string | Ref<string>,
  validator: (s: string, ctx: Context) => void,
  visible: true | Ref<boolean> = true,
  value = '',
): Input {
  const input: Input = reactive<ToMaybeRefs<Input>>({
    id,
    visible,
    label,
    value,
    error: undefined,
    shakeError: false,
    validator: markRaw(validator),
    lastValidation: markRaw({
      value: '',
      error: undefined,
    }),
  });

  // clear error on input change
  watch(
    () => input.value,
    () => {
      input.error = undefined;
      input.shakeError = false;
    },
  );

  // automatically stop shake animation
  let stopShakeTimeout: number;
  watch(
    () => input.shakeError,
    (shakeError) => {
      if (shakeError) {
        stopShakeTimeout = window.setTimeout(() => (input.shakeError = false), 500);
      } else {
        window.clearTimeout(stopShakeTimeout);
      }
    },
  );
  return input;
}

function validateInput(input: Input): void {
  const value = input.value;
  input.lastValidation.value = value;
  const ctx: Context = {
    features: toRaw(features),
    displaySettings: toRaw(displaySettings),
    requireTypes: semantics.value === 'static',
    metaVariables: new MetaVariableContext(),
    nextAddressIndex: { value: 0n },
    snapshot: new Snapshot(componentEffectScope),
  };
  try {
    input.validator(value, ctx);
    input.lastValidation.error = input.error = undefined;
  } catch (err) {
    if (__DEV__) {
      console.error(err);
    }
    input.lastValidation.error = input.error = err instanceof Error ? err.message : String(err);
  }
  ctx.snapshot.effectScope.stop();
}

function validateInputIfChanged(input: Input): void {
  if (input.value === input.lastValidation.value) {
    input.error = input.lastValidation.error;
  } else {
    validateInput(input);
  }
}

const inputTypeDefs = createInput(
  'typeDefs',
  'Typdefinitionen für Ausnahmen',
  parseTypeDefs,
  toRef(features, 'exceptions'),
);
const inputSigEnv = createInput(
  'sigEnv',
  computedEager(() => (semantics.value === 'static' ? '&Sigma;' : '&delta;')),
  (s, ctx) => (semantics.value === 'static' ? parseSignature : parseEnvironment)(s, ctx),
  toRef(features, 'decl'),
  '{}',
);
const showStore = computedEager(() => features.store && semantics.value === 'dynamic');
const inputStore = createInput('store', '&sigma;', parseStore, showStore, '{}');
const inputExprDecl = createInput(
  'exprDecl',
  computedEager(() => (exprOrDecl.value === 'expression' ? 'e' : 'd')),
  (s, ctx) => {
    const [correctParser, wrongParser, parsedWrongError] =
      exprOrDecl.value === 'expression'
        ? [parseExpression, parseDeclaration, 'Dies ist kein Ausdruck, sondern eine Deklaration.']
        : [parseDeclaration, parseExpression, 'Dies ist keine Deklaration, sondern ein Ausdruck.'];
    try {
      correctParser(s, ctx);
    } catch (err1) {
      try {
        wrongParser(s, { ...ctx, requireTypes: false });
      } catch (err2) {
        throw err1; // error from both parsers, use correctParser error
      }
      // correct parser gives error, wrong parser is satisfied
      // -> show error that exprOrDecl selection is wrong
      throw new Error(parsedWrongError);
    }
  },
);
const inputExternalEffects = createInput(
  'externalEffects',
  't',
  parseExternalEffects,
  computedEager(() => features.io && semantics.value === 'dynamic'),
);
const inputResult = createInput(
  'result',
  computedEager(() =>
    semantics.value === 'static'
      ? exprOrDecl.value === 'expression'
        ? 't'
        : "&Sigma;'"
      : exprOrDecl.value === 'expression'
      ? 'v'
      : "&delta;'",
  ),
  (s, ctx) =>
    (semantics.value === 'static'
      ? exprOrDecl.value === 'expression'
        ? parseType
        : parseSignature
      : exprOrDecl.value === 'expression'
      ? parseValue
      : parseEnvironment)(s, ctx),
);
const inputResultStore = createInput('resultStore', "&sigma;'", parseStore, showStore);
const simpleInputs: Input[] = [
  inputSigEnv,
  inputStore,
  inputExprDecl,
  inputExternalEffects,
  inputResult,
  inputResultStore,
];
const inputs: Input[] = simpleInputs.concat(inputTypeDefs);

// Revalidate all visible inputs when semantics, exprOrDecl, or the enabled features change
watch([semantics, exprOrDecl, features], () => {
  for (const input of inputs) {
    if (input.visible) {
      validateInput(input);
    }
  }
});

// --------------------------------------------------------------------------------------------------------------------

// State for parsed proof obligation
// eslint-disable-next-line @typescript-eslint/no-redundant-type-constituents
const proofTreeProp = shallowRef<ProofTreeProp<StaticSemanticsStatement> | ProofTreeProp<DynamicSemanticsStatement>>();

// Parse the user-provided input
function parseInput(): void {
  // Check whether we have an input error and animate it
  let haveError = false;
  for (const input of inputs) {
    if (input.visible) {
      validateInputIfChanged(input);
      if (input.error) {
        haveError = input.shakeError = true;
      }
    }
  }

  // Without error, we can continue
  if (!haveError) {
    const isStaticSemantics = semantics.value === 'static';
    const ctx: Context = {
      features: toRaw(features),
      displaySettings,
      requireTypes: isStaticSemantics,
      metaVariables: new MetaVariableContext(),
      nextAddressIndex: { value: 0n },
      snapshot: new Snapshot(componentEffectScope),
    };

    if (isStaticSemantics) {
      const sig = inputSigEnv.visible ? parseSignature(inputSigEnv.value, ctx) : new BasicSignature();
      proofTreeProp.value = {
        ctx,
        ruleset: getStaticSemanticsRuleset(ctx.features),
        stmt:
          exprOrDecl.value === 'expression'
            ? new StaticSemanticsExpressionStatement(
                sig,
                parseExpression(inputExprDecl.value, ctx),
                parseType(inputResult.value, ctx),
                ctx,
              )
            : new StaticSemanticsDeclarationStatement(
                sig,
                parseDeclaration(inputExprDecl.value, ctx),
                parseSignature(inputResult.value, ctx),
                ctx,
              ),
      };
    } else {
      const env = inputSigEnv.visible ? parseEnvironment(inputSigEnv.value, ctx) : new BasicEnvironment();
      let store: Store;
      let resultStore: Store;
      if (showStore.value) {
        store = parseStore(inputStore.value, ctx);
        resultStore = parseStore(inputResultStore.value, ctx);
      } else {
        store = resultStore = new BasicStore();
      }
      proofTreeProp.value = {
        ctx,
        ruleset: getDynamicSemanticsRuleset(ctx.features),
        stmt:
          exprOrDecl.value === 'expression'
            ? new DynamicSemanticsExpressionStatement(
                env,
                store,
                parseExpression(inputExprDecl.value, ctx),
                parseExternalEffects(inputExternalEffects.value, ctx),
                parseValue(inputResult.value, ctx),
                resultStore,
                ctx,
              )
            : new DynamicSemanticsDeclarationStatement(
                env,
                store,
                parseDeclaration(inputExprDecl.value, ctx),
                parseExternalEffects(inputExternalEffects.value, ctx),
                parseEnvironment(inputResult.value, ctx),
                resultStore,
                ctx,
              ),
      };
    }

    // Scroll to top
    window.scrollTo(0, 0);
  }
}

function disposeTree(): void {
  if (proofTreeProp.value) {
    proofTreeProp.value.ctx.snapshot.effectScope.stop();
    proofTreeProp.value = undefined;

    // Scroll to top
    window.scrollTo(0, 0);
  }
}

// --------------------------------------------------------------------------------------------------------------------

// Examples

function useExample({
  exprOrDecl: exampleExprOrDecl,
  typeDefs,
  sigEnv,
  store,
  exprDecl,
  externalEffects,
  result,
  resultStore,
}: Example): void {
  exprOrDecl.value = exampleExprOrDecl;
  inputTypeDefs.value = typeDefs;
  inputSigEnv.value = sigEnv;
  inputStore.value = store;
  inputExprDecl.value = exprDecl;
  inputExternalEffects.value = externalEffects;
  inputResult.value = result;
  inputResultStore.value = resultStore;
}

// --------------------------------------------------------------------------------------------------------------------

// Computed property used in template, listing things that we can use placeholders for
const placeholderDescription = computed(() => {
  const names = ['Ausdrücke'];
  if (features.decl) names.push('Deklarationen');
  names.push(semantics.value === 'static' ? 'Typen' : 'Werte');
  if (features.decl) names.push(semantics.value === 'static' ? 'Signaturen' : 'Umgebungen');
  if (showStore.value) names.push('Speicher');
  const last = names.pop()!;
  return names.join(', ') + ' und ' + last;
});
</script>

<template>
  <h1>Beweisbaum Werkzeug</h1>

  <!-- Landing page with inputs -->
  <template v-if="!proofTreeProp">
    <!-- Features and semantics selection -->
    <div class="configuration">
      <h2>Gewünschte Mini-F# Sprachfeatures:</h2>
      <label>
        <input type="checkbox" checked="true" disabled="true" />
        <span> Natürliche Zahlen</span> <span>(Kapitel 3.1)</span>
      </label>
      <label>
        <input type="checkbox" checked="true" disabled="true" />
        <span> Boolesche Werte</span> <span>(Kapitel 3.2)</span>
      </label>
      <label class="clickable">
        <input v-model="features.decl" type="checkbox" />
        <span> Wertedefinitionen &amp; Funktionen</span> <span>(Kapitel 3.3 bis 3.6)</span>
      </label>
      <label class="clickable">
        <input v-model="features.pairs" type="checkbox" />
        <span> Paare</span> <span>(Kapitel 4.1.1)</span>
      </label>
      <label class="clickable">
        <input v-model="features.io" type="checkbox" />
        <span> Ein- und Ausgabe</span> <span>(Kapitel 7.1)</span>
        <em> Das leere <span>Tupel &lsquo;()&rsquo;</span> benötigt &ldquo;Paare&rdquo;.</em>
      </label>
      <label class="clickable">
        <input v-model="features.store" type="checkbox" />
        <span> Zustand</span> <span>(Kapitel 7.2)</span>
      </label>
      <label v-if="featureFlagExceptions" class="clickable">
        <input v-model="features.exceptions" type="checkbox" />
        <span> Ausnahmen</span> <span>(Kapitel 7.4)</span>
      </label>

      <h2>Gewünschte Semantik:</h2>
      <label class="clickable">
        <input v-model="semantics" type="radio" value="static" class="clickable" />
        <span> Statische Semantik</span>
      </label>
      <label class="clickable">
        <input v-model="semantics" type="radio" value="dynamic" class="clickable" />
        <span> Dynamische Semantik</span>
      </label>

      <div v-show="features.decl">
        <h2>Eingegeben wird:</h2>
        <label class="clickable">
          <input v-model="exprOrDecl" type="radio" value="expression" class="clickable" />
          <span> ein Ausdruck</span>
        </label>
        <label class="clickable">
          <input v-model="exprOrDecl" type="radio" value="declaration" class="clickable" />
          <span> eine Deklaration</span>
        </label>
      </div>
    </div>

    <!-- Input for the statement to prove -->
    <div class="input">
      <!-- Display the statement with placeholders only -->
      <h2>
        <template v-if="semantics === 'static'">
          <template v-if="features.decl"><label for="sigEnv">&Sigma;</label> &vdash; </template>
          <label for="exprDecl" v-text="exprOrDecl === 'expression' ? 'e' : 'd'" />
          :
          <label for="result" v-text="exprOrDecl === 'expression' ? 't' : '&Sigma;\''" />
        </template>
        <template v-else>
          <template v-if="features.decl"><label for="sigEnv">&delta;</label> &vdash; </template>
          <template v-if="features.store"><label for="store">&sigma;</label> &Vert; </template>
          <label for="exprDecl" v-text="exprOrDecl === 'expression' ? 'e' : 'd'" />
          &DoubleDownArrow;<label v-if="features.io" for="externalEffects" class="sub">t</label>{{ ' ' }}
          <label for="result" v-text="exprOrDecl === 'expression' ? 'v' : '&delta;\''" />
          <template v-if="features.store"> &Vert; <label for="resultStore">&sigma;'</label></template>
        </template>
      </h2>

      <!-- Input field for each placeholder -->
      <div v-show="inputTypeDefs.visible">
        <label :for="inputTypeDefs.id" v-html="inputTypeDefs.label + ':'" />
        <textarea
          :id="inputTypeDefs.id"
          v-model="inputTypeDefs.value"
          rows="5"
          @blur.passive="validateInputIfChanged(inputTypeDefs)"
        />
        <ErrorMessage tag="p" class="error" :msg="inputTypeDefs.error" :class="{ shake: inputTypeDefs.shakeError }" />
      </div>
      <table>
        <template v-for="input of simpleInputs" :key="input.id">
          <tr v-show="input.visible">
            <td><label :for="input.id" v-html="input.label + ':'" /></td>
            <td>
              <input
                :id="input.id"
                v-model="input.value"
                placeholder="?"
                @blur.passive="validateInputIfChanged(input)"
                @keyup.enter.passive="parseInput"
              />
            </td>
          </tr>
          <tr v-if="input.visible && input.error">
            <td></td>
            <td><ErrorMessage tag="p" class="error" :msg="input.error" :class="{ shake: input.shakeError }" /></td>
          </tr>
        </template>
      </table>

      <p>
        Du kannst in den Eingabefeldern Fragezeichen (?) als Platzhalter für beliebige
        {{ placeholderDescription }} verwenden. Der tatsächliche Inhalt der Platzhalter wird dann beim Aufbau des
        Beweisbaumes ermittelt. Leere Eingabefelder sind gleichbedeutend mit einem Platzhalter für das gesamte
        Eingabefeld.
      </p>
    </div>

    <!-- Start button -->
    <p><button @click.passive="parseInput">Weiter</button></p>

    <!-- Examples -->
    <ExamplesList :features="features" :semantics="semantics" @use-example="useExample" />
  </template>

  <!-- We have a parsed statement to prove -->
  <template v-else>
    <!-- Display settings -->
    <div v-if="features.decl || (semantics === 'dynamic' && features.store)" class="configuration">
      <h2>Anzeige-Einstellungen:</h2>
      <template v-if="features.decl">
        <label v-if="semantics === 'static'" class="clickable">
          <input v-model="displaySettings.showEmptySignature" type="checkbox" />
          Leere Signaturen anzeigen
        </label>
        <label v-else class="clickable">
          <input v-model="displaySettings.showEmptyEnvironment" type="checkbox" />
          Leere Umgebungen anzeigen
        </label>
      </template>
      <label v-if="semantics === 'dynamic' && features.store" class="clickable">
        <input v-model="displaySettings.showEmptyStore" type="checkbox" />
        Leere Speicher anzeigen
      </label>
    </div>

    <!-- Proof tree -->
    <ProofTree :prop="proofTreeProp as any /* TODO: type-safety */" @dispose-tree="disposeTree" />
  </template>
</template>
