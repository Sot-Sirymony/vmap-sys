import { authorizedFetch } from './apiClient';
import type { ClientErrorLogEntry, ClientErrorLogRequest } from '../types/vision';

// Diagnostics: requests that failed in the browser, recorded per page. Both
// calls use authorizedFetch rather than apiClient: apiClient would report
// these calls' own failures back into the log, clear the GET cache on every
// entry, and serve a cached list that misses the entry just sent.

/** Resolves to whether the server kept the entry. */
export async function recordClientError(token: string, request: ClientErrorLogRequest) {
  const response = await authorizedFetch('/client-errors', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  }, token);
  return response.ok;
}

/** The caller's saved entries for one page, newest first. Throws when the server cannot give them. */
export async function listClientErrors(token: string, pageKey: string) {
  const response = await authorizedFetch(`/client-errors?page=${encodeURIComponent(pageKey)}`, {}, token);
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
  return (await response.json()) as ClientErrorLogEntry[];
}
