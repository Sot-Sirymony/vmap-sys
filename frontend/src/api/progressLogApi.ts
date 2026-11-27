import { fetchAllPages } from './apiClient';
import type { ProgressLog } from '../types/vision';

export function listProgressLogs(token: string) {
  return fetchAllPages<ProgressLog>('/progress-logs', token);
}
