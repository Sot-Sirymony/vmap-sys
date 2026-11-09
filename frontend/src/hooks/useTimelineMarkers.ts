import { useEffect, useMemo, useState } from 'react';
import { listCommunicationMessages } from '../api/communicationApi';
import { listMilestones } from '../api/planApi';
import { listReviews } from '../api/reviewApi';
import type { CommunicationMessage, Dream, Goal, Milestone, Review, TaskItem, VisionStep } from '../types/vision';
import { buildMarkers, type TimelineMarker } from '../utils/timeline';

/** Enough messages for any one participant's follow-ups; the server allows far more per page. */
const MESSAGE_PAGE = 500;

/**
 * V16 (TL-FR-13): milestones, reviews and follow-ups for the dreams on a
 * timeline. Each source is a help, not part of the plan: one that fails to
 * load just leaves its marks out.
 */
export function useTimelineMarkers(
  token: string | null | undefined,
  dreams: Dream[],
  goals: Goal[],
  steps: VisionStep[],
  tasks: TaskItem[],
  enabled = true,
): TimelineMarker[] {
  const [milestones, setMilestones] = useState<Milestone[]>([]);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [messages, setMessages] = useState<CommunicationMessage[]>([]);
  const dreamKey = dreams.map((dream) => dream.id).join(',');

  useEffect(() => {
    if (!token || !enabled) return undefined;
    let active = true;
    listReviews(token).then((data) => { if (active) setReviews(data); }).catch(() => undefined);
    listCommunicationMessages(token, 0, MESSAGE_PAGE)
      .then((page) => { if (active) setMessages(page.content); })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [token, enabled]);

  useEffect(() => {
    if (!token || !enabled || !dreamKey) return undefined;
    let active = true;
    Promise.all(dreamKey.split(',').map((id) => listMilestones(token, Number(id)).catch(() => [] as Milestone[])))
      .then((lists) => { if (active) setMilestones(lists.flat()); });
    return () => {
      active = false;
    };
  }, [token, enabled, dreamKey]);

  return useMemo(
    () => (enabled ? buildMarkers(dreams, goals, steps, tasks, milestones, reviews, messages) : []),
    [enabled, dreams, goals, steps, tasks, milestones, reviews, messages],
  );
}
