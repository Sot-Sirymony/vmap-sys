import { apiClient } from './apiClient';
import { stepUpHeader } from './stepUpApi';
import type { AuthResponse, TwoFactorChallenge } from '../types/auth';

/** V21 (BRD 21.0.0): two-factor sign-in. */

export type TrustedDevice = { id: number; label: string; createdAt: string; lastUsedAt: string; expiresAt: string };

export type TwoFactorStatus = {
  enabled: boolean;
  method?: 'TOTP' | 'EMAIL' | null;
  enabledAt?: string | null;
  recoveryCodesLeft: number;
  dueAt?: string | null;
  emailMethodAvailable: boolean;
  canTurnOff: boolean;
  trustedDevices: TrustedDevice[];
};

export type TotpSetup = { secret: string; otpauthUri: string; expiresAt: string };

export type RecoveryCodes = { recoveryCodes: string[] };

/** TF-FR-13 to TF-FR-17: the second step; a code or a recovery code. */
export function verifySignIn(request: { challengeToken: string; code?: string; recoveryCode?: string; rememberDevice: boolean }) {
  return apiClient<AuthResponse>('/auth/two-factor/verify', { method: 'POST', body: JSON.stringify(request) });
}

/** TF-FR-12: a new email code, at most once a minute. */
export function resendSignInCode(challengeToken: string) {
  return apiClient<TwoFactorChallenge>('/auth/two-factor/resend', { method: 'POST', body: JSON.stringify({ challengeToken }) });
}

export function getTwoFactorStatus(token: string | null) {
  return apiClient<TwoFactorStatus>('/account/two-factor', { token });
}

/** TF-FR-02: needs a fresh "Verify it's you" code (stepUp). */
export function startAuthenticatorSetup(token: string | null, stepUp?: number) {
  return apiClient<TotpSetup>('/account/two-factor/totp', { method: 'POST', headers: stepUpHeader(stepUp), token });
}

/** TF-FR-03, TF-FR-04: the first code from the app; answers the new recovery codes. */
export function confirmAuthenticatorSetup(token: string | null, code: string) {
  return apiClient<RecoveryCodes>('/account/two-factor/totp/confirm', { method: 'POST', body: JSON.stringify({ code }), token });
}

/** TF-FR-05: participants only; needs a fresh code. */
export function chooseEmailCodes(token: string | null, stepUp?: number) {
  return apiClient<RecoveryCodes>('/account/two-factor/email', { method: 'POST', headers: stepUpHeader(stepUp), token });
}

/** TF-FR-07: needs a fresh code. */
export function newRecoveryCodes(token: string | null, stepUp?: number) {
  return apiClient<RecoveryCodes>('/account/two-factor/recovery-codes', { method: 'POST', headers: stepUpHeader(stepUp), token });
}

/** TF-FR-08: needs a fresh code. */
export function turnOffTwoFactor(token: string | null, stepUp?: number) {
  return apiClient<void>('/account/two-factor/disable', { method: 'POST', headers: stepUpHeader(stepUp), token });
}

/** TF-FR-23. */
export function removeTrustedDevice(token: string | null, id: number) {
  return apiClient<void>(`/account/trusted-devices/${id}`, { method: 'DELETE', token });
}

export function removeAllTrustedDevices(token: string | null) {
  return apiClient<void>('/account/trusted-devices', { method: 'DELETE', token });
}

/** TF-FR-33: an admin or coach chose "Skip for now" (logged). */
export function skipTwoFactorSetup(token: string | null) {
  return apiClient<void>('/account/two-factor/skip', { method: 'POST', token });
}

export type TwoFactorReportRow = {
  userId: number;
  fullName: string;
  email: string;
  role: string;
  enabled: boolean;
  method?: 'TOTP' | 'EMAIL' | null;
  dueAt?: string | null;
  overdue: boolean;
};

/** TF-FR-36: admins only. */
export function getTwoFactorReport(token: string | null) {
  return apiClient<TwoFactorReportRow[]>('/admin/two-factor', { token });
}

/** TF-FR-28: admins only; needs the admin's own fresh code. */
export function resetTwoFactor(token: string | null, userId: number, reason: string, stepUp?: number) {
  return apiClient<void>(`/admin/users/${userId}/two-factor/reset`, {
    method: 'POST', headers: stepUpHeader(stepUp), body: JSON.stringify({ reason }), token,
  });
}
