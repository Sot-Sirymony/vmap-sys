import { apiClient } from './apiClient';
import { stepUpHeader } from './stepUpApi';
import type {
  AdminRoleChangeInput,
  AdminUserCreateInput,
  AdminUserDetail,
  AdminUserFilter,
  AdminUserPage,
  AdminUserUpdateInput,
} from '../types/admin';

/** V23 (BRD 23.0.0, UM-FR-17 to UM-FR-38): the Users page. The server checks every permission again. */

export function listAdminUsers(token: string, filter: AdminUserFilter, page = 0, size = 25) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  Object.entries(filter).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') params.set(key, String(value));
  });
  return apiClient<AdminUserPage>(`/admin/users?${params.toString()}`, { token });
}

export function getAdminUser(token: string, userId: number) {
  return apiClient<AdminUserDetail>(`/admin/users/${userId}`, { token });
}

export function createAdminUser(token: string, input: AdminUserCreateInput, stepUp?: number) {
  return apiClient<AdminUserDetail>('/admin/users', {
    method: 'POST', token, headers: stepUpHeader(stepUp), body: JSON.stringify(input),
  });
}

export function updateAdminUser(token: string, userId: number, input: AdminUserUpdateInput) {
  return apiClient<AdminUserDetail>(`/admin/users/${userId}`, { method: 'PATCH', token, body: JSON.stringify(input) });
}

export function changeAdminUserRole(token: string, userId: number, input: AdminRoleChangeInput, stepUp?: number) {
  return apiClient<AdminUserDetail>(`/admin/users/${userId}/role`, {
    method: 'PUT', token, headers: stepUpHeader(stepUp), body: JSON.stringify(input),
  });
}

export function blockAdminUser(token: string, userId: number, reason: string, stepUp?: number) {
  return apiClient<AdminUserDetail>(`/admin/users/${userId}/block`, {
    method: 'POST', token, headers: stepUpHeader(stepUp), body: JSON.stringify({ reason }),
  });
}

export function unblockAdminUser(token: string, userId: number, reason: string, stepUp?: number) {
  return apiClient<AdminUserDetail>(`/admin/users/${userId}/unblock`, {
    method: 'POST', token, headers: stepUpHeader(stepUp), body: JSON.stringify({ reason }),
  });
}

export function resendAdminUserInvitation(token: string, userId: number) {
  return apiClient<void>(`/admin/users/${userId}/invitation`, { method: 'POST', token });
}

export function sendAdminUserPasswordReset(token: string, userId: number) {
  return apiClient<void>(`/admin/users/${userId}/password-reset`, { method: 'POST', token });
}

export function deleteAdminUser(token: string, userId: number, confirmEmail: string, reason: string, stepUp?: number) {
  return apiClient<void>(`/admin/users/${userId}`, {
    method: 'DELETE', token, headers: stepUpHeader(stepUp), body: JSON.stringify({ confirmEmail, reason }),
  });
}
