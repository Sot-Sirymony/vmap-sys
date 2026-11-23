import type { AppearancePreferences } from './preferences';

export type RegisterRequest = {
  fullName: string;
  email: string;
  password: string;
  /** V9 (D-7): optional cohort join code. */
  joinCode?: string;
};

export type LoginRequest = {
  email: string;
  password: string;
  /** V10: a 30-day session, or one that ends with the browser tab. */
  rememberMe?: boolean;
  /** V21 (TF-FR-22): this browser's token from an earlier "Remember this device". */
  deviceToken?: string;
};

/**
 * The current password is required even though the request is authenticated: a
 * token proves a session was opened at some point, not that the person holding
 * it now is the account owner.
 *
 * There is no user identifier — the backend changes the password of whoever the
 * token belongs to, so one account can never re-password another.
 */
export type ChangePasswordRequest = {
  /** Left out by an account created with Google, which has no password yet (V14 D-14-3). */
  currentPassword?: string;
  newPassword: string;
};

/** Asking for a reset link. Only the address — the caller is signed out. */
export type ForgotPasswordRequest = {
  email: string;
};

/**
 * Redeeming a reset link. The token stands in for the old password: it is proof
 * of access to the account's inbox, which is the only credential someone who has
 * forgotten their password still has. There is no email field — the token
 * identifies the account by itself.
 */
export type ResetPasswordRequest = {
  token: string;
  newPassword: string;
};

export type AuthResponse = {
  token: string;
  tokenType: string;
  userId: number;
  fullName: string;
  email: string;
  role: string;
  /** V23 (UM-FR-05): the role's permission keys. Optional for responses that predate V23. */
  permissions?: string[];
  /**
   * FR-39.6: the saved appearance travels with the session, so the first paint
   * after signing in is already the user's own theme rather than the default
   * followed by a visible swap. Optional so the app still works against a
   * backend that predates FR-39.
   */
  appearance?: AppearancePreferences;
  /**
   * V10 (SEC-07-04): `token` above is a short-lived access token, kept in
   * memory only. The refresh token renews it and is the one value kept in
   * browser storage. Optional for responses that predate V10.
   */
  refreshToken?: string;
  /** Seconds until `token` runs out. */
  expiresIn?: number;
  /** V21 (TF-FR-21): present only right after a second step with "Remember this device". */
  deviceToken?: string;
};

export type AuthUser = {
  id: number;
  fullName: string;
  email: string;
  role: string;
  /** V23: absent in sessions saved before V23 (see utils/permissions). */
  permissions?: string[];
};

export type AuthState = {
  /** The access token. In memory only; never written to browser storage. */
  token: string | null;
  user: AuthUser | null;
};

/**
 * V21 (TF-FR-10): the answer to a correct password (or Google) when two-factor
 * sign-in is on: no session yet, only the second step.
 */
export type TwoFactorChallenge = {
  status: 'TWO_FACTOR_REQUIRED';
  challengeToken: string;
  method: 'TOTP' | 'EMAIL';
  /** The masked account email, for the email method. */
  sentTo?: string;
  /** True when the email code could not be sent; only a recovery code can finish. */
  emailUnavailable: boolean;
  expiresAt: string;
};

/** What a first factor answers: the session, or the second step. */
export type SignInAnswer = AuthResponse | TwoFactorChallenge;

export function isTwoFactorChallenge(answer: SignInAnswer): answer is TwoFactorChallenge {
  return (answer as TwoFactorChallenge).status === 'TWO_FACTOR_REQUIRED';
}
