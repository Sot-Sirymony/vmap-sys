import { afterEach, describe, expect, it } from 'vitest';
import { formatDate } from './formatDate';
import { setCurrentLanguage } from './translate';

describe('formatDate', () => {
  afterEach(() => setCurrentLanguage('en'));

  it('builds Khmer day and month names itself, since browsers may lack them', () => {
    setCurrentLanguage('km');
    expect(formatDate(new Date(2026, 10, 2), { weekday: true })).toBe('ថ្ងៃច័ន្ទ 2 វិច្ឆិកា');
    expect(formatDate(new Date(2026, 0, 15), { year: true })).toBe('15 មករា 2026');
  });

  it('keeps the usual English format', () => {
    expect(formatDate(new Date(2026, 10, 2), { year: true })).toBe('Nov 2, 2026');
  });
});
