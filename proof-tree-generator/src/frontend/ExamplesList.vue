<script setup lang="ts">
import { getCurrentScope, type Ref } from 'vue';

import type { Context, SupportedFeatures } from '../context';
import { type Example, type ExampleSelector, examplesGroups } from '../examples';
import type { Semantics, Statement } from '../semantics';
import type { Store } from '../semantics/dynamic';
import {
  DynamicSemanticsDeclarationStatement,
  DynamicSemanticsExpressionStatement,
} from '../semantics/dynamic/statements';
import { BasicStore } from '../semantics/dynamic/stores';
import { BasicEnvironment } from '../semantics/dynamic/values';
import {
  StaticSemanticsDeclarationStatement,
  StaticSemanticsExpressionStatement,
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
  parseValue,
} from '../syntax/parser';
import { computedEager } from '../utils';

interface ParsedExamplesGroup {
  readonly visible: Readonly<Ref<boolean>>;
  readonly examples: readonly ParsedExample[];
}

interface ParsedExample {
  readonly example: Example;
  readonly stmt: Statement;
}

// Get properties passed from parent component (App.vue)
const props = defineProps<{
  features: SupportedFeatures;
  semantics: Semantics;
}>();

// Define the events we can emit to the parent component
const emit = defineEmits<(event: 'useExample', example: Example) => void>();

// Get this component's effect scope
const componentEffectScope = getCurrentScope()!;

// Array of each example group's `visible` flag
const visibleFlags: Readonly<Ref<boolean>>[] = [];

// Count the number of examples with parsing error
let errorCounter = 0;

// Parse each examples group
const parsedExamplesGroups = examplesGroups.map<ParsedExamplesGroup>(({ selector, examples }) => {
  const visible = computedEager(() => checkSelector(selector));
  visibleFlags.push(visible);

  const parsedExamples: ParsedExample[] = [];
  for (const example of examples) {
    try {
      parsedExamples.push({ example, stmt: parseExample(selector, example) });
    } catch (err) {
      if (__DEV__) {
        console.log('Error parsing example', selector, example, err);
        errorCounter++;
      }
    }
  }

  return {
    visible,
    examples: parsedExamples,
  };
});

// Warn if we have any parsing errors
if (__DEV__ && errorCounter) {
  alert(`${errorCounter} examples did not parse, check the browser's developer console for details!`);
}

// Check whether a selector matches the current feature and semantics selection
function checkSelector({ decl, pairs, io, store, exceptions, semantics }: ExampleSelector): boolean {
  return (
    decl === props.features.decl &&
    pairs === props.features.pairs &&
    io === props.features.io &&
    store === props.features.store &&
    exceptions === props.features.exceptions &&
    semantics === props.semantics
  );
}

// Parse a single example
function parseExample({ semantics, ...features }: ExampleSelector, example: Example): Statement {
  const isStaticSemantics = semantics === 'static';
  const ctx: Context = {
    features,
    displaySettings: {
      showEmptySignature: true,
      showEmptyEnvironment: true,
      showEmptyStore: true,
    },
    requireTypes: isStaticSemantics,
    metaVariables: new MetaVariableContext(),
    nextAddressIndex: { value: 0n },
    snapshot: new Snapshot(componentEffectScope),
  };

  if (isStaticSemantics) {
    const sig = features.decl ? parseSignature(example.sigEnv, ctx) : new BasicSignature();
    return example.exprOrDecl === 'expression'
      ? new StaticSemanticsExpressionStatement(
          sig,
          parseExpression(example.exprDecl, ctx),
          parseType(example.result, ctx),
          ctx,
        )
      : new StaticSemanticsDeclarationStatement(
          sig,
          parseDeclaration(example.exprDecl, ctx),
          parseSignature(example.result, ctx),
          ctx,
        );
  } else {
    const env = features.decl ? parseEnvironment(example.sigEnv, ctx) : new BasicEnvironment();
    let store: Store;
    let resultStore: Store;
    if (features.store) {
      store = parseStore(example.store, ctx);
      resultStore = parseStore(example.resultStore, ctx);
    } else {
      store = resultStore = new BasicStore();
    }
    return example.exprOrDecl === 'expression'
      ? new DynamicSemanticsExpressionStatement(
          env,
          store,
          parseExpression(example.exprDecl, ctx),
          parseExternalEffects(example.externalEffects, ctx),
          parseValue(example.result, ctx),
          resultStore,
          ctx,
        )
      : new DynamicSemanticsDeclarationStatement(
          env,
          store,
          parseDeclaration(example.exprDecl, ctx),
          parseExternalEffects(example.externalEffects, ctx),
          parseEnvironment(example.result, ctx),
          resultStore,
          ctx,
        );
  }
}
</script>

<template>
  <!-- Examples -->
  <div v-if="visibleFlags.some((visible) => visible.value)" class="examples">
    <h2>Beispiele</h2>
    <ul class="examples">
      <template v-for="{ visible, examples } of parsedExamplesGroups">
        <template v-if="visible.value">
          <!-- eslint-disable-next-line vue/require-v-for-key -- because child nodes are stateless -->
          <li
            v-for="{ example, stmt } of examples"
            :class="{
              valid: example.valid === true,
              invalid: example.valid === false,
            } /* TODO: change to example.valid ? 'valid' : 'invalid' once all examples have been categorized */"
            @click.passive="emit('useExample', example)"
            v-html="stmt.rendered.value.html"
          />
        </template>
      </template>
    </ul>
    <p>
      Beispiele können durch Anklicken in die Eingabefelder eingefüllt werden. Die Liste der angezeigten Beispielen
      hängt von den ausgewählten Mini-F# Sprachfeatures und der gewählten Semantik ab. Mit
      <span class="invalid" /> markierte Beispiele sind ungültig, d.h. der Beweisbaum kann hier nicht abgeschlossen
      werden. Mit <span class="valid" /> markierte Beispiele sind gültig. Bei Beispielen ohne Markierung wurde diese
      Information noch nicht ins System eingepflegt.
    </p>
  </div>
</template>
