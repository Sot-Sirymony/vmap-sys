import { apiClient } from './apiClient';
import type {
  AuthResponse,
  ChangePasswordRequest,
  ForgotPasswordRequest,
  LoginRequest,
  RegisterRequest,
  ResetPasswordRequest,
} from '../types/auth';

export function login(request: LoginRequest) {
  return apiClient<AuthResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

/**
 * V10 (SEC-07-04): trades the refresh token for a new access token and a new
 * refresh token. 401 means the session has ended; 409 means another tab
 * renewed it a moment ago and its newer token should be used.
 */
export function refreshSession(refreshToken: string) {
  return apiClient<AuthResponse>('/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
  });
}

/** V10 (SEC-07-03): ends the session on the server. Always answers 204. */
export function logoutSession(refreshToken: string) {
  return apiClient<void>('/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
  });
}

export function register(request: RegisterRequest) {
  return apiClient<AuthResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

/**
 * Sets a new password for the signed-in user. Answers 204, so there is no body
 * to read — and nothing worth returning, since echoing any part of the request
 * would put a password somewhere it does not need to be.
 */
export function changePassword(request: ChangePasswordRequest, token: string | null) {
  return apiClient<void>('/auth/change-password', {
    method: 'POST',
    body: JSON.stringify(request),
    token,
  });
}

/**
 * Requests a reset link. Answers 204 whether or not the address has an account,
 * so there is deliberately nothing here to tell the two apart — the UI must not
 * imply it knows either.
 */
export function forgotPassword(request: ForgotPasswordRequest) {
  return apiClient<void>('/auth/forgot-password', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

/** Redeems a reset link. Public: the caller has no session to authenticate with. */
export function resetPassword(request: ResetPasswordRequest) {
  return apiClient<void>('/auth/reset-password', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}
