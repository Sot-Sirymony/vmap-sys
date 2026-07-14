import { apiClient } from './apiClient';
import type { CommunicationMessage, CommunicationMessageRequest, Page } from '../types/vision';

/** Empty values are omitted, which the API reads as "don't filter on this". */
export type CommunicationFilters = {
  partnerId?: string;
  status?: string;
};

/**
 * `sort` is a Spring sort expression, e.g. "subject,asc". `search` matches the
 * message's text fields. Filters are applied by the server, not the browser —
 * the client only holds one page, so filtering here would report just the
 * matches on that page.
 */
export function listCommunicationMessages(
  token: string,
  page = 0,
  size = 20,
  includeArchived = false,
  sort?: string,
  search?: string,
  filters: CommunicationFilters = {},
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

  return apiClient<Page<CommunicationMessage>>(`/communication-messages?${params.toString()}`, { token });
}

export function createCommunicationMessage(token: string, request: CommunicationMessageRequest) {
  return apiClient<CommunicationMessage>('/communication-messages', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}

export function updateCommunicationMessage(token: string, id: number, request: CommunicationMessageRequest) {
  return apiClient<CommunicationMessage>(`/communication-messages/${id}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}

export function archiveCommunicationMessage(token: string, id: number) {
  return apiClient<void>(`/communication-messages/${id}`, {
    method: 'DELETE',
    token,
  });
}

export function restoreCommunicationMessage(token: string, id: number) {
  return apiClient<void>(`/communication-messages/${id}/restore`, {
    method: 'POST',
    token,
  });
}

export function permanentlyDeleteCommunicationMessage(token: string, id: number) {
  return apiClient<void>(`/communication-messages/${id}/permanent`, {
    method: 'DELETE',
    token,
  });
}
