import { apiClient } from './apiClient';
import type { ModuleContent, ModuleContentRequest } from '../types/vision';

export function listModuleContent(token: string, language: string) {
  return apiClient<ModuleContent[]>(`/admin/modules?language=${language}`, { token });
}

export function updateModuleContent(token: string, number: number, language: string, request: ModuleContentRequest) {
  return apiClient<ModuleContent>(`/admin/modules/${number}/content/${language}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}

export function reorderModules(token: string, orderedNumbers: number[]) {
  return apiClient<void>('/admin/modules/order', {
    method: 'PUT',
    token,
    body: JSON.stringify({ orderedNumbers }),
  });
}
