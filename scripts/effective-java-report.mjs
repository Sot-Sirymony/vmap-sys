#!/usr/bin/env node
// BRD 13 EJ-FR-03: prints the "Target state" measures of BRD 13 for the
// backend source, so each phase can show its progress. Reads the source tree
// only; it needs no build and no network.
//
// Usage: node scripts/effective-java-report.mjs [--markdown]

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(fileURLToPath(new URL('.', import.meta.url)), '..', 'backend', 'src', 'main', 'java', 'com', 'visionmapping');

// Files allowed to read the system clock for good (same list as EffectiveJavaRulesTest).
const CLOCK_ALLOWED = new Set([
  'entity/BaseAuditableEntity.java',
  'exception/GlobalExceptionHandler.java',
  'security/RestAccessDeniedHandler.java',
  'security/RestAuthenticationEntryPoint.java',
]);

function javaFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return javaFiles(path);
    return name.endsWith('.java') ? [path] : [];
  });
}

const files = javaFiles(ROOT).map((path) => ({
  path: relative(ROOT, path).split(sep).join('/'),
  text: readFileSync(path, 'utf8'),
}));

/** Source lines that are code, not comments. */
function codeLines(text) {
  return text.split('\n').filter((line) => {
    const code = line.trim();
    return code && !code.startsWith('//') && !code.startsWith('*') && !code.startsWith('/*');
  });
}

function count(filter, regex) {
  let total = 0;
  for (const file of files.filter(filter)) {
    for (const line of codeLines(file.text)) {
      total += (line.match(regex) ?? []).length;
    }
  }
  return total;
}

const inDir = (prefix) => (file) => file.path.startsWith(prefix);
const all = () => true;

/** The text inside the outermost brackets of each record header. */
function recordComponents(text) {
  const result = [];
  const regex = /\brecord\s+(\w+)\s*(<[^>]*>)?\s*\(/g;
  let match;
  while ((match = regex.exec(text)) !== null) {
    let depth = 1;
    let i = regex.lastIndex;
    while (i < text.length && depth > 0) {
      if (text[i] === '(') depth += 1;
      if (text[i] === ')') depth -= 1;
      i += 1;
    }
    const body = text.slice(regex.lastIndex, i - 1).replace(/@\w+(\([^)]*\))?/g, '').replace(/<[^<>]*>/g, '');
    result.push({ name: match[1], components: body.trim() ? body.split(',').length : 0, body, rest: text.slice(i, i + 600) });
  }
  return result;
}

const records = files.flatMap((file) => recordComponents(file.text).map((record) => ({ ...record, path: file.path })));
const wideRecords = records.filter((record) => record.components > 20);
const collectionRecords = records.filter((record) => /\b(List|Set|Map)\b/.test(record.body));
const uncopiedCollectionRecords = collectionRecords.filter((record) => !/\b(List|Set|Map)\.copyOf\(|\bCopies\.(list|set|map)\(/.test(record.rest));

const publicTypeWithoutJavadoc = files.filter((file) => {
  const header = file.text.split(/^(public\s+)?(final\s+|abstract\s+|sealed\s+)*(class|record|interface|enum|@interface)\s/m)[0];
  return !header.trimEnd().replace(/(@\w+(\([^)]*\))?\s*)+$/, '').trimEnd().endsWith('*/');
}).length;

const rows = [
  ['`entity.setX(...)` calls in service/', count(inDir('service/'), /\bentity\.set[A-Z]\w*\(/g), '80 or fewer'],
  ['All `.setX(...)` calls in service/ (for information)', count(inDir('service/'), /\.set[A-Z]\w*\(/g), '–'],
  ['`return null;` (all methods)', count(all, /\breturn null;/g), 'private only, marked @Nullable'],
  ['`@Nullable` annotations', count(all, /@Nullable\b/g), '–'],
  ['Free-text status/priority fields in request DTOs', count(inDir('dto/request/'), /\bString\s+(status|priority|reviewType|supportType|severity|obstacleType|dreamType)\b/g), '0'],
  ['Public service/controller methods (not records) with a `boolean` parameter', count((f) => f.path.startsWith('service/') || f.path.startsWith('controller/'), /\bpublic\s(?![^=;(]*\brecord\b)[^=;(]*\([^)]*\bboolean\s+\w+/g), '10 or fewer'],
  ['Records with more than 20 components', wideRecords.length, '0 built by hand'],
  ['Records with collection components and no copy', `${uncopiedCollectionRecords.length} of ${collectionRecords.length}`, '0'],
  ['Files reading the clock directly (outside the allow-list)', files.filter((f) => !CLOCK_ALLOWED.has(f.path) && codeLines(f.text).some((line) => /\b(Instant|LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime)\.now\(\s*\)|System\.currentTimeMillis\(\)/.test(line))).length, '0'],
  ['`Objects.requireNonNull` uses', count(all, /\brequireNonNull\(/g), 'more'],
  ['`Optional` fields or parameters', count(all, /(private\s+(final\s+)?Optional<[^>]+>\s+\w+\s*[;=]|[(,]\s*Optional<[^>]+>\s+\w+\s*[,)])/g), '0'],
  ['Boxed `Boolean` fields in entities', count(inDir('entity/'), /\bprivate\s+Boolean\s/g), 'only tri-state ones'],
  ['`@Value` fields (for information)', count(all, /@Value\(/g), '–'],
  ['Files without a class-level comment', `${publicTypeWithoutJavadoc} of ${files.length}`, 'core packages documented'],
];

const markdown = process.argv.includes('--markdown');
const date = new Date().toISOString().slice(0, 10);
if (markdown) {
  console.log(`Effective Java measures (${date})\n`);
  console.log('| Measure | Now | BRD 13 target |\n| --- | --- | --- |');
  for (const [name, value, target] of rows) console.log(`| ${name} | ${value} | ${target} |`);
} else {
  console.log(`Effective Java measures (${date}), ${files.length} Java files\n`);
  const width = Math.max(...rows.map(([name]) => name.length));
  for (const [name, value, target] of rows) console.log(`${name.padEnd(width)}  ${String(value).padStart(9)}   target: ${target}`);
  if (wideRecords.length) {
    console.log('\nRecords with more than 20 components:');
    for (const record of wideRecords) console.log(`  ${record.components}  ${record.name} (${record.path})`);
  }
}
