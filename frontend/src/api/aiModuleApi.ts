import { apiClient } from './apiClient';
import type { AssessmentExplanation, AssessmentPractice, ModuleHelp, WhatChanged, ModuleHelpExtras, ModuleHelpMode, ModuleJourney, ModuleNext, ModuleRow } from '../types/vision';

// V9 Phase 7: AI help in the module exercises and the assessment explanation. Drafts only.

export function getModuleHelp(
  token: string, moduleNumber: number, mode: ModuleHelpMode, rows: ModuleRow[], language: string, extras: ModuleHelpExtras = {},
) {
  return apiClient<ModuleHelp>(`/ai/modules/${moduleNumber}/help`, {
    method: 'POST',
    token,
    body: JSON.stringify({ mode, rows, language, ...extras }),
  });
}

/** V15 (AH-FR-13): which module next, chosen by Claude from the open modules; the server falls back to the rule. */
export function suggestNextModule(token: string, language: string) {
  return apiClient<ModuleNext>(`/ai/modules/next?language=${language}`, { method: 'POST', token });
}

/** V15 (AH-FR-14): which module next by the program's rule. No AI. */
export function getNextModuleByRule(token: string, language: string) {
  return apiClient<ModuleNext>(`/modules/next?language=${language}`, { token });
}

/** V15 (AH-FR-15): modules done per stage and chain scores since the first assessment. No AI. */
export function getModuleJourney(token: string, language: string) {
  return apiClient<ModuleJourney>(`/modules/journey?language=${language}`, { token });
}

export function explainAssessment(token: string, language: string) {
  return apiClient<AssessmentExplanation>(`/ai/assessment/explain?language=${language}`, { method: 'POST', token });
}

/** V15 (AH-FR-26): one practice for each barrier scored 3 or less. */
export function getAssessmentPractice(token: string, language: string) {
  return apiClient<AssessmentPractice>(`/ai/assessment/practice?language=${language}`, { method: 'POST', token });
}

/** V15 (AH-FR-29): what changed since the previous take. */
export function getWhatChanged(token: string, language: string) {
  return apiClient<WhatChanged>(`/ai/assessment/what-changed?language=${language}`, { method: 'POST', token });
}
