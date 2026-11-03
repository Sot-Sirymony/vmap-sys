import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { clearApiCache, onSessionEnded } from '../api/apiClient';
import type { AuthResponse, AuthState } from '../types/auth';
import type { AppearancePreferences } from '../types/preferences';

type AuthContextValue = AuthState & {
  isAuthenticated: boolean;
  setSession: (response: AuthResponse, remember?: boolean) => void;
  logout: () => void;
  /**
   * True after the session ended on its own (the token expired or the server
   * refused it), rather than the user signing out. The login page says so.
   * Cleared by the next sign-in.
   */
  sessionExpired: boolean;
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

/** setTimeout cannot wait longer than this (about 24.8 days). */
const MAX_TIMER_MS = 2_147_483_647;

/**
 * When the token stops working, in milliseconds since 1970, read from its
 * `exp` claim. Null when it has none or cannot be read; the server's 401 still
 * catches those. Reading the payload needs no secret; it is not verified here.
 */
export function tokenExpiresAt(token: string | null): number | null {
  if (!token) {
    return null;
  }
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const claims = JSON.parse(atob(payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), '='))) as { exp?: number };
    return typeof claims.exp === 'number' ? claims.exp * 1000 : null;
  } catch {
    return null;
  }
}

function isExpired(token: string | null) {
  const expiresAt = tokenExpiresAt(token);
  return expiresAt !== null && expiresAt <= Date.now();
}

/**
 * "Remember me" decides which store the token lands in: localStorage survives a
 * browser restart, sessionStorage dies with the tab. Both are read on startup,
 * so an existing session is found wherever it was put.
 */
function readStoredAuth(): { auth: AuthState; expired: boolean } {
  const stored = localStorage.getItem(STORAGE_KEY) ?? sessionStorage.getItem(STORAGE_KEY);
  if (!stored) {
    return { auth: EMPTY_AUTH, expired: false };
  }

  try {
    const parsed = JSON.parse(stored) as AuthState;
    // A token that ran out while the app was closed is not a session to
    // restore, but the login page should still say why the user is there.
    if (isExpired(parsed.token)) {
      localStorage.removeItem(STORAGE_KEY);
      sessionStorage.removeItem(STORAGE_KEY);
      return { auth: EMPTY_AUTH, expired: true };
    }
    return { auth: parsed, expired: false };
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    sessionStorage.removeItem(STORAGE_KEY);
    return { auth: EMPTY_AUTH, expired: false };
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [initial] = useState(readStoredAuth);
  const [auth, setAuth] = useState<AuthState>(initial.auth);
  // FR-39.6: held in memory only, and only for the sign-in that just happened.
  const [appearance, setAppearance] = useState<AppearancePreferences | null>(null);
  const [sessionExpired, setSessionExpired] = useState(initial.expired);

  const clearSession = useCallback((expired: boolean) => {
    clearApiCache();
    localStorage.removeItem(STORAGE_KEY);
    sessionStorage.removeItem(STORAGE_KEY);
    setAppearance(null);
    setSessionExpired(expired);
    setAuth(EMPTY_AUTH);
  }, []);

  // The server refused a signed-in request: the session is over.
  useEffect(() => onSessionEnded(() => clearSession(true)), [clearSession]);

  // Sign out when the token runs out, even if no request is made at that moment.
  useEffect(() => {
    const expiresAt = tokenExpiresAt(auth.token);
    if (expiresAt === null) {
      return undefined;
    }
    const delay = expiresAt - Date.now();
    if (delay <= 0) {
      clearSession(true);
      return undefined;
    }
    const timer = window.setTimeout(() => clearSession(true), Math.min(delay, MAX_TIMER_MS));
    return () => window.clearTimeout(timer);
  }, [auth.token, clearSession]);

  const value = useMemo<AuthContextValue>(() => ({
    ...auth,
    appearance,
    sessionExpired,
    isAuthenticated: Boolean(auth.token),
    setSession: (response, remember = true) => {
      // A different account is signing in; nothing cached for the last one
      // may be reused, even though the cache keys already include the token.
      clearApiCache();
      setSessionExpired(false);
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
    logout: () => clearSession(false),
  }), [auth, appearance, sessionExpired, clearSession]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
