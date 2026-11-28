import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router';
import Link from '@mui/material/Link';
import { getInnerBlockerCheckins, listInnerBlockers } from '../../api/innerBlockerApi';
import { tr } from '../../i18n/translate';
import type { InnerBlocker, InnerBlockerAnswer } from '../../types/vision';
import { ANSWER_LABELS } from './InnerBlockerCard';

export type CheckinAnswers = Record<number, InnerBlockerAnswer>;

const ANSWERS: InnerBlockerAnswer[] = ['YES', 'PARTLY', 'NO', 'NOT_DONE'];

type Props = {
  token: string;
  /** The review being edited, whose saved answers are loaded; null for a new review. */
  reviewId: number | null;
  value: CheckinAnswers;
  onChange: (answers: CheckinAnswers) => void;
};

/**
 * V34 (§2.4, D-34-10): in a weekly review, "Did the practice help this
 * week?" for each active inner blocker. Answers are optional and saved with
 * the review. Rules only, no AI.
 */
export function WeeklyInnerBlockerCheckins({ token, reviewId, value, onChange }: Props) {
  const [blockers, setBlockers] = useState<InnerBlocker[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    listInnerBlockers(token, { status: 'ACTIVE' })
      .then((list) => !cancelled && setBlockers(list))
      .catch(() => !cancelled && setBlockers([]));
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    if (reviewId === null) {
      return undefined;
    }
    let cancelled = false;
    getInnerBlockerCheckins(token, reviewId)
      .then((saved) => {
        if (!cancelled) {
          onChange(Object.fromEntries(saved.checkins.map((checkin) => [checkin.innerBlockerId, checkin.answer])));
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
    // onChange is the parent's setter; loading again only when the review changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, reviewId]);

  if (blockers === null) {
    return null;
  }
  return (
    <Box component="fieldset" className="field-full" sx={{ border: 1, borderColor: 'divider', borderRadius: 'var(--style-radius-control)', p: 1.5, m: 0 }}>
      <Typography component="legend" variant="subtitle2" sx={{ px: 0.5 }}>{tr('My inner blockers')}</Typography>
      {blockers.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          {tr('No inner blockers yet.')}{' '}
          <Link component={RouterLink} to="/assessment">{tr('Add them on the Assessment page')}</Link>
        </Typography>
      ) : (
        <Box sx={{ display: 'grid', gap: 1.5 }}>
          <Typography variant="body2" color="text.secondary">{tr('Did the practice help this week? Answers are optional.')}</Typography>
          {blockers.map((blocker) => (
            <Box key={blocker.id} sx={{ display: 'grid', gap: 0.5 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }} id={`checkin-${blocker.id}`}>{blocker.blocker}</Typography>
              <Typography variant="caption" color="text.secondary">{blocker.practice}</Typography>
              <ToggleButtonGroup exclusive size="small" aria-labelledby={`checkin-${blocker.id}`} value={value[blocker.id] ?? null}
                sx={{ flexWrap: 'wrap' }}
                onChange={(_event, answer: InnerBlockerAnswer | null) => {
                  const next = { ...value };
                  if (answer === null) {
                    delete next[blocker.id];
                  } else {
                    next[blocker.id] = answer;
                  }
                  onChange(next);
                }}>
                {ANSWERS.map((answer) => <ToggleButton key={answer} value={answer}>{tr(ANSWER_LABELS[answer])}</ToggleButton>)}
              </ToggleButtonGroup>
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );
}
