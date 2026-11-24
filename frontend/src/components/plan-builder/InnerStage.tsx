import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link as RouterLink, useNavigate } from 'react-router';
import { getChainAssessments } from '../../api/chainAssessmentApi';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import type { ChainAssessment, ChainScoreKey, PlanDraftContent } from '../../types/vision';
import { PRACTICE_ACTIONS, PRACTICE_PROGRAM } from '../../utils/assessmentContent';
import { CHAINS, chainScore } from '../../utils/chains';
import { Button } from '../common/Button';
import { Loading } from '../common/Loading';
import type { DraftUpdate } from './draftModel';

/** A take older than this is offered again (DC-FR-19). */
const STALE_DAYS = 90;
/** At most this many chains are named (DC-FR-20). */
const MAX_CHAINS = 2;
/** Scores at or below this hold the participant back (1 to 5, 5 = barely holds me back). */
const HOLDS_BACK = 3;

/** DC-FR-20: the chains most likely to slow this dream: the lowest scores of 3 or less, at most two. */
export function chainsToWatch(take: ChainAssessment | undefined): ChainScoreKey[] {
  if (!take) {
    return [];
  }
  return CHAINS
    .map((chain) => ({ key: chain.key, score: chainScore(take, chain.key) }))
    .filter((item): item is { key: ChainScoreKey; score: number } => item.score !== null && item.score <= HOLDS_BACK)
    .sort((left, right) => left.score - right.score)
    .slice(0, MAX_CHAINS)
    .map((item) => item.key);
}

type Props = {
  content: PlanDraftContent;
  onChange: (update: DraftUpdate) => void;
  token: string;
  onDone: () => void;
  today?: Date;
};

/**
 * V31 v1 step 2 (BRD 31 stage 3, DC-FR-19 to DC-FR-23, D-31-7): inner
 * blockers come before mapping. Shows the latest six-chain result, names at
 * most two chains that may slow this dream with the program's own practice
 * for each, and offers the two-minute check when there is no recent result.
 * It names chains from the participant's own scores, never diagnoses, and
 * uses no AI. The draft is saved as it changes, so leaving for the check
 * loses nothing. "I'm ready to plan" or "Skip for now" both continue; the choice
 * is kept in the draft, and skipping shows once as a warning on the check.
 */
export function InnerStage({ content, onChange, token, onDone, today = new Date() }: Props) {
  const navigate = useNavigate();
  const [takes, setTakes] = useState<ChainAssessment[] | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    getChainAssessments(token)
      .then((summary) => !cancelled && setTakes(summary.assessments ?? []))
      .catch((loadError) => !cancelled && setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load your check.')));
    return () => {
      cancelled = true;
    };
  }, [token]);

  const latest = takes?.length ? [...takes].sort((left, right) => right.takenOn.localeCompare(left.takenOn))[0] : undefined;
  const ageDays = latest ? (today.getTime() - new Date(`${latest.takenOn}T00:00:00`).getTime()) / 86_400_000 : null;
  const stale = latest === undefined || (ageDays !== null && ageDays > STALE_DAYS);
  const watch = chainsToWatch(latest);

  function choose(value: 'READY' | 'SKIPPED') {
    onChange((draft) => ({ ...draft, innerBlockers: value }));
    onDone();
  }

  return (
    <Stack spacing={2}>
      <Typography variant="body2">
        {tr('Before you map this dream, take a moment to see what might hold you back inside. Gaps in knowledge, skill or money come later, on the steps.')}
      </Typography>
      {takes === null && !error && <Loading />}
      {error && <Alert severity="error">{error}</Alert>}
      {takes !== null && stale && (
        <Alert severity="info" action={<Button variant="secondary" onClick={() => navigate('/assessment')}>{tr('Take the 2-minute check')}</Button>}>
          {latest ? tr('Your last check was more than three months ago.') : tr('You have not taken the 2-minute check yet.')}
        </Alert>
      )}
      {latest && (
        <Typography variant="body2" color="text.secondary">
          {tr('From your check on {date}.', { date: formatDate(new Date(`${latest.takenOn}T00:00:00`), { year: true }) })}
        </Typography>
      )}
      {latest && watch.length === 0 && (
        <Alert severity="success">{tr('None of the six chains holds you back much right now.')}</Alert>
      )}
      {watch.map((key) => (
        <Box key={key} sx={{ p: 1.5, border: 1, borderColor: 'divider', borderRadius: 'var(--style-radius-control)', display: 'grid', gap: 0.5 }}>
          <Typography variant="subtitle2">{tr(CHAINS.find((chain) => chain.key === key)?.label ?? key)}</Typography>
          <Typography variant="body2">{tr(PRACTICE_PROGRAM[key].practice)}</Typography>
          <Link component={RouterLink} to={PRACTICE_ACTIONS[key].path} variant="body2">
            {tr('Open {page}', { page: tr(PRACTICE_ACTIONS[key].page) })}
          </Link>
        </Box>
      ))}
      <Typography variant="caption" color="text.secondary">
        {tr('This is a practice, not a diagnosis. If something feels heavy, talk to your coach or someone you trust.')}
      </Typography>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        <Button onClick={() => choose('READY')}>{tr("I'm ready to plan")}</Button>
        <Button variant="secondary" onClick={() => choose('SKIPPED')}>{tr('Skip for now')}</Button>
      </Box>
      {content.innerBlockers && (
        <Typography variant="caption" color="text.secondary">
          {content.innerBlockers === 'READY' ? tr('You said you are ready to plan.') : tr('You skipped this for now. You can come back any time.')}
        </Typography>
      )}
    </Stack>
  );
}
