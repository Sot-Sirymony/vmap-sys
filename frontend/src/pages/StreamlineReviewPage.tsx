import Alert from '@mui/material/Alert';
import MuiButton from '@mui/material/Button';
import Box from '@mui/material/Box';
import Step from '@mui/material/Step';
import StepButton from '@mui/material/StepButton';
import Stepper from '@mui/material/Stepper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { Lock } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/apiClient';
import { listPartners } from '../api/partnerApi';
import {
  abandonStreamlineReview, adviseStreamlineSummary, bulkQuestionStreamline, completeStreamlineStage, deleteStreamlineItem, getParticipantStreamlineReview, getStreamlineReview, mergeStreamlineItem, repeatStreamlineItem, restoreStreamlineItem, setStreamlineTarget, simplifyStreamlineItem, skipStreamlineStage, updateStreamlineItem,
} from '../api/streamlineApi';
import { Button } from '../components/common/Button';
import { ConfirmDialog } from '../components/common/ConfirmDialog';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { Modal } from '../components/common/Modal';
import {
  AutomateStage,
  BeforeAfter,
  DeleteStage,
  QuestionStage,
  SimplifyStage,
  SpeedStage,
  type StageActions,
} from '../components/streamline/StreamlineStages';
import { StreamlineSuggestionsPanel } from '../components/streamline/StreamlineSuggestionsPanel';
import { PageAssistantProvider, PageHelpButton } from '../components/ai/page-assistant/PageAssistant';
import { days, hours, STAGE_INTROS, STAGE_NAMES, STAGE_PROMPTS, STATE_LABELS } from '../components/streamline/streamlineLabels';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { surface } from '../design/surface';
import { currentLanguage, tr } from '../i18n/translate';
import type { StreamlineAdvice, StreamlineItem, StreamlineReview } from '../types/streamline';
import { PageSection } from './PageSection';

/** BR-27-8: the server refuses to empty a goal or step without confirmation; this is how the page recognises it. */
function needsEmptyConfirmation(error: unknown): boolean {
  return error instanceof ApiError && /no open (steps|tasks)/i.test(error.message);
}

/**
 * V27 (BRD 27): the Streamline review page — a five-stage stepper (question,
 * delete, simplify, speed up, automate) that unlocks in order (D-27-3), the
 * stage panels, AI suggestions (SR-FR-14) and the summary. A coach sees the
 * same page read-only (D-27-10).
 */
export function StreamlineReviewPage({ coach = false }: { coach?: boolean }) {
  const params = useParams();
  const { token } = useAuth();
  const { showToast } = useToast();
  const reviewId = Number(coach ? params.reviewId : params.id);
  const participantId = Number(params.userId);
  const [review, setReview] = useState<StreamlineReview | null>(null);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [viewStage, setViewStage] = useState<number | null>(null);
  const [skipOpen, setSkipOpen] = useState(false);
  const [skipReason, setSkipReason] = useState('');
  const [confirmEmpty, setConfirmEmpty] = useState<(() => Promise<StreamlineReview>) | null>(null);
  const [abandonOpen, setAbandonOpen] = useState(false);
  const [partners, setPartners] = useState<{ id: number; name: string }[]>([]);

  const load = useCallback(async () => {
    if (!token) return;
    try {
      setError('');
      const loaded = coach ? await getParticipantStreamlineReview(token, participantId, reviewId) : await getStreamlineReview(token, reviewId);
      setReview(loaded);
    } catch (loadError) {
      setError(loadError instanceof Error ? tr(loadError.message) : tr('Could not load this review.'));
    }
  }, [token, coach, participantId, reviewId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!token || coach) return;
    listPartners(token, 0, 100).then((page) => setPartners(page.content.map((partner) => ({ id: partner.id, name: partner.name })))).catch(() => setPartners([]));
  }, [token, coach]);

  const readOnly = coach || !review || review.status !== 'OPEN';

  /** Runs one review action and shows the updated review, or the server's reason. */
  // Resolves after the review is updated; rejects on failure (after showing the error), so V30 bulk apply can stop.
  const run = useCallback(async (action: () => Promise<StreamlineReview>, after?: (updated: StreamlineReview) => void) => {
    setActionError('');
    try {
      const updated = await action();
      setReview(updated);
      after?.(updated);
    } catch (runError) {
      setActionError(runError instanceof Error ? tr(runError.message) : tr('That did not work. Try again.'));
      throw runError;
    }
  }, []);
  const quiet = (promise: Promise<void>) => promise.catch(() => undefined);

  const actions = useMemo<StageActions | null>(() => {
    if (!token || !review) return null;
    const id = review.id;
    const undoToast = (item: StreamlineItem, verb: string) =>
      showToast(tr('{code} {verb}. You can bring it back.', { code: item.code, verb }), {
        action: { label: tr('Undo'), onClick: () => void run(() => restoreStreamlineItem(token, id, item.id)) },
      });
    return {
      updateItem: (itemId, update) => run(() => updateStreamlineItem(token, id, itemId, update)),
      bulkQuestion: (itemIds, answers) => quiet(run(() => bulkQuestionStreamline(token, id, { itemIds, ...answers }))),
      deleteItem: (item) => {
        const attempt = (confirm: boolean) => deleteStreamlineItem(token, id, item.id, confirm);
        setActionError('');
        return attempt(false)
          .then((updated) => { setReview(updated); undoToast(item, tr('deleted')); })
          .catch((deleteError) => {
            if (needsEmptyConfirmation(deleteError)) {
              setConfirmEmpty(() => () => attempt(true).then((updated) => { undoToast(item, tr('deleted')); return updated; }));
            } else {
              setActionError(deleteError instanceof Error ? tr(deleteError.message) : tr('That did not work. Try again.'));
              throw deleteError;
            }
          });
      },
      restoreItem: (item) => quiet(run(() => restoreStreamlineItem(token, id, item.id), () => showToast(tr('{code} is back.', { code: item.code })))),
      mergeItem: (item, targetItemId) => {
        const attempt = (confirm: boolean) => mergeStreamlineItem(token, id, item.id, targetItemId, confirm);
        setActionError('');
        return attempt(false)
          .then((updated) => { setReview(updated); undoToast(item, tr('merged')); })
          .catch((mergeError) => {
            if (needsEmptyConfirmation(mergeError)) {
              setConfirmEmpty(() => () => attempt(true));
            } else {
              setActionError(mergeError instanceof Error ? tr(mergeError.message) : tr('That did not work. Try again.'));
              throw mergeError;
            }
          });
      },
      simplify: (item, body) => run(() => simplifyStreamlineItem(token, id, item.id, body)),
      setTarget: (value) => quiet(run(() => setStreamlineTarget(token, id, value))),
      repeat: (item, cadence) => run(() => repeatStreamlineItem(token, id, item.id, cadence), () => showToast(tr('{code} will repeat.', { code: item.code }))),
    };
  }, [token, review, run, showToast]);

  // The stage panels get actions that never reject (errors are already shown); only bulk apply needs to know of failures.
  const safeActions = useMemo<StageActions | null>(() => {
    if (!actions) return null;
    const calm = <A extends unknown[]>(action: (...args: A) => void | Promise<void>) => (...args: A) => {
      void Promise.resolve(action(...args)).catch(() => undefined);
    };
    return {
      updateItem: calm(actions.updateItem), bulkQuestion: calm(actions.bulkQuestion), deleteItem: calm(actions.deleteItem),
      restoreItem: calm(actions.restoreItem), mergeItem: calm(actions.mergeItem), simplify: calm(actions.simplify),
      setTarget: calm(actions.setTarget), repeat: calm(actions.repeat),
    };
  }, [actions]);

  if (error) return <PageSection title={tr('Streamline review')} subtitle=""><ErrorMessage message={error} onRetry={() => void load()} /></PageSection>;
  if (!review || !actions || !safeActions || !token) return <PageSection title={tr('Streamline review')} subtitle=""><Loading /></PageSection>;

  const names = STAGE_NAMES();
  const states = STATE_LABELS();
  const stage = viewStage ?? (review.status === 'OPEN' ? review.currentStage : 5);
  const isCurrent = review.status === 'OPEN' && stage === review.currentStage;
  const stageProps = { review, readOnly: readOnly || !review.stages[stage - 1].unlocked, actions: safeActions, partners };
  const anchor = review.anchorType === 'GOAL' ? tr('Goal') : tr('Step');

  const page = (
    <PageSection
      title={tr('Streamline: {title}', { title: review.anchorTitle })}
      subtitle={tr('{code} · {anchor} {anchorCode} · {status}', {
        code: review.code,
        anchor,
        anchorCode: review.anchorCode,
        status: review.status === 'OPEN' ? tr('Stage {stage} of 5', { stage: review.currentStage }) : review.status === 'COMPLETED' ? tr('Completed') : tr('Abandoned'),
      })}
      actions={
        <Box sx={{ display: 'flex', gap: 1 }}>
          <MuiButton size="small" variant="outlined" component={Link} to={coach ? `/coach/participants/${participantId}` : '/streamline'}>{tr('All reviews')}</MuiButton>
          {!coach && review.status === 'OPEN' && <Button variant="secondary" onClick={() => setAbandonOpen(true)}>{tr('Abandon')}</Button>}
          {/* V30 (D-30-10): ask the AI about this review. */}
          {!coach && <PageHelpButton />}
        </Box>
      }
    >
      {coach && <Alert severity="info">{tr("You are viewing a participant's review. Coaches can read it but not change it.")}</Alert>}

      <Box sx={{ ...surface('card'), overflowX: 'auto' }}>
        <Stepper nonLinear activeStep={stage - 1} alternativeLabel aria-label={tr('Stages')}>
          {review.stages.map((item) => (
            <Step key={item.number} completed={item.state !== 'PENDING'} disabled={!item.unlocked}>
              <StepButton
                onClick={() => item.unlocked && setViewStage(item.number)}
                icon={item.unlocked ? undefined : <Lock size={18} aria-hidden="true" />}
                // WCAG 2.5.3: the visible name, stage state included, is the accessible name.
                optional={<Typography variant="caption" sx={{ color: 'var(--text-secondary)' }}>{item.unlocked ? states[item.state] : tr('Locked')}</Typography>}
              >
                {names[item.number - 1]}
              </StepButton>
            </Step>
          ))}
        </Stepper>
      </Box>

      {review.status !== 'OPEN' && <SummaryCard review={review} token={token} advice={!coach && review.status === 'COMPLETED'} />}

      <Box component="section" aria-labelledby="stage-title" sx={{ ...surface('card'), display: 'grid', gap: 2 }}>
        <Box sx={{ display: 'grid', gap: 0.5 }}>
          <Typography id="stage-title" variant="h2">{tr('Stage {number}: {name}', { number: stage, name: names[stage - 1] })}</Typography>
          <Typography variant="body1">{STAGE_INTROS()[stage - 1]}</Typography>
          <Box component="ul" sx={{ m: 0, pl: 2.5, color: 'var(--text-secondary)' }}>
            {STAGE_PROMPTS()[stage - 1].map((prompt) => <Typography component="li" variant="body2" key={prompt}>{prompt}</Typography>)}
          </Box>
          {review.stages[stage - 1].state === 'NOT_NEEDED' && (
            <Alert severity="info">{tr('Marked not needed: {reason}', { reason: review.stages[stage - 1].reason ?? '' })}</Alert>
          )}
        </Box>

        {isCurrent && !readOnly && <StreamlineSuggestionsPanel token={token} review={review} stage={stage} actions={actions} onReview={setReview} />}
        {actionError && <Alert severity="error" onClose={() => setActionError('')}>{actionError}</Alert>}

        {stage === 1 && <QuestionStage {...stageProps} />}
        {stage === 2 && <DeleteStage {...stageProps} />}
        {stage === 3 && <SimplifyStage {...stageProps} />}
        {stage === 4 && <SpeedStage {...stageProps} />}
        {stage === 5 && <AutomateStage {...stageProps} />}

        {isCurrent && !readOnly && (
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', borderTop: '1px solid var(--divider)', pt: 2 }}>
            <Button onClick={() => void run(() => completeStreamlineStage(token, review.id, stage), () => setViewStage(null))}>
              {stage === 5 ? tr('Finish the review') : tr('Done, next stage')}
            </Button>
            <Button variant="secondary" onClick={() => { setSkipReason(''); setSkipOpen(true); }}>{tr('Not needed…')}</Button>
          </Box>
        )}
      </Box>

      {skipOpen && (
      <Modal onClose={() => setSkipOpen(false)} title={tr('Why is this stage not needed?')}>
        <Box component="form" sx={{ display: 'grid', gap: 2 }} onSubmit={(event) => {
          event.preventDefault();
          setSkipOpen(false);
          void run(() => skipStreamlineStage(token, review.id, stage, skipReason), () => setViewStage(null));
        }}>
          <TextField multiline minRows={2} label={tr('Reason (at least 10 characters)')} value={skipReason} onChange={(event) => setSkipReason(event.target.value)} autoFocus />
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button type="submit" disabled={skipReason.trim().length < 10}>{tr('Mark not needed')}</Button>
            <Button variant="secondary" onClick={() => setSkipOpen(false)}>{tr('Cancel')}</Button>
          </Box>
        </Box>
      </Modal>
      )}

      {confirmEmpty !== null && (
      <ConfirmDialog
        title={review.anchorType === 'GOAL' ? tr('Leave the goal with no open steps?') : tr('Leave the step with no open tasks?')}
        message={tr('Confirm only if it is finished or no longer needed. You can bring items back afterwards.')}
        confirmLabel={tr('Yes, it is finished or not needed')}
        onClose={() => setConfirmEmpty(null)}
        danger
        onConfirm={() => {
          const next = confirmEmpty;
          setConfirmEmpty(null);
          if (next) void run(next);
        }}
      />
      )}
      {abandonOpen && (
      <ConfirmDialog
        title={tr('Abandon this review?')}
        message={tr('What you already changed stays changed. The review stops at stage {stage}.', { stage: review.currentStage })}
        confirmLabel={tr('Abandon')}
        onClose={() => setAbandonOpen(false)}
        onConfirm={() => { setAbandonOpen(false); void run(() => abandonStreamlineReview(token, review.id)); }}
      />
      )}
    </PageSection>
  );
  // D-30-11: a coach's read-only view has no AI controls.
  return coach ? page : <PageAssistantProvider page="STREAMLINE" itemId={review.id}>{page}</PageAssistantProvider>;
}

/** BRD 27 §2.4: cut / added back / simpler / faster / automated. Counts, never percentages (Q-27-7). */
function SummaryCard({ review, token, advice }: { review: StreamlineReview; token: string; advice: boolean }) {
  const { summary } = review;
  const cutCount = summary.deleted + summary.merged;
  return (
    <Box component="section" aria-label={tr('Summary')} sx={{ ...surface('card'), display: 'grid', gap: 1.5 }}>
      <Typography variant="h2">{tr('Summary')}</Typography>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' }, gap: 1 }}>
        <Stat label={tr('Cut')} value={tr('{deleted} deleted · {merged} merged · {kept} kept', { deleted: summary.deleted, merged: summary.merged, kept: summary.kept })} />
        <Stat label={tr('Brought back')} value={tr('{count} of {cut}', { count: summary.addedBack, cut: cutCount })} />
        <Stat label={tr('Faster')} value={tr('{before} → target {target} · now {now}', { before: days(summary.cycleBefore), target: days(summary.cycleTarget), now: days(summary.cycleNow) })} />
        <Stat label={tr('Automated')} value={tr('{repeat} repeating · {remind} reminders · {delegate} delegated', { repeat: summary.repeating, remind: summary.reminders, delegate: summary.delegated })} />
      </Box>
      <BeforeAfter review={review} />
      <Typography variant="caption" sx={{ color: 'var(--text-secondary)' }}>
        {tr('Estimated hours: {before} → {after}. Items cut here can be restored for 90 days and still count as brought back.', { before: hours(summary.hoursBefore), after: hours(summary.hoursAfter) })}
      </Typography>
      {summary.nothingBroughtBack && (
        <Alert severity="info" data-testid="add-back-hint">{tr('You brought nothing back. You may be able to cut more next time.')}</Alert>
      )}
      {advice && <SummaryAdvice token={token} reviewId={review.id} />}
    </Box>
  );
}

/** V30 (D-30-9, SA-FR-07): 2 to 4 points on what to do next. Advice only. */
function SummaryAdvice({ token, reviewId }: { token: string; reviewId: number }) {
  const [points, setPoints] = useState<StreamlineAdvice['points'] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function ask() {
    setBusy(true);
    setError('');
    try {
      setPoints((await adviseStreamlineSummary(token, reviewId, currentLanguage())).points);
    } catch (adviceError) {
      setError(adviceError instanceof Error ? tr(adviceError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Box sx={{ ...surface('inset'), p: 1.5, display: 'grid', gap: 1 }} role="region" aria-label={tr('AI advice')}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
        <Typography variant="subtitle2">{tr('AI advice')}</Typography>
        <Button variant="secondary" disabled={busy} onClick={() => void ask()}>
          {busy ? tr('Thinking…') : points ? tr('Ask again') : tr('Get AI advice')}
        </Button>
      </Box>
      {error && <Alert severity="warning">{error}</Alert>}
      {points && points.length === 0 && <Typography variant="body2">{tr('No advice this time.')}</Typography>}
      {points && points.length > 0 && (
        <Box component="ul" sx={{ m: 0, pl: 2.5, display: 'grid', gap: 0.5 }}>
          {points.map((point, index) => (
            <li key={index}>
              <Typography variant="body2">{point.itemCode && <strong>{point.itemCode}: </strong>}{point.text}</Typography>
            </li>
          ))}
        </Box>
      )}
    </Box>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <Box sx={{ ...surface('inset'), p: 1.25 }}>
      <Typography variant="caption" sx={{ color: 'var(--text-secondary)', display: 'block' }}>{label}</Typography>
      <Typography variant="body2" sx={{ fontWeight: 600 }}>{value}</Typography>
    </Box>
  );
}
