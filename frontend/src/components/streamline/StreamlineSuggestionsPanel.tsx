import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Checkbox from '@mui/material/Checkbox';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import { useCallback, useEffect, useState } from 'react';
import { getAiStatus } from '../../api/aiApi';
import { applyStreamlineBatch, dismissStreamlineSuggestion, draftAllStreamline, getSavedStreamlineSuggestions, getStreamlineDrafts, suggestStreamline, undoStreamlineBatch } from '../../api/streamlineApi';
import { formatDate } from '../../i18n/formatDate';
import { currentLanguage, tr } from '../../i18n/translate';
import { surface } from '../../design/surface';
import type { AiStatus } from '../../types/vision';
import type { RequirementSource, SpeedLever, StreamlineBatchAction, StreamlineDrafts, StreamlineReview, StreamlineSuggestion, StreamlineSuggestions, StreamlineVerdict } from '../../types/streamline';
import { Button } from '../common/Button';
import type { StageActions } from './StreamlineStages';
import { StreamlineDraftPreview } from './StreamlineDraftPreview';
import { StreamlineRecentChanges } from './StreamlineRecentChanges';
import { CADENCE_LABELS, LEVER_LABELS, SOURCE_LABELS, VERDICT_LABELS } from './streamlineLabels';

/** D-27-9: why suggestions are missing, in plain words. */
export function aiUnavailableMessage(reason: AiStatus['reason']): string {
  switch (reason) {
    case 'NO_CONSENT':
      return tr('AI suggestions need your AI consent. You can turn it on in Account settings. The review works fully without it.');
    case 'DAILY_LIMIT':
      return tr("You have used today's AI requests. Suggestions come back tomorrow; the review works fully without them.");
    case 'BUDGET_REACHED':
      return tr("This month's AI budget is used up. Suggestions come back next month; the review works fully without them.");
    default:
      return tr('AI suggestions are switched off for now. The review works fully without them.');
  }
}

export function actionLabel(suggestion: StreamlineSuggestion): string {
  switch (suggestion.action) {
    case 'QUESTION': return tr('Ask yourself');
    case 'VERDICT': return tr('Mark as: {verdict}', { verdict: suggestion.verdict ? VERDICT_LABELS()[suggestion.verdict] : '' });
    case 'ANSWER': return tr('Draft answer');
    case 'DELETE': return tr('Delete');
    case 'KEEP': return tr('Keep');
    case 'MERGE': return tr('Merge');
    case 'SIMPLIFY': return tr('Simplify');
    case 'LEVER': return suggestion.lever ? LEVER_LABELS()[suggestion.lever] : tr('Speed up');
    case 'REPEAT': return tr('Repeat: {cadence}', { cadence: suggestion.cadence ? CADENCE_LABELS()[suggestion.cadence] : '' });
    case 'DELEGATE': return tr('Delegate');
    case 'REMIND': return tr('Remind me');
    default: return suggestion.action;
  }
}

/** What the user may change on a card before applying it (D-30-7, D-30-8). */
type Edits = {
  requirementSource?: RequirementSource | null;
  requirementOwner?: string;
  whyNeeded?: string;
  verdict?: StreamlineVerdict | null;
  newTitle?: string;
};

type Card = StreamlineSuggestion & { key: number; edits: Edits; picked: boolean };

/** D-30-5: suggestions load by themselves once per review and stage in this browser session. */
function sessionKey(reviewId: number, stage: number) {
  return `vms-streamline-suggested:${reviewId}:${stage}`;
}

function alreadyAsked(reviewId: number, stage: number) {
  try {
    return sessionStorage.getItem(sessionKey(reviewId, stage)) === '1';
  } catch {
    return false;
  }
}

function rememberAsked(reviewId: number, stage: number) {
  try {
    sessionStorage.setItem(sessionKey(reviewId, stage), '1');
  } catch {
    // A private window may refuse; the panel then asks again next time, which is harmless.
  }
}

const applicable = (card: Card) => card.action !== 'QUESTION';

/**
 * V32 D-32-4: a card as a batch action, the same change "Apply" makes on its
 * own. Levers already chosen are left out; several levers for one item add up.
 */
export function toBatchAction(card: StreamlineSuggestion & { edits?: Edits }, review: StreamlineReview,
  levers: Map<number, SpeedLever[]>): StreamlineBatchAction | null {
  const item = review.items.find((candidate) => candidate.id === card.itemId);
  if (!item) return null;
  const edits = card.edits ?? {};
  const itemId = item.id;
  switch (card.action) {
    case 'VERDICT': return card.verdict ? { type: 'UPDATE', itemId, update: { verdict: card.verdict } } : null;
    case 'ANSWER': return { type: 'UPDATE', itemId, update: {
      requirementSource: (edits.requirementSource ?? card.requirementSource) ?? undefined,
      requirementOwner: (edits.requirementOwner ?? card.requirementOwner ?? '').trim() || undefined,
      whyNeeded: (edits.whyNeeded ?? card.whyNeeded ?? '').trim() || undefined,
      verdict: (edits.verdict ?? card.verdict) ?? undefined,
    } };
    case 'DELETE': return { type: 'DELETE', itemId };
    case 'KEEP': return { type: 'UPDATE', itemId, update: { keep: true } };
    case 'MERGE': return card.targetItemId ? { type: 'MERGE', itemId, targetItemId: card.targetItemId } : null;
    case 'SIMPLIFY': {
      const title = (edits.newTitle ?? card.newTitle ?? '').trim();
      return { type: 'SIMPLIFY', itemId, simplify: {
        ...(title ? { title } : {}),
        ...(card.newDescription ? { description: card.newDescription } : {}),
        note: card.detail || card.reason,
      } };
    }
    case 'LEVER': {
      const chosen = levers.get(itemId) ?? item.speedLevers;
      if (!card.lever || chosen.includes(card.lever)) return null;
      const next = [...chosen, card.lever];
      levers.set(itemId, next);
      return { type: 'UPDATE', itemId, update: { speedLevers: next } };
    }
    case 'REPEAT': return card.cadence ? { type: 'REPEAT', itemId, cadence: card.cadence } : null;
    case 'DELEGATE': return { type: 'UPDATE', itemId, update: { automation: 'DELEGATE' } };
    case 'REMIND': return { type: 'UPDATE', itemId, update: { automation: 'REMIND' } };
    default: return null;
  }
}

function toCards(answer: StreamlineSuggestions): Card[] {
  return answer.suggestions.map((suggestion, key) => ({
    ...suggestion,
    key,
    picked: false,
    edits: {
      requirementSource: suggestion.requirementSource ?? null,
      requirementOwner: suggestion.requirementOwner ?? '',
      whyNeeded: suggestion.whyNeeded ?? '',
      verdict: suggestion.verdict ?? null,
      newTitle: suggestion.newTitle ?? '',
    },
  }));
}

/**
 * SR-FR-14: AI suggestions for the current stage. Suggestions only (D-27-9):
 * each card changes nothing until the user applies it, through the same
 * actions as doing it by hand. V30: they load by themselves (D-30-5), several
 * can be applied at once (D-30-6), and stage 1 drafts and Simplify titles can
 * be edited on the card before applying (D-30-7, D-30-8). V32: the last
 * suggestions are saved on the server and shown again at once, even without
 * AI (D-32-1, SF-FR-02); auto-suggest asks only when none still fit. Apply
 * selected and Apply all run as one batch on the server that can be undone as
 * a whole (D-32-4, D-32-5); a single Apply works as before (D-32-6).
 */
export function StreamlineSuggestionsPanel({ token, review, stage, actions, onReview }: {
  token: string;
  review: StreamlineReview;
  stage: number;
  actions: StageActions;
  /** V32: shows the review as a batch or an undo left it. */
  onReview: (review: StreamlineReview) => void;
}) {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [cards, setCards] = useState<Card[] | null>(null);
  const [asked, setAsked] = useState(() => alreadyAsked(review.id, stage));
  const [applying, setApplying] = useState<{ done: number; total: number } | null>(null);
  const [notice, setNotice] = useState('');
  // V32 D-32-1: null while the saved set is being looked up; then when it was saved, or '' for none.
  const [savedAt, setSavedAt] = useState<string | null>(null);
  // V32: the batch just applied, offered for Undo; reloadKey reloads Recent changes.
  const [lastBatch, setLastBatch] = useState<number | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  // V32 phase 3: drafts for later stages, whether Draft all stages can run, and the current cards' early-draft label.
  const [drafts, setDrafts] = useState<StreamlineDrafts | null>(null);
  const [drafting, setDrafting] = useState(false);
  const [early, setEarly] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getAiStatus(token).then((value) => !cancelled && setStatus(value)).catch(() => !cancelled && setStatus(null));
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    setNotice('');
    setLastBatch(null);
  }, [review.id, stage]);

  useEffect(() => {
    let cancelled = false;
    setCards(null);
    setSavedAt(null);
    setAsked(alreadyAsked(review.id, stage));
    getSavedStreamlineSuggestions(token, review.id, stage)
      .then((saved) => {
        if (cancelled) return;
        if (saved) {
          setCards(toCards(saved));
          setEarly(Boolean(saved.draftedBeforeChanges));
          setAsked(true);
        }
        setSavedAt(saved?.savedAt ?? '');
      })
      .catch(() => !cancelled && setSavedAt(''));
    return () => {
      cancelled = true;
    };
  }, [token, review.id, stage]);

  const loadDrafts = useCallback(() => {
    getStreamlineDrafts(token, review.id).then((value) => setDrafts(value ?? null)).catch(() => setDrafts(null));
  }, [token, review.id]);

  useEffect(() => {
    setEarly(false);
    loadDrafts();
  }, [loadDrafts, stage]);

  const ask = useCallback(async () => {
    setBusy(true);
    setError('');
    setNotice('');
    rememberAsked(review.id, stage);
    setAsked(true);
    try {
      const answer = await suggestStreamline(token, review.id, stage, currentLanguage());
      setCards(toCards(answer));
      setSavedAt('');
      setEarly(false);
    } catch (askError) {
      setError(askError instanceof Error ? tr(askError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }, [token, review.id, stage]);

  // D-30-5: once AI is known to be available, ask by ourselves the first time this stage opens in the session,
  // unless saved suggestions still fit (D-32-1).
  useEffect(() => {
    if (status?.available && savedAt !== null && !asked && !busy && cards === null) {
      void ask();
    }
  }, [status, savedAt, asked, busy, cards, ask]);

  const aiOff = status !== null && !status.available;
  // SF-FR-02: saved cards can still be applied without AI; with none, only the reason shows.
  if (aiOff && !(cards && cards.length > 0)) {
    return <Alert severity="info" data-testid="ai-unavailable">{aiUnavailableMessage(status.reason)}</Alert>;
  }

  async function apply(card: Card) {
    const item = review.items.find((candidate) => candidate.id === card.itemId);
    if (!item) return;
    const edits = card.edits;
    switch (card.action) {
      case 'VERDICT': if (card.verdict) await actions.updateItem(item.id, { verdict: card.verdict }); break;
      case 'ANSWER': await actions.updateItem(item.id, {
        requirementSource: edits.requirementSource ?? undefined,
        requirementOwner: edits.requirementOwner?.trim() || undefined,
        whyNeeded: edits.whyNeeded?.trim() || undefined,
        verdict: edits.verdict ?? undefined,
      }); break;
      case 'DELETE': await actions.deleteItem(item); break;
      case 'KEEP': await actions.updateItem(item.id, { keep: true }); break;
      case 'MERGE': if (card.targetItemId) await actions.mergeItem(item, card.targetItemId); break;
      case 'SIMPLIFY': {
        // D-30-8: the (edited) new title and smaller scope change the item; the detail is kept as the note.
        const title = edits.newTitle?.trim();
        await actions.simplify(item, {
          ...(title ? { title } : {}),
          ...(card.newDescription ? { description: card.newDescription } : {}),
          note: card.detail || card.reason,
        });
        break;
      }
      case 'LEVER': if (card.lever && !item.speedLevers.includes(card.lever)) await actions.updateItem(item.id, { speedLevers: [...item.speedLevers, card.lever] }); break;
      case 'REPEAT': if (card.cadence) await actions.repeat(item, card.cadence); break;
      case 'DELEGATE': await actions.updateItem(item.id, { automation: 'DELEGATE' }); break;
      case 'REMIND': await actions.updateItem(item.id, { automation: 'REMIND' }); break;
      default: break;
    }
  }

  async function applyOne(card: Card) {
    setNotice('');
    try {
      await apply(card);
      dismiss(card);
    } catch {
      // The page shows what went wrong; the card stays so it can be tried again.
    }
  }

  // D-30-6, D-32-4: one batch on the server, in the order shown; a failure stops the run and can be undone with the rest.
  async function applyMany(chosen: Card[]) {
    setNotice('');
    setError('');
    setLastBatch(null);
    const levers = new Map<number, SpeedLever[]>();
    const planned = chosen.map((card) => ({ card, action: toBatchAction(card, review, levers) }));
    const runnable = planned.filter((entry): entry is { card: Card; action: StreamlineBatchAction } => entry.action !== null);
    // A lever already chosen has nothing left to do.
    planned.filter((entry) => entry.action === null).forEach((entry) => dismiss(entry.card));
    if (runnable.length === 0) return;
    setApplying({ done: 0, total: runnable.length });
    try {
      const result = await applyStreamlineBatch(token, review.id, stage, runnable.map((entry) => entry.action));
      onReview(result.review);
      runnable.slice(0, result.applied).forEach((entry) => dismiss(entry.card));
      setLastBatch(result.batchId);
      setNotice(result.failedAtPosition === null
        ? tr('Applied {count} suggestion(s).', { count: result.applied })
        : tr('Applied {done} of {total}. The rest were not applied.', { done: result.applied, total: result.total })
          + (result.error ? ` ${tr(result.error)}` : ''));
    } catch (applyError) {
      setError(applyError instanceof Error ? tr(applyError.message) : tr('That did not work. Try again.'));
    } finally {
      setApplying(null);
      setReloadKey((key) => key + 1);
    }
  }

  // D-32-7: one request for this stage and every later one; later stages only become a preview.
  async function draftAll() {
    setDrafting(true);
    setError('');
    setNotice('');
    rememberAsked(review.id, stage);
    setAsked(true);
    try {
      const answer = await draftAllStreamline(token, review.id, currentLanguage());
      const current = answer.stages.find((entry) => entry.stage === stage);
      setCards(current ? toCards(current) : []);
      setSavedAt('');
      setEarly(false);
      loadDrafts();
    } catch (draftError) {
      setError(draftError instanceof Error ? tr(draftError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setDrafting(false);
    }
  }

  // D-32-5: the whole batch goes back; items changed since are left as they are (BR-32-6).
  async function undo(batchId: number) {
    setError('');
    setApplying({ done: 0, total: 0 });
    try {
      const result = await undoStreamlineBatch(token, review.id, batchId);
      onReview(result.review);
      setLastBatch(null);
      // The undone cards are open again, so the saved set shows them once more (D-32-1).
      const saved = await getSavedStreamlineSuggestions(token, review.id, stage).catch(() => null);
      if (saved) setCards(toCards(saved));
      const skipped = result.skipped.map((entry) => entry.code).join(', ');
      setNotice(tr('Undid {count} change(s).', { count: result.undone })
        + (skipped ? ` ${tr('Changed since, so left as they are: {codes}.', { codes: skipped })}` : ''));
    } catch (undoError) {
      setError(undoError instanceof Error ? tr(undoError.message) : tr('That did not work. Try again.'));
    } finally {
      setApplying(null);
      setReloadKey((key) => key + 1);
    }
  }

  function dismiss(card: Card) {
    setCards((current) => (current ?? []).filter((value) => value.key !== card.key));
  }

  // D-32-1: a card the user turns down is also removed from the saved set. Applied cards drop out by themselves.
  function turnDown(card: Card) {
    dismiss(card);
    dismissStreamlineSuggestion(token, review.id, stage, card).catch(() => undefined);
  }

  function update(card: Card, patch: Partial<Card>) {
    setCards((current) => (current ?? []).map((value) => (value.key === card.key ? { ...value, ...patch } : value)));
  }

  function edit(card: Card, patch: Edits) {
    update(card, { edits: { ...card.edits, ...patch } });
  }

  const applicableCards = (cards ?? []).filter(applicable);
  const picked = applicableCards.filter((card) => card.picked);
  const working = busy || drafting || applying !== null;
  // D-32-8: offered while there are later stages; disabled, with the reason, when the budget has no room.
  const showDraftAll = stage < 5 && drafts !== null;
  const draftAllReason = drafts && !drafts.canDraftAll && drafts.unavailableReason && drafts.unavailableReason !== 'AI_UNAVAILABLE'
    ? drafts.unavailableReason : '';

  return (
    <Box sx={{ ...surface('inset'), p: 1.5, display: 'grid', gap: 1 }} role="region" aria-label={tr('AI suggestions')}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
        <Typography variant="subtitle2">{tr('AI suggestions')}</Typography>
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          <Button variant="secondary" onClick={() => void ask()} disabled={working || aiOff}>
            {busy ? tr('Thinking…') : asked ? tr('Suggest again') : tr('Suggest for this stage')}
          </Button>
          {showDraftAll && (
            <Button variant="secondary" onClick={() => void draftAll()} disabled={working || aiOff || !drafts?.canDraftAll}>
              {drafting ? tr('Drafting stages {from} to 5…', { from: stage }) : tr('Draft all stages')}
            </Button>
          )}
        </Box>
      </Box>
      <Typography variant="caption" sx={{ color: 'var(--text-secondary)' }}>
        {tr('Suggestions only. Nothing changes until you apply one.')}
      </Typography>
      {savedAt && (
        <Typography variant="caption" sx={{ color: 'var(--text-secondary)' }} data-testid="suggested-on">
          {tr('Suggested on {date}.', { date: formatDate(new Date(savedAt), { year: true }) })}
        </Typography>
      )}
      {showDraftAll && draftAllReason && !aiOff && (
        <Typography variant="caption" sx={{ color: 'var(--text-secondary)' }} data-testid="draft-all-reason">{tr(draftAllReason)}</Typography>
      )}
      {early && cards && cards.length > 0 && (
        <Typography variant="caption" sx={{ color: 'var(--text-secondary)' }} data-testid="drafted-early">
          {tr('Drafted before you finished the earlier stages. Cards about items you cut since have been left out.')}
        </Typography>
      )}
      {aiOff && <Alert severity="info" data-testid="ai-unavailable">{aiUnavailableMessage(status.reason)} {tr('These saved suggestions can still be applied.')}</Alert>}
      {error && <Alert severity="warning">{error}</Alert>}
      <Box role="status" aria-live="polite">
        {applying && <Typography variant="body2">{applying.total > 0 ? tr('Applying {count} suggestion(s)…', { count: applying.total }) : tr('Undoing…')}</Typography>}
        {notice && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
            <Typography variant="body2">{notice}</Typography>
            {lastBatch !== null && !applying && (
              <Button variant="secondary" onClick={() => void undo(lastBatch)}>{tr('Undo')}</Button>
            )}
          </Box>
        )}
      </Box>
      {drafts && <StreamlineDraftPreview drafts={drafts} currentStage={stage} label={actionLabel} />}
      <StreamlineRecentChanges token={token} reviewId={review.id} stage={stage} refreshKey={reloadKey} disabled={working}
        onUndo={(batchId) => void undo(batchId)} />
      {cards && cards.length === 0 && !notice && <Typography variant="body2">{tr('No suggestions for this stage.')}</Typography>}
      {applicableCards.length > 1 && (
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          <Button disabled={working || picked.length === 0} onClick={() => void applyMany(picked)}>
            {tr('Apply selected ({count})', { count: picked.length })}
          </Button>
          <Button variant="secondary" disabled={working} onClick={() => void applyMany(applicableCards)}>{tr('Apply all')}</Button>
        </Box>
      )}
      {cards?.map((card) => (
        <Box key={card.key} sx={{ ...surface('card', { flush: true }), p: 1.25, display: 'grid', gap: 0.75 }} data-testid="ai-suggestion">
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            {applicable(card) && (
              <Checkbox
                size="small"
                checked={card.picked}
                disabled={working}
                onChange={(event) => update(card, { picked: event.target.checked })}
                slotProps={{ input: { 'aria-label': tr('Select {code} {action}', { code: card.code, action: actionLabel(card) }) } }}
                sx={{ p: 0.5 }}
              />
            )}
            <Typography variant="body2" sx={{ fontWeight: 600 }}>{card.code} · {actionLabel(card)}</Typography>
          </Box>
          {card.action === 'ANSWER' && <AnswerFields card={card} disabled={working} onChange={(patch) => edit(card, patch)} />}
          {card.action === 'SIMPLIFY' && card.newTitle && (
            <TextField size="small" label={tr('New title')} value={card.edits.newTitle ?? ''} disabled={working}
              onChange={(event) => edit(card, { newTitle: event.target.value.slice(0, 220) })} />
          )}
          {card.action === 'SIMPLIFY' && card.newDescription && <Typography variant="body2">{card.newDescription}</Typography>}
          {card.detail && <Typography variant="body2">{card.detail}</Typography>}
          {card.reason && <Typography variant="caption" sx={{ color: 'var(--text-secondary)' }}>{card.reason}</Typography>}
          <Box sx={{ display: 'flex', gap: 1 }}>
            {applicable(card) && <Button disabled={working} onClick={() => void applyOne(card)}>{tr('Apply')}</Button>}
            <Button variant="secondary" disabled={working} onClick={() => turnDown(card)}>{card.action === 'QUESTION' ? tr('Got it') : tr('Dismiss')}</Button>
          </Box>
        </Box>
      ))}
    </Box>
  );
}

/** D-30-7: the stage 1 draft, editable before it is saved. */
function AnswerFields({ card, disabled, onChange }: { card: Card; disabled: boolean; onChange: (patch: Edits) => void }) {
  const sources = SOURCE_LABELS();
  const verdicts = VERDICT_LABELS();
  return (
    <Box sx={{ display: 'grid', gap: 1, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
      <TextField select size="small" label={tr('Who asked for it')} value={card.edits.requirementSource ?? ''} disabled={disabled}
        onChange={(event) => onChange({ requirementSource: event.target.value as RequirementSource })}>
        {(Object.keys(sources) as RequirementSource[]).map((source) => <MenuItem key={source} value={source}>{sources[source]}</MenuItem>)}
      </TextField>
      <TextField size="small" label={tr('Asked for by (a role)')} value={card.edits.requirementOwner ?? ''} disabled={disabled}
        onChange={(event) => onChange({ requirementOwner: event.target.value.slice(0, 120) })} />
      <TextField size="small" label={tr('Why it is needed')} value={card.edits.whyNeeded ?? ''} disabled={disabled}
        onChange={(event) => onChange({ whyNeeded: event.target.value.slice(0, 300) })} />
      <TextField select size="small" label={tr('Verdict')} value={card.edits.verdict ?? ''} disabled={disabled}
        onChange={(event) => onChange({ verdict: event.target.value as StreamlineVerdict })}>
        {(Object.keys(verdicts) as StreamlineVerdict[]).map((verdict) => <MenuItem key={verdict} value={verdict}>{verdicts[verdict]}</MenuItem>)}
      </TextField>
    </Box>
  );
}
