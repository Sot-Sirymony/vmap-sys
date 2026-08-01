import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { grey, palette } from '../theme';

/**
 * FR-47: `global.css` exposes the palette as `--palette-*` variables so plain
 * CSS — and components ported from a design that names those variables — can
 * reach the same colours the theme uses.
 *
 * Duplicating 40 values into a second file is exactly the drift BR-15 warns
 * about, and it was the reason for not doing it earlier. This test is what makes
 * it safe: it parses the stylesheet and compares every declaration against the
 * TypeScript source, so the copy cannot silently diverge. Change a colour in
 * `theme.ts` without updating the CSS and this fails.
 */
// Resolved from the project root: under jsdom, `import.meta.url` is not a file
// URL, so it cannot be used to locate the stylesheet.
const css = readFileSync(resolve(process.cwd(), 'src/styles/global.css'), 'utf8');

function declaredVars(): Map<string, string> {
  const found = new Map<string, string>();
  for (const match of css.matchAll(/--palette-([a-z0-9-]+)\s*:\s*([^;]+);/gi)) {
    found.set(match[1].toLowerCase(), match[2].trim().toUpperCase());
  }
  return found;
}

describe('--palette-* CSS variables (FR-47)', () => {
  const declared = declaredVars();

  it('declares every semantic shade with the value theme.ts holds', () => {
    for (const [family, shades] of Object.entries(palette)) {
      for (const [shade, hex] of Object.entries(shades)) {
        const name = `${family}-${shade}`.toLowerCase();
        expect(declared.get(name), `--palette-${name} missing from global.css`).toBe(hex.toUpperCase());
      }
    }
  });

  it('declares every grey step with the value theme.ts holds', () => {
    for (const [step, hex] of Object.entries(grey)) {
      const name = `grey-${step}`;
      expect(declared.get(name), `--palette-${name} missing from global.css`).toBe(hex.toUpperCase());
    }
  });

  /**
   * Catches the other direction of drift: a variable left behind in CSS after
   * its shade was renamed or removed from the theme would otherwise sit there
   * being quietly wrong.
   */
  it('declares nothing the theme does not define', () => {
    const expected = new Set<string>();
    for (const [family, shades] of Object.entries(palette)) {
      for (const shade of Object.keys(shades)) {
        expected.add(`${family}-${shade}`.toLowerCase());
      }
    }
    for (const step of Object.keys(grey)) {
      expected.add(`grey-${step}`);
    }

    for (const name of declared.keys()) {
      expect(expected.has(name), `--palette-${name} is in global.css but not in theme.ts`).toBe(true);
    }
  });

  it('covers the whole palette — 30 semantic shades plus 10 greys', () => {
    expect(declared.size).toBe(40);
  });
});
