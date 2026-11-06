// V11 (PB-NFR-06, decision D-4): export the Khmer drafts for a Khmer speaker to review.
// Usage: node scripts/export-km-review.mjs [section-prefix] > ../docs/km-review-v11.csv
// Takes every entry in src/i18n/km.ts that follows a comment starting with the
// prefix (default "V11"), up to the next comment that does not.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const prefix = process.argv[2] ?? 'V11';
const source = readFileSync(fileURLToPath(new URL('../src/i18n/km.ts', import.meta.url)), 'utf8');
const unquote = (text) => text.slice(1, -1).replace(/\\'/g, "'").replace(/\\\\/g, '\\');
const csv = (text) => `"${text.replace(/"/g, '""')}"`;

let section = null;
const rows = [['Section', 'English', 'Khmer draft', 'Reviewed Khmer (leave empty if the draft is right)', 'Comment']];
for (const line of source.split('\n')) {
  const comment = line.match(/^\s*\/\/\s*(.*)$/);
  if (comment) {
    section = comment[1].startsWith(prefix) ? comment[1] : null;
    continue;
  }
  const entry = line.match(/^\s*('(?:[^'\\]|\\.)*'|[A-Za-z]\w*):\s*('(?:[^'\\]|\\.)*'),?\s*$/);
  if (section && entry) {
    const english = entry[1].startsWith("'") ? unquote(entry[1]) : entry[1];
    rows.push([section, english, unquote(entry[2]), '', '']);
  }
}
process.stdout.write(`${rows.map((row) => row.map(csv).join(',')).join('\n')}\n`);
