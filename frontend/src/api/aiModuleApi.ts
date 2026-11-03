import { apiClient } from './apiClient';
import type { AssessmentExplanation, ModuleHelp, ModuleHelpMode, ModuleRow } from '../types/vision';

// V9 Phase 7: AI help in the module exercises and the assessment explanation. Drafts only.

export function getModuleHelp(token: string, moduleNumber: number, mode: ModuleHelpMode, rows: ModuleRow[], language: string) {
  return apiClient<ModuleHelp>(`/ai/modules/${moduleNumber}/help`, {
    method: 'POST',
    token,
    body: JSON.stringify({ mode, rows, language }),
  });
}

export function explainAssessment(token: string, language: string) {
  return apiClient<AssessmentExplanation>(`/ai/assessment/explain?language=${language}`, { method: 'POST', token });
}
