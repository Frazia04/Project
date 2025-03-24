<script lang="ts">
import { getCurrentScope, nextTick, onBeforeUnmount, onMounted, shallowReactive, shallowRef, watchEffect } from 'vue';

import { isMobileBrowser } from '../constants';
import type { Context } from '../context';
import type { Rule, Ruleset, Statement } from '../semantics';
import type { Constraint } from '../semantics/constraints';
import type { RuleApplicationResult } from '../semantics/rules';
import { MetaVariableContext } from '../state/metaVariables';
import { Snapshot } from '../state/snapshots';
import ErrorMessage from './ErrorMessage.vue';
import { isHtmlMessage, stripHtmlPrefix } from './errors';
import type { ExposedProofTreeNodeApi } from './ProofTreeNode.vue';

export interface ProofTreeProp<T extends Statement> {
  readonly stmt: T;
  readonly ruleset: Ruleset<T>;
  readonly ctx: Context;
}

export interface ProofTreeApi {
  readonly clickGoal: (goalId: symbol) => void;
  readonly selectedGoal: symbol | undefined;
  readonly isDraggingRule: boolean;
  readonly dropRuleOnGoal: (goalId: symbol, event: DragEvent) => void;
  readonly errorConstraint: Constraint | undefined;
  readonly completed: boolean;
}

interface HistoryEntry<T extends Statement> {
  readonly snapshot: Snapshot;
  readonly ruleApplication: RuleApplication<T>;
}

interface RuleApplication<T extends Statement> {
  readonly rule: Rule<T>;
  readonly goalIndex: number;
  readonly args?: unknown[];
}
</script>

<script setup lang="ts" generic="T extends Statement">
import ProofTreeNode from './ProofTreeNode.vue';

// Get properties passed from parent component (App.vue)
const props = defineProps<{ prop: ProofTreeProp<T> }>();
// eslint-disable-next-line vue/no-setup-props-destructure
const { stmt, ruleset, ctx: rootCtx } = props.prop;

// Define the events we can emit to the parent component
const emit = defineEmits<(e: 'disposeTree') => void>();

// Maintain a reference to the root ProofTreeNode.vue component
const rootNode = shallowRef<ExposedProofTreeNodeApi<T>>();

// Get this component's effect scope
const componentEffectScope = getCurrentScope()!;

// State for error message
const error = shallowRef<string>();
const errorConstraint = shallowRef<Constraint>();
function dismissError(): void {
  error.value = undefined;
  errorConstraint.value = undefined;
}

// History for undo/redo operations
const undoBuffer: HistoryEntry<T>[] = [];
const redoBuffer: RuleApplication<T>[] = [];
const canUndo = shallowRef(false);
const canRedo = shallowRef(false);

// --------------------------------------------------------------------------------------------------------------------
// Rule application by clicking (first on goal, then on rule)

const selectedGoal = shallowRef<symbol>();

function clickGoal(goalId: symbol): void {
  dismissError();
  selectedGoal.value = goalId;
}

function clickRule(rule: Rule<T>): void {
  if (selectedGoal.value) {
    void applyRuleToGoalId(selectedGoal.value, rule);
  }
}

// Initially select the root node
onMounted(() => (selectedGoal.value = rootNode.value?.id));

// --------------------------------------------------------------------------------------------------------------------
// Rule application by dragging the rule and dropping it onto a goal

const draggingRule = shallowRef<Rule<T>>();
let selectedGoalBeforeDrag: symbol | undefined;

function dragRule(rule: Rule<T>, event: DragEvent): void {
  selectedGoalBeforeDrag = selectedGoal.value;
  selectedGoal.value = undefined;
  draggingRule.value = rule;
  const dt = event.dataTransfer;
  if (dt) {
    dt.dropEffect = dt.effectAllowed = 'copy';

    // Ensure that the mouse is grabbing the rule at the top left corner
    dt.setDragImage(event.target as Element, 0, 0);
  }
}

function abortDragRule(): void {
  draggingRule.value = undefined;

  // Restore the selected goal, but do not change it if a new one has already been selected
  if (!selectedGoal.value) {
    selectedGoal.value = selectedGoalBeforeDrag;
  }
}

function dropRuleOnGoal(goalId: symbol, event: DragEvent): void {
  const rule = draggingRule.value;
  if (rule) {
    event.preventDefault(); // successful drop
    selectedGoal.value = goalId; // in case the rule fails, we want to select that goal
    void applyRuleToGoalId(goalId, rule);
  }
}

// --------------------------------------------------------------------------------------------------------------------
// Rule application

let locked = false;
async function applyRuleToGoalId(goalId: symbol, rule: Rule<T>, isRedo = false, redoArgs?: unknown[]): Promise<void> {
  if (!locked) {
    const root = rootNode.value;
    if (root) {
      dismissError();

      const goalIndex: number = root.openGoals.findIndex(({ id }) => id === goalId);
      if (__DEV__ && goalIndex === -1) {
        console.error('Could not find goal', goalId);
      }

      return await applyRule(goalIndex, rule, isRedo, redoArgs);
    }
  }
}

async function applyRule(goalIndex: number, rule: Rule<T>, isRedo = false, redoArgs?: unknown[]): Promise<void> {
  if (!locked) {
    const root = rootNode.value;
    if (root) {
      dismissError();

      const goal = root.openGoals[goalIndex];
      if (!goal) {
        error.value = `Das Beweisziel id ${goalIndex} existiert nicht.`;
        return;
      }

      try {
        locked = true;

        // Create a fresh snapshot to track changes from this rule application. Its effect scope is a child of
        // the tree root effect scope such that it gets garbage-collected when the tree is disposed.
        const snapshot = new Snapshot(rootCtx.snapshot.effectScope);
        const ctx: Context = { ...rootCtx, snapshot };
        let ruleApplicationResult: RuleApplicationResult<T>;
        try {
          // Apply the rule and save result
          ruleApplicationResult = await rule.apply(goal.stmt, ctx, redoArgs);
          snapshot.setRef(goal.ruleApplicationResult, ruleApplicationResult);

          // Check all constraints. Await nextTick such that we also get constraints added above.
          await nextTick();
          root.constraints.forEach((constraint) => {
            try {
              constraint.check(ctx);
            } catch (err) {
              errorConstraint.value = constraint;
              throw err;
            }
          });
        } catch (err) {
          // Undo changes if rule failed
          snapshot.undo();
          error.value = err instanceof Error ? err.message : String(err);
          if (__DEV__) {
            console.error(err);
          }
          return;
        }

        // Select left-most open goal (if any)
        selectedGoal.value = root.openGoals[0]?.id;

        // Add to undo buffer
        undoBuffer.push({
          snapshot,
          ruleApplication: {
            rule,
            goalIndex,
            args: ruleApplicationResult.args,
          },
        });
        canUndo.value = true;

        // Clear redo buffer when applying manually
        if (!isRedo) {
          redoBuffer.length = 0;
          canRedo.value = false;
        }
      } finally {
        locked = false;
      }
    }
  }
}

// --------------------------------------------------------------------------------------------------------------------
// History (for undo/redo operation)

function undo(): void {
  dismissError();
  const entry = undoBuffer.pop();
  if (entry) {
    entry.snapshot.undo();
    canUndo.value = !!undoBuffer.length;

    redoBuffer.push(entry.ruleApplication);
    canRedo.value = true;

    // Select left-most open goal
    selectedGoal.value = rootNode.value?.openGoals[0]?.id;
  }
}

function redo(): void {
  dismissError();
  const entry = redoBuffer.pop();
  if (entry) {
    canRedo.value = !!redoBuffer.length;
    applyRule(entry.goalIndex, entry.rule, true, entry.args).catch(console.error);
  }
}

// --------------------------------------------------------------------------------------------------------------------
// Keyboard events

function keydown(event: KeyboardEvent): void {
  switch (event.key) {
    case 'Escape':
      dismissError();
      selectedGoal.value = undefined;
      break;

    // PageUp is the back button on presenter devices
    case 'PageUp':
      event.preventDefault();
      undo();
      break;

    // PageUp is the forward button on presenter devices
    case 'PageDown':
      event.preventDefault();
      redo();
      break;
  }
}

onMounted(() => window.addEventListener('keydown', keydown));
onBeforeUnmount(() => window.removeEventListener('keydown', keydown));

// --------------------------------------------------------------------------------------------------------------------
// Detect completion and runtime errors

const completed = shallowRef(false);
const runtimeErrors = shallowRef<string[]>([]);
const successMessageHtml = shallowRef<string>();

watchEffect(() => {
  const root = rootNode.value;
  if (root) {
    const errors = Array.from(new Set(root.errors)).sort();
    if (!root.openGoals.length) {
      if (root.constraints.some((constraint) => !constraint.fulfilled)) {
        errors.push('Der Baum enthält noch nicht erfüllte Nebenbedingungen!');
      }
      completed.value = true;
      if (!errors.length) {
        successMessageHtml.value = `Du hast erfolgreich gezeigt, dass ${stmt.rendered.value.htmlNoTooltip} gilt.`;
        runtimeErrors.value = [];
        return;
      }
    } else {
      completed.value = false;
    }
    runtimeErrors.value = errors;
  } else {
    completed.value = false;
    runtimeErrors.value = [];
  }
  successMessageHtml.value = undefined;
});

// --------------------------------------------------------------------------------------------------------------------
// LaTeX Export

async function latexExport(event: MouseEvent): Promise<void> {
  const r = rootNode.value;
  if (r) {
    const tree: Generator<string> = (function* () {
      yield '\\begin{prooftree}\n';
      yield* r.generateLatex();
      yield '\\end{prooftree}\n';
    })();

    if (event.shiftKey) {
      // Shift + click => copy tree code to clipboard
      await navigator.clipboard.writeText(Array.from(tree).join(''));
      alert('LaTeX Code wurde in die Zwischenablage kopiert');
    } else {
      // Click without holding shift => download whole LaTeX document
      const link = document.createElement('a');
      link.href = URL.createObjectURL(
        new Blob(
          [
            '\\documentclass[a4paper]{article}\n',
            '\\usepackage[utf8]{inputenc}\n',
            '\\usepackage{color}\n',
            '\\usepackage{amsmath}\n',
            '\\usepackage{bussproofs}\n',
            // Code for \monus copied from GdP.fmt
            '\\providecommand{\\monus}{\\mathbin{\\vphantom{+}\\text{\\mathsurround=0pt\\ooalign{\\noalign{\\kern-.35ex}\\hidewidth$\\smash{\\cdot}$\\hidewidth\\cr\\noalign{\\kern.35ex}$-$\\cr}}}}',
            '\\begin{document}\n',
            ...tree,
            '\\end{document}\n',
          ],
          { type: 'text/x-tex', endings: 'native' },
        ),
      );
      link.download = 'tree.tex';
      link.click();
      URL.revokeObjectURL(link.href);
    }
  }
}

// --------------------------------------------------------------------------------------------------------------------
// Prepare rendered rules

const renderedRulesByChapter = ruleset.map(
  ([chapter, rules]) =>
    [
      chapter,
      rules.map((rule) => {
        const ctx: Context = {
          ...rootCtx,
          metaVariables: new MetaVariableContext(),
          nextAddressIndex: { value: 0n },
          snapshot: new Snapshot(componentEffectScope),
        };
        const ruleRenderResult = rule.render(ctx);
        ctx.snapshot.effectScope.stop();
        return [rule, ruleRenderResult] as const;
      }),
    ] as const,
);

const chaptersExpansionState = shallowReactive<Record<string, boolean>>(
  ruleset.reduce((obj, [chapter]) => ({ [chapter]: true, ...obj }), {}),
);

// --------------------------------------------------------------------------------------------------------------------
// API

const api: ProofTreeApi = {
  clickGoal,
  get selectedGoal() {
    return selectedGoal.value;
  },
  dropRuleOnGoal,
  get isDraggingRule() {
    return !!draggingRule.value;
  },
  get errorConstraint() {
    return errorConstraint.value;
  },
  get completed() {
    return completed.value;
  },
};
</script>

<template>
  <!-- Buttons -->
  <div class="buttons">
    <button @click.passive="emit('disposeTree')">Zurück zur Startseite</button>
    <button :disabled="!canUndo" @click.passive="undo">Rückgängig</button>
    <button :disabled="!canRedo" @click.passive="redo">Wiederholen</button>
    <button @click.passive="latexExport">LaTeX Export</button>
    <!-- TODO: Button for auo-solve -->
  </div>

  <!-- Messages -->
  <ErrorMessage v-if="error" tag="div" class="error clickable" :msg="error" @click.passive="dismissError" />
  <template v-for="runtimeError of runtimeErrors">
    <div
      v-if="isHtmlMessage(runtimeError)"
      :key="'html' + runtimeError"
      class="error"
      v-html="stripHtmlPrefix(runtimeError)"
    />
    <div v-else :key="'text' + runtimeError" class="error" v-text="runtimeError" />
  </template>
  <div v-if="successMessageHtml" class="success" v-html="successMessageHtml" />

  <!-- Proof -->
  <div class="proof">
    <ProofTreeNode ref="rootNode" :api="api" :stmt="stmt as T /* TODO: type-safety */" />
  </div>

  <!-- Ruleset -->
  <div class="ruleset">
    <h2>Regeln:</h2>
    <template v-for="[chapter, renderedRules] of renderedRulesByChapter" :key="chapter">
      <h3
        :class="{ collapsed: !chaptersExpansionState[chapter] }"
        @click.passive="chaptersExpansionState[chapter] = !chaptersExpansionState[chapter]"
        v-text="chapter"
      />
      <div v-show="chaptersExpansionState[chapter]">
        <template v-for="[rule, { conclusion, premises, constraints }] of renderedRules" :key="rule.ruleId">
          <!-- Rule -->
          <div
            :class="{ clickable: selectedGoal }"
            :title="rule.ruleName"
            draggable="true"
            @click.passive="clickRule(rule)"
            @dragstart.passive="dragRule(rule, $event)"
            @dragend.passive="abortDragRule"
          >
            <!-- Vertical container (premises over conclusion) -->
            <div>
              <!-- Container for premises (if not an axiom) -->
              <div v-if="premises.length">
                <!-- eslint-disable-next-line vue/require-v-for-key -- because child nodes are stateless -->
                <div v-for="premise of premises" v-html="premise.rendered.value.html" />
              </div>
              <div v-html="conclusion.rendered.value.html" />
            </div>
            <!-- Constraints -->
            <!-- eslint-disable-next-line vue/require-v-for-key -- because child nodes are stateless -->
            <div
              v-for="constraint of constraints"
              :class="{ 'runtime-error': api.errorConstraint === constraint }"
              v-html="constraint.rendered.value.html"
            />
          </div>
        </template>
      </div>
    </template>

    <p>
      <template v-if="isMobileBrowser">
        Zum Anwenden einer Regel zuerst ein offenes Beweisziel und dann die gewünschte Regel anklicken. Das linkeste
        offene Beweisziel ist vorausgewählt.
      </template>
      <template v-else>
        Zum Anwenden eine Regel entweder per Drag &amp; Drop auf ein offenes Beweisziel ziehen oder zuerst ein offenes
        Beweisziel und dann die gewünschte Regel anklicken. Das linkeste offene Beweisziel ist vorausgewählt.
      </template>
    </p>
  </div>
</template>
