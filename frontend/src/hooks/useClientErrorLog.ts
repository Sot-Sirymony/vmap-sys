import { useCallback, useEffect, useState } from 'react';
import { onApiFailure, type ApiFailure } from '../api/apiClient';
import { listClientErrors, recordClientError } from '../api/clientErrorApi';
import { APP_VERSION } from '../config/appMeta';
import type { ClientErrorLogEntry } from '../types/vision';

/** One failed request as the page shows it. `sent` is false when the server did not keep it. */
export type LoggedFailure = {
  key: string;
  occurredAt: string;
  method: string;
  path: string;
  status: number;
  message: string | null;
  reference: string | null;
  sent: boolean;
};

/** At most this many are shown; the server keeps its own 50. */
const MAX_SHOWN = 20;

/**
 * Failures the server has not accepted yet, per page. Kept outside React so
 * they survive leaving and reopening the page: they are exactly the ones a
 * broken backend loses (for example, one that has no log endpoint either).
 */
const unsentByPage = new Map<string, LoggedFailure[]>();

let nextKey = 0;

function fromSaved(entry: ClientErrorLogEntry): LoggedFailure {
  return {
    key: `saved-${entry.id}`,
    occurredAt: entry.occurredAt,
    method: entry.method,
    path: entry.path,
    status: entry.status,
    message: entry.message,
    reference: entry.reference,
    sent: true,
  };
}

/** Only the path is kept: a query string can hold search text or tokens. */
function withoutQuery(path: string) {
  const cut = path.indexOf('?');
  return cut < 0 ? path : path.slice(0, cut);
}

/**
 * Diagnostics for one page: while it is open, every failed API request whose
 * path starts with one of `pathPrefixes` is shown on the page and recorded on
 * the server under `pageKey`. A 401 is left out: an ended session is not a
 * fault, and the app signs the user out anyway.
 */
export function useClientErrorLog(token: string | null, pageKey: string, pathPrefixes: readonly string[]) {
  const [saved, setSaved] = useState<LoggedFailure[]>([]);
  const [unsent, setUnsent] = useState<LoggedFailure[]>(() => unsentByPage.get(pageKey) ?? []);
  const prefixKey = pathPrefixes.join('|');

  const keepUnsent = useCallback((next: LoggedFailure[]) => {
    unsentByPage.set(pageKey, next);
    setUnsent(next);
  }, [pageKey]);

  useEffect(() => {
    if (!token) {
      return undefined;
    }
    let active = true;
    listClientErrors(token, pageKey)
      .then((entries) => {
        if (active) setSaved(entries.map(fromSaved));
      })
      // The log is a help, not part of the page: if it cannot load, show what this session saw.
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [token, pageKey]);

  useEffect(() => {
    if (!token) {
      return undefined;
    }
    const prefixes = prefixKey.split('|');
    return onApiFailure((failure: ApiFailure) => {
      const path = withoutQuery(failure.path);
      if (failure.status === 401 || !prefixes.some((prefix) => path.startsWith(prefix))) {
        return;
      }
      nextKey += 1;
      const entry: LoggedFailure = {
        key: `local-${nextKey}`,
        occurredAt: new Date().toISOString(),
        method: failure.method,
        path,
        status: failure.status,
        message: failure.message || null,
        reference: failure.reference ?? null,
        sent: false,
      };
      keepUnsent([entry, ...(unsentByPage.get(pageKey) ?? [])].slice(0, MAX_SHOWN));
      recordClientError(token, {
        page: pageKey,
        method: entry.method,
        path: entry.path,
        status: entry.status,
        message: entry.message ?? undefined,
        reference: entry.reference ?? undefined,
        appVersion: APP_VERSION,
      })
        .then((kept) => {
          if (!kept) return;
          keepUnsent((unsentByPage.get(pageKey) ?? []).filter((item) => item.key !== entry.key));
          setSaved((current) => [{ ...entry, sent: true }, ...current]);
        })
        .catch(() => undefined);
    });
  }, [token, pageKey, prefixKey, keepUnsent]);

  const failures = [...unsent, ...saved]
    .sort((first, second) => second.occurredAt.localeCompare(first.occurredAt))
    .slice(0, MAX_SHOWN);

  return { failures };
}

/** For tests: forget unsent entries kept between page visits. */
export function resetClientErrorLog() {
  unsentByPage.clear();
}
