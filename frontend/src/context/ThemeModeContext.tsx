import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { accentOptions, backgroundTones, buildTheme, fontFamilies, fontStack, interfaceStyles, matchPreset, type AccentId, type BackgroundToneId, type Density, type FontFamilyId, type InterfaceStyleId, type StoredThemePreset } from '../theme';
import { loadFont } from '../fonts';
import { getAppearancePreferences, updateAppearancePreferences } from '../api/preferencesApi';
import { toSettings, toWire } from './appearance-mapping';
import { useAuth } from './AuthContext';
import type { AppearancePreferences } from '../types/preferences';

export type ThemeMode = 'light' | 'dark' | 'system';
export type FontSize = 'small' | 'medium' | 'large';

export type ThemeSettings = {
  mode: ThemeMode;
  accent: AccentId;
  density: Density;
  fontSize: FontSize;
  /** FR-40 — which coordinated set of surfaces the app paints. */
  backgroundTone: BackgroundToneId;
  /** FR-42 — the typeface. `system` loads nothing. */
  fontFamily: FontFamilyId;
  /** FR-48 — shape and chrome. Decides no colour, so it composes with all of the above. */
  interfaceStyle: InterfaceStyleId;
  /** FR-39.3 — composes with light and dark rather than replacing them. */
  highContrast: boolean;
  /** FR-39.4 — asks for less motion than the OS preference, never more. */
  reduceMotion: boolean;
};

const STORAGE_KEY = 'vms-theme-settings';
// Pre-FR-18 key that held only 'light' | 'dark'; migrated on first load.
const LEGACY_MODE_KEY = 'vms-theme-mode';
/**
 * One-shot re-baseline to the Modern interface style. Before Modern became the
 * baseline, Classic was the default — so virtually every stored profile says
 * CLASSIC without the user ever having chosen it. The first time this build
 * sees a profile (local cache or account), a stored Classic is treated as
 * "never chose" and re-based to Modern, then this flag stops it happening
 * again — an explicit Classic pick afterwards is honoured forever. The flag is
 * also set by any explicit style pick, so a deliberate choice is never undone.
 */
const STYLE_REBASELINE_KEY = 'vms-style-rebaseline';

function styleRebaselineDone(): boolean {
  try {
    return localStorage.getItem(STYLE_REBASELINE_KEY) === '1';
  } catch {
    // Unreadable storage: never force anything.
    return true;
  }
}

function markStyleRebaselined(): void {
  try {
    localStorage.setItem(STYLE_REBASELINE_KEY, '1');
  } catch {
    // Best effort — worst case the re-baseline repeats, which is idempotent.
  }
}

/** The re-baseline itself, pure so it can be tested directly. */
export function rebaselineInterfaceStyle(settings: ThemeSettings, alreadyDone: boolean): ThemeSettings {
  if (alreadyDone || settings.interfaceStyle !== 'classic') {
    return settings;
  }
  return { ...settings, interfaceStyle: 'modern' };
}

const DEFAULT_SETTINGS: ThemeSettings = {
  mode: 'system',
  accent: 'blue',
  density: 'comfortable',
  fontSize: 'medium',
  backgroundTone: 'neutral',
  fontFamily: 'system',
  interfaceStyle: 'modern',
  highContrast: false,
  reduceMotion: false,
};

/**
 * The ids this build understands, for validating what comes out of storage.
 * Derived from the registries rather than written out: a hand-typed list here
 * silently dropped the ten FR-40 tone additions and the three new fonts from
 * the local cache on reload — the account round-trip corrected it a moment
 * later, but every cold start flashed the default first.
 */
const KNOWN_TONES = new Set<BackgroundToneId>(backgroundTones.map((tone) => tone.id));
const KNOWN_FONTS = new Set<FontFamilyId>(fontFamilies.map((font) => font.id));
const KNOWN_STYLES = new Set<InterfaceStyleId>(interfaceStyles.map((style) => style.id));

/**
 * The device-local background image (Appearance → Background image). A data
 * URL in localStorage, deliberately NOT part of ThemeSettings: the account
 * payload is a set of enum names, and a multi-megabyte image does not belong
 * in it — so this stays a per-device decoration, like a desktop wallpaper.
 */
const BG_IMAGE_KEY = 'vms-bg-image';
const BG_WASH_KEY = 'vms-bg-image-wash';
const BG_CONTRAST_KEY = 'vms-bg-image-contrast';
/** % of the tone's page colour in the scrim laid over the image (0–95).
    0 means no scrim at all — the image fully visible, the user's explicit
    call; 95 leaves only a hint of it. */
const DEFAULT_BG_WASH = 85;
/** CSS contrast() applied to the image layer, as a percentage (50–150). */
const DEFAULT_BG_CONTRAST = 100;

function clampWash(value: number): number {
  return Math.min(95, Math.max(0, Math.round(value)));
}

function clampContrast(value: number): number {
  return Math.min(150, Math.max(50, Math.round(value)));
}

function initialBackgroundImage(): string | null {
  try {
    return localStorage.getItem(BG_IMAGE_KEY);
  } catch {
    return null;
  }
}

function initialBackgroundWash(): number {
  try {
    // Null-checked before Number(): Number(null) is 0, which is now a LEGAL
    // wash value (fully visible image), so "no stored value" must not be
    // mistaken for it.
    const raw = localStorage.getItem(BG_WASH_KEY);
    if (raw === null) {
      return DEFAULT_BG_WASH;
    }
    const stored = Number(raw);
    return Number.isFinite(stored) ? clampWash(stored) : DEFAULT_BG_WASH;
  } catch {
    return DEFAULT_BG_WASH;
  }
}

function initialBackgroundContrast(): number {
  try {
    const stored = Number(localStorage.getItem(BG_CONTRAST_KEY));
    return Number.isFinite(stored) && stored > 0 ? clampContrast(stored) : DEFAULT_BG_CONTRAST;
  } catch {
    return DEFAULT_BG_CONTRAST;
  }
}

/** How long to wait before saving, so dragging through swatches sends one request. */
const SAVE_DEBOUNCE_MS = 600;

type ThemeSettingsContextValue = {
  settings: ThemeSettings;
  /** The mode actually in effect — 'system' resolved against the OS preference. */
  resolvedMode: 'light' | 'dark';
  /** Which preset the current settings amount to, or 'CUSTOM' (FR-39.1). */
  preset: StoredThemePreset;
  update: (changes: Partial<ThemeSettings>) => void;
  /** Applies a preset's mode + accent together (FR-39.1). */
  applyPreset: (mode: ThemeMode, accent: AccentId) => void;
  /** Back to the shipped defaults. */
  reset: () => void;
  /**
   * Set when the last save to the account failed. The choice is still applied
   * and cached locally (BR-33) — this only lets the UI say so honestly.
   */
  saveError: string | null;
  /** Device-local background image as a data URL, or null when none is set. */
  backgroundImage: string | null;
  /** Scrim strength over the image: % of the page colour, 0–95. */
  backgroundWash: number;
  /** contrast() on the image layer as a percentage, 50–150 (100 = as shot). */
  backgroundContrast: number;
  setBackgroundImage: (dataUrl: string | null) => void;
  setBackgroundWash: (wash: number) => void;
  setBackgroundContrast: (contrast: number) => void;
};

const ThemeSettingsContext = createContext<ThemeSettingsContextValue>({
  settings: DEFAULT_SETTINGS,
  resolvedMode: 'light',
  preset: 'FLUENT_SYSTEM',
  update: () => undefined,
  applyPreset: () => undefined,
  reset: () => undefined,
  saveError: null,
  backgroundImage: null,
  backgroundWash: DEFAULT_BG_WASH,
  backgroundContrast: DEFAULT_BG_CONTRAST,
  setBackgroundImage: () => undefined,
  setBackgroundWash: () => undefined,
  setBackgroundContrast: () => undefined,
});

function initialSettings(): ThemeSettings {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      const parsed = JSON.parse(stored) as Partial<ThemeSettings>;
      return rebaselineInterfaceStyle({
        mode: parsed.mode === 'light' || parsed.mode === 'dark' || parsed.mode === 'system' ? parsed.mode : DEFAULT_SETTINGS.mode,
        accent: parsed.accent && parsed.accent in accentOptions ? parsed.accent : DEFAULT_SETTINGS.accent,
        density: parsed.density === 'compact' ? 'compact' : 'comfortable',
        fontSize: parsed.fontSize === 'small' || parsed.fontSize === 'large' ? parsed.fontSize : 'medium',
        backgroundTone: parsed.backgroundTone && KNOWN_TONES.has(parsed.backgroundTone) ? parsed.backgroundTone : 'neutral',
        fontFamily: parsed.fontFamily && KNOWN_FONTS.has(parsed.fontFamily) ? parsed.fontFamily : 'system',
        interfaceStyle: parsed.interfaceStyle && KNOWN_STYLES.has(parsed.interfaceStyle) ? parsed.interfaceStyle : 'modern',
        highContrast: parsed.highContrast === true,
        reduceMotion: parsed.reduceMotion === true,
        // The flag is deliberately NOT set here: a signed-in user's account
        // value arrives later via adopt(), which is where the re-baseline is
        // recorded as done. Setting it this early would let a stale local
        // cache block the account migration.
      }, styleRebaselineDone());
    }
    const legacyMode = localStorage.getItem(LEGACY_MODE_KEY);
    if (legacyMode === 'light' || legacyMode === 'dark') {
      return { ...DEFAULT_SETTINGS, mode: legacyMode };
    }
  } catch {
    // Corrupted storage — fall through to defaults.
  }
  return DEFAULT_SETTINGS;
}

function systemPrefersDark(): boolean {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
}

/**
 * Owns the appearance settings (FR-18, extended by FR-39): mode (Light / Dark /
 * System, where System tracks the OS preference live), accent colour, density,
 * font size, high contrast, and reduce motion. Everything is exposed three ways
 * — the MUI theme for components, data attributes on <html> for global.css, and
 * the accent's CSS variables so non-MUI styles use the same ramp.
 *
 * FR-39.6 makes the account the source of truth, with localStorage demoted to a
 * cache. The order matters: local state renders immediately, the account value
 * arrives with the login response (so there is no flash of the wrong theme),
 * and changes are written back on a debounce. If that write fails the choice
 * still stands locally (BR-33) — appearance never becomes a feature that only
 * works when the backend does.
 *
 * This provider sits INSIDE AuthProvider, because it needs the session token.
 */
export function ThemeModeProvider({ children }: { children: ReactNode }) {
  const { token, appearance } = useAuth();
  const [settings, setSettings] = useState<ThemeSettings>(initialSettings);
  const [osPrefersDark, setOsPrefersDark] = useState(systemPrefersDark);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [backgroundImage, setBackgroundImageState] = useState<string | null>(initialBackgroundImage);
  const [backgroundWash, setBackgroundWashState] = useState<number>(initialBackgroundWash);
  const [backgroundContrast, setBackgroundContrastState] = useState<number>(initialBackgroundContrast);

  /**
   * The last payload known to match the account, as a comparable string. The
   * save effect skips anything equal to it, which is what stops the values we
   * just loaded from being written straight back.
   *
   * This started life as a "skip the next save" flag and that was subtly wrong:
   * on mount the save effect runs once before the loaded state lands, consuming
   * the flag, so the echo write went through anyway. Comparing against the known
   * server state has no such ordering dependency — and it also drops redundant
   * writes when a user toggles something off and straight back on.
   */
  const lastSynced = useRef<string | null>(null);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Set the moment the user changes anything. A restored session fetches the
  // stored appearance on mount, and without this a choice made while that
  // request was still in flight would be silently overwritten by the response —
  // the user would watch their own change undo itself a moment later.
  const userTouched = useRef(false);

  const resolvedMode: 'light' | 'dark' =
    settings.mode === 'system' ? (osPrefersDark ? 'dark' : 'light') : settings.mode;

  /**
   * Takes the account's stored appearance as the truth and records it as already
   * synced, so the save effect knows there is nothing to write back.
   *
   * Unrecognised fields fall back to the shipped defaults rather than to the
   * local cache: when the account is the source of truth, the documented default
   * is a better answer for an unreadable field than whatever this browser
   * happened to have (BR-33).
   */
  const adopt = useCallback((wire: AppearancePreferences) => {
    const stored = toSettings(wire, DEFAULT_SETTINGS);
    // Record what the account actually holds, THEN apply the one-shot Modern
    // re-baseline. When it changes anything, settings and lastSynced disagree,
    // so the debounced save below writes MODERN back to the account — the
    // migration persists rather than repeating on every device forever.
    lastSynced.current = JSON.stringify(toWire(stored));
    userTouched.current = false;
    const next = rebaselineInterfaceStyle(stored, styleRebaselineDone());
    markStyleRebaselined();
    setSettings(next);
  }, []);

  // FR-18.2: System mode follows OS changes live, without a reload.
  useEffect(() => {
    if (settings.mode !== 'system' || !window.matchMedia) {
      return;
    }
    const query = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = (event: MediaQueryListEvent) => setOsPrefersDark(event.matches);
    query.addEventListener('change', onChange);
    setOsPrefersDark(query.matches);
    return () => query.removeEventListener('change', onChange);
  }, [settings.mode]);

  // FR-39.6: adopt the account's appearance. `appearance` normally arrives with
  // the login response; the GET is the fallback for a session restored from
  // storage, which was saved before this field existed.
  useEffect(() => {
    if (!token) {
      // Signed out: forget that anything was touched, so the next account's
      // stored appearance is adopted rather than being treated as stale against
      // the previous user's edits.
      userTouched.current = false;
      return;
    }

    if (appearance) {
      adopt(appearance);
      return;
    }

    let cancelled = false;
    getAppearancePreferences(token)
      .then((loaded) => {
        // Anything the user did while this was in flight wins: their intent is
        // newer than the server's answer.
        if (!cancelled && !userTouched.current) {
          adopt(loaded);
        }
      })
      .catch(() => {
        // Offline or an older backend: the cached local settings stand (BR-33).
      });
    return () => {
      cancelled = true;
    };
  }, [token, appearance, adopt]);

  // The local cache, and the <html> attributes global.css keys off.
  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    const root = document.documentElement;
    root.dataset.theme = resolvedMode;
    root.dataset.density = settings.density;
    root.dataset.fontsize = settings.fontSize;
    // FR-39.3/39.4: present only when on, so the attribute selectors in
    // global.css stay simple and an absent attribute means "normal".
    if (settings.highContrast) {
      root.dataset.contrast = 'high';
    } else {
      delete root.dataset.contrast;
    }
    if (settings.reduceMotion) {
      root.dataset.motion = 'reduced';
    } else {
      delete root.dataset.motion;
    }
    // FR-40: absent when neutral, so the default costs no selector work and an
    // absent attribute unambiguously means "the surfaces that shipped before".
    if (settings.backgroundTone === 'neutral') {
      delete root.dataset.tone;
    } else {
      root.dataset.tone = settings.backgroundTone;
    }
    // FR-48: same convention — absent means Modern, the baseline the tokens in
    // global.css now carry; Classic is the stamped override. Unlike the tone,
    // this one is NOT suppressed by high contrast: that setting is about
    // legibility, and a corner radius cannot make anything harder to read, so
    // there is no reason to take the user's chosen shape away when it is on.
    if (settings.interfaceStyle === 'modern') {
      delete root.dataset.style;
    } else {
      root.dataset.style = settings.interfaceStyle;
    }
    // The accent's CSS variables (FR-18.3). Inline custom properties win over
    // both the :root defaults and the [data-theme="dark"] block, so the
    // chosen ramp applies in either mode.
    //
    // FR-39.3: high contrast steps the accent one notch along its own ramp —
    // darker in light mode, lighter in dark — matching what buildTheme does for
    // the MUI palette. Both must agree or a button and its CSS-styled sibling
    // would disagree about the same accent.
    // FR-42: the face is applied as a CSS variable that global.css's unlayered
    // :root rule reads, and buildTheme sets the same stack on the MUI theme.
    // Set unconditionally (including for `system`) so switching back restores
    // the default rather than leaving the previous font in place.
    root.style.setProperty('--font-family', fontStack(settings.fontFamily));

    const accent = accentOptions[settings.accent][resolvedMode];
    const accentStrong = resolvedMode === 'dark' ? accent.hover : accent.pressed;
    const accentMain = settings.highContrast ? accentStrong : accent.main;
    root.style.setProperty('--primary', accentMain);
    root.style.setProperty('--primary-foreground', accent.contrastText);
    root.style.setProperty('--ring', accentMain);
    root.style.setProperty('--accent', accent.tint);
    root.style.setProperty('--accent-foreground', accent.tintForeground);
    root.style.setProperty('--sidebar-primary', accentMain);
    root.style.setProperty('--sidebar-primary-foreground', accent.contrastText);
    root.style.setProperty('--sidebar-accent', accent.tint);
    root.style.setProperty('--sidebar-accent-foreground', accent.tintForeground);
    root.style.setProperty('--sidebar-ring', accentMain);
  }, [settings, resolvedMode]);

  // The device-local background image. The attribute drives global.css's
  // `[data-bg-image]` rule; the scrim keeps canvas text legible, and a
  // separate `[data-contrast="high"]` rule removes the image entirely —
  // the accessibility mode outranks the decoration (same shape as FR-40.5),
  // and switching high contrast off restores it exactly.
  useEffect(() => {
    const root = document.documentElement;
    if (backgroundImage) {
      root.dataset.bgImage = 'on';
      root.style.setProperty('--bg-image', `url("${backgroundImage}")`);
      root.style.setProperty('--bg-wash', String(backgroundWash));
      root.style.setProperty('--bg-contrast', String(backgroundContrast));
    } else {
      delete root.dataset.bgImage;
      root.style.removeProperty('--bg-image');
      root.style.removeProperty('--bg-wash');
      root.style.removeProperty('--bg-contrast');
    }
  }, [backgroundImage, backgroundWash, backgroundContrast]);

  // FR-42.2: fetch the font files only once a font is actually chosen. The
  // stack already names the face, so the browser applies it the moment the
  // @font-face rules arrive; until then it renders the system fallback rather
  // than blocking on the download.
  useEffect(() => {
    void loadFont(settings.fontFamily);
  }, [settings.fontFamily]);

  // FR-39.6: persist to the account, debounced so dragging through swatches
  // sends one request rather than one per swatch.
  useEffect(() => {
    if (!token) {
      return;
    }

    const wire = toWire(settings);
    const payload = JSON.stringify(wire);
    // Nothing to say: either these are the values the account already holds, or
    // they are the ones we just loaded from it.
    if (lastSynced.current === payload) {
      return;
    }

    if (saveTimer.current) {
      clearTimeout(saveTimer.current);
    }
    saveTimer.current = setTimeout(() => {
      updateAppearancePreferences(token, wire)
        .then(() => {
          lastSynced.current = payload;
          setSaveError(null);
        })
        .catch((error: unknown) => {
          // The choice is already applied and cached — this is about telling the
          // truth, not rolling back what the user asked for (BR-33).
          setSaveError(error instanceof Error ? error.message : 'Could not save appearance to your account.');
        });
    }, SAVE_DEBOUNCE_MS);

    return () => {
      if (saveTimer.current) {
        clearTimeout(saveTimer.current);
      }
    };
  }, [settings, token]);

  const theme = useMemo(
    () => buildTheme(resolvedMode, settings.accent, settings.density, settings.highContrast, settings.backgroundTone, settings.fontFamily, settings.interfaceStyle),
    [resolvedMode, settings.accent, settings.density, settings.highContrast, settings.backgroundTone, settings.fontFamily, settings.interfaceStyle],
  );

  const update = useCallback((changes: Partial<ThemeSettings>) => {
    userTouched.current = true;
    // An explicit style pick is a deliberate decision — record it so the
    // one-shot Modern re-baseline can never override it later.
    if (changes.interfaceStyle) {
      markStyleRebaselined();
    }
    setSettings((current) => ({ ...current, ...changes }));
  }, []);

  // FR-39.1: a preset writes mode and accent in a single update, so the two
  // never land as separate renders — a preset must not flash an intermediate
  // look on its way to the one the user picked.
  const applyPreset = useCallback((mode: ThemeMode, accent: AccentId) => {
    userTouched.current = true;
    setSettings((current) => ({ ...current, mode, accent }));
  }, []);

  const reset = useCallback(() => {
    userTouched.current = true;
    setSettings(DEFAULT_SETTINGS);
  }, []);

  const setBackgroundImage = useCallback((dataUrl: string | null) => {
    setBackgroundImageState(dataUrl);
    try {
      if (dataUrl) {
        localStorage.setItem(BG_IMAGE_KEY, dataUrl);
      } else {
        localStorage.removeItem(BG_IMAGE_KEY);
      }
    } catch {
      // Quota exceeded: the image still applies for this session — losing it
      // on reload is a better outcome than refusing the choice.
    }
  }, []);

  const setBackgroundWash = useCallback((wash: number) => {
    const clamped = clampWash(wash);
    setBackgroundWashState(clamped);
    try {
      localStorage.setItem(BG_WASH_KEY, String(clamped));
    } catch {
      // Same best-effort stance as the image itself.
    }
  }, []);

  const setBackgroundContrast = useCallback((contrast: number) => {
    const clamped = clampContrast(contrast);
    setBackgroundContrastState(clamped);
    try {
      localStorage.setItem(BG_CONTRAST_KEY, String(clamped));
    } catch {
      // Same best-effort stance as the image itself.
    }
  }, []);

  const value = useMemo<ThemeSettingsContextValue>(
    () => ({
      settings,
      resolvedMode,
      preset: matchPreset(settings.mode, settings.accent),
      update,
      applyPreset,
      reset,
      saveError,
      backgroundImage,
      backgroundWash,
      backgroundContrast,
      setBackgroundImage,
      setBackgroundWash,
      setBackgroundContrast,
    }),
    [settings, resolvedMode, update, applyPreset, reset, saveError, backgroundImage, backgroundWash, backgroundContrast, setBackgroundImage, setBackgroundWash, setBackgroundContrast],
  );

  return (
    <ThemeSettingsContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ThemeSettingsContext.Provider>
  );
}

export function useThemeSettings() {
  return useContext(ThemeSettingsContext);
}
