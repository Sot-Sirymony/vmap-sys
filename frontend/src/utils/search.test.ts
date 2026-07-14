import { describe, expect, it } from 'vitest';
import { matchesSearch } from './search';

describe('matchesSearch', () => {
  it('matches everything when the term is empty or only whitespace', () => {
    expect(matchesSearch('', 'anything')).toBe(true);
    expect(matchesSearch('   ', 'anything')).toBe(true);
  });

  it('matches a substring of any field, ignoring case', () => {
    expect(matchesSearch('MAR', 'Dr Maria Chan', 'Epidemiologist')).toBe(true);
    expect(matchesSearch('epidem', 'Dr Maria Chan', 'Epidemiologist')).toBe(true);
  });

  it('does not match when no field contains the term', () => {
    expect(matchesSearch('budget', 'Dr Maria Chan', 'Epidemiologist')).toBe(false);
  });

  it('ignores missing fields instead of matching on "null" or "undefined"', () => {
    expect(matchesSearch('null', 'Dr Maria Chan', null)).toBe(false);
    expect(matchesSearch('undefined', 'Dr Maria Chan', undefined)).toBe(false);
  });

  it('searches numeric fields, such as a sequence number', () => {
    expect(matchesSearch('12', 'Step title', 12)).toBe(true);
  });
});
