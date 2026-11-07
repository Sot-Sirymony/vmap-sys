import { describe, expect, it } from 'vitest';
import { letterRankFilterOptions } from './sortRank';

describe('letterRankFilterOptions', () => {
  it('lists each letter rank in use once, in alphabetical order, skipping items without one', () => {
    const items = [{ letterRank: 'C' }, { letterRank: 'A' }, { letterRank: null }, { letterRank: 'C' }, {}, { letterRank: 'B' }];
    expect(letterRankFilterOptions(items)).toEqual([
      { value: 'A', label: 'A' },
      { value: 'B', label: 'B' },
      { value: 'C', label: 'C' },
    ]);
  });
});
