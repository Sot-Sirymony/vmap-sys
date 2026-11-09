import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL, apiClient, clearApiCache } from '../api/apiClient';
import { PageProblemsPanel } from '../components/diagnostics/PageProblemsPanel';
import { resetClientErrorLog, useClientErrorLog } from './useClientErrorLog';

const PATHS = ['/strengths-profiles'] as const;

function Probe() {
  const { failures } = useClientErrorLog('token', 'strengths', PATHS);
  return <PageProblemsPanel failures={failures} />;
}

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const noEndpoint = {
  status: 404,
  error: 'Not Found',
  message: 'No endpoint for /api/strengths-profiles/177',
  path: '/api/strengths-profiles/177',
  reference: 'a1b2c3d4e5f6',
};

/** Answers by path; the log endpoint's answer is chosen per test. */
function stubServer(logStatus: number) {
  const fetchMock = vi.fn(async (url: string, init: RequestInit = {}) => {
    const path = String(url).replace(API_BASE_URL, '');
    if (path.startsWith('/client-errors')) {
      if ((init.method ?? 'GET') === 'POST') return new Response(null, { status: logStatus });
      return logStatus === 204 ? json([], 200) : json({ message: 'No endpoint for /api/client-errors' }, 404);
    }
    if (path.startsWith('/strengths-profiles')) return json(noEndpoint, 404);
    if (path.startsWith('/session-check')) return json({ message: 'Expired' }, 401);
    return json({ message: 'Broken' }, 500);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('useClientErrorLog (diagnostics on a page)', () => {
  beforeEach(() => {
    clearApiCache();
    resetClientErrorLog();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows a failed request of the page, explains it, and records it without the query string', async () => {
    const fetchMock = stubServer(204);
    render(<Probe />);
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());

    await expect(apiClient('/strengths-profiles/177?view=full', { token: 'token' })).rejects.toThrow('No endpoint');

    expect(await screen.findByText('Problems on this page')).toBeInTheDocument();
    expect(screen.getByText('GET /strengths-profiles/177')).toBeInTheDocument();
    expect(screen.getByText(/A server update has probably not finished deploying/)).toBeInTheDocument();
    expect(screen.getByText(/Reference: a1b2c3d4e5f6/)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('Not sent')).not.toBeInTheDocument());

    const post = fetchMock.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === 'POST');
    expect(post).toBeDefined();
    expect(JSON.parse(String((post![1] as RequestInit).body))).toMatchObject({
      page: 'strengths', method: 'GET', path: '/strengths-profiles/177', status: 404,
      message: 'No endpoint for /api/strengths-profiles/177', reference: 'a1b2c3d4e5f6',
    });
  });

  it('keeps a failure on the page, marked not sent, when the server has no log endpoint either', async () => {
    stubServer(404);
    render(<Probe />);

    await expect(apiClient('/strengths-profiles/177', { token: 'token' })).rejects.toThrow();

    expect(await screen.findByText('Not sent')).toBeInTheDocument();
    expect(screen.getByText(/could not be sent to the server/)).toBeInTheDocument();
  });

  it('leaves out other pages’ requests and ended sessions, and shows nothing when all is well', async () => {
    const fetchMock = stubServer(204);
    const { container } = render(<Probe />);

    await expect(apiClient('/partners', { token: 'token' })).rejects.toThrow();
    await expect(apiClient('/session-check', { token: 'token' })).rejects.toThrow();

    expect(container).toBeEmptyDOMElement();
    expect(fetchMock.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'POST')).toBe(false);
  });
});
