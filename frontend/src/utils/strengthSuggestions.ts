import type {
  ChainAssessment,
  ChainScoreKey,
  SelfAssessmentSource,
  StrengthKind,
  WorkStyleProfile,
} from '../types/vision';
import { chainScore } from './chains';
import { tr } from '../i18n/translate';
import { ARCHETYPE_GUIDANCE } from './workStyleAssessment';

export type StrengthSuggestion = {
  kind: StrengthKind;
  description: string;
  source: SelfAssessmentSource;
};

// FR-80 (before V36): the gap text a six-chain score of 2 or lower suggested, in the user's own voice.
// V36 (D-36-1) no longer suggests these; they are kept to recognise items saved before, for the move (D-36-4).
const CHAIN_GAPS: Record<ChainScoreKey, string> = {
  settlingScore: 'I tend to settle for what is comfortable.',
  fearScore: 'Fear of failing stops me from trying.',
  criticismScore: 'Worry about what others will say stops me from acting.',
  visionScore: 'I am not yet clear on exactly what I want.',
  prioritiesScore: 'My time goes to what is urgent instead of what matters most.',
  persistenceScore: 'I give up when progress is slow.',
};

const LOW_SCORE = 2;

/** V36 (D-36-2): the latest check has a chain scored 2 or lower, so it points to inner blockers. */
export function checkSuggestsInnerBlocker(latestAssessment: ChainAssessment | undefined): boolean {
  return (Object.keys(CHAIN_GAPS) as ChainScoreKey[])
    .some((key) => (chainScore(latestAssessment, key) ?? LOW_SCORE + 1) <= LOW_SCORE);
}

/** V36 (D-36-4): the chain whose suggestion an item's text came from, in English or in the language it was saved in. */
export function chainForGapText(description: string): ChainScoreKey | '' {
  const text = normalise(description);
  const match = (Object.keys(CHAIN_GAPS) as ChainScoreKey[])
    .find((key) => normalise(CHAIN_GAPS[key]) === text || normalise(tr(CHAIN_GAPS[key])) === text);
  return match ?? '';
}

/**
 * V8.1 FR-80: suggested items from Work Style (strengths and the likely blind
 * spot). V36 (D-36-1): the six-chain check now points to inner blockers
 * instead (checkSuggestsInnerBlocker). Nothing here is saved: the
 * user confirms or edits each one, and adds the evidence, before it is.
 * Suggestions already in the list, by description, are left out.
 */
export function strengthSuggestions(
  workStyle: WorkStyleProfile | null,
  existingDescriptions: string[],
): StrengthSuggestion[] {
  const suggestions: StrengthSuggestion[] = [];
  if (workStyle?.dominant) {
    const guidance = ARCHETYPE_GUIDANCE[workStyle.dominant];
    suggestions.push({ kind: 'STRENGTH', description: tr(guidance.strengths), source: 'WORK_STYLE' });
    suggestions.push({ kind: 'WEAKNESS', description: tr(guidance.blindSpot), source: 'WORK_STYLE' });
  }
  const existing = new Set(existingDescriptions.map(normalise));
  return suggestions.filter((suggestion) => !existing.has(normalise(suggestion.description)));
}

function normalise(text: string) {
  return text.trim().toLowerCase();
}
