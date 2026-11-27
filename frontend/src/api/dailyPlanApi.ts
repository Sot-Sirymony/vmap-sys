import { apiClient } from './apiClient';
import type { AttitudeCheckIn, AttitudeCheckInRequest, DailyPlan, Page } from '../types/vision';

export function getDailyPlan(token: string, date: string) {
  return apiClient<DailyPlan>(`/daily-plan?date=${date}`, { token });
}

export function listAttitudeCheckIns(token: string) {
  // V33: the newest 30, as before; the list is paged on the server.
  return apiClient<Page<AttitudeCheckIn>>('/attitude-checkins?page=0&size=30', { token }).then((page) => page.content);
}

export function saveAttitudeCheckIn(token: string, date: string, request: AttitudeCheckInRequest) {
  return apiClient<AttitudeCheckIn>(`/attitude-checkins/${date}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}
