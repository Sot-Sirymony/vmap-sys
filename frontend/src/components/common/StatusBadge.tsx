import { statusColor, type StatusToken } from '../../theme';
import { useThemeSettings } from '../../context/ThemeModeContext';
import { TintedChip } from './TintedChip';

export function StatusBadge({ status }: { status: string }) {
  // BR-34: under high contrast the hue moves in lightness only — a Completed
  // badge is still green. TintedChip's label already blends with --foreground,
  // so this is mostly about keeping the tint and border visible.
  const { settings, resolvedMode } = useThemeSettings();
  const hue = statusColor(status.toUpperCase() as StatusToken, resolvedMode, settings.highContrast);

  return <TintedChip label={status.replaceAll('_', ' ')} hue={hue} />;
}
