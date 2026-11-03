import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const css = readFileSync(resolve(process.cwd(), 'src/styles/global.css'), 'utf8');

describe('board card height', () => {
  it('keeps each card its own height when a column is taller than its cards', () => {
    const rule = /\n\.stack-list \{([^}]*)\}/.exec(css)?.[1] ?? '';
    expect(rule).toContain('display: grid');
    expect(rule).toContain('align-content: start');
  });
});
