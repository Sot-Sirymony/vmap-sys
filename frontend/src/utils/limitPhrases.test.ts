import { describe, expect, it } from 'vitest';
import { findLimitPhrase } from './limitPhrases';

describe('limit phrases (V17 BL-FR-13)', () => {
  it('finds a limit in any of the texts, ignoring case and curly apostrophes', () => {
    expect(findLimitPhrase(['Own a home', 'If I can afford it, by 2030'])).toBe('if i can afford');
    expect(findLimitPhrase(['Run a marathon but I don’t have time'])).toBe("but i don't");
  });

  it('stays quiet for a bold, outcome-only dream', () => {
    expect(findLimitPhrase(['Own a family home with a garden', 'Keys in hand by June 2030', undefined, null])).toBeNull();
    expect(findLimitPhrase([])).toBeNull();
  });
});
