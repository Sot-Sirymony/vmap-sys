import { apiClient } from './apiClient';
import type { LearningModuleDetail, LearningModuleSummary, ModuleRow } from '../types/vision';

export function listModules(token: string, language = 'en') {
  return apiClient<LearningModuleSummary[]>(`/modules?language=${language}`, { token });
}

export function getModule(token: string, number: number, language = 'en') {
  return apiClient<LearningModuleDetail>(`/modules/${number}?language=${language}`, { token });
}

export function completeModule(token: string, number: number, rows: ModuleRow[], language = 'en') {
  return apiClient<LearningModuleDetail>(`/modules/${number}/completion?language=${language}`, {
    method: 'PUT',
    token,
    body: JSON.stringify({ rows }),
  });
}
