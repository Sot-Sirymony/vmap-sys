import type { AppearancePreferences } from './preferences';

export type RegisterRequest = {
  fullName: string;
  email: string;
  password: string;
};

export type LoginRequest = {
  email: string;
  password: string;
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
  currentPassword: string;
  newPassword: string;
};

export type AuthResponse = {
  token: string;
  tokenType: string;
  userId: number;
  fullName: string;
  email: string;
  role: string;
  /**
   * FR-39.6: the saved appearance travels with the session, so the first paint
   * after signing in is already the user's own theme rather than the default
   * followed by a visible swap. Optional so the app still works against a
   * backend that predates FR-39.
   */
  appearance?: AppearancePreferences;
};

export type AuthState = {
  token: string | null;
  user: {
    id: number;
    fullName: string;
    email: string;
    role: string;
  } | null;
};
