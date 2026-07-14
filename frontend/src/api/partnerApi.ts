import { apiClient } from './apiClient';
import type { Page, Partner, PartnerRequest } from '../types/vision';

/** `sort` is a Spring sort expression, e.g. "name,asc". `search` matches the partner's text fields. */
export function listPartners(token: string, page = 0, size = 20, includeArchived = false, sort?: string, search?: string) {
  const sortParam = sort ? `&sort=${sort}` : '';
  const searchParam = search ? `&search=${encodeURIComponent(search)}` : '';
  return apiClient<Page<Partner>>(
    `/partners?page=${page}&size=${size}&includeArchived=${includeArchived}${sortParam}${searchParam}`,
    { token },
  );
}

export function createPartner(token: string, request: PartnerRequest) {
  return apiClient<Partner>('/partners', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}

export function updatePartner(token: string, id: number, request: PartnerRequest) {
  return apiClient<Partner>(`/partners/${id}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}

export function archivePartner(token: string, id: number) {
  return apiClient<void>(`/partners/${id}`, {
    method: 'DELETE',
    token,
  });
}

export function restorePartner(token: string, id: number) {
  return apiClient<void>(`/partners/${id}/restore`, {
    method: 'POST',
    token,
  });
}

export function permanentlyDeletePartner(token: string, id: number) {
  return apiClient<void>(`/partners/${id}/permanent`, {
    method: 'DELETE',
    token,
  });
}
