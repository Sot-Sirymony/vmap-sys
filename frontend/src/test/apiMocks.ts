import { vi } from 'vitest';
import type { Page } from '../types/vision';

/** An empty page of results, for API functions that answer a page at a time. */
export function emptyPage<T>(size = 20): Page<T> {
  return { content: [], totalElements: 0, totalPages: 0, number: 0, size, first: true, last: true };
}

/** A single page holding the given items. */
export function pageOf<T>(items: T[], size = 20): Page<T> {
  return { content: items, totalElements: items.length, totalPages: 1, number: 0, size, first: true, last: true };
}

/**
 * COV-FR-08: makes every `list…` function in the given auto-mocked API modules
 * answer an empty list, and every function named in `paged` answer an empty
 * page, so a test only sets the data it cares about.
 *
 * The modules must be mocked in the test file first, for example
 * `vi.mock('../api/dreamApi')`, then passed in as `import * as dreamApi`.
 */
export function answerEmptyLists(modules: Record<string, unknown>[], paged: string[] = []) {
  for (const module of modules) {
    for (const [name, value] of Object.entries(module)) {
      if (!vi.isMockFunction(value)) {
        continue;
      }
      if (paged.includes(name)) {
        value.mockResolvedValue(emptyPage());
      } else if (name.startsWith('list')) {
        value.mockResolvedValue([]);
      }
    }
  }
}
