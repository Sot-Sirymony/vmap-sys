import { apiClient } from './apiClient';
import type { ApprovedResource, ModuleContentDraft } from '../types/vision';

// V9 Phase 9: admin-only AI tools. Drafts are never published by the AI.

export type ApprovedResourceInput = { kind: string; name: string; url: string; description: string; active: boolean };

export function listApprovedResources(token: string) {
  return apiClient<ApprovedResource[]>('/admin/ai/resources', { token });
}

export function createApprovedResource(token: string, input: ApprovedResourceInput) {
  return apiClient<ApprovedResource>('/admin/ai/resources', { method: 'POST', token, body: JSON.stringify(input) });
}

export function updateApprovedResource(token: string, id: number, input: ApprovedResourceInput) {
  return apiClient<ApprovedResource>(`/admin/ai/resources/${id}`, { method: 'PUT', token, body: JSON.stringify(input) });
}

export function deleteApprovedResource(token: string, id: number) {
  return apiClient<void>(`/admin/ai/resources/${id}`, { method: 'DELETE', token });
}

export function draftModuleContent(token: string, moduleNumber: number, instruction: string) {
  return apiClient<ModuleContentDraft>(`/admin/ai/modules/${moduleNumber}/draft`, {
    method: 'POST',
    token,
    body: JSON.stringify({ instruction }),
  });
}

/** One Khmer draft per text, same order; null where a draft was dropped. */
export function translateToKhmer(token: string, texts: string[]) {
  return apiClient<{ translations: (string | null)[] }>('/admin/ai/translate', {
    method: 'POST',
    token,
    body: JSON.stringify({ texts, targetLanguage: 'km' }),
  });
}
