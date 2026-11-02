import { statusColor, type StatusToken } from '../../theme';
import { useThemeSettings } from '../../context/ThemeModeContext';
import { TintedChip } from './TintedChip';
import { currentLanguage, tr } from '../../i18n/translate';

export function StatusBadge({ status }: { status: string }) {
  // BR-34: under high contrast the hue moves in lightness only — a Completed
  // badge is still green. TintedChip's label already blends with --foreground,
  // so this is mostly about keeping the tint and border visible.
  const { settings, resolvedMode } = useThemeSettings();
  const hue = statusColor(status.toUpperCase() as StatusToken, resolvedMode, settings.highContrast);

  return <TintedChip label={statusText(status)} hue={hue} />;
}

/** English keeps the raw upper-case status; other languages look up its label, e.g. "In Progress". */
function statusText(status: string) {
  const text = status.replaceAll('_', ' ');
  if (currentLanguage() === 'en') {
    return text;
  }
  return tr(text.toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase()));
}
