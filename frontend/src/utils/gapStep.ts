import type { ObstacleType, PartnerSupportType } from '../types/vision';
import { suggestPartnerFor } from './partnerSuggestion';
import { tr } from '../i18n/translate';

/**
 * V17 phase 3 (BL-FR-17 to BL-FR-20): defaults for turning a gap into a
 * "find help" step. Everything here is only a starting point; the participant
 * can edit every field before saving.
 */

/** The kinds offered by the step's "Kind of gap" picker: the task "What's missing?" list (BL-FR-18). */
export const GAP_KINDS: readonly ObstacleType[] = ['KNOWLEDGE', 'SKILL', 'TIME', 'MONEY', 'DECISION', 'PARTNER', 'MOTIVATION'];

/** Gap kinds where creating a partner to contact is ticked by default (BL-FR-19). */
const PARTNER_BY_DEFAULT: readonly ObstacleType[] = ['KNOWLEDGE', 'SKILL', 'MONEY'];

const MAX_GAP_IN_TITLE = 80;

/** The gap's first line, shortened for a step title. */
export function shortGap(gap: string): string {
  const firstLine = gap.trim().split(/\r?\n/)[0]?.trim() ?? '';
  return firstLine.length <= MAX_GAP_IN_TITLE ? firstLine : `${firstLine.slice(0, MAX_GAP_IN_TITLE - 1).trim()}…`;
}

/** "Find a {helper type} for {short gap}", or "Find help for …" when the kind has no helper type. */
export function gapStepTitle(gap: string, kind?: ObstacleType | null): string {
  const suggestion = kind ? suggestPartnerFor(kind) : null;
  const gapText = shortGap(gap);
  return suggestion
    ? tr('Find a {helper} for {gap}', { helper: suggestion.label.toLowerCase(), gap: gapText })
    : tr('Find help for {gap}', { gap: gapText });
}

export function partnerByDefault(kind?: ObstacleType | null): boolean {
  return !!kind && PARTNER_BY_DEFAULT.includes(kind);
}

export function supportTypeFor(kind?: ObstacleType | null): PartnerSupportType {
  return (kind && suggestPartnerFor(kind)?.supportType) || 'OTHER';
}

function isoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

/**
 * A date before the step this one comes before: the day before its date, but
 * never before today. Without a date to come before, two weeks from today.
 */
export function gapStepDate(beforeDate: string | null | undefined, today: Date = new Date()): string {
  const todayIso = isoDate(today);
  if (!beforeDate) {
    const later = new Date(today);
    later.setUTCDate(later.getUTCDate() + 14);
    return isoDate(later);
  }
  const dayBefore = new Date(`${beforeDate}T00:00:00Z`);
  dayBefore.setUTCDate(dayBefore.getUTCDate() - 1);
  const candidate = isoDate(dayBefore);
  return candidate < todayIso ? todayIso : candidate;
}
