import { describe, expect, it } from 'vitest';
import { gapStepDate, gapStepTitle, partnerByDefault, shortGap, supportTypeFor } from './gapStep';

describe('gap step defaults (V17 BL-FR-19)', () => {
  it('names the helper type for the kind of gap (BLC-10)', () => {
    expect(gapStepTitle('no statistics skills', 'SKILL')).toBe('Find a technical expert or trainer for no statistics skills');
    expect(gapStepTitle('no statistics skills', null)).toBe('Find help for no statistics skills');
    expect(gapStepTitle('a printer', 'SYSTEM')).toBe('Find help for a printer');
  });

  it('keeps only the first line of a long gap', () => {
    expect(shortGap('first line\nsecond line')).toBe('first line');
    expect(shortGap('x'.repeat(200))).toHaveLength(80);
  });

  it('ticks the partner box for knowledge, skill and money only', () => {
    expect(partnerByDefault('KNOWLEDGE')).toBe(true);
    expect(partnerByDefault('MONEY')).toBe(true);
    expect(partnerByDefault('TIME')).toBe(false);
    expect(partnerByDefault(null)).toBe(false);
    expect(supportTypeFor('SKILL')).toBe('TECHNICAL');
    expect(supportTypeFor('PARTNER')).toBe('OTHER');
  });

  it('dates the step the day before the step it comes before, never in the past', () => {
    const today = new Date('2026-11-15T00:00:00Z');
    expect(gapStepDate('2026-12-10', today)).toBe('2026-12-09');
    expect(gapStepDate('2026-11-01', today)).toBe('2026-11-15');
    expect(gapStepDate(undefined, today)).toBe('2026-11-29');
  });
});
