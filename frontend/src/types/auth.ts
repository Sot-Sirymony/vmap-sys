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
