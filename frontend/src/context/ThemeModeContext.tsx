import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { accentOptions, buildTheme, matchPreset, type AccentId, type Density, type StoredThemePreset } from '../theme';
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
  /** FR-39.3 — composes with light and dark rather than replacing them. */
  highContrast: boolean;
  /** FR-39.4 — asks for less motion than the OS preference, never more. */
  reduceMotion: boolean;
};

const STORAGE_KEY = 'vms-theme-settings';
// Pre-FR-18 key that held only 'light' | 'dark'; migrated on first load.
const LEGACY_MODE_KEY = 'vms-theme-mode';

const DEFAULT_SETTINGS: ThemeSettings = {
  mode: 'system',
  accent: 'blue',
  density: 'comfortable',
  fontSize: 'medium',
  highContrast: false,
  reduceMotion: false,
};

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
};

const ThemeSettingsContext = createContext<ThemeSettingsContextValue>({
  settings: DEFAULT_SETTINGS,
  resolvedMode: 'light',
  preset: 'FLUENT_SYSTEM',
  update: () => undefined,
  applyPreset: () => undefined,
  reset: () => undefined,
  saveError: null,
});

function initialSettings(): ThemeSettings {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      const parsed = JSON.parse(stored) as Partial<ThemeSettings>;
      return {
        mode: parsed.mode === 'light' || parsed.mode === 'dark' || parsed.mode === 'system' ? parsed.mode : DEFAULT_SETTINGS.mode,
        accent: parsed.accent && parsed.accent in accentOptions ? parsed.accent : DEFAULT_SETTINGS.accent,
        density: parsed.density === 'compact' ? 'compact' : 'comfortable',
        fontSize: parsed.fontSize === 'small' || parsed.fontSize === 'large' ? parsed.fontSize : 'medium',
        highContrast: parsed.highContrast === true,
        reduceMotion: parsed.reduceMotion === true,
      };
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
    const next = toSettings(wire, DEFAULT_SETTINGS);
    lastSynced.current = JSON.stringify(toWire(next));
    userTouched.current = false;
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
    // The accent's CSS variables (FR-18.3). Inline custom properties win over
    // both the :root defaults and the [data-theme="dark"] block, so the
    // chosen ramp applies in either mode.
    //
    // FR-39.3: high contrast steps the accent one notch along its own ramp —
    // darker in light mode, lighter in dark — matching what buildTheme does for
    // the MUI palette. Both must agree or a button and its CSS-styled sibling
    // would disagree about the same accent.
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
    () => buildTheme(resolvedMode, settings.accent, settings.density, settings.highContrast),
    [resolvedMode, settings.accent, settings.density, settings.highContrast],
  );

  const update = useCallback((changes: Partial<ThemeSettings>) => {
    userTouched.current = true;
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

  const value = useMemo<ThemeSettingsContextValue>(
    () => ({
      settings,
      resolvedMode,
      preset: matchPreset(settings.mode, settings.accent),
      update,
      applyPreset,
      reset,
      saveError,
    }),
    [settings, resolvedMode, update, applyPreset, reset, saveError],
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
