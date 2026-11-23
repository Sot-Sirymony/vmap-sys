import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * BRD 26 D-26-2 / IS-NFR-02 / ISC-04 — no component sets a radius, shadow,
 * surface, blur or border colour of its own. They use the --style-* tokens or
 * the shared surface() helper, so every surface follows the interface style.
 *
 * Scans src/components and src/pages (tests excluded). Allowed:
 *   - token references (`var(--…)`), `0`, `none`, `inherit`, `50%` (circles)
 *   - radii of 4px or less: texture, not shape (swatches, heat cells, ticks)
 *   - shadows that are 1px rings drawn from a variable (`0 0 0 1px var(--…)`)
 */
const ROOTS = ['src/components', 'src/pages'].map((dir) => resolve(process.cwd(), dir));

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return /\.tsx?$/.test(name) && !/\.test\.tsx?$/.test(name) ? [path] : [];
  });
}

const FILES = ROOTS.flatMap(sourceFiles).map((path) => ({ path: relative(process.cwd(), path), text: readFileSync(path, 'utf8') }));

type Rule = { name: string; pattern: RegExp; allowed?: (value: string) => boolean };

const tinyRadius = (value: string) => /^'[0-4](\.\d+)?px'$/.test(value) || value === "'50%'" || value === "'inherit'" || value === '0' || value === "'0'";

const RULES: Rule[] = [
  {
    // MUI multiplies a number by the theme's base radius, so `borderRadius: 2`
    // means 8px in one style and 24px in another.
    name: 'numeric MUI borderRadius',
    pattern: /borderRadius:\s*([0-9.]+)\b(?!px)/g,
    allowed: (value) => value === '0',
  },
  {
    name: 'literal borderRadius',
    pattern: /borderRadius:\s*('[^']*'|`[^`]*`)/g,
    allowed: (value) => value.includes('var(--') || tinyRadius(value) || value.includes('${'),
  },
  {
    name: 'literal or numeric boxShadow',
    pattern: /boxShadow:\s*('[^']*'|`[^`]*`|\d+)/g,
    allowed: (value) => /^'(none|0 0 0 \d+px var\(--[a-z0-9-]+\)|var\(--[^']*)'$/.test(value) || (value.includes('var(--') && !/rgba?\(|#[0-9a-f]{3,8}/i.test(value)),
  },
  {
    name: 'literal backdropFilter',
    pattern: /(?:backdropFilter|WebkitBackdropFilter):\s*('[^']*'|`[^`]*`)/g,
    allowed: (value) => value === "'none'" || value.startsWith("'var(--"),
  },
  {
    // The surface colours belong to the style tokens (--style-card-bg,
    // --style-floating-bg, --style-inset-bg) or the surface() helper.
    name: 'own surface colour',
    pattern: /(?:bgcolor|backgroundColor|background):\s*'(background\.paper|var\(--card\)|var\(--surface\)|var\(--surface-raised\)|var\(--popover\))'/g,
  },
  {
    name: 'literal border colour',
    pattern: /border(?:Color|Top|Bottom|Left|Right)?:\s*'[^']*(?:rgba?\(|#[0-9a-f]{3,8}\b)[^']*'/gi,
  },
];

function offenders(rule: Rule, files = FILES): string[] {
  const found: string[] = [];
  for (const file of files) {
    const lines = file.text.split('\n');
    lines.forEach((line, index) => {
      for (const match of line.matchAll(rule.pattern)) {
        const value = match[1] ?? match[0];
        if (!rule.allowed || !rule.allowed(value)) {
          found.push(`${file.path}:${index + 1}: ${match[0]}`);
        }
      }
    });
  }
  return found;
}

describe('no hard-coded style values in components (D-26-2)', () => {
  for (const rule of RULES) {
    it(`has no ${rule.name}`, () => {
      const found = offenders(rule);
      expect(found, `${rule.name}:\n${found.join('\n')}`).toEqual([]);
    });
  }

  it('fails when a literal radius, shadow or surface is added (ISC-04)', () => {
    const planted = [{ path: 'planted.tsx', text: "sx={{ borderRadius: 2, boxShadow: '0 2px 4px rgba(0,0,0,.2)', bgcolor: 'background.paper', borderRadius: '12px' }}" }];
    const hits = RULES.flatMap((rule) => offenders(rule, planted));
    expect(hits.length).toBeGreaterThanOrEqual(4);
  });
});

/** D-26-4 / ISC-05 — glass is only placed on the floating layers of §5. */
describe('glass placement (D-26-3, D-26-4)', () => {
  const blurUsers = FILES.filter((file) => file.text.includes("'var(--style-blur)'") || file.text.includes("surface('floating'"));

  it('uses the floating material only in the layers §5 allows', () => {
    const allowed = new Set([
      'src/components/layout/Header.tsx',
      'src/components/layout/Sidebar.tsx',
      'src/components/common/InterfaceStylePicker.tsx',
      'src/pages/DashboardPage.tsx',
    ]);
    expect(blurUsers.map((file) => file.path).filter((path) => !allowed.has(path))).toEqual([]);
  });

  it('no longer gives kanban columns, metric cards or empty states the glass treatment', () => {
    const css = readFileSync(resolve(process.cwd(), 'src/styles/global.css'), 'utf8');
    expect(css).not.toMatch(/data-style="(?:glass|liquidGlass)"[^{]*\.(?:kanban-column|metric-card|empty-state|auth-card)/);
  });
});
