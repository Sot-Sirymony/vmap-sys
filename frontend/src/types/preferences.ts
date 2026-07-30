/**
 * FR-39 — the wire shape of the appearance preferences.
 *
 * These are the backend's enum names (SCREAMING_CASE), kept deliberately
 * separate from the frontend's own lowercase setting types. The theme layer has
 * used `'light' | 'dark' | 'system'` and `'blue' | 'teal' | …` since FR-18, and
 * renaming those to match the wire would have meant touching every component
 * that reads a setting. Instead the two vocabularies stay distinct and
 * `context/appearance-mapping.ts` translates between them in one place.
 */
export type StoredThemeMode = 'LIGHT' | 'DARK' | 'SYSTEM';

export type StoredAccent =
  | 'BLUE'
  | 'TEAL'
  | 'PURPLE'
  | 'GREEN'
  | 'ORANGE'
  | 'MAGENTA'
  | 'RED'
  | 'BRASS'
  | 'STEEL'
  | 'PINK';

export type StoredDensity = 'COMFORTABLE' | 'COMPACT';

export type StoredFontSize = 'SMALL' | 'MEDIUM' | 'LARGE';

export type StoredPreset =
  | 'FLUENT_SYSTEM'
  | 'FLUENT_LIGHT'
  | 'FLUENT_DARK'
  | 'OCEAN'
  | 'FOREST'
  | 'SLATE'
  | 'MIDNIGHT'
  | 'CUSTOM';

export type AppearancePreferences = {
  themePreset: StoredPreset;
  themeMode: StoredThemeMode;
  themeAccent: StoredAccent;
  uiDensity: StoredDensity;
  fontSize: StoredFontSize;
  highContrast: boolean;
  reduceMotion: boolean;
};
