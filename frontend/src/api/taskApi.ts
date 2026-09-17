import { apiClient } from './apiClient';
import type { TaskItem, TaskItemRequest } from '../types/vision';

export function listTasks(token: string, includeArchived = false) {
  return apiClient<TaskItem[]>(`/tasks?includeArchived=${includeArchived}`, { token });
}

export function createTask(token: string, request: TaskItemRequest) {
  return apiClient<TaskItem>('/tasks', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  });
}

export function updateTask(token: string, id: number, request: TaskItemRequest) {
  return apiClient<TaskItem>(`/tasks/${id}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(request),
  });
}

export function archiveTask(token: string, id: number) {
  return apiClient<void>(`/tasks/${id}`, {
    method: 'DELETE',
    token,
  });
}

export function updateTaskStatus(token: string, id: number, status: string) {
  return apiClient<TaskItem>(`/tasks/${id}/status`, {
    method: 'PATCH',
    token,
    body: JSON.stringify({ status, manualOverride: true }),
  });
}

// Vision Map drag-and-drop: orderedIds is the full, reshuffled set of this
// step's tasks.
export function reorderTasks(token: string, stepId: number, orderedIds: number[]) {
  return apiClient<void>('/tasks/reorder', {
    method: 'PATCH',
    token,
    body: JSON.stringify({ parentId: stepId, orderedIds }),
  });
}

export function restoreTask(token: string, id: number) {
  return apiClient<void>(`/tasks/${id}/restore`, {
    method: 'POST',
    token,
  });
}

export function permanentlyDeleteTask(token: string, id: number) {
  return apiClient<void>(`/tasks/${id}/permanent`, {
    method: 'DELETE',
    token,
  });
}
