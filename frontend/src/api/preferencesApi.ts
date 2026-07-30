import { apiClient } from './apiClient';
import type { AppearancePreferences } from '../types/preferences';

/**
 * FR-39.6: the user's appearance, stored per account. Neither call takes a user
 * id — the backend resolves the caller from the token, so there is no shape of
 * request that could read or write somebody else's settings (BR-33).
 */
export function getAppearancePreferences(token: string) {
  return apiClient<AppearancePreferences>('/preferences/appearance', { token });
}

/**
 * Partial by design: the Appearance UI changes one control at a time, and an
 * omitted field keeps whatever is stored.
 */
export function updateAppearancePreferences(token: string, changes: Partial<AppearancePreferences>) {
  return apiClient<AppearancePreferences>('/preferences/appearance', {
    token,
    method: 'PUT',
    body: JSON.stringify(changes),
  });
}
