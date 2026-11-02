import type { ObstacleType, PartnerSupportType } from '../types/vision';
import { tr } from '../i18n/translate';

export type PartnerSuggestion = {
  supportType: PartnerSupportType;
  label: string;
};

const obstacleTypeToPartnerSuggestion: Record<ObstacleType, PartnerSuggestion | null> = {
  KNOWLEDGE: { supportType: 'MENTOR', label: tr('Mentor or expert') },
  SKILL: { supportType: 'TECHNICAL', label: tr('Technical expert or trainer') },
  TIME: { supportType: 'COLLEAGUE', label: tr('Delegate or assistant') },
  MONEY: { supportType: 'FINANCIAL', label: tr('Financial partner or sponsor') },
  MOTIVATION: { supportType: 'EMOTIONAL', label: tr('Accountability partner') },
  DECISION: { supportType: 'ADVISOR', label: tr('Advisor') },
  PARTNER: null,
  SYSTEM: null,
  OTHER: null,
};

export function suggestPartnerFor(obstacleType: ObstacleType): PartnerSuggestion | null {
  return obstacleTypeToPartnerSuggestion[obstacleType];
}
