import { apiClient } from './apiClient';
import type { CommunicationMessage, CommunicationMessageRequest, Page } from '../types/vision';

/** `sort` is a Spring sort expression, e.g. "subject,asc". `search` matches the message's text fields. */
export function listCommunicationMessages(
  token: string,
  page = 0,
  size = 20,
  includeArchived = false,
  sort?: string,
  search?: string,
) {
  const sortParam = sort ? `&sort=${sort}` : '';
  const searchParam = search ? `&search=${encodeURIComponent(search)}` : '';
  return apiClient<Page<CommunicationMessage>>(
    `/communication-messages?page=${page}&size=${size}&includeArchived=${includeArchived}${sortParam}${searchParam}`,
    { token },
  );
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
