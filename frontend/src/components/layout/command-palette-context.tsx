import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

type CommandPaletteContextValue = {
  open: boolean;
  openPalette: () => void;
  closePalette: () => void;
  /** What ⌘K does — the same key opens and dismisses it. */
  toggle: () => void;
};

const CommandPaletteContext = createContext<CommandPaletteContextValue | null>(null);

/**
 * FR-48.3 — who owns whether the command palette is open.
 *
 * It used to be local state inside `GlobalShortcuts`, which was right while the
 * keyboard was the only way in. The Modern sidebar adds a visible search
 * affordance, so a second, unrelated component now needs to open the same
 * palette, and the state has to sit above both.
 *
 * The alternative — having the search button dispatch a synthetic ⌘K
 * `KeyboardEvent` — would have avoided this file and been a lie: a click is not
 * a keypress, and it would break the moment the shortcut is rebound or the
 * listener is scoped to something narrower than `window`.
 */
export function CommandPaletteProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);

  // Stable identities, so the ⌘K listener in GlobalShortcuts is bound once
  // rather than re-subscribed every time the palette opens or closes.
  const openPalette = useCallback(() => setOpen(true), []);
  const closePalette = useCallback(() => setOpen(false), []);
  const toggle = useCallback(() => setOpen((current) => !current), []);

  const value = useMemo<CommandPaletteContextValue>(
    () => ({ open, openPalette, closePalette, toggle }),
    [open, openPalette, closePalette, toggle],
  );

  return <CommandPaletteContext.Provider value={value}>{children}</CommandPaletteContext.Provider>;
}

export function useCommandPalette() {
  const context = useContext(CommandPaletteContext);
  if (!context) {
    throw new Error('useCommandPalette must be used within a CommandPaletteProvider');
  }
  return context;
}
