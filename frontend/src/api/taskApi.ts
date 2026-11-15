import { apiClient } from './apiClient';
import type { TaskItem, TaskItemRequest, TaskSplitRequest, TaskSplitResult } from '../types/vision';

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

// V17 BL-FR-27: changeNote answers "What changed?" when the task leaves Blocked.
export function updateTaskStatus(token: string, id: number, status: string, changeNote?: string) {
  return apiClient<TaskItem>(`/tasks/${id}/status`, {
    method: 'PATCH',
    token,
    body: JSON.stringify(changeNote ? { status, manualOverride: true, changeNote } : { status, manualOverride: true }),
  });
}

// V17 BL-FR-25: breaks a task into 2 to 5 smaller tasks under the same step.
export function splitTask(token: string, id: number, request: TaskSplitRequest) {
  return apiClient<TaskSplitResult>(`/tasks/${id}/split`, {
    method: 'POST',
    token,
    body: JSON.stringify(request),
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

// Tasks page drag-and-drop: the user's own order across every step, front
// to back. Tasks left out keep their relative place after the listed ones.
export function reorderTaskList(token: string, orderedIds: number[]) {
  return apiClient<void>('/tasks/list-order', {
    method: 'PATCH',
    token,
    body: JSON.stringify({ orderedIds }),
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
