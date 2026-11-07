import type { PlanDraftContent } from '../../types/vision';
import type { ArrangeActions } from './parts';
import { type DraftUpdate, type ItemPath, canMove, mergeWithNext, moveItem, splitItem } from './draftModel';

/** PB-FR-22: the arrange actions that make sense for one new item where it stands now. */
export function arrangeFor(content: PlanDraftContent, path: ItemPath, onChange: (update: DraftUpdate) => void): ArrangeActions {
  return {
    onUp: canMove(content, path, -1) ? () => onChange((current) => moveItem(current, path, -1)) : undefined,
    onDown: canMove(content, path, 1) ? () => onChange((current) => moveItem(current, path, 1)) : undefined,
    onMerge: canMove(content, path, 1) ? () => onChange((current) => mergeWithNext(current, path)) : undefined,
    onSplit: () => onChange((current) => splitItem(current, path)),
  };
}
