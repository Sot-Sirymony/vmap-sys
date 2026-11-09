import { apiClient } from './apiClient';
import type { BlockedTask, ObstacleHelpAnswer, ObstacleHelpRequest, ObstacleType, PartnerMatches, RankedObstacle } from '../types/vision';

/** V15 (AH-FR-31 to AH-FR-33, AH-FR-37, AH-FR-41): one AI obstacle helper. Nothing is saved. */
export function obstacleHelp(token: string, language: string, request: ObstacleHelpRequest) {
  return apiClient<ObstacleHelpAnswer>(`/ai/obstacles/help?language=${language}`, { method: 'POST', token, body: JSON.stringify(request) });
}

/** V15 (AH-FR-35): the participant's own partners who fit the obstacle type. No AI. */
export function getPartnerMatches(token: string, obstacleType: ObstacleType) {
  return apiClient<PartnerMatches>(`/obstacles/partner-matches?obstacleType=${obstacleType}`, { token });
}

/** V15 (AH-FR-41): open obstacles in the rule order. No AI. */
export function getObstaclePriority(token: string) {
  return apiClient<RankedObstacle[]>('/obstacles/priority', { token });
}

/** V15 (AH-FR-39): Blocked tasks with a blocker reason and no obstacle. No AI. */
export function getBlockedWithoutObstacle(token: string) {
  return apiClient<BlockedTask[]>('/tasks/blocked-without-obstacle', { token });
}
