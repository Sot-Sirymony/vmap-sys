import { tr } from '../i18n/translate';
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

type RequestOptions = RequestInit & {
  token?: string | null;
};

type ApiErrorResponse = {
  message?: string;
  error?: string;
  // V10 (SEC-10-02): matches the server log line for this error.
  reference?: string;
};

/** A request the server refused. `status` lets callers tell 401, 409 and the rest apart. */
export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/**
 * V10 (SEC-07-04): renews the access token after a 401. Given the token that
 * was refused, it resolves to a new one, or to null when the session has
 * ended. It throws when the server cannot be reached, so being offline never
 * signs anyone out. AuthProvider registers it.
 */
type TokenRefresher = (refusedToken: string) => Promise<string | null>;

let tokenRefresher: TokenRefresher | null = null;

/** Returns a function that removes the refresher again. */
export function setTokenRefresher(refresher: TokenRefresher) {
  tokenRefresher = refresher;
  return () => {
    if (tokenRefresher === refresher) {
      tokenRefresher = null;
    }
  };
}

/**
 * After a 401 on a signed-in request: a fresh token to retry with, or null
 * when there is none (no refresher, the session ended, or the server could
 * not be reached).
 */
async function renewedToken(response: Response, token: string | null | undefined): Promise<string | null> {
  if (response.status !== 401 || !token || !tokenRefresher) {
    return null;
  }
  try {
    return await tokenRefresher(token);
  } catch {
    return null;
  }
}

/**
 * fetch with the bearer token, renewing it once on a 401, for the few calls
 * that need the raw Response (downloads, the Dream Coach stream). The caller
 * still checks `response.ok` and calls reportSessionEnded with the token it
 * passed, which signs the user out if the retry was refused too.
 */
export async function authorizedFetch(path: string, init: RequestInit, token: string): Promise<Response> {
  const send = (bearer: string) => {
    const headers = new Headers(init.headers);
    headers.set('Authorization', `Bearer ${bearer}`);
    return fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  };
  const response = await send(token);
  const retryToken = await renewedToken(response, token);
  return retryToken ? send(retryToken) : response;
}

/**
 * How long a GET stays reusable. The hierarchy pages each need several of the
 * same lists (areas, dreams, goals, steps, tasks) to label and filter their
 * rows, so moving between them refetched the same handful of endpoints every
 * time. This window is short enough that a change made in another tab or by
 * another device shows up on the next visit, and long enough to cover the
 * navigation that prompted it.
 *
 * It is deliberately far shorter than the backend's own ten-minute cache: this
 * one cannot be evicted by anything but its own TTL, so it errs small.
 */
const GET_CACHE_TTL_MS = 30_000;

type CacheEntry = { expiresAt: number; payload: unknown };

const responseCache = new Map<string, CacheEntry>();
/** Identical GETs issued before the first one answers share its promise. */
const inFlightGets = new Map<string, Promise<unknown>>();

/**
 * Forget everything cached. Called on any write, and on sign-in and sign-out —
 * the cache key includes the token, but clearing on a session change means a
 * signed-out user's data cannot outlive their session in memory either.
 */
export function clearApiCache() {
  responseCache.clear();
  inFlightGets.clear();
}

/** Scoped by token as well as path, so one account can never read another's entry. */
function cacheKey(path: string, token: string | null | undefined) {
  return `${token ?? 'anonymous'} ${path}`;
}

export async function apiClient<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();

  if (method === 'GET') {
    const key = cacheKey(path, options.token);
    const cached = responseCache.get(key);
    if (cached && cached.expiresAt > Date.now()) {
      return cached.payload as T;
    }
    const alreadyRunning = inFlightGets.get(key);
    if (alreadyRunning) {
      return alreadyRunning as Promise<T>;
    }
    const request = sendRequest<T>(path, options)
      .then((payload) => {
        responseCache.set(key, { expiresAt: Date.now() + GET_CACHE_TTL_MS, payload });
        return payload;
      })
      .finally(() => {
        // Whether it resolved or threw, it is no longer in flight; a failure
        // must not leave a rejected promise behind for the next caller.
        inFlightGets.delete(key);
      });
    inFlightGets.set(key, request);
    return request;
  }

  try {
    return await sendRequest<T>(path, options);
  } finally {
    // Anything that is not a GET may have changed what the GETs would return,
    // and a write that failed part-way may have changed some of it. Clearing
    // unconditionally is the cheap, safe choice: the cost of an unnecessary
    // clear is one refetch, the cost of a missed one is stale data on screen.
    clearApiCache();
  }
}

type SessionEndedListener = () => void;

const sessionEndedListeners = new Set<SessionEndedListener>();

/**
 * Called when the server says a signed-in request is no longer signed in: the
 * token expired or was revoked. AuthProvider listens and signs the user out,
 * so pages stop failing one request at a time and the login page opens.
 * Returns a function that stops listening.
 */
export function onSessionEnded(listener: SessionEndedListener) {
  sessionEndedListeners.add(listener);
  return () => {
    sessionEndedListeners.delete(listener);
  };
}

/**
 * For every call that carries a token, including the few that use fetch
 * directly (downloads, the Dream Coach stream). A 401 without a token is a
 * failed sign-in, not an ended session, so it is left alone.
 */
export function reportSessionEnded(response: Response, token: string | null | undefined) {
  if (response.status === 401 && token) {
    sessionEndedListeners.forEach((listener) => listener());
  }
}

async function sendRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const send = (token: string | null | undefined) => {
    const headers = new Headers(options.headers);
    if (!(options.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json');
    }
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    return fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  };

  let response = await send(options.token);
  // V10 (SEC-07-04): the access token is short-lived. A 401 on a signed-in
  // request is first answered by renewing it and trying once more; only if
  // that fails too is the session over.
  const retryToken = await renewedToken(response, options.token);
  if (retryToken) {
    response = await send(retryToken);
  }

  if (!response.ok) {
    reportSessionEnded(response, retryToken ?? options.token);
    throw new ApiError(await readErrorMessage(response), response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

async function readErrorMessage(response: Response) {
  const fallback = `Request failed with status ${response.status}`;
  const text = await response.text();
  if (!text) {
    return fallback;
  }

  try {
    const error = JSON.parse(text) as ApiErrorResponse;
    const message = error.message || error.error || fallback;
    // A server fault has nothing the user can fix, so give them the reference
    // to quote when they report it. Other errors keep their message as is, so
    // it can still be translated.
    return response.status >= 500 && error.reference ? tr('{message} (Reference: {reference})', { message: tr(message), reference: error.reference }) : message;
  } catch {
    return text;
  }
}
