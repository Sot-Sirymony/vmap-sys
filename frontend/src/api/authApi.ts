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
