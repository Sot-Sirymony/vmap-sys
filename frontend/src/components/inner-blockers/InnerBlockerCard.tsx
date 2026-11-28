import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { tr } from '../../i18n/translate';
import type { InnerBlocker, InnerBlockerAnswer } from '../../types/vision';
import { CHAIN_LABELS } from '../../utils/chains';

/** D-34-10: the four weekly answers, in plain words. */
export const ANSWER_LABELS: Record<InnerBlockerAnswer, string> = {
  YES: 'Yes',
  PARTLY: 'Partly',
  NO: 'No',
  NOT_DONE: "Didn't do it",
};

const ANSWER_COLORS: Record<InnerBlockerAnswer, 'success' | 'warning' | 'error' | 'default'> = {
  YES: 'success',
  PARTLY: 'warning',
  NO: 'error',
  NOT_DONE: 'default',
};

type Props = {
  blocker: InnerBlocker;
  /** Buttons under the card; none in a read-only view (D-34-11). */
  actions?: ReactNode;
  /** Shows "For all my dreams" or the dream's title. */
  showDream?: boolean;
};

/** V34: one inner blocker in its three parts, with its chain, dream, habit and latest weekly answers. */
export function InnerBlockerCard({ blocker, actions, showDream = true }: Props) {
  const resolved = blocker.status === 'RESOLVED';
  return (
    <Box component="article" aria-label={blocker.blocker}
      sx={{ p: 1.5, border: 1, borderColor: 'divider', borderRadius: 'var(--style-radius-control)', display: 'grid', gap: 0.75, opacity: blocker.archived ? 0.7 : 1 }}>
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
        <Typography variant="subtitle2" component="h3" sx={{ mr: 'auto' }}>{blocker.blocker}</Typography>
        {resolved && <Chip size="small" color="success" label={tr('No longer holds me back')} />}
        {blocker.archived && <Chip size="small" label={tr('Archived')} />}
      </Box>
      <Box component="dl" sx={{ m: 0, display: 'grid', gap: 0.5, '& dt': { fontWeight: 600, fontSize: '0.8rem', color: 'text.secondary' }, '& dd': { m: 0 } }}>
        <dt>{tr('How it shows up')}</dt>
        <dd><Typography variant="body2">{blocker.showsUp || tr('Not written yet')}</Typography></dd>
        <dt>{tr('Short practice')}</dt>
        <dd><Typography variant="body2">{blocker.practice}</Typography></dd>
      </Box>
      <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
        {blocker.chainKey && <Chip size="small" variant="outlined" label={tr(CHAIN_LABELS[blocker.chainKey])} />}
        {showDream && <Chip size="small" variant="outlined" label={blocker.dreamTitle ?? tr('For all my dreams')} />}
      </Box>
      {blocker.habitId && (
        <Typography variant="body2" color="text.secondary">
          {blocker.habitArchived
            ? tr('Daily habit stopped: {name}', { name: blocker.habitName ?? '' })
            : blocker.habitStreak !== null && blocker.habitStreak !== undefined
              ? tr('Daily habit: {name} · {count}-day streak', { name: blocker.habitName ?? '', count: blocker.habitStreak })
              : tr('Daily habit: {name}', { name: blocker.habitName ?? '' })}
        </Typography>
      )}
      {blocker.lastAnswers.length > 0 && (
        <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography variant="caption" color="text.secondary">{tr('Did the practice help? Latest first:')}</Typography>
          {blocker.lastAnswers.map((answer, index) => (
            <Chip key={`${answer}-${index}`} size="small" color={ANSWER_COLORS[answer]} label={tr(ANSWER_LABELS[answer])} />
          ))}
        </Box>
      )}
      {blocker.practiceNotHelping && !resolved && (
        <Alert severity="warning">{tr("This practice isn't helping yet. Try changing it.")}</Alert>
      )}
      {actions && <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>{actions}</Box>}
    </Box>
  );
}
