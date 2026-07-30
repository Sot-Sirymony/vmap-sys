import { createContext, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { AuthResponse, AuthState } from '../types/auth';
import type { AppearancePreferences } from '../types/preferences';

type AuthContextValue = AuthState & {
  isAuthenticated: boolean;
  setSession: (response: AuthResponse, remember?: boolean) => void;
  logout: () => void;
  /**
   * FR-39.6: the appearance the login/register response carried, for
   * ThemeModeProvider to adopt before the first paint. Deliberately NOT
   * persisted with the session — a stale cached theme is exactly what the GET
   * fallback in ThemeModeProvider exists to correct, so this is only ever
   * populated by a sign-in that just happened.
   */
  appearance: AppearancePreferences | null;
};

const STORAGE_KEY = 'visionMappingAuth';

const AuthContext = createContext<AuthContextValue | null>(null);

const EMPTY_AUTH: AuthState = { token: null, user: null };

/**
 * "Remember me" decides which store the token lands in: localStorage survives a
 * browser restart, sessionStorage dies with the tab. Both are read on startup,
 * so an existing session is found wherever it was put.
 */
function readStoredAuth(): AuthState {
  const stored = localStorage.getItem(STORAGE_KEY) ?? sessionStorage.getItem(STORAGE_KEY);
  if (!stored) {
    return EMPTY_AUTH;
  }

  try {
    return JSON.parse(stored) as AuthState;
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    sessionStorage.removeItem(STORAGE_KEY);
    return EMPTY_AUTH;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<AuthState>(() => readStoredAuth());
  // FR-39.6: held in memory only, and only for the sign-in that just happened.
  const [appearance, setAppearance] = useState<AppearancePreferences | null>(null);

  const value = useMemo<AuthContextValue>(() => ({
    ...auth,
    appearance,
    isAuthenticated: Boolean(auth.token),
    setSession: (response, remember = true) => {
      setAppearance(response.appearance ?? null);
      const nextAuth: AuthState = {
        token: response.token,
        user: {
          id: response.userId,
          fullName: response.fullName,
          email: response.email,
          role: response.role,
        },
      };
      // Write to one store and clear the other, so a session can never linger in
      // localStorage after the user asked not to be remembered.
      const [store, otherStore] = remember
        ? [localStorage, sessionStorage]
        : [sessionStorage, localStorage];
      store.setItem(STORAGE_KEY, JSON.stringify(nextAuth));
      otherStore.removeItem(STORAGE_KEY);
      setAuth(nextAuth);
    },
    logout: () => {
      localStorage.removeItem(STORAGE_KEY);
      sessionStorage.removeItem(STORAGE_KEY);
      setAppearance(null);
      setAuth(EMPTY_AUTH);
    },
  }), [auth, appearance]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
