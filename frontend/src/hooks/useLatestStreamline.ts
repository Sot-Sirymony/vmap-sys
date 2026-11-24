import { useEffect, useState } from 'react';
import { listStreamlineReviews } from '../api/streamlineApi';
import type { StreamlineListItem } from '../types/streamline';

export type LatestStreamline = { byGoal: Map<number, StreamlineListItem>; byStep: Map<number, StreamlineListItem> };

/**
 * V27 (BRD 27 §5): the latest completed Streamline review of each goal and step,
 * for the summary chip on the Goals and Steps pages. One request per page.
 */
export function useLatestStreamline(token: string | null): LatestStreamline {
  const [latest, setLatest] = useState<LatestStreamline>({ byGoal: new Map(), byStep: new Map() });

  useEffect(() => {
    if (!token) return undefined;
    let cancelled = false;
    listStreamlineReviews(token)
      .then((reviews) => {
        if (cancelled) return;
        const byGoal = new Map<number, StreamlineListItem>();
        const byStep = new Map<number, StreamlineListItem>();
        for (const review of reviews) {
          if (review.status !== 'COMPLETED' || !review.completedAt) continue;
          const map = review.goalId ? byGoal : byStep;
          const key = (review.goalId ?? review.stepId) as number;
          const current = map.get(key);
          if (!current || (current.completedAt ?? '') < review.completedAt) map.set(key, review);
        }
        setLatest({ byGoal, byStep });
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [token]);

  return latest;
}
