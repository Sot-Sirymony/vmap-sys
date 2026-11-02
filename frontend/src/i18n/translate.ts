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
];

// FR-20: interface text an admin changed, for the current language. Cached on
// the device so text built at load is right on the next load; I18nProvider
// refreshes the cache from the server each time the app starts.
const OVERRIDES_STORAGE_PREFIX = 'vms.uiText.';
let overrides: Record<string, string> = storedOverrides(current);

function storedOverrides(language: Language): Record<string, string> {
  try {
    return JSON.parse(localStorage.getItem(OVERRIDES_STORAGE_PREFIX + language) ?? '{}') as Record<string, string>;
  } catch {
    return {};
  }
}

/** Replaces the admin changes in use, and remembers them for the next load. Reports whether anything changed. */
export function setOverrides(next: Record<string, string>): boolean {
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
