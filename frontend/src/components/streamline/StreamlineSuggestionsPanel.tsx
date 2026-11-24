import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import { getAiStatus } from '../../api/aiApi';
import { suggestStreamline } from '../../api/streamlineApi';
import { currentLanguage, tr } from '../../i18n/translate';
import { surface } from '../../design/surface';
import type { AiStatus } from '../../types/vision';
import type { StreamlineReview, StreamlineSuggestion } from '../../types/streamline';
import { Button } from '../common/Button';
import type { StageActions } from './StreamlineStages';
import { CADENCE_LABELS, LEVER_LABELS, VERDICT_LABELS } from './streamlineLabels';

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

/**
 * SR-FR-14: AI suggestions for the current stage. Suggestions only (D-27-9):
 * each card changes nothing until the user applies it, through the same
 * actions as doing it by hand.
 */
export function StreamlineSuggestionsPanel({ token, review, stage, actions }: { token: string; review: StreamlineReview; stage: number; actions: StageActions }) {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [suggestions, setSuggestions] = useState<StreamlineSuggestion[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    getAiStatus(token).then((value) => !cancelled && setStatus(value)).catch(() => !cancelled && setStatus(null));
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => setSuggestions(null), [stage]);

  if (status && !status.available) {
    return <Alert severity="info" data-testid="ai-unavailable">{aiUnavailableMessage(status.reason)}</Alert>;
  }

  async function ask() {
    setBusy(true);
    setError('');
    try {
      const answer = await suggestStreamline(token, review.id, stage, currentLanguage());
      setSuggestions(answer.suggestions);
    } catch (askError) {
      setError(askError instanceof Error ? tr(askError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  function apply(suggestion: StreamlineSuggestion) {
    const item = review.items.find((candidate) => candidate.id === suggestion.itemId);
    if (!item) return;
    switch (suggestion.action) {
      case 'VERDICT': if (suggestion.verdict) actions.updateItem(item.id, { verdict: suggestion.verdict }); break;
      case 'DELETE': actions.deleteItem(item); break;
      case 'KEEP': actions.updateItem(item.id, { keep: true }); break;
      case 'MERGE': if (suggestion.targetItemId) actions.mergeItem(item, suggestion.targetItemId); break;
      case 'SIMPLIFY': actions.simplify(item, { note: suggestion.detail }); break;
      case 'LEVER': if (suggestion.lever && !item.speedLevers.includes(suggestion.lever)) actions.updateItem(item.id, { speedLevers: [...item.speedLevers, suggestion.lever] }); break;
      case 'REPEAT': if (suggestion.cadence) actions.repeat(item, suggestion.cadence); break;
      case 'DELEGATE': actions.updateItem(item.id, { automation: 'DELEGATE' }); break;
      case 'REMIND': actions.updateItem(item.id, { automation: 'REMIND' }); break;
      default: break;
    }
    dismiss(suggestion);
  }

  function dismiss(suggestion: StreamlineSuggestion) {
    setSuggestions((current) => (current ?? []).filter((value) => value !== suggestion));
  }

  return (
    <Box sx={{ ...surface('inset'), p: 1.5, display: 'grid', gap: 1 }} role="region" aria-label={tr('AI suggestions')}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
        <Typography variant="subtitle2">{tr('AI suggestions')}</Typography>
        <Button variant="secondary" onClick={() => void ask()} disabled={busy}>{busy ? tr('Thinking…') : tr('Suggest for this stage')}</Button>
      </Box>
      <Typography variant="caption" sx={{ color: 'var(--text-secondary)' }}>
        {tr('Suggestions only. Nothing changes until you apply one.')}
      </Typography>
      {error && <Alert severity="warning">{error}</Alert>}
      {suggestions && suggestions.length === 0 && <Typography variant="body2">{tr('No suggestions for this stage.')}</Typography>}
      {suggestions?.map((suggestion, index) => (
        <Box key={`${suggestion.itemId}-${index}`} sx={{ ...surface('card', { flush: true }), p: 1.25, display: 'grid', gap: 0.5 }} data-testid="ai-suggestion">
          <Typography variant="body2" sx={{ fontWeight: 600 }}>{suggestion.code} · {actionLabel(suggestion)}</Typography>
          {suggestion.detail && <Typography variant="body2">{suggestion.detail}</Typography>}
          {suggestion.reason && <Typography variant="caption" sx={{ color: 'var(--text-secondary)' }}>{suggestion.reason}</Typography>}
          <Box sx={{ display: 'flex', gap: 1 }}>
            {suggestion.action !== 'QUESTION' && <Button onClick={() => apply(suggestion)}>{tr('Apply')}</Button>}
            <Button variant="secondary" onClick={() => dismiss(suggestion)}>{suggestion.action === 'QUESTION' ? tr('Got it') : tr('Dismiss')}</Button>
          </Box>
        </Box>
      ))}
    </Box>
  );
}
