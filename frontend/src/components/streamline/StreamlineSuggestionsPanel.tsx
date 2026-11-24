import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Checkbox from '@mui/material/Checkbox';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import { useCallback, useEffect, useState } from 'react';
import { getAiStatus } from '../../api/aiApi';
import { suggestStreamline } from '../../api/streamlineApi';
import { currentLanguage, tr } from '../../i18n/translate';
import { surface } from '../../design/surface';
import type { AiStatus } from '../../types/vision';
import type { RequirementSource, StreamlineReview, StreamlineSuggestion, StreamlineVerdict } from '../../types/streamline';
import { Button } from '../common/Button';
import type { StageActions } from './StreamlineStages';
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

function actionLabel(suggestion: StreamlineSuggestion): string {
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
 * SR-FR-14: AI suggestions for the current stage. Suggestions only (D-27-9):
 * each card changes nothing until the user applies it, through the same
 * actions as doing it by hand. V30: they load by themselves (D-30-5), several
 * can be applied at once (D-30-6), and stage 1 drafts and Simplify titles can
 * be edited on the card before applying (D-30-7, D-30-8).
 */
export function StreamlineSuggestionsPanel({ token, review, stage, actions }: { token: string; review: StreamlineReview; stage: number; actions: StageActions }) {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [cards, setCards] = useState<Card[] | null>(null);
  const [asked, setAsked] = useState(() => alreadyAsked(review.id, stage));
  const [applying, setApplying] = useState<{ done: number; total: number } | null>(null);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    let cancelled = false;
    getAiStatus(token).then((value) => !cancelled && setStatus(value)).catch(() => !cancelled && setStatus(null));
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    setCards(null);
    setNotice('');
    setAsked(alreadyAsked(review.id, stage));
  }, [review.id, stage]);

  const ask = useCallback(async () => {
    setBusy(true);
    setError('');
    setNotice('');
    rememberAsked(review.id, stage);
    setAsked(true);
    try {
      const answer = await suggestStreamline(token, review.id, stage, currentLanguage());
      setCards(answer.suggestions.map((suggestion, key) => ({
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
      })));
    } catch (askError) {
      setError(askError instanceof Error ? tr(askError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }, [token, review.id, stage]);

  // D-30-5: once AI is known to be available, ask by ourselves the first time this stage opens in the session.
  useEffect(() => {
    if (status?.available && !asked && !busy && cards === null) {
      void ask();
    }
  }, [status, asked, busy, cards, ask]);

  if (status && !status.available) {
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

  // D-30-6: one at a time, in the order shown; a failure stops the run.
  async function applyMany(chosen: Card[]) {
    setNotice('');
    let done = 0;
    setApplying({ done, total: chosen.length });
    for (const card of chosen) {
      try {
        await apply(card);
      } catch {
        break;
      }
      done += 1;
      dismiss(card);
      setApplying({ done, total: chosen.length });
    }
    setApplying(null);
    setNotice(done === chosen.length
      ? tr('Applied {count} suggestion(s).', { count: done })
      : tr('Applied {done} of {total}. The rest were not applied.', { done, total: chosen.length }));
  }

  function dismiss(card: Card) {
    setCards((current) => (current ?? []).filter((value) => value.key !== card.key));
  }

  function update(card: Card, patch: Partial<Card>) {
    setCards((current) => (current ?? []).map((value) => (value.key === card.key ? { ...value, ...patch } : value)));
  }

  function edit(card: Card, patch: Edits) {
    update(card, { edits: { ...card.edits, ...patch } });
  }

  const applicableCards = (cards ?? []).filter(applicable);
  const picked = applicableCards.filter((card) => card.picked);
  const working = busy || applying !== null;

  return (
    <Box sx={{ ...surface('inset'), p: 1.5, display: 'grid', gap: 1 }} role="region" aria-label={tr('AI suggestions')}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
        <Typography variant="subtitle2">{tr('AI suggestions')}</Typography>
        <Button variant="secondary" onClick={() => void ask()} disabled={working}>
          {busy ? tr('Thinking…') : asked ? tr('Suggest again') : tr('Suggest for this stage')}
        </Button>
      </Box>
      <Typography variant="caption" sx={{ color: 'var(--text-secondary)' }}>
        {tr('Suggestions only. Nothing changes until you apply one.')}
      </Typography>
      {error && <Alert severity="warning">{error}</Alert>}
      <Box role="status" aria-live="polite">
        {applying && <Typography variant="body2">{tr('Applying {done} of {total}…', { done: applying.done + 1, total: applying.total })}</Typography>}
        {notice && <Typography variant="body2">{notice}</Typography>}
      </Box>
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
            <Button variant="secondary" disabled={working} onClick={() => dismiss(card)}>{card.action === 'QUESTION' ? tr('Got it') : tr('Dismiss')}</Button>
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
