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

/**
 * Must stay in step with both `accentOptions` in `theme.ts` and the backend's
 * `AccentColor` enum — a name missing from any of the three is an accent the
 * picker offers but the account cannot store. `accent-wire.test.ts` checks all
 * three against each other.
 */
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
  | 'PINK'
  // FR-43 additions, derived from the supplied primary/secondary ramps.
  | 'VERMILION'
  | 'VIOLET'
  // The royal blue from the Stitch project's DESIGN.md style guide.
  | 'COBALT'
  // Ten more hues, spanning the wheel plus two neutrals-with-a-cast.
  | 'INDIGO'
  | 'SKY'
  | 'EMERALD'
  | 'OLIVE'
  | 'AMBER'
  | 'ROSE'
  | 'FUCHSIA'
  | 'GRAPHITE'
  | 'COFFEE'
  | 'NAVY';

export type StoredDensity = 'COMFORTABLE' | 'COMPACT';

export type StoredFontSize = 'SMALL' | 'MEDIUM' | 'LARGE';

export type StoredFontFamily =
  | 'SYSTEM'
  | 'PUBLIC_SANS'
  | 'INTER'
  | 'DM_SANS'
  | 'NUNITO_SANS'
  | 'MONTSERRAT'
  | 'LEXEND'
  | 'PLUS_JAKARTA_SANS';

export type StoredBackgroundTone =
  | 'NEUTRAL'
  | 'WARM'
  | 'COOL'
  | 'SOFT'
  | 'TINTED'
  | 'FLAT'
  | 'ROSE'
  | 'MINT'
  | 'LAVENDER'
  | 'SAND'
  | 'SAGE'
  | 'ICE'
  | 'LINEN'
  | 'SLATE'
  | 'PLUM'
  | 'STONE';

/** FR-48 — shape and chrome, orthogonal to every colour setting above. */
export type StoredInterfaceStyle = 'CLASSIC' | 'MODERN';

export type StoredPreset =
  | 'FLUENT_SYSTEM'
  | 'FLUENT_LIGHT'
  | 'FLUENT_DARK'
  | 'OCEAN'
  | 'FOREST'
  | 'SLATE'
  | 'MIDNIGHT'
  | 'STITCH'
  | 'CUSTOM';

export type AppearancePreferences = {
  themePreset: StoredPreset;
  themeMode: StoredThemeMode;
  themeAccent: StoredAccent;
  uiDensity: StoredDensity;
  fontSize: StoredFontSize;
  fontFamily: StoredFontFamily;
  backgroundTone: StoredBackgroundTone;
  interfaceStyle: StoredInterfaceStyle;
  highContrast: boolean;
  reduceMotion: boolean;
};
