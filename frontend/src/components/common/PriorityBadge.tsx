import { priorityColor, type PriorityToken } from '../../theme';
import { useThemeSettings } from '../../context/ThemeModeContext';
import { TintedChip } from './TintedChip';

export function PriorityBadge({ priority }: { priority: string }) {
  // BR-34: high contrast darkens/lightens the escalation scale without changing
  // its hues, so Critical stays red and HIGH still can't be read as BLOCKED.
  const { settings, resolvedMode } = useThemeSettings();
  const hue = priorityColor(priority.toUpperCase() as PriorityToken, resolvedMode, settings.highContrast);

  return <TintedChip label={priority} hue={hue} />;
}
