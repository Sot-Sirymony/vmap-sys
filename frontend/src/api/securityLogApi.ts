import { apiClient } from './apiClient';

/** V10 (SEC-09-05): one row of the security log. */
export type SecurityEvent = {
  id: number;
  occurredAt: string;
  eventType: string;
  outcome: 'SUCCESS' | 'FAILURE' | 'DENIED';
  actorId: number | null;
  ipAddress: string | null;
  targetType: string | null;
  targetId: number | null;
  detail: string | null;
};

export type SecurityLog = {
  events: SecurityEvent[];
  page: number;
  size: number;
  totalEvents: number;
  hasMore: boolean;
  recentAlerts: string[];
};

export type SecurityLogFilter = {
  type?: string;
  outcome?: string;
  actorId?: string;
};

/** Admins only. Read-only: the log cannot be changed through the API. */
export function getSecurityLog(token: string, filter: SecurityLogFilter, page: number) {
  const params = new URLSearchParams({ page: String(page), size: '50' });
  if (filter.type) params.set('type', filter.type);
  if (filter.outcome) params.set('outcome', filter.outcome);
  if (filter.actorId) params.set('actorId', filter.actorId);
  return apiClient<SecurityLog>(`/admin/security-events?${params.toString()}`, { token });
}
