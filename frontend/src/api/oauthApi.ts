import { apiClient } from './apiClient';
import type { AuthResponse } from '../types/auth';

/**
 * V14 (BRD 14.0.0, Appendix B): Sign in with Google. Every call except
 * getSignInMethods is public, because the person is not signed in yet.
 */

/** GA-FR-03: whether the server has Google sign-in set up. */
export function getGoogleSignInStatus() {
  return apiClient<{ enabled: boolean }>('/auth/oauth/google/status');
}

/**
 * GA-FR-05, GA-FR-22: where to send the browser to begin Google sign-in. The
 * server keeps "remember me" and the in-app page to return to with the attempt.
 */
export function getGoogleAuthorizeUrl(remember: boolean, from: string) {
  const query = new URLSearchParams({ remember: String(remember), from });
  return apiClient<{ url: string }>(`/auth/oauth/google/authorize-url?${query.toString()}`);
}

/** GA-FR-20: turns the one-time code from the callback into a session. */
export function exchangeGoogleCode(code: string) {
  return apiClient<AuthResponse>('/auth/oauth/google/exchange', {
    method: 'POST',
    body: JSON.stringify({ code }),
  });
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

export type SignInMethods = {
  /** False for an account created with Google until it sets a password (D-14-3). */
  passwordSet: boolean;
  linkedAccounts: { provider: 'GOOGLE'; email: string; linkedAt: string }[];
};

/** GA-FR-26, GA-FR-27: how the signed-in person can sign in. */
export function getSignInMethods(token: string | null) {
  return apiClient<SignInMethods>('/account/sign-in-methods', { token });
}
