import Box from '@mui/material/Box';
import { Circle, CircleAlert, CircleCheck, CircleDot, CirclePause, CircleSlash, Hourglass, OctagonX, type LucideIcon } from 'lucide-react';
import { useThemeSettings } from '../../context/ThemeModeContext';
import type { StatusFamily } from '../../design/tokens';
import { currentLanguage, tr } from '../../i18n/translate';
import { statusColor, statusFamily, type StatusToken } from '../../theme';
import { tintColors } from './TintedChip';

/** BRD 25 §5.2 — one icon per meaning family, so status never rests on colour alone (D-25-5). */
export const STATUS_ICONS: Record<StatusFamily, LucideIcon> = {
  moving: CircleDot,
  waiting: Hourglass,
  blocked: OctagonX,
  open: CircleAlert,
  declined: CircleSlash,
  done: CircleCheck,
  idle: Circle,
  held: CirclePause,
};

/** The family of any status string; unknown values read as "no state" (idle). */
export function familyOf(status: string): StatusFamily {
  return statusFamily(status.toUpperCase()) ?? 'idle';
}

/**
 * The fill for a status as a CSS value: the `--st-*` variable normally (so it
 * follows the mode with no re-render), or the high-contrast hue from theme.ts
 * when that mode is on, since the variables do not carry it (BR-34).
 */
export function useStatusFill() {
  const { settings, resolvedMode } = useThemeSettings();
  return (status: string) =>
    settings.highContrast
      ? statusColor(status.toUpperCase() as StatusToken, resolvedMode, true)
      : `var(--st-${familyOf(status)})`;
}

/** "IN_PROGRESS" → "In progress", translated. */
export function statusLabel(status: string): string {
  const words = status.replaceAll('_', ' ').toLowerCase();
  const sentence = words.charAt(0).toUpperCase() + words.slice(1);
  if (currentLanguage() === 'en') {
    return sentence;
  }
  // km.ts keys most statuses in Title Case ("In Progress"); try both.
  const title = words.replace(/\b\w/g, (letter) => letter.toUpperCase());
  const translated = tr(title);
  return translated === title ? tr(sentence) : translated;
}

type StatusChipProps = {
  status: string;
  /** `subtle` (tinted background, the default) or `outline` for dense tables. */
  variant?: 'subtle' | 'outline';
  /** Overrides the derived label, e.g. a domain-specific name for the status. */
  label?: string;
};

/**
 * BRD 25 §11 — the status chip: colour + icon + label, 24px high. It replaces
 * StatusBadge gradually; both read the same palette.
 */
export function StatusChip({ status, variant = 'subtle', label }: StatusChipProps) {
  const { settings, resolvedMode } = useThemeSettings();
  const family = familyOf(status);
  const Icon = STATUS_ICONS[family];

  let background = variant === 'outline' ? 'transparent' : `var(--st-${family}-bg)`;
  let color = `var(--st-${family}-fg)`;
  let border = variant === 'outline' || family === 'held' ? `1px ${family === 'held' ? 'dashed' : 'solid'} var(--st-${family})` : '1px solid transparent';
  if (settings.highContrast) {
    const tint = tintColors(statusColor(status.toUpperCase() as StatusToken, resolvedMode, true));
    background = variant === 'outline' ? 'transparent' : tint.background;
    color = tint.text;
    border = `1px solid ${tint.border}`;
  }

  return (
    <Box
      component="span"
      data-status-family={family}
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.5,
        height: 24,
        px: 1,
        borderRadius: 'var(--radius-pill)',
        fontSize: 'var(--font-caption)',
        lineHeight: 1,
        fontWeight: 500,
        whiteSpace: 'nowrap',
        bgcolor: background,
        color,
        border,
        verticalAlign: 'middle',
      }}
    >
      <Icon size={14} strokeWidth={1.75} aria-hidden="true" />
      {label ?? statusLabel(status)}
    </Box>
  );
}
