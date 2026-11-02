import { priorityColor, type PriorityToken } from '../../theme';
import { useThemeSettings } from '../../context/ThemeModeContext';
import { TintedChip } from './TintedChip';
import { currentLanguage, tr } from '../../i18n/translate';

export function PriorityBadge({ priority }: { priority: string }) {
  // BR-34: high contrast darkens/lightens the escalation scale without changing
  // its hues, so Critical stays red and HIGH still can't be read as BLOCKED.
  const { settings, resolvedMode } = useThemeSettings();
  const hue = priorityColor(priority.toUpperCase() as PriorityToken, resolvedMode, settings.highContrast);

  // English keeps what callers pass; other languages look up the label, e.g. "High".
  const label = currentLanguage() === 'en' ? priority : tr(priority.charAt(0).toUpperCase() + priority.slice(1).toLowerCase());
  return <TintedChip label={label} hue={hue} />;
}
