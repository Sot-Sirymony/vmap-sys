import { useCallback, useEffect, useState } from 'react';
import { useThemeSettings } from '../context/ThemeModeContext';
import { styleTokens, type InterfaceStyle, type StyleTokens } from '../design/tokens';

/** Why Liquid Glass is showing solid panels (D-26-6), or null when it is not. */
export type GlassFallbackReason = 'highContrast' | 'prefersContrast' | 'reducedTransparency' | 'unsupported';

const CONTRAST_QUERY = '(prefers-contrast: more)';
const TRANSPARENCY_QUERY = '(prefers-reduced-transparency: reduce)';

function matches(query: string): boolean {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function' && window.matchMedia(query).matches;
}

function useMediaMatch(query: string): boolean {
  const [value, setValue] = useState(() => matches(query));
  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return undefined;
    }
    const media = window.matchMedia(query);
    const onChange = () => setValue(media.matches);
    setValue(media.matches);
    media.addEventListener?.('change', onChange);
    return () => media.removeEventListener?.('change', onChange);
  }, [query]);
  return value;
}

/** Whether this browser can blur what is behind an element. */
export function supportsBackdropFilter(): boolean {
  if (typeof CSS === 'undefined' || typeof CSS.supports !== 'function') {
    // No way to ask (tests, very old browsers): assume the stylesheet's
    // @supports fallback decides, and report glass as available.
    return true;
  }
  return CSS.supports('backdrop-filter', 'blur(1px)') || CSS.supports('-webkit-backdrop-filter', 'blur(1px)');
}

/** The fallback rule of D-26-6, pure so it can be tested directly. */
export function glassFallbackReason(
  style: InterfaceStyle,
  conditions: { highContrast: boolean; prefersContrast: boolean; reducedTransparency: boolean; backdropFilter: boolean },
): GlassFallbackReason | null {
  if (style !== 'glass') return null;
  if (conditions.highContrast) return 'highContrast';
  if (conditions.prefersContrast) return 'prefersContrast';
  if (conditions.reducedTransparency) return 'reducedTransparency';
  if (!conditions.backdropFilter) return 'unsupported';
  return null;
}

export type InterfaceStyleState = {
  style: InterfaceStyle;
  tokens: StyleTokens;
  /** Header anatomy (D-26-9): greeting for Modern and Glass, breadcrumbs for Classic. */
  header: StyleTokens['header'];
  /** Navigation selection shape: pill, or rect with an accent bar. */
  navShape: StyleTokens['navShape'];
  /** True when Liquid Glass is chosen and no fallback applies. */
  glassActive: boolean;
  glassFallback: GlassFallbackReason | null;
  /** Persists through the existing appearance save. */
  setStyle: (style: InterfaceStyle) => void;
};

/**
 * IS-FR-07 — the active interface style, its tokens, and whether glass is
 * really showing after the fallbacks. Components decide anatomy from
 * `header` and `navShape`, never by comparing the style's name (IS-FR-05).
 */
export function useInterfaceStyle(): InterfaceStyleState {
  const { settings, resolvedMode, update } = useThemeSettings();
  const prefersContrast = useMediaMatch(CONTRAST_QUERY);
  const reducedTransparency = useMediaMatch(TRANSPARENCY_QUERY);
  const style = settings.interfaceStyle;
  const tokens = styleTokens[style][resolvedMode];
  const fallback = glassFallbackReason(style, {
    highContrast: settings.highContrast,
    prefersContrast,
    reducedTransparency,
    backdropFilter: supportsBackdropFilter(),
  });
  const setStyle = useCallback((next: InterfaceStyle) => update({ interfaceStyle: next }), [update]);

  return {
    style,
    tokens,
    header: tokens.header,
    navShape: tokens.navShape,
    glassActive: style === 'glass' && fallback === null,
    glassFallback: fallback,
    setStyle,
  };
}
