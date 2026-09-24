const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

type RequestOptions = RequestInit & {
  token?: string | null;
};

type ApiErrorResponse = {
  message?: string;
  error?: string;
};

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

async function sendRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (!(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (options.token) {
    headers.set('Authorization', `Bearer ${options.token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
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
    return error.message || error.error || fallback;
  } catch {
    return text;
  }
}
