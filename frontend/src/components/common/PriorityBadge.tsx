import { neutralFallback, priorityColors, type PriorityToken } from '../../theme';
import { TintedChip } from './TintedChip';

export function PriorityBadge({ priority }: { priority: string }) {
  const hue = priorityColors[priority.toUpperCase() as PriorityToken] ?? neutralFallback;

  return <TintedChip label={priority} hue={hue} />;
}
