import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { listStreamlineCandidates, listStreamlineReviews, startStreamlineReview } from '../api/streamlineApi';
import { Button } from '../components/common/Button';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { CANDIDATE_REASONS, STAGE_NAMES } from '../components/streamline/streamlineLabels';
import { RepeatingTasksCard } from '../components/streamline/RepeatingTasksCard';
import { useAuth } from '../context/AuthContext';
import { surface } from '../design/surface';
import { tr } from '../i18n/translate';
import type { StreamlineCandidate, StreamlineListItem } from '../types/streamline';
import { PageSection } from './PageSection';

const DISMISS_KEY = 'vms-streamline-dismissed';
const DISMISS_DAYS = 30;

/** §6.2: a suggestion can be dismissed for 30 days, per browser. */
function dismissedNow(): Record<string, number> {
  try {
    const stored = JSON.parse(localStorage.getItem(DISMISS_KEY) ?? '{}') as Record<string, number>;
    const cutoff = Date.now() - DISMISS_DAYS * 24 * 3600 * 1000;
    return Object.fromEntries(Object.entries(stored).filter(([, at]) => at > cutoff));
  } catch {
    return {};
  }
}

function dismiss(key: string) {
  try {
    localStorage.setItem(DISMISS_KEY, JSON.stringify({ ...dismissedNow(), [key]: Date.now() }));
  } catch {
    // Storage unavailable: the card just comes back next time.
  }
}

/** The suggestion cards (§6.2), shared by this page and the dashboard card. */
export function StreamlineCandidates({ candidates, onStart, limit }: { candidates: StreamlineCandidate[]; onStart: (candidate: StreamlineCandidate) => void; limit?: number }) {
  const [dismissed, setDismissed] = useState(dismissedNow);
  const reasons = CANDIDATE_REASONS();
  const visible = candidates.filter((candidate) => !dismissed[`${candidate.anchorType}-${candidate.anchorId}`]).slice(0, limit ?? candidates.length);
  if (visible.length === 0) return null;
  return (
    <Box sx={{ display: 'grid', gap: 1 }} role="region" aria-label={tr('Worth streamlining')}>
      {visible.map((candidate) => {
        const key = `${candidate.anchorType}-${candidate.anchorId}`;
        return (
          <Box key={key} sx={{ ...surface('inset'), p: 1.25, display: 'flex', alignItems: 'center', gap: 1, justifyContent: 'space-between', flexWrap: 'wrap' }}>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {candidate.anchorType === 'GOAL' ? tr('Goal') : tr('Step')} {candidate.code} · {candidate.title}
              </Typography>
              <Typography variant="caption" sx={{ color: 'var(--text-secondary)' }}>
                {reasons[candidate.reason] ?? candidate.reason} — {candidate.detail}
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button onClick={() => onStart(candidate)}>{tr('Run a Streamline review')}</Button>
              <Button variant="secondary" onClick={() => { dismiss(key); setDismissed(dismissedNow()); }}>{tr('Not now')}</Button>
            </Box>
          </Box>
        );
      })}
    </Box>
  );
}

/** V27 SR-FR-11: open and completed Streamline reviews, and goals or steps worth streamlining. */
export function StreamlineListPage() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const [reviews, setReviews] = useState<StreamlineListItem[] | null>(null);
  const [candidates, setCandidates] = useState<StreamlineCandidate[]>([]);
  const [error, setError] = useState('');
  const [startError, setStartError] = useState('');
  const names = STAGE_NAMES();

  useEffect(() => {
    if (!token) return;
    listStreamlineReviews(token).then(setReviews).catch((loadError: unknown) => setError(loadError instanceof Error ? tr(loadError.message) : tr('Could not load reviews.')));
    listStreamlineCandidates(token).then(setCandidates).catch(() => setCandidates([]));
  }, [token]);

  async function start(anchor: { goalId?: number; stepId?: number }) {
    if (!token) return;
    setStartError('');
    try {
      const review = await startStreamlineReview(token, anchor);
      navigate(`/streamline/${review.id}`);
    } catch (startFailure) {
      setStartError(startFailure instanceof Error ? tr(startFailure.message) : tr('Could not start the review.'));
    }
  }

  return (
    <PageSection
      title={tr('Streamline')}
      subtitle={tr('Trim a goal or a step in five stages: question, delete, simplify, speed up, then automate. Start from the Streamline action on any goal or step.')}
    >
      {startError && <Alert severity="warning">{startError}</Alert>}
      <StreamlineCandidates candidates={candidates} onStart={(candidate) => void start(candidate.anchorType === 'GOAL' ? { goalId: candidate.anchorId } : { stepId: candidate.anchorId })} />
      {error && <ErrorMessage message={error} />}
      {!reviews && !error && <Loading />}
      {reviews && reviews.length === 0 && (
        <EmptyState>{tr('No Streamline reviews yet. Open a goal or step and choose Streamline.')}</EmptyState>
      )}
      {reviews && reviews.length > 0 && (
        <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 1 }}>
          {reviews.map((review) => (
            <Box component="li" key={review.id}>
              <Box component={Link} to={`/streamline/${review.id}`} sx={{ ...surface('card', { interactive: true }), display: 'flex', gap: 1.5, alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', color: 'inherit', textDecoration: 'none' }}>
                <Box sx={{ minWidth: 0 }}>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>{review.anchorTitle}</Typography>
                  <Typography variant="caption" sx={{ color: 'var(--text-secondary)' }}>
                    {review.code} · {review.anchorType === 'GOAL' ? tr('Goal') : tr('Step')} · {new Date(review.startedAt).toLocaleDateString()}
                  </Typography>
                </Box>
                <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                  <Typography variant="body2" sx={{ color: 'var(--text-secondary)' }}>
                    {tr('Steps {before} → {after} · {cut} cut · {back} brought back', { before: review.stepsBefore, after: review.stepsAfter, cut: review.deleted, back: review.addedBack })}
                  </Typography>
                  <Chip size="small" label={review.status === 'OPEN' ? tr('Stage {number}: {name}', { number: review.currentStage, name: names[review.currentStage - 1] }) : review.status === 'COMPLETED' ? tr('Completed') : tr('Abandoned')}
                    // Outlined chips draw the colour as text, and MUI's success green is a fill colour that fails 4.5:1 as text.
                    color={review.status === 'OPEN' ? 'primary' : 'default'} variant="outlined" />
                </Box>
              </Box>
            </Box>
          ))}
        </Box>
      )}
      {token && <RepeatingTasksCard token={token} />}
    </PageSection>
  );
}

/** /streamline/new?goalId= or ?stepId=: starts a review and opens it (SR-FR-01). */
export function StreamlineStartPage() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token) return;
    const goalId = params.get('goalId');
    const stepId = params.get('stepId');
    let cancelled = false;
    startStreamlineReview(token, goalId ? { goalId: Number(goalId) } : { stepId: Number(stepId) })
      .then((review) => !cancelled && navigate(`/streamline/${review.id}`, { replace: true }))
      .catch((startError: unknown) => !cancelled && setError(startError instanceof Error ? tr(startError.message) : tr('Could not start the review.')));
    return () => {
      cancelled = true;
    };
  }, [token, params, navigate]);

  return (
    <PageSection title={tr('Streamline')} subtitle="">
      {error ? (
        <Alert severity="warning" action={<Button variant="secondary" onClick={() => navigate('/streamline')}>{tr('All reviews')}</Button>}>{error}</Alert>
      ) : <Loading />}
    </PageSection>
  );
}
