import { useState } from 'react';

/**
 * FR-23.3 remembered views: useState backed by localStorage, so per-page
 * view choices (board/list, page size, show-archived) survive navigation
 * and reloads. Pass a null key to fall back to plain state (used by
 * components where persistence is optional). URL filters keep winning over
 * remembered state simply by being read after it, as today.
 */
export function useStoredState<T>(key: string | null, initial: T): [T, (value: T) => void] {
  const [value, setValue] = useState<T>(() => {
    if (!key) {
      return initial;
    }
    try {
      const stored = localStorage.getItem(key);
      return stored === null ? initial : (JSON.parse(stored) as T);
    } catch {
      return initial;
    }
  });

  function set(next: T) {
    setValue(next);
    if (key) {
      try {
        localStorage.setItem(key, JSON.stringify(next));
      } catch {
        // Storage full or unavailable — the in-memory value still works.
      }
    }
  }

  return [value, set];
}
