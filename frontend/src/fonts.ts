import type { FontFamilyId } from './theme';

/**
 * FR-42.2 — on-demand font loading.
 *
 * The four web fonts are self-hosted (`@fontsource-variable/*`), not fetched
 * from a CDN: the app makes no third-party request, keeps working on a
 * restricted network or offline, and never hands a user's IP to a font host.
 *
 * They are also imported *lazily*, one at a time, only when a user actually
 * selects one. That matters because the default is a system face that downloads
 * nothing — bundling all four upfront would make every user pay for typefaces
 * most of them will never choose. `import()` gives us a per-font chunk that Vite
 * emits separately and the browser caches after the first use.
 *
 * The promises are memoised, so switching back and forth re-uses the first load
 * rather than re-importing.
 */
const loaders: Record<Exclude<FontFamilyId, 'system'>, () => Promise<unknown>> = {
  publicSans: () => import('@fontsource-variable/public-sans'),
  inter: () => import('@fontsource-variable/inter'),
  dmSans: () => import('@fontsource-variable/dm-sans'),
  nunitoSans: () => import('@fontsource-variable/nunito-sans'),
};

const started = new Map<FontFamilyId, Promise<unknown>>();

/**
 * Ensures the font files for `id` are loaded. Resolves immediately for
 * `system`, which is the platform's own face and has nothing to fetch.
 *
 * A failed load is deliberately swallowed: the CSS stack always ends in a
 * system fallback, so the app renders in a slightly different face rather than
 * breaking. An appearance setting is not worth an error state.
 */
export function loadFont(id: FontFamilyId): Promise<unknown> {
  if (id === 'system') {
    return Promise.resolve();
  }
  const existing = started.get(id);
  if (existing) {
    return existing;
  }
  const promise = loaders[id]().catch(() => undefined);
  started.set(id, promise);
  return promise;
}
