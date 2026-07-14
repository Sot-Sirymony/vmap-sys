import { apiClient } from './apiClient';
import type { Page, Partner, PartnerRequest } from '../types/vision';

/** Empty values are omitted, which the API reads as "don't filter on this". */
export type PartnerFilters = {
  supportType?: string;
  status?: string;
  dreamId?: string;
};

/**
 * `sort` is a Spring sort expression, e.g. "name,asc". `search` matches the
 * partner's text fields. Filters are applied by the server, not the browser —
 * the client only holds one page, so filtering here would report just the
 * matches on that page.
 */
export function listPartners(
  token: string,
  page = 0,
  size = 20,
  includeArchived = false,
  sort?: string,
  search?: string,
  filters: PartnerFilters = {},
) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    includeArchived: String(includeArchived),
  });
  if (sort) {
    params.set('sort', sort);
  }
  if (search) {
    params.set('search', search);
  }
  for (const [key, value] of Object.entries(filters)) {
    if (value) {
      params.set(key, value);
    }
  }

  return apiClient<Page<Partner>>(`/partners?${params.toString()}`, { token });
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
