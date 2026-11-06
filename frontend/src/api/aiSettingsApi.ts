import { apiClient } from './apiClient';
import type { AiSettings } from '../types/vision';

// V9 (AI-FR-24): admin-only AI settings. The key goes to the server once and never comes back.

export type AiSettingsInput = {
  enabled: boolean;
  model: string;
  monthlyBudgetUsd: number;
  dailyMessageLimit?: number;
  helpLines?: string;
  aiReminders?: boolean;
  coachingGuidelines?: string;
  // V11 (PB-FR-39): blank uses the main model for plan building too.
  planModel?: string;
};

export function getAiSettings(token: string) {
  return apiClient<AiSettings>('/admin/ai-settings', { token });
}

export function updateAiSettings(token: string, input: AiSettingsInput) {
  return apiClient<AiSettings>('/admin/ai-settings', { method: 'PUT', token, body: JSON.stringify(input) });
}

export function saveAiApiKey(token: string, apiKey: string) {
  return apiClient<AiSettings>('/admin/ai-settings/api-key', { method: 'PUT', token, body: JSON.stringify({ apiKey }) });
}

export function removeAiApiKey(token: string) {
  return apiClient<AiSettings>('/admin/ai-settings/api-key', { method: 'DELETE', token });
}

export function testAiConnection(token: string) {
  return apiClient<AiSettings>('/admin/ai-settings/test', { method: 'POST', token });
}
