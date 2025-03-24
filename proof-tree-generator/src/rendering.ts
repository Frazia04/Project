// This module provides aspects for rendering objects (expressions, types, values, environments, ...) to HTML strings.

import type { Context } from './context';
import { FailedSimplification, getSimplification, type Simplification, SuccessfulSimplification } from './simplify';
import type { RefLike } from './state/snapshots';
import { computedEager } from './utils';

/**
 * Interface for objects that can be rendered to HTML.
 */
export interface Renderable {
  /**
   * Renderable objects must have a property
   * ```
   * public readonly rendered: RenderingRef;
   * ```
   * that is set as:
   * ```ts
   * constructor(..., ctx: Context) {
   *   this.rendered = reactiveRendering(ctx, () => [...]);
   * }
   * ```
   * or as:
   * ```ts
   * constructor(...) {
   *   this.rendered = staticRendering(...);
   * }
   * ```
   */
  readonly rendered: RenderingRef;
}

// Add a special symbol property to make sure using this module is the only way to create a RenderingRef.
// The property does not exist at runtime, it is only for type-checking.
declare const renderingRefSymbol: unique symbol;
export type RenderingRef = Readonly<RefLike<Rendering>> & { [renderingRefSymbol]: true };

/**
 * Rendering for an object, containing the html, the operator precedence the whole construct has
 * and a list of errors (string error messages) that occurred while calculating the rendering.
 */
export class Rendering {
  public readonly html: string;
  public readonly latex: string;
  public readonly htmlNoTooltip: string;
  public readonly precedence: Precedence;
  public readonly errors: string[];

  constructor(
    html: string,
    latex: string = html,
    htmlNoTooltip: string = html,
    precedence: Precedence = Precedence.Atom,
    errors?: string[],
  ) {
    this.html = html;
    this.latex = latex;
    this.htmlNoTooltip = htmlNoTooltip;
    this.precedence = precedence;
    this.errors = errors ?? [];
  }
}

export const enum Precedence {
  Low,
  Comparison,
  PlusMinus,
  MultDiv,
  Application,
  Atom,
}

/**
 * Define a reactive rendering
 * @param ctx The context to get the effect scope from
 * @param getSpec Getter for the rendering specification. The function can either return a `string`, `Rendering` or
 * `Renderable` object to use (without applying any simplifications), or a specification array with:
 *
 * **First entry:** the operator precedence of the rendered object.
 *
 * **Remaining entries:**
 * - `Precedence` (or `number`):
 *    Defines the minimal allowed operator precedence for subsequent entries. Defaults to the first array entry.
 * - `string`:
 *    Is rendered as is (No parentheses).
 * - `Rendering`:
 *    Is wrapped into parentheses if its precedence is lower than currently allowed operator precedence.
 *    Contained rendering errors are propagated to the resulting rendering errors array.
 * - `Renderable`:
 *    Is simplified as much as possible, then the resulting object's `Rendering` is used as described above.
 *    If simplification fails, then the error message is put into the resulting rendering errors array and
 *    the rendering is wrapped into HTML tags for errors.
 */
export function reactiveRendering(
  ctx: Context,
  getSpec: () => string | Rendering | Renderable | [Precedence, ...(string | Rendering | Renderable | Precedence)[]],
): RenderingRef {
  const ref: Omit<RenderingRef, typeof renderingRefSymbol> = ctx.snapshot.effectScope.run(() =>
    computedEager(() => {
      const spec = getSpec();
      if (!Array.isArray(spec)) {
        return typeof spec === 'string' ? new Rendering(spec) : spec instanceof Rendering ? spec : spec.rendered.value;
      }

      const html: string[] = [];
      const latex: string[] = [];
      const htmlNoTooltip: string[] = [];
      const [precedence, ...parts] = spec;
      const errors: string[] = [];
      let minAllowedPrecedence = precedence;
      for (const part of parts) {
        if (typeof part === 'number') {
          minAllowedPrecedence = part;
        } else {
          let toRender = part;
          let isError = false;
          if (!(part instanceof Rendering)) {
            let stringOrRenderable = part;
            let simplification: Simplification<string | Renderable>;
            while ((simplification = getSimplification(stringOrRenderable)) instanceof SuccessfulSimplification) {
              stringOrRenderable = simplification.result;
            }
            if (simplification instanceof FailedSimplification) {
              isError = true;
              errors.push(simplification.errorMessage);
              toRender = simplification.partialResult ?? stringOrRenderable;
            } else {
              toRender = stringOrRenderable;
            }
          }
          if (typeof toRender === 'string') {
            html.push(toRender);
            latex.push(toRender);
            htmlNoTooltip.push(toRender);
          } else {
            const rendering = toRender instanceof Rendering ? toRender : toRender.rendered.value;
            errors.push(...rendering.errors);
            const needParens = rendering.precedence < minAllowedPrecedence;
            if (needParens) {
              html.push('(');
              latex.push('(');
              htmlNoTooltip.push('(');
            }
            if (isError) {
              const tag = '<span class="runtime-error">';
              html.push(tag);
              latex.push('\\textcolor{red}{');
              htmlNoTooltip.push(tag);
            }
            html.push(rendering.html);
            latex.push(rendering.latex);
            htmlNoTooltip.push(rendering.htmlNoTooltip);
            if (isError) {
              const tag = '</span>';
              html.push(tag);
              latex.push('}');
              htmlNoTooltip.push(tag);
            }
            if (needParens) {
              html.push(')');
              latex.push(')');
              htmlNoTooltip.push(')');
            }
          }
        }
      }
      return new Rendering(html.join(''), latex.join(''), htmlNoTooltip.join(''), precedence, errors);
    }),
  )!;
  return ref as RenderingRef;
}

export function render(x: string | Rendering | Renderable): string {
  return typeof x === 'string' ? x : (x instanceof Rendering ? x : x.rendered.value).html;
}

/**
 * Define a non-reactive rendering
 * @param html The html string to render
 * @param precedence The operator precedence this rendering has (optional, defaults to highest precedence)
 */
export function staticRendering(rendering: Rendering): RenderingRef;
export function staticRendering(html: string, latex?: string, precedence?: Precedence): RenderingRef;
export function staticRendering(
  htmlOrRendering: string | Rendering,
  latex?: string,
  precedence?: Precedence,
): RenderingRef {
  return {
    value:
      htmlOrRendering instanceof Rendering
        ? htmlOrRendering
        : new Rendering(htmlOrRendering, latex, undefined, precedence),
  } satisfies Omit<RenderingRef, typeof renderingRefSymbol> as RenderingRef;
}

export function renderQuotedChar(char: string): Rendering {
  let html = '';
  let latex = '';
  if (char === "'") {
    html += '\\';
    latex += '\\backslash';
  }
  if (char.length === 1) {
    const code = char.charCodeAt(0);
    html += `&#${code};`;
    latex += `\\char${code}`;
  } else {
    // Used in rules when the char itself is styled as variable
    html += char;
  }
  return new Rendering(`'${html}'`, `'${latex}'`);
}

export const centerDot = new Rendering(' &centerdot; ', ' \\cdot ');
