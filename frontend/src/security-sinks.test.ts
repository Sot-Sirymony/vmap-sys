import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * V10 (SEC-05-02, SEC-05-03): user, imported and AI text is always shown as
 * plain text through React, which escapes it. These are the ways around that.
 * None is used today; a new one fails here until it is reviewed and the text
 * is passed through a vetted sanitiser.
 */
const SINKS: RegExp[] = [
  /\bdangerouslySetInnerHTML\b/,
  /\.innerHTML\b/,
  /\.outerHTML\b/,
  /\binsertAdjacentHTML\b/,
  /\bdocument\.write(ln)?\s*\(/,
  /\bnew\s+Function\s*\(/,
  /(?<![\w.])eval\s*\(/,
];

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      return sourceFiles(path);
    }
    return /\.(ts|tsx)$/.test(name) && !/\.test\.(ts|tsx)$/.test(name) ? [path] : [];
  });
}

describe('HTML injection sinks', () => {
  it('none is used in the app source', () => {
    const files = sourceFiles(join(__dirname));
    expect(files.length).toBeGreaterThan(100);
    const found = files.flatMap((file) => {
      const text = readFileSync(file, 'utf8');
      return SINKS.filter((sink) => sink.test(text)).map((sink) => `${file}: ${sink.source}`);
    });
    expect(found).toEqual([]);
  });
});
