import { apiClient } from './apiClient';
import type { AttitudeCheckIn, AttitudeCheckInRequest, DailyPlan } from '../types/vision';

export function getDailyPlan(token: string, date: string) {
  return apiClient<DailyPlan>(`/daily-plan?date=${date}`, { token });
}

export function listAttitudeCheckIns(token: string) {
  return apiClient<AttitudeCheckIn[]>('/attitude-checkins', { token });
}

export function saveAttitudeCheckIn(token: string, date: string, request: AttitudeCheckInRequest) {
  return apiClient<AttitudeCheckIn>(`/attitude-checkins/${date}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}
