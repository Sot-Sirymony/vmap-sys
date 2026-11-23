import { apiClient } from './apiClient';
import { stepUpHeader } from './stepUpApi';
import type { PermissionInfo, RoleInfo, RoleInput } from '../types/admin';

/** V23 (BRD 23.0.0, UM-FR-08 to UM-FR-16): roles and the permission catalogue. */

export function listPermissions(token: string) {
  return apiClient<PermissionInfo[]>('/admin/permissions', { token });
}

export function listRoles(token: string) {
  return apiClient<RoleInfo[]>('/admin/roles', { token });
}

export function createRole(token: string, input: RoleInput, stepUp?: number) {
  return apiClient<RoleInfo>('/admin/roles', { method: 'POST', token, headers: stepUpHeader(stepUp), body: JSON.stringify(input) });
}

export function updateRole(token: string, key: string, input: RoleInput, stepUp?: number) {
  return apiClient<RoleInfo>(`/admin/roles/${encodeURIComponent(key)}`, {
    method: 'PUT', token, headers: stepUpHeader(stepUp), body: JSON.stringify(input),
  });
}

export function deleteRole(token: string, key: string, reason: string, moveTo?: string, stepUp?: number) {
  return apiClient<void>(`/admin/roles/${encodeURIComponent(key)}`, {
    method: 'DELETE', token, headers: stepUpHeader(stepUp), body: JSON.stringify(moveTo ? { moveTo, reason } : { reason }),
  });
}
