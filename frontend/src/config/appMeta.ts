// The app version stamped onto issue reports (FR-38.2) so a bug is tied to the
// build it was seen on. Sourced from the single version of truth — package.json,
// exposed by vite.config.ts as VITE_APP_VERSION (the same value the sidebar
// footer shows) — with a fallback for tests where the env isn't populated.
export const APP_VERSION = import.meta.env.VITE_APP_VERSION ?? '0.0.0';
