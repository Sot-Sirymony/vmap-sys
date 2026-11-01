import { apiClient } from './apiClient';
import type { Habit } from '../types/vision';

export function createHabit(token: string, date: string, name: string) {
  return apiClient<Habit>(`/habits?date=${date}`, {
    method: 'POST',
    token,
    body: JSON.stringify({ name }),
  });
}

export function setHabitChecked(token: string, id: number, date: string, checked: boolean) {
  return apiClient<Habit>(`/habits/${id}/checks/${date}`, {
    method: checked ? 'PUT' : 'DELETE',
    token,
  });
}

export function archiveHabit(token: string, id: number) {
  return apiClient<void>(`/habits/${id}`, {
    method: 'DELETE',
    token,
  });
}
