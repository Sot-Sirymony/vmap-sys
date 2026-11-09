import { describe, expect, it } from 'vitest';
import { progressTone, progressToneLegend } from './progressTone';

describe('progressTone', () => {
  it('maps a percentage to its colour level at each boundary', () => {
    expect(progressTone(0)).toBe('low');
    expect(progressTone(24)).toBe('low');
    expect(progressTone(25)).toBe('mid');
    expect(progressTone(74)).toBe('mid');
    expect(progressTone(75)).toBe('high');
    expect(progressTone(99)).toBe('high');
    expect(progressTone(100)).toBe('done');
  });

  it('rounds like the label does and clamps odd input', () => {
    expect(progressTone(24.6)).toBe('mid');
    expect(progressTone(99.6)).toBe('done');
    expect(progressTone(-5)).toBe('low');
    expect(progressTone(140)).toBe('done');
    expect(progressTone(Number.NaN)).toBe('low');
  });

  it('lists the legend from just started to done', () => {
    expect(progressToneLegend().map((item) => item.tone)).toEqual(['low', 'mid', 'high', 'done']);
  });
});
