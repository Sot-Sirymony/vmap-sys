import type {
  ChainAssessment,
  ChainScoreKey,
  SelfAssessmentSource,
  StrengthKind,
  WorkStyleProfile,
} from '../types/vision';
import { tr } from '../i18n/translate';
import { ARCHETYPE_GUIDANCE } from './workStyleAssessment';

export type StrengthSuggestion = {
  kind: StrengthKind;
  description: string;
  source: SelfAssessmentSource;
};

// FR-80: a six-chain score of 2 or lower suggests a gap, in the user's own voice.
const CHAIN_GAPS: Record<ChainScoreKey, string> = {
  settlingScore: tr('I tend to settle for what is comfortable.'),
  fearScore: tr('Fear of failing stops me from trying.'),
  criticismScore: tr('Worry about what others will say stops me from acting.'),
  visionScore: tr('I am not yet clear on exactly what I want.'),
  knowledgeScore: tr('I stop when I do not know how.'),
  resourcesScore: tr('I stop when I lack something instead of looking for who could supply it.'),
};

const LOW_SCORE = 2;

/**
 * V8.1 FR-80: suggested items from Work Style (strengths and the likely blind
 * spot) and from the latest six-chain assessment. Nothing here is saved: the
 * user confirms or edits each one, and adds the evidence, before it is.
 * Suggestions already in the list, by description, are left out.
 */
export function strengthSuggestions(
  workStyle: WorkStyleProfile | null,
  latestAssessment: ChainAssessment | undefined,
  existingDescriptions: string[],
): StrengthSuggestion[] {
  const suggestions: StrengthSuggestion[] = [];
  if (workStyle?.dominant) {
    const guidance = ARCHETYPE_GUIDANCE[workStyle.dominant];
    suggestions.push({ kind: 'STRENGTH', description: tr(guidance.strengths), source: 'WORK_STYLE' });
    suggestions.push({ kind: 'WEAKNESS', description: tr(guidance.blindSpot), source: 'WORK_STYLE' });
  }
  if (latestAssessment) {
    (Object.keys(CHAIN_GAPS) as ChainScoreKey[])
      .filter((key) => latestAssessment[key] <= LOW_SCORE)
      .forEach((key) => suggestions.push({ kind: 'WEAKNESS', description: CHAIN_GAPS[key], source: 'SIX_CHAIN' }));
  }
  const existing = new Set(existingDescriptions.map(normalise));
  return suggestions.filter((suggestion) => !existing.has(normalise(suggestion.description)));
}

function normalise(text: string) {
  return text.trim().toLowerCase();
}
