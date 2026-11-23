import { afterEach, describe, expect, it } from 'vitest';
import { setCurrentLanguage } from '../i18n/translate';
import { formatDelta, formatNumber, formatPercent } from './format';

describe('dashboard number formats (BRD 25 §4)', () => {
  afterEach(() => setCurrentLanguage('en'));

  it('rounds to whole numbers with grouping', () => {
    expect(formatNumber(1234.6)).toBe('1,235');
  });

  it('clamps percentages to 0–100 and rounds', () => {
    expect(formatPercent(61.6)).toBe('62%');
    expect(formatPercent(140)).toBe('100%');
    expect(formatPercent(-5)).toBe('0%');
  });

  it('always signs a change in points, with a true minus sign', () => {
    expect(formatDelta(8)).toBe('+8 pts');
    expect(formatDelta(-3.4)).toBe('−3 pts');
    expect(formatDelta(0)).toBe('0 pts');
  });
});
