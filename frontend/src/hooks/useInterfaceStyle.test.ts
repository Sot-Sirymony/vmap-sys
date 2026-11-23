import { describe, expect, it } from 'vitest';
import { glassFallbackReason } from './useInterfaceStyle';

const none = { highContrast: false, prefersContrast: false, reducedTransparency: false, backdropFilter: true };

describe('glass fallback rule (D-26-6)', () => {
  it('never reports a fallback for the solid styles', () => {
    expect(glassFallbackReason('modern', { ...none, highContrast: true })).toBeNull();
    expect(glassFallbackReason('classic', { ...none, reducedTransparency: true })).toBeNull();
  });

  it('is active glass when nothing asks for solid panels', () => {
    expect(glassFallbackReason('glass', none)).toBeNull();
  });

  it('falls back for each D-26-6 condition, high contrast first', () => {
    expect(glassFallbackReason('glass', { ...none, highContrast: true, reducedTransparency: true })).toBe('highContrast');
    expect(glassFallbackReason('glass', { ...none, prefersContrast: true })).toBe('prefersContrast');
    expect(glassFallbackReason('glass', { ...none, reducedTransparency: true })).toBe('reducedTransparency');
    expect(glassFallbackReason('glass', { ...none, backdropFilter: false })).toBe('unsupported');
  });
});
