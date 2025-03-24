<script setup lang="ts">
import { expectEOF, expectSingleResult } from 'typescript-parsec';
import { type Parameter, FunctionClosure, Environment, type PrimitiveValue, type DiagramNodeProp, PendingValue, createEvaluator, type Value } from './../evaluator';
import { EXPR, PROGRAM } from '@/parser/rules';
import { lexer } from '@/parser/tok';
import { shallowRef, computed, ref } from 'vue';

import DiagramNode from './DiagramNode.vue';
import type { MultiParamFunctionDecl } from '@/parser/ast';
import { standardLibrary } from '../stdlib';


const props = defineProps<{
  funDecl: string;
  expr: string;
  stopAtRecCall: boolean;
  onlyShowRecDepth: boolean;
  shortenNodeContent: boolean;
}>();


const colors = ['background-color: #ffffff', 'background-color: #A9A9A9', 'background-color: #606060']

const prop = ref<DiagramNodeProp>();

const suc = shallowRef(false);
const err = shallowRef<string>("");
const result = shallowRef<() => Value | PendingValue>();
const recCall = shallowRef(true);

const functionsToDebug = ref<[string, string][]>();

try {
  // add standard library, defined in stdlib.ts
  const functionDeclarations = standardLibrary.join(" \n") + " \n" + props.funDecl;
  __DEV__ && console.log("funDecls:", functionDeclarations);
  
  // parse input, create FunctionClosure and Environment
  console.log(expectEOF(PROGRAM.parse(lexer.parse(functionDeclarations))));
  const prog = expectSingleResult(expectEOF(PROGRAM.parse(lexer.parse(functionDeclarations))))
  __DEV__ && console.log("Prog:", prog)
  const funDecls: MultiParamFunctionDecl[] = prog.declarations;
  const env = new Environment();
  let color = 0;

  funDecls.forEach(funDecl => {
    let funClosure;

    if (funDecl.parameters === undefined) {
      funClosure = new FunctionClosure(funDecl.name, undefined, funDecl.expr);
    } else {
      let params: Parameter[] = [];
      funDecl.parameters.forEach(param => 
        param.parameters.forEach(innerParam => {
          params.push(innerParam[0])
        })
      )
      funClosure = new FunctionClosure(funDecl.name, params, funDecl.expr);
    }
    env.addOrUpdateBinding(funClosure.name, funClosure);

    // alternating colors if more than one function is debugged
    if (funDecl.toDebug) {
      env.addFunctionToDebug(funDecl.name, colors[color % colors.length]);
      color++;
    }
  });
  __DEV__ && console.log("Functions to debug:", env.getFunctionsToDebug());
  functionsToDebug.value = env.getFunctionsToDebug();

  const expr = expectSingleResult(expectEOF(EXPR.parse(lexer.parse(props.expr))));
  __DEV__ && console.log("Expression to evaluate:", expr);
  
  const { childEdges, childProps, evaluateExpression } = createEvaluator(0);
  result.value = evaluateExpression(env, expr, props.stopAtRecCall);

  if (childProps.length < 1) {
    recCall.value = false;
  }
  prop.value = childProps[0];

  suc.value = true;

} catch (e: any) {
  console.log("Exception caught: ", e)
  err.value = e;
}

const output = computed(() => {
  return result.value;
})

function updateResult(result: PrimitiveValue | PendingValue): void {
  prop.value!.recCall = result;
}
</script>

<template>  
  <div v-if="suc">

    <div v-if="functionsToDebug && functionsToDebug.length > 1" class="colors">
      <div v-for="fun of functionsToDebug">
        <label>{{ fun[0] }}</label>
        <div class="color" :style="fun[1]"></div>
      </div>
    </div>

    <h2 v-if="!recCall">Output: {{ output }}</h2>      
    <div v-else class="diagram">
      <div class="graph">
          <DiagramNode v-if="prop" :prop="prop" :ellipsis="false" :stopAtRecCall="stopAtRecCall" :onlyShowRecDepth="onlyShowRecDepth" :shortenNodeContent="shortenNodeContent" @computed="updateResult" /> 
      </div>
    </div>
  </div>
  <div v-else>
    <p style="color: red; font-size: large;">Exception caught: {{ err }}</p>
  </div>

</template>


<style>

</style>