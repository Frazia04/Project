<script lang="ts">
import { nextTick, type Ref, shallowReactive, shallowRef, type UnwrapRef, watch, watchPostEffect } from 'vue';

import type { Statement } from '../semantics';
import type { Constraint } from '../semantics/constraints';
import type { RuleApplicationResult } from '../semantics/rules';
import { computedEager } from '../utils';
import type { ProofTreeApi } from './ProofTree.vue';

export interface ProofTreeNodeApi<T extends Statement> {
  readonly id: symbol;
  readonly stmt: T;
  readonly conclusionElement: Ref<HTMLElement | undefined>;
  readonly constraintElements: Ref<HTMLElement[]>;
  readonly ruleApplicationResult: Ref<RuleApplicationResult<T> | undefined>;
  readonly openGoals: Ref<ProofTreeNodeApi<T>[]>;
  readonly constraints: Ref<Constraint[]>;
  readonly errors: Ref<string[]>;
  readonly generateLatex: () => Generator<string>;
}

export type ExposedProofTreeNodeApi<T extends Statement> = UnwrapRef<ProofTreeNodeApi<T>>;
</script>

<script setup lang="ts" generic="T extends Statement">
// Unique symbol for the identity of this proof tree node
const id = Symbol(__DEV__ ? 'ProofTreeNode_Identity' : '');

// Get properties passed from parent component (ProofTree.vue or parent ProofTreeNode.vue)
const props = defineProps<{
  stmt: T;
  api: ProofTreeApi;
}>();
// eslint-disable-next-line vue/no-setup-props-destructure
const { stmt, api } = props;

// Maintain references to our direct child components and to some DOM elements
const subtrees = shallowRef<ExposedProofTreeNodeApi<T>[]>([]);
const containerElement = shallowRef<HTMLElement>();
const conclusionElement = shallowRef<HTMLElement>();
const constraintElements = shallowRef<HTMLElement[]>([]);

// State to store the result of applying a rule to our node
const ruleApplicationResult = shallowRef<RuleApplicationResult<T>>();

// Expose our own API to the parent. We cannot use computed(...) for part depending on subtrees,
// because evaluation must happen after child components have been updated.
const openGoals = shallowRef<ProofTreeNodeApi<T>[]>([]);
const constraints = shallowRef<Constraint[]>([]);
const errors = shallowRef<string[]>([]);
const myNodeApi: ProofTreeNodeApi<T> = {
  id,
  stmt,
  conclusionElement,
  constraintElements,
  ruleApplicationResult,
  openGoals,
  constraints,
  errors,
  generateLatex,
};
defineExpose<ProofTreeNodeApi<T>>(myNodeApi);

// Collect open goals
watchPostEffect(() => {
  const result: ProofTreeNodeApi<T>[] = [];
  if (ruleApplicationResult.value) {
    // Collect open goals from our subtrees
    for (const child of subtrees.value) {
      result.push(...child.openGoals);
    }
  } else {
    // Our own proof goal is not yet done
    result.push(myNodeApi);
  }
  openGoals.value = result;
});

// Collect constraints
watchPostEffect(() => {
  const result: Constraint[] = [];
  const r = ruleApplicationResult.value;
  if (r) {
    // Add our own constraints
    result.push(...r.constraints);

    // Collect constraints from our subtrees
    for (const child of subtrees.value) {
      result.push(...child.constraints);
    }
  }
  constraints.value = result;
});

// Collect runtime errors
watchPostEffect(() => {
  const result: string[] = [];
  result.push(...stmt.rendered.value.errors);
  const r = ruleApplicationResult.value;
  if (r) {
    // Collect errors from our subtrees
    for (const child of subtrees.value) {
      result.push(...child.errors);
    }

    // Collect errors from our constraints
    for (const constraint of r.constraints) {
      result.push(...constraint.rendered.value.errors);
    }
  }
  errors.value = result;
});

// --------------------------------------------------------------------------------------------------------------------

// Define the events we can emit to the parent component:
// updating: This component is about to update its styling.
// updated: This component has completed updating its styling.
// The events come in pairs, each 'updated' event belongs to an earlier 'updating' event.
const emit = defineEmits<(event: 'updating' | 'updated') => void>();

// Whether we have emitted an 'updating' event that was not yet followed by an 'updated' event
let emittedUpdating = false;

// Emit an 'updating' event if necessary
function emitUpdating(): void {
  if (!emittedUpdating) {
    emit('updating');
    emittedUpdating = true;
  }
}

// Emit an 'updated' event and allow for the next 'updating' event to be emitted
function emitUpdated(): void {
  emit('updated');
  emittedUpdating = false;
}

// --------------------------------------------------------------------------------------------------------------------
// Conclusion alignment

// We need to make sure that the horizontal line separating premises and conclusion is wide enough to cover all premise
// statements, but it should not take the full width and spread over our subtrees full width. Therefore, we need to keep
// track of our premises' conclusion positions and update the style for our own conclusion accordingly.
// We cannot add margins or min-width as this would prevent nodes from shrinking when premises need less space (undo
// button or fulfilled constraints). We therefore use a flexbox together with flex-grow to let the border and two spacer
// elements grow to the required size.

// `flex-grow` values for the space/right of the conclusion and the container having the border-top
const conclusionGrowth = shallowReactive<{
  left?: number;
  conclusion?: number;
  right?: number;
}>({});

// Several events can trigger a re-calculation. The calculation is delayed after the next Vue tick, such that the DOM
// is up to date when reading the viewport positions. We keep a counter of how often an update has been triggered to
// avoid redundant updates. We increment the counter whenever an update has been triggered and decrement it when a tick
// has passed afterwards. Only when the counter reached zero we actually re-compute the conclusion alignment.
let updateTriggerCount = 0;

async function updateConclusionStyle(): Promise<void> {
  // Increment the trigger counter
  updateTriggerCount++;

  // Inform the parent component that we are about to update
  emitUpdating();

  // Wait for the DOM to be updated. Need to await two ticks for template refs to be up to date.
  await nextTick();
  await nextTick();

  // Decrement the trigger counter
  if (--updateTriggerCount) {
    // If after decrementing the counter is still positive, then there will be another update. Skip the computation.
    return;
  }

  // Helper function to fix a calculated size
  function fixSize(x: number): number | undefined {
    // Round to one decimal place
    const result = Math.round(x * 10) / 10;

    // Replace zero or negative values by undefined (remove attribute entirely instead of `flex-grow: 0`)
    return result > 0 ? result : undefined;
  }

  // Check that we have at least one premise and that the required DOM elements are set
  const s = subtrees.value;
  let containerElem, conclusionElem: HTMLElement | undefined;
  if (s.length && (containerElem = containerElement.value) && (conclusionElem = conclusionElement.value)) {
    // Bounding box of the container with premises and conclusion (without our own constraints)
    const containerRect = containerElem.getBoundingClientRect();

    // Leftmost x coordinate of our premise statements
    const premisesLeft = s[0].conclusionElement?.getBoundingClientRect().left ?? containerRect.left;

    // Rightmost x coordinate of our premise statements or constraints
    const premisesRight = (() => {
      const rightNode = s[s.length - 1];
      const constraintElems = rightNode.constraintElements;
      return (
        (constraintElems.length
          ? constraintElems[constraintElems.length - 1]
          : rightNode.conclusionElement
        )?.getBoundingClientRect().right ?? containerRect.right
      );
    })();

    // Spacing elements grow proportional to the margins of premise statements relative to the
    // container holding premises and conclusion
    const leftGrowth = (conclusionGrowth.left = fixSize(premisesLeft - containerRect.left));
    const rightGrowth = (conclusionGrowth.right = fixSize(containerRect.right - premisesRight));

    // The container responsible for drawing the line above the conclusion grows proportional to
    // the amount that our premises are wider than our conclusion
    conclusionGrowth.conclusion =
      leftGrowth || rightGrowth
        ? fixSize(premisesRight - premisesLeft - conclusionElem.getBoundingClientRect().width)
        : // If the spacing elements do not grow, then all values will behave the same here.
          // Use a constant to avoid unnecessary component updates.
          1;
  } else {
    // Otherwise remove all `flex-grow` styles
    conclusionGrowth.left = conclusionGrowth.conclusion = conclusionGrowth.right = undefined;
  }

  // Inform the parent component that our update has completed
  emitUpdated();
}

// We need to call updateConclusionStyle when:
// (1) setting/unsetting our rule application result
// (2) our conclusion statement rendering changes
// (3) one of our constraints is updated
// (4) one of our premises completed updating its own conclusion style

// Cases (1), (2) and (3) are covered by this watcher:
watch(
  [
    ruleApplicationResult,
    () => stmt.rendered.value.html,
    () => ruleApplicationResult.value?.constraints.map((c) => (c.fulfilled ? '' : c.rendered.value.html)).join('|'),
  ],
  updateConclusionStyle,
  // Use a sync watcher such that we can emit the 'updating' event as soon as possible.
  // Our trigger counter takes care of skipping redundant updates, thus avoiding performance issues.
  { flush: 'sync' },
);

// Case (4) is covered by handling the events emitted by our child components.

function onChildUpdating(): void {
  // Increment the counter to defer all updates until the child has completed its update
  updateTriggerCount++;

  // Since the child will eventually emit an 'updated' event and thus trigger an update for our own component,
  // we already can inform our parent component that this component is about to update.
  emitUpdating();
}

function onChildUpdated(): Promise<void> {
  // Decrement the counter to again allow for update computations
  updateTriggerCount--;
  return updateConclusionStyle();
}

// --------------------------------------------------------------------------------------------------------------------
// LaTeX rendering

function* generateLatex(): Generator<string> {
  const latex = `$${stmt.rendered.value.latex}$`;
  const r = ruleApplicationResult.value;
  if (r) {
    const s = subtrees.value;
    if (s.length) {
      for (const premise of s) {
        yield* premise.generateLatex();
      }
    } else {
      yield '  \\AxiomC{}\n';
    }
    const constraints = r.constraints
      .filter((c) => !c.fulfilled)
      .map((c) => c.rendered.value.latex)
      .join(',\\quad ');
    if (constraints.length) {
      yield `  \\RightLabel{$${constraints}$}\n`;
    }
    yield `  \\${['U', 'U', 'Bi', 'Tri', 'Quater', 'Qui'][s.length]}naryInfC{${latex}}\n`;
  } else {
    yield `  \\AxiomC{${latex}}\n`;
  }
}

// --------------------------------------------------------------------------------------------------------------------
// Interaction with proof goal

// Click on this goal (and afterwards on a rule)
function click(): void {
  if (!ruleApplicationResult.value) {
    api.clickGoal(id);
  }
}

// Whether the user is currently dragging a rule over this goal
const activeDrag = shallowRef(false);

// Enable the drop zone when dragging a rule over this goal
function enableDropZone(event: DragEvent): void {
  if (!ruleApplicationResult.value && api.isDraggingRule) {
    event.preventDefault(); // enable drop zone
    activeDrag.value = true;
  }
}
function disableDropZone(event: DragEvent): void {
  // Only disable the drop zone when drag leaves for a node that is not a child node
  const castToNode = (target: EventTarget | null): Node | null => (target instanceof Node ? target : null);
  if (!castToNode(event.currentTarget)?.contains(castToNode(event.relatedTarget))) {
    activeDrag.value = false;
  }
}

// Drop a rule on this goal
function drop(event: DragEvent): void {
  if (!ruleApplicationResult.value) {
    activeDrag.value = false;
    api.dropRuleOnGoal(id, event);
  }
}

// Whether this goal should be highlighted
const selected = computedEager(() => activeDrag.value || id === api.selectedGoal);
</script>

<template>
  <!-- Regular node (known premises) -->
  <div v-if="ruleApplicationResult" class="node">
    <!-- Vertical container (premises over conclusion) -->
    <div ref="containerElement">
      <!-- Container for premises (if not an axiom) -->
      <div v-if="ruleApplicationResult.premises.length">
        <!-- eslint-disable-next-line vue/valid-v-for -- because array does not change after creation -->
        <ProofTreeNode
          v-for="premise of ruleApplicationResult.premises"
          ref="subtrees"
          :stmt="premise as any /* TODO: type-safety */"
          :api="api"
          @updating="onChildUpdating"
          @updated="onChildUpdated"
        />
      </div>

      <!-- Container for space/conclusion/space -->
      <div>
        <!-- Space left of the conclusion, growing to position the conclusion below premises -->
        <div :style="{ 'flex-grow': conclusionGrowth.left }" />

        <!-- Container with border-top, growing to cover all premises' statements -->
        <div :style="{ 'flex-grow': conclusionGrowth.conclusion }">
          <!-- Actual conclusion element with automatic width -->
          <div ref="conclusionElement" v-html="(stmt as T).rendered.value.html /* TODO: type-safety */" />
        </div>

        <!-- Space right of the conclusion, growing to position the conclusion below premises -->
        <div :style="{ 'flex-grow': conclusionGrowth.right }" />
      </div>
    </div>

    <!-- Constraints -->
    <template v-for="constraint of ruleApplicationResult.constraints">
      <!-- eslint-disable-next-line vue/require-v-for-key -- because child nodes are stateless -->
      <div
        v-if="!constraint.fulfilled"
        ref="constraintElements"
        class="constraint"
        :class="{ 'runtime-error': api.errorConstraint === constraint || api.completed }"
        v-html="constraint.rendered.value.html"
      />
    </template>
  </div>

  <!-- Goal node (unknown premises, only a statement to prove) -->
  <div
    v-else
    class="goal"
    :class="{ selected }"
    @click.passive="click"
    @dragenter="enableDropZone"
    @dragover="enableDropZone"
    @dragleave.passive="disableDropZone"
    @drop="drop"
    v-html="(stmt as T).rendered.value.html /* TODO: type-safety */"
  />
</template>
