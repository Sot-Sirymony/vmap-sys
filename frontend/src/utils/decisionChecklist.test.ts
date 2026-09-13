import { describe, expect, it } from 'vitest';
import { DECISION_CHECKLIST_QUESTIONS, EMPTY_DECISION_ANSWERS, isDecisionChecklistComplete } from './enumLabels';

describe('isDecisionChecklistComplete (FR-55.2)', () => {
  it('is incomplete when every answer is unset', () => {
    expect(isDecisionChecklistComplete(EMPTY_DECISION_ANSWERS)).toBe(false);
  });

  it('is incomplete when even one of the eight items is unanswered', () => {
    const answers = { ...EMPTY_DECISION_ANSWERS };
    for (const question of DECISION_CHECKLIST_QUESTIONS.slice(0, 7)) {
      answers[question.key] = false;
    }
    expect(isDecisionChecklistComplete(answers)).toBe(false);
  });

  it('is complete once all eight items have a value, regardless of which way they went', () => {
    const answers = { ...EMPTY_DECISION_ANSWERS };
    for (const question of DECISION_CHECKLIST_QUESTIONS) {
      answers[question.key] = true;
    }
    expect(isDecisionChecklistComplete(answers)).toBe(true);
  });

  it('treats an honest "yes, this trap applies" as answered, not as a failure', () => {
    const answers = { ...EMPTY_DECISION_ANSWERS };
    for (const question of DECISION_CHECKLIST_QUESTIONS) {
      answers[question.key] = false;
    }
    answers.decisionSkippedResearch = true;
    expect(isDecisionChecklistComplete(answers)).toBe(true);
  });
});
