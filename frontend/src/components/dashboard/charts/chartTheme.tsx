import { useMemo } from 'react';
import { useThemeSettings } from '../../../context/ThemeModeContext';
import { brand, chartCategorical, chartOther, heatScale, neutrals, type Mode } from '../../../design/tokens';
import { useReducedMotion } from '../../../hooks/useReducedMotion';
import { statusColor, type StatusToken } from '../../../theme';
import { familyOf } from '../../common/StatusChip';

/**
 * BRD 25 §7 — one place for every Recharts style decision (D-25-6). Charts take
 * their colours from here, never from hex literals, so they follow the mode,
 * tone, accent and high-contrast settings like the rest of the page.
 */

/** Reads a CSS variable from the root, or the fallback when the stylesheet is absent (tests). */
export function cssVar(name: string, fallback: string): string {
  if (typeof document === 'undefined') {
    return fallback;
  }
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

function fallbacks(mode: Mode) {
  const n = neutrals[mode];
  return {
    divider: n.divider,
    textTertiary: n.textTertiary,
    textSecondary: n.textSecondary,
    borderStrong: n.borderStrong,
    surface: n.surface,
  };
}

export type ChartTheme = ReturnType<typeof buildChartTheme>;

function buildChartTheme(mode: Mode, reduced: boolean, statusFill: (status: string) => string) {
  const f = fallbacks(mode);
  const textTertiary = cssVar('--text-tertiary', f.textTertiary);
  const surface = cssVar('--surface', f.surface);
  return {
    /** Horizontal dashed gridlines only, no vertical (BRD 25 §7.1). */
    grid: { stroke: cssVar('--divider', f.divider), strokeDasharray: '2 4', vertical: false },
    /** No axis or tick lines; quiet tabular tick labels. */
    axis: {
      axisLine: false,
      tickLine: false,
      tickMargin: 8,
      tick: { fill: textTertiary, fontSize: 12, style: { fontVariantNumeric: 'tabular-nums' } },
    },
    /** Rounded ends, 12–16px thick. */
    bar: { radius: [4, 4, 4, 4] as [number, number, number, number], maxBarSize: 16 },
    line: {
      strokeWidth: 2,
      type: 'monotone' as const,
      dot: false,
      activeDot: { r: 4, strokeWidth: 2, stroke: surface },
    },
    /** Dashed target / reference line, labelled inline. */
    reference: { stroke: textTertiary, strokeDasharray: '4 4' },
    /** A 1px guide instead of Recharts' grey cursor box. */
    tooltipCursor: { stroke: cssVar('--border-strong', f.borderStrong), strokeWidth: 1 },
    brand: cssVar('--primary', brand[mode].main),
    surface,
    labelColor: cssVar('--text-secondary', f.textSecondary),
    categorical: chartCategorical[mode].map((color, index) => cssVar(`--chart-${index + 1}`, color)),
    other: cssVar('--chart-other', chartOther[mode]),
    heat: heatScale[mode].map((color, index) => cssVar(`--heat-${index}`, color)),
    /** The fill for a status, honouring high contrast. */
    status: statusFill,
    /** Recharts animation props: 600ms ease-out once, or none under reduced motion. */
    animation: reduced
      ? { isAnimationActive: false as const }
      : { isAnimationActive: true as const, animationDuration: 600, animationEasing: 'ease-out' as const },
  };
}

/**
 * The chart theme for the current settings. Recomputed when the mode, accent,
 * tone or contrast changes, because those change the CSS variables it reads.
 */
export function useChartTheme(): ChartTheme {
  const { settings, resolvedMode } = useThemeSettings();
  const reduced = useReducedMotion();
  const { accent, backgroundTone, highContrast } = settings;
  return useMemo(() => {
    const statusFill = (status: string) =>
      highContrast ? statusColor(status.toUpperCase() as StatusToken, resolvedMode, true) : `var(--st-${familyOf(status)})`;
    return buildChartTheme(resolvedMode, reduced, statusFill);
    // accent and backgroundTone change the CSS variables buildChartTheme reads.
  }, [resolvedMode, reduced, accent, backgroundTone, highContrast]);
}

/**
 * A vertical gradient for area fills, 18% → 0% of the series colour.
 * Use inside <defs>: `<AreaGradient id="trend" color={theme.brand} />`, then `fill="url(#trend)"`.
 */
export function AreaGradient({ id, color }: { id: string; color: string }) {
  return (
    <linearGradient id={id} x1="0" x2="0" y1="0" y2="1">
      <stop offset="0%" stopColor={color} stopOpacity={0.18} />
      <stop offset="100%" stopColor={color} stopOpacity={0} />
    </linearGradient>
  );
}
