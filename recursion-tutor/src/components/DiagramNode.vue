<script lang="ts">
import { expectEOF, expectSingleResult } from 'typescript-parsec';
import { EXPR } from '@/parser/rules';
import { lexer } from '@/parser/tok';
import { ref, shallowRef, watch, watchEffect } from 'vue';
import { type PrimitiveValue, createEvaluator, PendingValue, type DiagramNodeProp } from '@/evaluator';
import { getDataStructureDepth, getTextWidth, longestChild, printResult } from '@/parser/utils';
import gsap from 'gsap';
import { idCounterStore } from '@/plugins/idcounter';

</script>

<script setup lang="ts">

  const props = defineProps<{ 
    prop: DiagramNodeProp,
    ellipsis: boolean,
    stopAtRecCall: boolean;
    onlyShowRecDepth: boolean;
    shortenNodeContent: boolean;
  }>();

  const idCounter = idCounterStore();
  const id = idCounter.getNextId();

  const resultNode = ref<PrimitiveValue>();
  const ellipsis = shallowRef(false); // TODO rename
  const showPopup = shallowRef(false);
  const showEdgePopup = shallowRef(false);
  const edgePopup = shallowRef()
  const depth = shallowRef();
  const showFunName = props.prop.funName && !props.onlyShowRecDepth;
  var resultEdges: (String | undefined)[] = [];

  const emit = defineEmits<{
    (event: 'computed', result: PrimitiveValue | PendingValue): void
  }>();

  const { childEdges, childProps, evaluateExpression } = createEvaluator(id);

  var evaluated = false;

  const parameterPopup = props.prop.parameter.toString();


  // if only the recursion depth is supposed to be shown, calculate it and override prop.parameter
  if (props.onlyShowRecDepth) {
    const expr = expectSingleResult(expectEOF(EXPR.parse(lexer.parse(parameterPopup))))
    depth.value = getDataStructureDepth(expr);
    __DEV__ && console.log("Datastructure depth:", depth.value);
    props.prop.parameter = depth.value.toString();
    // props.prop.parameter = (parameterPopup.split("(").length - 1).toString();
  }

  // if node content shall be shortened,
  if (!props.onlyShowRecDepth && props.shortenNodeContent && parameterPopup.length > 7) {
    props.prop.parameter = parameterPopup.substring(0, 7) + "...";
  }

  // check if space needed by parameter in node is too large for the default node size
  const doc = document.documentElement;
  const nodeSize = parseInt(getComputedStyle(doc).getPropertyValue("--node-size"));
  let paramLength;
  if (props.prop.funName != undefined) {
    paramLength = parseInt(getTextWidth(props.prop.funName + props.prop.parameter.toString()))
  } else {
    paramLength = parseInt(getTextWidth(props.prop.parameter.toString()));
  }

  if (nodeSize < paramLength + 10) {
    doc.style.setProperty("--node-size", String(paramLength + 10) + "px");
  };
  
  if ((paramLength > 200 || props.ellipsis)) {
    ellipsis.value = true;
  }

  function continueEvaluation(stop: boolean) {
    if (evaluated) {
      __DEV__ && console.log("already evaluated");
      return;
    } else {
      evaluated = true;
    }

    // animations
    gsap.to('#res'+id, {
      backgroundColor: 'white',
      duration: 0.1,
      onComplete: () => {
        gsap.to('#res'+id, {
          backgroundColor: '#ffff62',
          duration: 0.4,
        });
      },
    });
    gsap.to('#node'+id, {
      backgroundColor: 'white',
      duration: 0.1,
      onComplete: () => {
        gsap.to('#node'+id, {
          backgroundColor: '#ffff62',
          duration: 0.4,
        });
      },
    });

    const resultGetter = evaluateExpression(props.prop.env, props.prop.expr, stop);
    
    watchEffect(() => {
      const result = resultGetter() as PrimitiveValue | PendingValue;
      //  __DEV__ && console.log("Watcheffect of node", props.prop.id, "triggered", result instanceof PendingValue);

      if (result instanceof PendingValue) { // result can still not be computed
        __DEV__ && console.log("result:", result)
        resultNode.value = undefined;
        if (resultEdges.length == 0) {
          // allow only once
          var resultEdge = printResult(result)
          edgePopup.value = resultEdge;
          
          // possible shorten the edge
          __DEV__ && console.log("Result edge length:", resultEdge.length);
          if (!props.onlyShowRecDepth && props.shortenNodeContent && resultEdge.length > 17){
            resultEdge = resultEdge.substring(0,17) + "...";
          } else if (resultEdge.length > 200) {
            resultEdge = resultEdge.substring(0,200) + "...";
            showEdgePopup.value = true;
          }
          resultEdges.push(resultEdge);
          
          // check if width of arrow is less than the width needed for the text
          if (childProps.length <= 1) { 
            const edgeLength = parseInt(getComputedStyle(doc).getPropertyValue("--node-spacing-v"));
            
            const textLength = Math.max(
              parseInt(getTextWidth(resultEdge)),
              parseInt(getTextWidth(longestChild(childEdges)))
            );
            if (edgeLength < textLength + 25) {
              __DEV__ && console.log(edgeLength, textLength, "edge too small");
              const newLength = textLength + 50;
              doc.style.setProperty("--node-spacing-h", String(newLength) + 'px');
              if (ellipsis) {
                doc.style.setProperty("--node-spacing-v", String(Math.max(newLength * 0.75, edgeLength)) + 'px');
              } else {
                doc.style.setProperty("--node-spacing-v", String(newLength) + 'px');
              }
            } else if  (textLength < 100) {
              doc.style.setProperty("--node-spacing-h", String(150) + 'px');
            }
          }
        } 
      } else { // result is now computed

        resultNode.value = result;
      
      }

      // notify parents that a result might be available
      // __DEV__ && console.log("Notify parents of ", props.prop.id);
      emit('computed', result);
    });
    
  }

  const sleep = (ms: number) => { // TODO output ist auch verzögert
    return new Promise(resolve => setTimeout(resolve, ms))
  }

  function updateResult(childIndex: number, result: PrimitiveValue | PendingValue) {
    sleep(500).then(() => {
      childProps[childIndex].recCall = result;
    })
  }

  // animation when result is evaluated
  watch(resultNode, () => {
    
    // greenification
    gsap.to('#res'+id, {
      backgroundColor: '#ffff62',
      duration: 0.5,
      onComplete: () => {
        gsap.to('#res'+id, {
          backgroundColor: '#4CAF50',
          duration: 0.5,
        });
      },
    });
    gsap.to('#node'+id, {
      backgroundColor: '#ffff62',
      duration: 0.5,
      onComplete: () => {
        gsap.to('#node'+id, {
          backgroundColor: '#4CAF50',
          duration: 0.5,
        });
      },
    });
  })

</script>

<template>

  <div v-if="childProps.length > 0" class="node" :class="{ 'ellipsis': ellipsis }" @click.stop="continueEvaluation(true)" @contextmenu.prevent="continueEvaluation(false)">
    <!-- Mindestens ein Kind -->
    <p :id="'node'+id" @mouseenter="showPopup = true" @mouseleave="showPopup = false">{{ showFunName ? props.prop.funName + " " + props.prop.parameter.toString() : props.prop.parameter.toString() }} </p>
    <div v-if="showPopup && (onlyShowRecDepth || shortenNodeContent)" @mouseenter="showPopup = true" @mouseleave="showPopup = false" class="popup">{{ parameterPopup }}</div>
    <div class="edge-down"><i class="arrow-down"></i></div>
    <div class="node result" :class="{ 'ellipsis': ellipsis }" @click.stop="continueEvaluation(true)" @contextmenu.prevent="continueEvaluation(false)">
      <p :id="'res'+id">{{ resultNode === undefined ? "?" : resultNode}}</p>
    </div>
  </div>
  <div v-if="childProps.length > 0" class="graph" v-for="(child, childIndex) of childProps">
    
    <div v-if="childProps.length == 1" class="fill">
      <!-- Ein Kind -->
      <div class="edge-right" :class="{ 'ellipsis': ellipsis }">{{ childEdges[childEdges.length - 1] }}<i class="arrow-right" :class="{ 'ellipsis': ellipsis }"></i></div>
      <div class="v-fill"></div>
      <div class="edge-right result" :class="{ 'ellipsis': ellipsis }">
        <span @mouseenter="showEdgePopup = true" @mouseleave="showEdgePopup = false">{{ resultEdges[resultEdges.length - 1] }}</span>
        <div v-if="showEdgePopup && shortenNodeContent && !onlyShowRecDepth" class="edgePopup">{{ edgePopup }}</div>
        <i class="arrow-left" :class="{ 'ellipsis': ellipsis }"></i>
      </div>
    </div>
    <DiagramNode v-if="childProps.length == 1" :prop="child" :ellipsis="ellipsis" :stopAtRecCall="stopAtRecCall" :onlyShowRecDepth="onlyShowRecDepth" :shortenNodeContent="shortenNodeContent" @computed="(result) => updateResult(childIndex, result)"/>
    
    <div v-else class="node" :class="{ 'ellipsis': ellipsis }">
      <!-- Mehr als ein Kind -->
      <div class="edges-right" >
        <div class="edge-right edgeshift" :class="{ 'ellipsis': ellipsis }">{{ childEdges[childIndex + (childEdges.length - childProps.length)] }}<i class="arrow-down edgeshift"></i></div>
        <div v-if="childIndex < childProps.length - 1" class="edge-right-fill" :class="{ 'ellipsis': ellipsis }"></div>  
      </div>
      <div class="graph" :style="child.color">
        <DiagramNode :prop="child" :ellipsis="ellipsis" :stopAtRecCall="stopAtRecCall" :onlyShowRecDepth="onlyShowRecDepth" :shortenNodeContent="shortenNodeContent" @computed="(result) => updateResult(childIndex, result)"/>
      </div>
      <div class="edges-right">
        <div class="edge-right edgeshift result" :class="{ 'ellipsis': ellipsis }">          
          <i v-if="childIndex < childProps.length - 1" class="arrow-left edgeshift"  :class="{ 'ellipsis': ellipsis }"></i>
        </div>
        <div v-if="childIndex < childProps.length - 1" class="edge-right-fill result" :class="{ 'ellipsis': ellipsis }">
          <!-- {{ resultEdges[childIndex] }} -->
        </div>
      </div>
    </div>
  </div>

  <!-- Keine Kinder -->
  <div v-else class="node mr" :class="{ 'ellipsis': ellipsis }" @click.stop="continueEvaluation(true)" @contextmenu.prevent="continueEvaluation(false)">
    <!-- <p>{{ childProps.length }}</p> -->
    <p :id="'node'+id" @mouseenter="showPopup = true" @mouseleave="showPopup = false">{{ showFunName ? props.prop.funName + " " + props.prop.parameter.toString() : props.prop.parameter.toString() }}</p>
    <div v-if="showPopup && (onlyShowRecDepth || shortenNodeContent)" @mouseenter="showPopup = true" @mouseleave="showPopup = false" class="popup">{{ parameterPopup }}</div>
    <div class="edge-down"><i class="arrow-down"></i></div>
    <div class="node result" :class="{ 'ellipsis': ellipsis }" @click.stop="continueEvaluation(true)" @contextmenu.prevent="continueEvaluation(false)">
      <p :id="'res'+id">{{ resultNode === undefined ? "?" : resultNode}}</p>
    </div>
  </div>


</template>

<style>

</style>@/plugins/idcounter