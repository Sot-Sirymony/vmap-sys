#!/usr/bin/env node
// BRD 12 COV-FR-06: the coverage report for the end of each phase, read from
// SonarQube Cloud's latest analysis of main. The project is public, so no
// token is needed.
//
// Usage: node scripts/coverage-report.mjs [--markdown]

const PROJECT = 'Sot-Sirymony_vmap-sys';
const API = 'https://sonarcloud.io/api';
const METRICS = 'lines_to_cover,uncovered_lines,conditions_to_cover,uncovered_conditions';

// The areas in the BRD's baseline table. A file counts in the first area whose test matches.
const AREAS = [
  ['Backend', (path) => path.startsWith('backend/')],
  ['Frontend pages', (path) => path.startsWith('frontend/src/pages/')],
  ['Frontend vision map', (path) => path.startsWith('frontend/src/components/vision-map/')],
  ['Frontend other', (path) => path.startsWith('frontend/')],
];

async function get(path) {
  const response = await fetch(`${API}${path}`);
  if (!response.ok) {
    throw new Error(`SonarQube Cloud answered ${response.status} for ${path}`);
  }
  return response.json();
}

async function allFiles() {
  const files = [];
  for (let page = 1; ; page += 1) {
    const data = await get(`/measures/component_tree?component=${PROJECT}&metricKeys=${METRICS}&qualifiers=FIL&ps=500&p=${page}`);
    files.push(...data.components);
    if (page * data.paging.pageSize >= data.paging.total) {
      return files;
    }
  }
}

function counts(measures) {
  const value = (key) => Number(measures.find((measure) => measure.metric === key)?.value ?? 0);
  return {
    total: value('lines_to_cover') + value('conditions_to_cover'),
    uncovered: value('uncovered_lines') + value('uncovered_conditions'),
  };
}

const percent = ({ total, uncovered }) => (total === 0 ? 100 : (100 * (total - uncovered)) / total);

const [analyses, gate, files] = await Promise.all([
  get(`/project_analyses/search?project=${PROJECT}&ps=1`),
  get(`/qualitygates/project_status?projectKey=${PROJECT}`),
  allFiles(),
]);

const sums = new Map([['Whole project', { total: 0, uncovered: 0 }], ...AREAS.map(([name]) => [name, { total: 0, uncovered: 0 }])]);
const frontend = { total: 0, uncovered: 0 };
for (const file of files) {
  const { total, uncovered } = counts(file.measures);
  const area = AREAS.find(([, matches]) => matches(file.path));
  for (const sum of [sums.get('Whole project'), area && sums.get(area[0]), file.path.startsWith('frontend/') ? frontend : null]) {
    if (sum) {
      sum.total += total;
      sum.uncovered += uncovered;
    }
  }
}
sums.set('Frontend, all', frontend);

const latest = analyses.analyses[0];
const rows = [...sums].map(([name, sum]) => [name, `${percent(sum).toFixed(1)}%`, sum.uncovered.toLocaleString('en-US')]);
const header = ['Area', 'Coverage', 'Lines and branches not covered'];
const whole = sums.get('Whole project');
const toEighty = Math.max(0, Math.ceil(0.8 * whole.total - (whole.total - whole.uncovered)));

if (process.argv.includes('--markdown')) {
  console.log(`Coverage on main, commit \`${latest.revision.slice(0, 7)}\`, analysed ${latest.date}. Quality gate: ${gate.projectStatus.status}.\n`);
  console.log(`| ${header.join(' | ')} |\n| --- | --- | --- |`);
  rows.forEach((row) => console.log(`| ${row.join(' | ')} |`));
  console.log(`\nStill to cover to reach 80%: ${toEighty.toLocaleString('en-US')} lines and branches.`);
} else {
  console.log(`Commit ${latest.revision.slice(0, 7)}, analysed ${latest.date}, quality gate ${gate.projectStatus.status}\n`);
  rows.forEach(([name, coverage, uncovered]) => console.log(`${name.padEnd(22)} ${coverage.padStart(6)}   ${uncovered.padStart(6)} not covered`));
  console.log(`\nStill to cover to reach 80%: ${toEighty.toLocaleString('en-US')} lines and branches`);
}
