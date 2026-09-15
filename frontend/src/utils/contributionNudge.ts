import { dreamHasLinkedPartner } from '../api/dreamApi';
import { goalHasLinkedPartner } from '../api/goalApi';

type ShowToast = (message: string, options?: { action?: { label: string; onClick: () => void }; duration?: number }) => void;

const MESSAGE = "Who helped make this possible? Consider linking a partner or logging a gratitude entry.";

/**
 * FR-59.4 / BR-48: fires only on the OPEN status → COMPLETED transition
 * (callers check that before calling), and only ever informs — it never
 * blocks or reverses the completion that already happened. Fails open: an
 * error checking for a linked partner is treated as "has one," so a
 * network hiccup never nags where there's nothing to nag about.
 */
export async function nudgeContributionForDream({ token, dreamId, showToast, navigate }: {
  token: string;
  dreamId: number;
  showToast: ShowToast;
  navigate: (path: string) => void;
}) {
  const hasPartner = await dreamHasLinkedPartner(token, dreamId).catch(() => true);
  if (hasPartner) {
    return;
  }
  showToast(MESSAGE, {
    duration: 8000,
    action: { label: 'Add partner', onClick: () => navigate(`/partners?create=partner&relatedDreamId=${dreamId}`) },
  });
}

export async function nudgeContributionForGoal({ token, goalId, showToast, navigate }: {
  token: string;
  goalId: number;
  showToast: ShowToast;
  navigate: (path: string) => void;
}) {
  const hasPartner = await goalHasLinkedPartner(token, goalId).catch(() => true);
  if (hasPartner) {
    return;
  }
  showToast(MESSAGE, {
    duration: 8000,
    action: { label: 'Add partner', onClick: () => navigate(`/partners?create=partner&relatedGoalId=${goalId}`) },
  });
}
