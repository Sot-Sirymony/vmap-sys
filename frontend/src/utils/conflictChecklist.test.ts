import { describe, expect, it } from 'vitest';
import { CONFLICT_CHECKLIST_QUESTIONS, EMPTY_CONFLICT_CHECKLIST_ANSWERS, isConflictChecklistComplete } from './enumLabels';

describe('isConflictChecklistComplete (FR-62.2)', () => {
  it('is incomplete when every answer is unset', () => {
    expect(isConflictChecklistComplete(EMPTY_CONFLICT_CHECKLIST_ANSWERS)).toBe(false);
  });

  it('is incomplete when even one of the four items is unanswered', () => {
    const answers = { ...EMPTY_CONFLICT_CHECKLIST_ANSWERS };
    for (const question of CONFLICT_CHECKLIST_QUESTIONS.slice(0, 3)) {
      answers[question.key] = true;
    }
    expect(isConflictChecklistComplete(answers)).toBe(false);
  });

  it('is complete once all four items have a value, regardless of which way they went', () => {
    const answers = { ...EMPTY_CONFLICT_CHECKLIST_ANSWERS };
    for (const question of CONFLICT_CHECKLIST_QUESTIONS) {
      answers[question.key] = true;
    }
    expect(isConflictChecklistComplete(answers)).toBe(true);
  });

  it('treats an honest "No" as answered, not as a failure', () => {
    const answers = { ...EMPTY_CONFLICT_CHECKLIST_ANSWERS };
    for (const question of CONFLICT_CHECKLIST_QUESTIONS) {
      answers[question.key] = true;
    }
    answers.conflictStayedOnIncident = false;
    expect(isConflictChecklistComplete(answers)).toBe(true);
  });
});
