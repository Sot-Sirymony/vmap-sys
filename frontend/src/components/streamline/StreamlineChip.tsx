import Chip from '@mui/material/Chip';
import { useNavigate } from 'react-router';
import { tr } from '../../i18n/translate';
import type { StreamlineListItem } from '../../types/streamline';

/** BRD 27 §5: "Streamlined 12 Nov: −5 steps" on a goal or step, linking to the review. */
export function streamlineChipLabel(review: StreamlineListItem): string {
  const date = new Date(review.completedAt as string).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
  const goal = review.anchorType === 'GOAL';
  const cut = goal ? review.stepsBefore - review.stepsAfter : review.tasksBefore - review.tasksAfter;
  if (cut <= 0) {
    return tr('Streamlined {date}', { date });
  }
  return goal ? tr('Streamlined {date}: −{count} steps', { date, count: cut }) : tr('Streamlined {date}: −{count} tasks', { date, count: cut });
}

export function StreamlineChip({ review }: { review: StreamlineListItem | undefined }) {
  const navigate = useNavigate();
  if (!review) return null;
  return (
    <Chip
      size="small"
      variant="outlined"
      label={streamlineChipLabel(review)}
      onClick={(event) => {
        event.stopPropagation();
        navigate(`/streamline/${review.id}`);
      }}
      sx={{ ml: 0.75 }}
    />
  );
}
