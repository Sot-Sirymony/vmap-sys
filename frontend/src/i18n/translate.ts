import { km } from './km';

export type Language = 'en' | 'km';

export const LANGUAGE_STORAGE_KEY = 'vms.language';

function storedLanguage(): Language {
  try {
    return localStorage.getItem(LANGUAGE_STORAGE_KEY) === 'km' ? 'km' : 'en';
  } catch {
    return 'en';
  }
}

// Read once at load. Changing language reloads the page (see I18nProvider), so
// text built when a module loads, such as column and option lists, is in the
// right language too.
let current: Language = storedLanguage();

export function currentLanguage(): Language {
  return current;
}

export function setCurrentLanguage(language: Language) {
  current = language;
}

// Server text with numbers inside (PlanStatusService, ProgressMetricsService).
// Each pattern maps to a km.ts key, with the captured numbers as its values.
const SERVER_PATTERNS: [RegExp, string, string[]][] = [
  [/^(\d+) goals? in place\.$/, '{count} goal(s) in place.', ['count']],
  [/^(\d+) steps? in place\.$/, '{count} step(s) in place.', ['count']],
  [/^(\d+) goals? still without steps\.$/, '{count} goal(s) still without steps.', ['count']],
  [/^(\d+) steps? still without a date\.$/, '{count} step(s) still without a date.', ['count']],
  [/^\+(\d+)% from your baseline$/, '+{percent}% from your baseline', ['percent']],
  [/^(\d+)% or more of the last 7 days' steps$/, "{percent}% or more of the last 7 days' steps", ['percent']],
  [/^(\d+) of (\d+) \((\d+)%\)$/, '{done} of {total} ({percent}%)', ['done', 'total', 'percent']],
  // V9 (AI-FR-19): risk signals from CoachRiskSignals.
  [/^Finished (\d+) item\(s\) this week, down from (\d+) the week before\.$/,
    'Finished {now} item(s) this week, down from {before} the week before.', ['now', 'before']],
  [/^(\d+) step\(s\) overdue again after being given a new date\.$/, '{count} step(s) overdue again after being given a new date.', ['count']],
  [/^(\d+) items overdue\.$/, '{count} items overdue.', ['count']],
  [/^Attitude averaged ([\d.]+) this week, down from ([\d.]+)\.$/, 'Attitude averaged {now} this week, down from {before}.', ['now', 'before']],
  [/^Attitude averaged ([\d.]+) of 5 this week\.$/, 'Attitude averaged {score} of 5 this week.', ['score']],
];

// FR-20: interface text an admin changed, for the current language. Cached on
// the device so text built at load is right on the next load; I18nProvider
// refreshes the cache from the server each time the app starts.
const OVERRIDES_STORAGE_PREFIX = 'vms.uiText.';
let overrides: Record<string, string> = storedOverrides(current);

// The longest admin change the server accepts (UiTextOverrideRequest).
const MAX_OVERRIDE_LENGTH = 1000;

/**
 * Only plain text entries: anything else in storage, or from the server, is
 * dropped, so a tampered or old value cannot change what the interface shows
 * in unexpected ways.
 */
export function cleanOverrides(value: unknown): Record<string, string> {
  const clean: Record<string, string> = {};
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    return clean;
  }
  for (const [key, text] of Object.entries(value)) {
    if (typeof text === 'string' && text.length <= MAX_OVERRIDE_LENGTH && key.length <= MAX_OVERRIDE_LENGTH) {
      clean[key] = text;
    }
  }
  return clean;
}

function storedOverrides(language: Language): Record<string, string> {
  try {
    return cleanOverrides(JSON.parse(localStorage.getItem(OVERRIDES_STORAGE_PREFIX + language) ?? '{}'));
  } catch {
    return {};
  }
}

/** Replaces the admin changes in use, and remembers them for the next load. Reports whether anything changed. */
export function setOverrides(given: Record<string, string>): boolean {
  const next = cleanOverrides(given);
  const changed = JSON.stringify(next) !== JSON.stringify(overrides);
  overrides = next;
  try {
    localStorage.setItem(OVERRIDES_STORAGE_PREFIX + current, JSON.stringify(next));
  } catch {
    // Without storage the changes still apply for this page view.
  }
  return changed;
}

/** The text to show for one key: an admin change first, then Khmer from km.ts, then the English itself. */
function textFor(language: Language, key: string): string | undefined {
  if (language === current && overrides[key] !== undefined) {
    return overrides[key];
  }
  if (language === 'km') {
    return km[key];
  }
  return language === 'en' ? key : undefined;
}

function lookup(language: Language, text: string, values?: Record<string, string | number>): [string, Record<string, string | number> | undefined] {
  const direct = language === current ? overrides[text] : undefined;
  if (direct !== undefined || (language === 'km' && km[text] !== undefined)) {
    return [textFor(language, text) ?? text, values];
  }
  for (const [pattern, key, names] of SERVER_PATTERNS) {
    const match = pattern.exec(text);
    const template = match ? textFor(language, key) : undefined;
    if (match && template !== undefined && template !== key) {
      return [template, Object.fromEntries(names.map((name, index) => [name, match[index + 1]]))];
    }
  }
  return [text, values];
}

export function translate(language: Language, text: string, values?: Record<string, string | number>) {
  const [template, filled] = lookup(language, text, values);
  return filled
    ? template.replace(/\{(\w+)\}/g, (match, key: string) => (key in filled ? String(filled[key]) : match))
    : template;
}

/**
 * NFR-03: translate English UI text into the current language. Usable anywhere,
 * including outside components; English text is the key and is the fallback.
 */
export function tr(text: string, values?: Record<string, string | number>) {
  return translate(current, text, values);
}
