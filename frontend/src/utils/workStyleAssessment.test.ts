import { describe, expect, it } from 'vitest';
import { getComplementarySuggestion, QUIZ_ITEMS, scoreAssessment } from './workStyleAssessment';

describe('scoreAssessment', () => {
  it('counts every "a" answer toward its axis\'s first pole', () => {
    const allA = QUIZ_ITEMS.map(() => 'a' as const);

    expect(scoreAssessment(allA)).toEqual({
      paceFastScore: 8,
      paceDeliberateScore: 0,
      focusTaskScore: 8,
      focusPeopleScore: 0,
    });
  });

  it('counts every "b" answer toward its axis\'s second pole', () => {
    const allB = QUIZ_ITEMS.map(() => 'b' as const);

    expect(scoreAssessment(allB)).toEqual({
      paceFastScore: 0,
      paceDeliberateScore: 8,
      focusTaskScore: 0,
      focusPeopleScore: 8,
    });
  });

  it('keeps the two axes independent of each other', () => {
    // All pace items "a" (fast), all focus items "b" (people).
    const answers = QUIZ_ITEMS.map((item) => (item.axis === 'pace' ? 'a' as const : 'b' as const));

    expect(scoreAssessment(answers)).toEqual({
      paceFastScore: 8,
      paceDeliberateScore: 0,
      focusTaskScore: 0,
      focusPeopleScore: 8,
    });
  });

  it('has exactly 16 items, 8 per axis', () => {
    expect(QUIZ_ITEMS).toHaveLength(16);
    expect(QUIZ_ITEMS.filter((item) => item.axis === 'pace')).toHaveLength(8);
    expect(QUIZ_ITEMS.filter((item) => item.axis === 'focus')).toHaveLength(8);
  });
});

describe('getComplementarySuggestion', () => {
  it('returns null when the user has no profile yet', () => {
    expect(getComplementarySuggestion(null)).toBeNull();
  });

  it('suggests Planner for a Driver', () => {
    expect(getComplementarySuggestion('DRIVER')).toEqual(['PLANNER']);
  });

  it('suggests both deliberate-pace archetypes for a Connector', () => {
    expect(getComplementarySuggestion('CONNECTOR')).toEqual(['STEADIER', 'PLANNER']);
  });
});
