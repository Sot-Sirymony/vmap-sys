import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL, clearApiCache } from './apiClient';

/**
 * BRD 12 COV-FR-25: the request every API function sends. Each function is
 * called with sample arguments picked from its parameter names, and the
 * method, path, body and whether the session token is attached are recorded.
 * The snapshot is the contract between the frontend and the backend's routes:
 * a changed path, method or body shows up here as a failing test, and an
 * intended change is accepted by updating the snapshot (`vitest -u`).
 */

const modules = import.meta.glob<Record<string, unknown>>(['./*.ts', '!./*.test.ts', '!./apiClient.ts'], { eager: true });

const SAMPLE_BODY = { sample: 'value' };

/** The parameter names of a function, read from its source. */
function parameterNames(fn: (...args: unknown[]) => unknown): string[] {
  const source = fn.toString();
  const start = source.indexOf('(');
  let depth = 0;
  let end = start;
  for (let index = start; index < source.length; index += 1) {
    if ('([{'.includes(source[index])) depth += 1;
    if (')]}'.includes(source[index])) depth -= 1;
    if (depth === 0) {
      end = index;
      break;
    }
  }
  const names: string[] = [];
  let current = '';
  depth = 0;
  for (const character of source.slice(start + 1, end)) {
    if ('([{'.includes(character)) depth += 1;
    if (')]}'.includes(character)) depth -= 1;
    if (character === ',' && depth === 0) {
      names.push(current);
      current = '';
    } else {
      current += character;
    }
  }
  if (current.trim()) names.push(current);
  return names.map((name) => name.trim()).map((name) => (name.startsWith('{') ? '{object}' : name.split('=')[0].trim()));
}

/** A sample value for a parameter, chosen by its name. */
function sampleFor(name: string, position: number): unknown {
  if (name === 'token') return 'token';
  if (/token$/i.test(name)) return `${name}-value`;
  if (/^(ids|orderedIds)$/.test(name)) return [3, 1, 2];
  if (/(^id|Id|Number|^number)$/.test(name)) return 10 + position;
  if (name === 'language') return 'km';
  if (/^(includeArchived|stretch|enabled|consent|checked)$/.test(name)) return true;
  if (/date$/i.test(name)) return '2026-11-07';
  if (name === 'page') return 0;
  if (name === 'size') return 50;
  if (name === 'rating') return 1;
  if (name === 'file') return new File(['sample'], 'Vision_Mapping.xlsx');
  if (name === 'rows') return [SAMPLE_BODY];
  if (name === 'status') return 'COMPLETED';
  if (/^on[A-Z]/.test(name)) return () => undefined;
  if (/^(request|body|input|ask|response|data|filters|options|focus|{object})$/.test(name) || /Request$/.test(name)) return SAMPLE_BODY;
  return 'text';
}

function describeBody(body: BodyInit | null | undefined): string {
  if (body === undefined || body === null) return '';
  if (typeof body === 'string') return ` ${body.length > 160 ? `${body.slice(0, 160)}…` : body}`;
  return ` [${body.constructor.name}]`;
}

const fetchMock = vi.fn(async () => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }));

beforeAll(() => {
  vi.stubGlobal('fetch', fetchMock);
  URL.createObjectURL = vi.fn(() => 'blob:sample');
  URL.revokeObjectURL = vi.fn();
});

afterAll(() => {
  vi.unstubAllGlobals();
});

async function record(name: string, fn: (...args: unknown[]) => unknown): Promise<string> {
  clearApiCache();
  fetchMock.mockClear();
  const names = parameterNames(fn);
  const args = names.map(sampleFor);
  let outcome = '';
  try {
    await fn(...args);
  } catch (error) {
    outcome = ` (throws: ${error instanceof Error ? error.message : String(error)})`;
  }
  const requests = fetchMock.mock.calls.map((call) => {
    const [url, init = {}] = call as unknown as [string, RequestInit | undefined];
    const headers = new Headers(init.headers);
    const auth = headers.has('Authorization') ? ' [auth]' : '';
    return `${(init.method ?? 'GET').toUpperCase()} ${String(url).replace(API_BASE_URL, '')}${auth}${describeBody(init.body)}`;
  });
  return `${name}(${names.join(', ')}) -> ${requests.join(' | ') || 'no request'}${outcome}`;
}

describe('API contract (BRD 12 COV-FR-25)', () => {
  it('sends the expected method, path and body from every API function', async () => {
    const lines: string[] = [];
    for (const [file, module] of Object.entries(modules).sort(([first], [second]) => first.localeCompare(second))) {
      for (const [name, value] of Object.entries(module).sort(([first], [second]) => first.localeCompare(second))) {
        if (typeof value === 'function' && !/^[A-Z]/.test(name)) {
          lines.push(await record(`${file.replace('./', '').replace('.ts', '')}.${name}`, value as (...args: unknown[]) => unknown));
        }
      }
    }
    expect(lines.length).toBeGreaterThan(200);
    expect(lines).toMatchSnapshot();
  });

  it('attaches the session token to every request made with one', async () => {
    const missing: string[] = [];
    for (const [file, module] of Object.entries(modules)) {
      for (const [name, value] of Object.entries(module)) {
        if (typeof value !== 'function' || /^[A-Z]/.test(name)) continue;
        const fn = value as (...args: unknown[]) => unknown;
        if (!parameterNames(fn).includes('token')) continue;
        const line = await record(`${file}.${name}`, fn);
        if (line.includes(' -> ') && !line.includes('no request') && line.split(' | ').some((request) => !request.includes('[auth]'))) {
          missing.push(line);
        }
      }
    }
    expect(missing).toEqual([]);
  });
});
