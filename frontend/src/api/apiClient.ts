import { tr } from '../i18n/translate';
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

type RequestOptions = RequestInit & {
  token?: string | null;
};

/**
 * V33 (D-33-13): an RFC 9457 problem (application/problem+json). `detail` is the
 * message to show; `message`, `error` and `path` are the older names the server
 * still sends during API version 1.
 */
type ApiErrorResponse = {
  detail?: string;
  title?: string;
  message?: string;
  error?: string;
  // V10 (SEC-10-02): matches the server log line for this error.
  reference?: string;
  // V20: what happened, for a page that acts on it (for example STEP_UP_REQUIRED).
  code?: string;
  triesLeft?: number;
  action?: string;
};

/** A request the server refused. `status` lets callers tell 401, 409 and the rest apart. */
export class ApiError extends Error {
  readonly status: number;
  /** V20: the server's code for what happened, such as STEP_UP_REQUIRED. */
  readonly code?: string;
  /** V20: tries left after a wrong "Verify it's you" code. */
  readonly triesLeft?: number;
  /** V20: on STEP_UP_REQUIRED, which code the server wants. */
  readonly action?: string;

  constructor(message: string, status: number, code?: string, triesLeft?: number, action?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.triesLeft = triesLeft;
    this.action = action;
  }
}

/** V21 (TF-FR-34): the code, and the window event the app listens for. */
export const TWO_FACTOR_SETUP_REQUIRED = 'TWO_FACTOR_SETUP_REQUIRED';

/** V20: the ApiError for a refused file download, so pages can act on its code like any other request. */
export async function apiErrorFrom(response: Response): Promise<ApiError> {
  const { message, code, triesLeft, action } = await readError(response);
  return new ApiError(message, response.status, code, triesLeft, action);
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

/** A request that failed: status 0 when the server could not be reached. */
export type ApiFailure = {
  method: string;
  /** The API path as called, without API_BASE_URL. */
  path: string;
  status: number;
  message: string;
  reference?: string;
};

type ApiFailureListener = (failure: ApiFailure) => void;

const apiFailureListeners = new Set<ApiFailureListener>();

/**
 * Diagnostics: told about every failed apiClient request, so a page can record
 * the failures of its own calls (see useClientErrorLog). Returns a function
 * that stops listening. A listener that throws never breaks the request.
 */
export function onApiFailure(listener: ApiFailureListener) {
  apiFailureListeners.add(listener);
  return () => {
    apiFailureListeners.delete(listener);
  };
}

function reportFailure(failure: ApiFailure) {
  apiFailureListeners.forEach((listener) => {
    try {
      listener(failure);
    } catch {
      // A diagnostics listener must never change what the caller sees.
    }
  });
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

  const method = (options.method ?? 'GET').toUpperCase();
  let response: Response;
  try {
    response = await send(options.token);
  } catch (networkError) {
    reportFailure({ method, path, status: 0, message: networkError instanceof Error ? networkError.message : String(networkError) });
    throw networkError;
  }
  // V10 (SEC-07-04): the access token is short-lived. A 401 on a signed-in
  // request is first answered by renewing it and trying once more; only if
  // that fails too is the session over.
  const retryToken = await renewedToken(response, options.token);
  if (retryToken) {
    response = await send(retryToken);
  }

  if (!response.ok) {
    reportSessionEnded(response, retryToken ?? options.token);
    const { message, reference, code, triesLeft, action } = await readError(response);
    reportFailure({ method, path, status: response.status, message, reference });
    if (code === TWO_FACTOR_SETUP_REQUIRED) {
      // V21 (TF-FR-34): an admin or coach past their date; the app opens the setup page.
      window.dispatchEvent(new Event(TWO_FACTOR_SETUP_REQUIRED));
    }
    throw new ApiError(message, response.status, code, triesLeft, action);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

/** V33 (D-33-9): the largest page the server sends; asking for more still gets this many. */
export const MAX_PAGE_SIZE = 200;

/** A safety stop: 50 pages of 200 is 10,000 items, far more than any one user has. */
const MAX_PAGES = 50;

/**
 * V33 (D-33-9): every item of a paged list, for screens that need the whole list
 * (the task board, the timeline, pickers). It asks for the largest pages and
 * stops at the last one. `path` may already have a query string.
 */
export async function fetchAllPages<T>(path: string, token: string | null | undefined): Promise<T[]> {
  const items: T[] = [];
  const separator = path.includes('?') ? '&' : '?';
  for (let page = 0; page < MAX_PAGES; page++) {
    const result = await apiClient<{ content: T[]; last: boolean }>(`${path}${separator}page=${page}&size=${MAX_PAGE_SIZE}`, { token });
    items.push(...result.content);
    if (result.last || result.content.length === 0) {
      break;
    }
  }
  return items;
}

/** V33: the text to show from an error body, whether it is a problem or the older shape. */
export function problemMessage(body: { detail?: string; message?: string; title?: string; error?: string } | null | undefined): string | undefined {
  if (!body) {
    return undefined;
  }
  return body.detail || body.message || body.title || body.error || undefined;
}

/** The message to show, and the server's reference for this error when it sent one. */
async function readError(response: Response): Promise<{ message: string; reference?: string; code?: string; triesLeft?: number; action?: string }> {
  const fallback = `Request failed with status ${response.status}`;
  const text = await response.text();
  if (!text) {
    return { message: fallback };
  }

  try {
    const error = JSON.parse(text) as ApiErrorResponse;
    const message = problemMessage(error) || fallback;
    // A server fault has nothing the user can fix, so give them the reference
    // to quote when they report it. Other errors keep their message as is, so
    // it can still be translated.
    return {
      message: response.status >= 500 && error.reference ? tr('{message} (Reference: {reference})', { message: tr(message), reference: error.reference }) : message,
      reference: error.reference,
      code: error.code,
      triesLeft: error.triesLeft,
      action: error.action,
    };
  } catch {
    return { message: text };
  }
}
