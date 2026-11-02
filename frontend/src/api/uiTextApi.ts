import { apiClient } from './apiClient';
import type { UiTextOverride } from '../types/vision';

/** FR-20: the admin's text changes for one language. Public, so the sign-in page can use them. */
export function getUiTextOverrides(language: string) {
  return apiClient<Record<string, string>>(`/ui-text?language=${language}`);
}

export function listUiTextOverrides(token: string, language: string) {
  return apiClient<UiTextOverride[]>(`/admin/ui-text?language=${language}`, { token });
}

export function saveUiTextOverride(token: string, language: string, sourceText: string, text: string) {
  return apiClient<UiTextOverride>('/admin/ui-text', {
    method: 'PUT',
    token,
    body: JSON.stringify({ language, sourceText, text }),
  });
}

export function resetUiTextOverride(token: string, id: number) {
  return apiClient<void>(`/admin/ui-text/${id}`, { method: 'DELETE', token });
}
