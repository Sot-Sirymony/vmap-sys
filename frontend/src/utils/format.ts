import { currentLanguage, tr } from '../i18n/translate';

export function formatStatus(value: string) {
  return value.replaceAll('_', ' ').toLowerCase();
}

/**
 * BRD 25 §4 — one number format for the dashboard, in the user's language.
 * Tabular figures are a CSS concern (`font-variant-numeric: tabular-nums`);
 * these only decide the characters.
 */
function locale(): string {
  return currentLanguage() === 'km' ? 'km-KH' : 'en-GB';
}

export function formatNumber(value: number): string {
  return new Intl.NumberFormat(locale(), { maximumFractionDigits: 0 }).format(value);
}

/** Whole percent, clamped to 0–100: 62 → "62%". */
export function formatPercent(value: number): string {
  return `${formatNumber(Math.min(100, Math.max(0, Math.round(value))))}%`;
}

/** A change in percentage points, always signed: 8 → "+8 pts", -3 → "−3 pts", 0 → "0 pts". */
export function formatDelta(points: number): string {
  const rounded = Math.round(points);
  let sign = '';
  if (rounded > 0) {
    sign = '+';
  } else if (rounded < 0) {
    sign = '−';
  }
  return tr('{n} pts', { n: `${sign}${formatNumber(Math.abs(rounded))}` });
}
