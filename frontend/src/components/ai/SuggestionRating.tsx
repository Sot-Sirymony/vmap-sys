import { useState } from 'react';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import { ThumbsDown, ThumbsUp } from 'lucide-react';
import { ratePlanAnswer } from '../../api/aiPlanApi';
import { tr } from '../../i18n/translate';

/**
 * V11 (PB-FR-34): thumbs up or down on one set of plan suggestions. Pressing
 * the chosen thumb again clears it. Hidden when the answer has no request id
 * (for example the plan check without AI).
 */
export function SuggestionRating({ token, requestId }: { token: string; requestId?: number | null }) {
  const [rating, setRating] = useState(0);
  const [failed, setFailed] = useState(false);

  if (!requestId) {
    return null;
  }

  async function rate(value: number) {
    const next = rating === value ? 0 : value;
    setFailed(false);
    try {
      await ratePlanAnswer(token, requestId as number, next);
      setRating(next);
    } catch {
      setFailed(true);
    }
  }

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
      <Typography variant="caption" color="text.secondary">{tr('Were these suggestions helpful?')}</Typography>
      <IconButton size="small" aria-label={tr('Helpful')} aria-pressed={rating === 1} color={rating === 1 ? 'primary' : 'default'}
        onClick={() => void rate(1)}>
        <ThumbsUp size={14} />
      </IconButton>
      <IconButton size="small" aria-label={tr('Not helpful')} aria-pressed={rating === -1} color={rating === -1 ? 'primary' : 'default'}
        onClick={() => void rate(-1)}>
        <ThumbsDown size={14} />
      </IconButton>
      {failed && <Typography variant="caption" color="error">{tr('Could not save your rating.')}</Typography>}
    </Box>
  );
}

/** V11 (PB-FR-35): whether the participant changed a suggestion before adding it. */
export function wasEdited<T extends object>(original: T, current: T, fields: (keyof T)[]) {
  return fields.some((field) => (original[field] ?? '') !== (current[field] ?? ''));
}
