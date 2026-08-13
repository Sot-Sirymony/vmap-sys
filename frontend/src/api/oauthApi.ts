import { apiClient } from './apiClient';
import type { AuthResponse } from '../types/auth';

/**
 * Google OAuth endpoints. The backend does not implement these yet — the UI
 * flow (loading → error / account linking) is wired end-to-end so the screens
 * are real routes, and until the backend ships its OAuth2 support the start
 * call fails and the flow lands on the designed error screen instead of a
 * broken page.
 */

/** Asks the backend where to send the browser to begin Google sign-in. */
export function getGoogleAuthorizeUrl() {
  return apiClient<{ url: string }>('/auth/oauth/google/authorize-url');
}

/**
 * Links a Google identity to an existing password account. The link token
 * comes back from the OAuth callback when the email already has an account;
 * the password proves the person linking owns that account.
 */
export function linkGoogleAccount(request: { linkToken: string; password: string }) {
  return apiClient<AuthResponse>('/auth/oauth/google/link', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}
