import type { ChainAssessment, ChainScoreKey, RetiredChainKey } from '../types/vision';

/**
 * V17 BL-FR-30 (D-17-3): the six chains, in the method's order, with the
 * statement each is scored on. Original wording (BL-NFR-05). The backend's
 * ModuleJourneyService.CHAINS holds the same list.
 */
export const CHAINS: readonly { key: ChainScoreKey; label: string; question: string }[] = [
  { key: 'settlingScore', label: 'Settling for average', question: 'I expect more of myself than what is merely comfortable.' },
  { key: 'fearScore', label: 'Fear of failure', question: 'I try things even when they might not work.' },
  { key: 'criticismScore', label: 'Avoiding criticism', question: 'What others may say does not stop me from acting.' },
  { key: 'prioritiesScore', label: 'Misplaced priorities', question: 'My time goes to what matters most, not to what is loudest.' },
  { key: 'persistenceScore', label: 'Giving up too soon', question: 'When progress is slow, I keep going instead of quitting.' },
  { key: 'visionScore', label: 'Unclear vision', question: 'I can describe exactly what I want and when I want it.' },
];

/** V17 BL-FR-31, BL-FR-32: chains older takes measured; now step gaps, never asked. */
export const RETIRED_CHAINS: readonly { key: RetiredChainKey; label: string }[] = [
  { key: 'knowledgeScore', label: 'Not knowing how' },
  { key: 'resourcesScore', label: 'Lack of resources' },
];

export const CHAIN_LABELS = Object.fromEntries(CHAINS.map((chain) => [chain.key, chain.label])) as Record<ChainScoreKey, string>;

/** A take's score for a chain, or null when that take did not measure it. */
export function chainScore(take: ChainAssessment | undefined, key: ChainScoreKey | RetiredChainKey): number | null {
  const value = take?.[key];
  return typeof value === 'number' ? value : null;
}

/** Retired chains any of these takes measured, to label them "No longer measured" (BL-FR-31). */
export function retiredChainsIn(takes: readonly ChainAssessment[]) {
  return RETIRED_CHAINS.filter((chain) => takes.some((take) => chainScore(take, chain.key) !== null));
}

/** BL-FR-34: fear of failure or avoiding criticism scored 1 or 2 in this take. */
export function fearLinkSuggested(take: ChainAssessment | undefined): boolean {
  return [chainScore(take, 'fearScore'), chainScore(take, 'criticismScore')].some((score) => score !== null && score <= 2);
}
