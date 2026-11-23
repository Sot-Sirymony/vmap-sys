import { useEffect, useState } from 'react';
import { useThemeSettings } from '../context/ThemeModeContext';

const QUERY = '(prefers-reduced-motion: reduce)';

function systemPrefersReduced(): boolean {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function' && window.matchMedia(QUERY).matches;
}

/**
 * BRD 25 D-25-8 — true when either the device asks for less motion or the user
 * turned on Reduce motion in Appearance (FR-39.4). Components use it for the
 * motion CSS cannot switch off by itself: count-ups, ring and bar growth.
 */
export function useReducedMotion(): boolean {
  const { settings } = useThemeSettings();
  const [system, setSystem] = useState(systemPrefersReduced);

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return undefined;
    }
    const media = window.matchMedia(QUERY);
    const onChange = () => setSystem(media.matches);
    media.addEventListener?.('change', onChange);
    return () => media.removeEventListener?.('change', onChange);
  }, []);

  return system || settings.reduceMotion;
}
