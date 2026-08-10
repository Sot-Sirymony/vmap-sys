import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * FR-48 — what makes the interface-style axis actually reach hand-written CSS.
 *
 * The style setting works by swapping the `--radius-*` and `--shadow-*` tokens,
 * so a surface that hardcodes `border-radius: 8px` silently opts out of it —
 * exactly the drift that once left the kanban board, toasts, and the command
 * palette square in a rounded app. Every corner in global.css must go through
 * the token scale.
 *
 * Allowlisted: the tiny radii that are texture rather than shape — the 2px
 * focus-ring corner and the 3px `.area-dot` — plus the token declarations
 * themselves (which are, by definition, literal).
 */
const css = readFileSync(resolve(process.cwd(), 'src/styles/global.css'), 'utf8');

const ALLOWED = new Set(['2px', '3px']);

describe('radius tokens (FR-48)', () => {
  it('routes every border-radius in global.css through the token scale', () => {
    const offenders: string[] = [];
    const lines = css.split('\n');
    lines.forEach((line, index) => {
      const match = /border(?:-(?:top|bottom)-(?:left|right))?-radius\s*:\s*([^;]+);/.exec(line);
      if (!match) {
        return;
      }
      // Token declarations like `--radius-md: 8px;` don't match the property
      // regex above, so anything matched here is a real style rule.
      const value = match[1].trim();
      if (value.startsWith('var(--radius') || ALLOWED.has(value)) {
        return;
      }
      offenders.push(`line ${index + 1}: ${line.trim()}`);
    });
    expect(offenders, `hardcoded border-radius values found:\n${offenders.join('\n')}`).toEqual([]);
  });

  it('declares no literal box-shadow outside the token blocks', () => {
    // Shadows may be composed (e.g. an inset rail + a token), but every
    // standalone elevation must come from --shadow-*. Inset strokes are rings,
    // not elevation, and are allowed.
    const offenders: string[] = [];
    const lines = css.split('\n');
    lines.forEach((line, index) => {
      const match = /^\s*box-shadow\s*:\s*([^;]+);/.exec(line);
      if (!match) {
        return;
      }
      const value = match[1].trim();
      if (value.includes('var(--shadow') || value.startsWith('inset') || value === 'none') {
        return;
      }
      offenders.push(`line ${index + 1}: ${line.trim()}`);
    });
    expect(offenders, `hardcoded box-shadow values found:\n${offenders.join('\n')}`).toEqual([]);
  });
});
