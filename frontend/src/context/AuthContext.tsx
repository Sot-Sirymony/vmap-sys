import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { ApiError, clearApiCache, onSessionEnded, setTokenRefresher } from '../api/apiClient';
import { logoutSession, refreshSession } from '../api/authApi';
import type { AuthResponse, AuthState, AuthUser } from '../types/auth';
import type { AppearancePreferences } from '../types/preferences';

export type AuthContextValue = AuthState & {
  isAuthenticated: boolean;
  /**
   * V10: true while a stored session is being renewed as the app opens. The
   * access token lives in memory only, so every page load starts by trading
   * the stored refresh token for a new one. Protected pages wait for it.
   */
  restoring: boolean;
  setSession: (response: AuthResponse, remember?: boolean) => void;
  logout: () => void;
  /**
   * True after the session ended on its own (it expired or the server refused
   * it), rather than the user signing out. The login page says so. Cleared by
   * the next sign-in.
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

/**
 * V10 (SEC-07-05): what is kept in browser storage. Only the refresh token and
 * the user's name for the header — never the access token, which stays in
 * memory. "Remember me" decides the store: localStorage survives a browser
 * restart, sessionStorage ends with the tab.
 */
type StoredSession = { refreshToken: string; user: AuthUser };

const STORAGE_KEY = 'visionMappingAuth';
const REFRESH_LOCK = 'vision-mapping-refresh';
/** Renew this long before the access token runs out. */
const REFRESH_AHEAD_MS = 60_000;

// Exported so tests can supply a signed-in user (src/test/render.tsx); the app uses AuthProvider.
export const AuthContext = createContext<AuthContextValue | null>(null);

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

/** The stored session and the store it is in, or nulls. */
function readStored(): { session: StoredSession | null; store: Storage | null } {
  for (const store of [localStorage, sessionStorage]) {
    const raw = store.getItem(STORAGE_KEY);
    if (!raw) {
      continue;
    }
    try {
      const parsed = JSON.parse(raw) as Partial<StoredSession>;
      if (typeof parsed.refreshToken === 'string' && parsed.user) {
        return { session: parsed as StoredSession, store };
      }
    } catch {
      // Unreadable: treated like the old format below.
    }
    // A session saved before V10 holds a long-lived access token instead.
    // The server no longer accepts those, so it is dropped: sign in again.
    store.removeItem(STORAGE_KEY);
  }
  return { session: null, store: null };
}

function clearStored() {
  localStorage.removeItem(STORAGE_KEY);
  sessionStorage.removeItem(STORAGE_KEY);
}

function userOf(response: AuthResponse): AuthUser {
  return { id: response.userId, fullName: response.fullName, email: response.email, role: response.role };
}

const wait = (ms: number) => new Promise((resolve) => { window.setTimeout(resolve, ms); });

/**
 * Trades the stored refresh token for a new pair and saves the new refresh
 * token where the old one was. Resolves to null when the session has ended;
 * throws when the server cannot be reached.
 *
 * Tabs share localStorage, and each refresh token works once. The Web Locks
 * API makes tabs take turns, and each reads the newest token from storage
 * after waiting, so two tabs never spend the same one. Where locks are not
 * available, the server's 409 ("renewed a moment ago") covers the race: read
 * storage again and retry once.
 */
async function renewStoredSession(): Promise<AuthResponse | null> {
  const run = async (): Promise<AuthResponse | null> => {
    for (let attempt = 0; attempt < 2; attempt += 1) {
      const { session, store } = readStored();
      if (!session || !store) {
        return null;
      }
      try {
        const response = await refreshSession(session.refreshToken);
        if (response.refreshToken) {
          store.setItem(STORAGE_KEY, JSON.stringify({ refreshToken: response.refreshToken, user: userOf(response) }));
        }
        return response;
      } catch (error) {
        if (error instanceof ApiError && error.status === 409 && attempt === 0) {
          await wait(300);
          continue;
        }
        if (error instanceof ApiError && (error.status === 401 || error.status === 409)) {
          return null;
        }
        throw error;
      }
    }
    return null;
  };
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
  return locks?.request ? locks.request(REFRESH_LOCK, run) : run();
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [initial] = useState(() => readStored().session);
  // The user is known from storage straight away, so the header can show their
  // name; the access token arrives once the stored session is renewed.
  const [auth, setAuth] = useState<AuthState>({ token: null, user: initial?.user ?? null });
  const [restoring, setRestoring] = useState(Boolean(initial));
  // FR-39.6: held in memory only, and only for the sign-in that just happened.
  const [appearance, setAppearance] = useState<AppearancePreferences | null>(null);
  const [sessionExpired, setSessionExpired] = useState(false);
  const tokenRef = useRef<string | null>(null);
  const renewing = useRef<Promise<string | null> | null>(null);

  const applyToken = useCallback((token: string | null, user: AuthUser | null) => {
    tokenRef.current = token;
    setAuth({ token, user });
  }, []);

  const clearSession = useCallback((expired: boolean) => {
    clearApiCache();
    clearStored();
    tokenRef.current = null;
    setAppearance(null);
    setSessionExpired(expired);
    setRestoring(false);
    setAuth(EMPTY_AUTH);
  }, []);

  /**
   * A new access token. One renewal at a time per tab: requests that fail
   * together share it. If the token that was refused is no longer the current
   * one, another request already renewed it, and the current one is returned.
   */
  const renewAccessToken = useCallback((refusedToken?: string | null): Promise<string | null> => {
    if (refusedToken && tokenRef.current && tokenRef.current !== refusedToken) {
      return Promise.resolve(tokenRef.current);
    }
    if (!renewing.current) {
      renewing.current = renewStoredSession()
        .then((response) => {
          if (!response) {
            return null;
          }
          applyToken(response.token, userOf(response));
          return response.token;
        })
        .finally(() => {
          renewing.current = null;
        });
    }
    return renewing.current;
  }, [applyToken]);

  // The API client renews the token after a 401 before giving up.
  useEffect(() => setTokenRefresher(renewAccessToken), [renewAccessToken]);

  // The server refused a signed-in request even after renewing: it is over.
  useEffect(() => onSessionEnded(() => clearSession(true)), [clearSession]);

  // Opening the app: trade the stored refresh token for an access token.
  useEffect(() => {
    if (!initial) {
      return;
    }
    renewAccessToken()
      .then((token) => {
        if (token) {
          setRestoring(false);
        } else {
          clearSession(true);
        }
      })
      .catch(() => {
        // The server could not be reached. Keep the stored session for the
        // next visit; this one shows the sign-in page.
        setRestoring(false);
      });
    // Runs once, for the session found at start-up.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Renew shortly before the access token runs out, so a page left open keeps
  // working without a failed request first.
  useEffect(() => {
    const expiresAt = tokenExpiresAt(auth.token);
    if (expiresAt === null) {
      return undefined;
    }
    const delay = Math.max(0, expiresAt - REFRESH_AHEAD_MS - Date.now());
    const timer = window.setTimeout(() => {
      renewAccessToken(auth.token)
        .then((token) => {
          if (!token) {
            clearSession(true);
          }
        })
        // Offline: the next request's 401 tries again.
        .catch(() => undefined);
    }, Math.min(delay, MAX_TIMER_MS));
    return () => window.clearTimeout(timer);
  }, [auth.token, renewAccessToken, clearSession]);

  // Signing out in one tab signs out the others.
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === STORAGE_KEY && event.newValue === null && tokenRef.current) {
        clearSession(false);
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [clearSession]);

  const value = useMemo<AuthContextValue>(() => ({
    ...auth,
    appearance,
    sessionExpired,
    restoring,
    isAuthenticated: Boolean(auth.token),
    setSession: (response, remember = true) => {
      // A different account is signing in; nothing cached for the last one
      // may be reused, even though the cache keys already include the token.
      clearApiCache();
      clearStored();
      setSessionExpired(false);
      setRestoring(false);
      setAppearance(response.appearance ?? null);
      const user = userOf(response);
      if (response.refreshToken) {
        const stored: StoredSession = { refreshToken: response.refreshToken, user };
        (remember ? localStorage : sessionStorage).setItem(STORAGE_KEY, JSON.stringify(stored));
      }
      applyToken(response.token, user);
    },
    logout: () => {
      const { session } = readStored();
      if (session) {
        // Ends the session on the server too; signing out here does not wait for it.
        void logoutSession(session.refreshToken).catch(() => undefined);
      }
      clearSession(false);
    },
  }), [auth, appearance, sessionExpired, restoring, clearSession, applyToken]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
