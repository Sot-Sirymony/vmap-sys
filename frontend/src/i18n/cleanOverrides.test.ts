import { describe, expect, it } from 'vitest';
import { cleanOverrides } from './translate';

describe('cleanOverrides', () => {
  it('keeps only plain text entries', () => {
    expect(cleanOverrides({ Today: 'ថ្ងៃនេះ', Count: 3, Nested: { a: 'b' }, Empty: null })).toEqual({ Today: 'ថ្ងៃនេះ' });
  });

  it('drops anything that is not an object of text, and text over the server limit', () => {
    expect(cleanOverrides('Today')).toEqual({});
    expect(cleanOverrides(['Today'])).toEqual({});
    expect(cleanOverrides(null)).toEqual({});
    expect(cleanOverrides({ Long: 'x'.repeat(1001), Short: 'x'.repeat(1000) })).toEqual({ Short: 'x'.repeat(1000) });
  });
});
