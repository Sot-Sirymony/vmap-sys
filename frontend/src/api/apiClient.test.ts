import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient, clearApiCache } from './apiClient';

/**
 * The GET cache is the one piece of this layer that can show someone stale or,
 * worse, someone else's data — so its rules are pinned here: reuse within the
 * window, one request for concurrent callers, nothing survives a write, and
 * nothing crosses accounts.
 */

function jsonResponse(body: unknown) {
  return {
    ok: true,
    status: 200,
    json: () => Promise.resolve(body),
  } as unknown as Response;
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  clearApiCache();
  fetchMock = vi.fn().mockResolvedValue(jsonResponse({ value: 'first' }));
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
  clearApiCache();
});

describe('apiClient GET caching', () => {
  it('serves a repeated GET from the cache instead of refetching', async () => {
    await apiClient('/dreams', { token: 'token-a' });
    await apiClient('/dreams', { token: 'token-a' });

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('collapses concurrent identical GETs into one request', async () => {
    // The hierarchy pages fire several lists at once; two components asking
    // for the same one must not become two round trips.
    const [a, b] = await Promise.all([
      apiClient<{ value: string }>('/goals', { token: 'token-a' }),
      apiClient<{ value: string }>('/goals', { token: 'token-a' }),
    ]);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(a).toEqual(b);
  });

  it('keeps one account out of another account cache entry', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ value: 'account-a' }));
    fetchMock.mockResolvedValueOnce(jsonResponse({ value: 'account-b' }));

    const forA = await apiClient<{ value: string }>('/dreams', { token: 'token-a' });
    const forB = await apiClient<{ value: string }>('/dreams', { token: 'token-b' });

    expect(forA).toEqual({ value: 'account-a' });
    expect(forB).toEqual({ value: 'account-b' });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('refetches after a write, so a change is never hidden by the cache', async () => {
    await apiClient('/dreams', { token: 'token-a' });
    await apiClient('/dreams', { method: 'POST', token: 'token-a', body: '{}' });
    await apiClient('/dreams', { token: 'token-a' });

    // The GET, the POST, then the GET again: the write invalidated the entry.
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('refetches after a failed write too, since it may have changed something', async () => {
    await apiClient('/dreams', { token: 'token-a' });
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 500,
      text: () => Promise.resolve(''),
    } as unknown as Response);

    await expect(apiClient('/dreams/1', { method: 'PUT', token: 'token-a', body: '{}' })).rejects.toThrow();
    await apiClient('/dreams', { token: 'token-a' });

    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('does not leave a failed GET cached', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 500,
      text: () => Promise.resolve('boom'),
    } as unknown as Response);

    await expect(apiClient('/dreams', { token: 'token-a' })).rejects.toThrow('boom');
    await apiClient('/dreams', { token: 'token-a' });

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('refetches once the entry has aged out', async () => {
    vi.useFakeTimers();
    await apiClient('/dreams', { token: 'token-a' });
    vi.advanceTimersByTime(31_000);
    await apiClient('/dreams', { token: 'token-a' });

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
