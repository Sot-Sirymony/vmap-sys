import { Link } from 'react-router';
import { tr } from '../../i18n/translate';
import type { BlockerCount } from '../../utils/blockerRollup';

/** "2 blockers · 1 blocked task" in the participant's language. */
export function blockerRollupText({ blockers, blockedTasks }: BlockerCount): string {
  const parts: string[] = [];
  if (blockers > 0) {
    parts.push(blockers === 1 ? tr('1 blocker') : tr('{count} blockers', { count: blockers }));
  }
  if (blockedTasks > 0) {
    parts.push(blockedTasks === 1 ? tr('1 blocked task') : tr('{count} blocked tasks', { count: blockedTasks }));
  }
  return parts.join(' · ');
}

/**
 * V17 BL-FR-15, BL-FR-16: a read-only count on a dream or goal of what blocks
 * the steps and tasks under it, linking to the Obstacles page filtered to that
 * dream or goal. There is deliberately no "add obstacle" here: blockers are
 * added on a step or task. Renders nothing when there is nothing blocked.
 */
export function BlockerRollupLink({ count, dreamId, goalId }: { count: BlockerCount; dreamId?: number; goalId?: number }) {
  const text = blockerRollupText(count);
  if (!text) {
    return null;
  }
  const to = goalId !== undefined ? `/obstacles?goalId=${goalId}` : `/obstacles?dreamId=${dreamId}`;
  return (
    <Link to={to} className="blocker-rollup" aria-label={tr('Blockers under this: {summary}', { summary: text })}>
      {text}
    </Link>
  );
}
