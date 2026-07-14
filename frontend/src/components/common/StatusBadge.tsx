import { neutralFallback, statusColors, type StatusToken } from '../../theme';
import { TintedChip } from './TintedChip';

export function StatusBadge({ status }: { status: string }) {
  const hue = statusColors[status.toUpperCase() as StatusToken] ?? neutralFallback;

  return <TintedChip label={status.replaceAll('_', ' ')} hue={hue} />;
}
