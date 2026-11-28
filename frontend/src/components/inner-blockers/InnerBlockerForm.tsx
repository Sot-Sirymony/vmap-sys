import { FormEvent, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import FormControl from '@mui/material/FormControl';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Typography from '@mui/material/Typography';
import { createInnerBlocker, updateInnerBlocker } from '../../api/innerBlockerApi';
import { tr } from '../../i18n/translate';
import type { ChainScoreKey, InnerBlocker, InnerBlockerRequest } from '../../types/vision';
import { CHAINS } from '../../utils/chains';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { Textarea } from '../common/Textarea';

/** D-34-2: the field limits, the same as the server's. */
export const BLOCKER_MAX = 120;
export const SHOWS_UP_MAX = 500;
export const PRACTICE_MAX = 300;

/** What the form starts with: an existing blocker to edit, or a prefill (D-34-5). */
export type InnerBlockerDraft = Partial<Pick<InnerBlocker, 'id' | 'blocker' | 'showsUp' | 'practice' | 'chainKey' | 'dreamId'>>;

type Props = {
  token: string;
  initial?: InnerBlockerDraft;
  /** The participant's dreams to choose from. Without it, the blocker is saved for fixedDreamId. */
  dreams?: { id: number; title: string }[];
  /** Stage 3: the draft's dream, set for the participant (D-34-8). */
  fixedDreamId?: number;
  onSaved: (blocker: InnerBlocker) => void;
  onCancel: () => void;
  /** V36 (D-36-5): a save action of its own, such as moving a gap; otherwise the blocker is created or updated. */
  onSubmit?: (request: InnerBlockerRequest) => Promise<InnerBlocker>;
  /** The save button's text, when it is not "Save blocker". */
  submitLabel?: string;
};

/**
 * V34 (§2.3): the one form for adding or editing an inner blocker, used in the
 * Inner blockers stage and on the Assessment page. It names, never diagnoses
 * (D-34-13); the wording is the program's own.
 */
export function InnerBlockerForm({ token, initial, dreams, fixedDreamId, onSaved, onCancel, onSubmit, submitLabel }: Props) {
  const [blocker, setBlocker] = useState(initial?.blocker ?? '');
  const [showsUp, setShowsUp] = useState(initial?.showsUp ?? '');
  const [practice, setPractice] = useState(initial?.practice ?? '');
  const [chainKey, setChainKey] = useState<ChainScoreKey | ''>(initial?.chainKey ?? '');
  const [dreamId, setDreamId] = useState<number | null>(initial?.dreamId ?? fixedDreamId ?? null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const editing = initial?.id !== undefined;
  const ready = blocker.trim().length > 0 && practice.trim().length > 0;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!ready) {
      setError(tr('Write the blocker and a short practice.'));
      return;
    }
    setSaving(true);
    setError('');
    const request: InnerBlockerRequest = { blocker: blocker.trim(), showsUp: showsUp.trim(), practice: practice.trim(), chainKey, dreamId };
    try {
      const saved = onSubmit
        ? await onSubmit(request)
        : editing && initial?.id !== undefined
          ? await updateInnerBlocker(token, initial.id, request)
          : await createInnerBlocker(token, request);
      onSaved(saved);
    } catch (saveError) {
      setError(saveError instanceof Error ? tr(saveError.message) : tr('Unable to save this blocker.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Box component="form" onSubmit={(event) => void handleSubmit(event)} sx={{ display: 'grid', gap: 1.5 }} aria-label={tr('Inner blocker')}>
      <label>
        {tr('Possible blocker')}
        <Input value={blocker} onChange={(event) => setBlocker(event.target.value)} maxLength={BLOCKER_MAX} required autoFocus
          placeholder={tr('For example: Inconsistency')} />
      </label>
      <label htmlFor="inner-blocker-shows-up">
        {tr('How it shows up')}
        <Typography variant="caption" color="text.secondary" component="span" sx={{ display: 'block' }}>
          {tr('What do you see yourself doing when this blocker is at work?')}
        </Typography>
        <Textarea id="inner-blocker-shows-up" value={showsUp} onChange={(event) => setShowsUp(event.target.value)} maxLength={SHOWS_UP_MAX} rows={2} />
      </label>
      <label htmlFor="inner-blocker-practice">
        {tr('Short practice')}
        <Typography variant="caption" color="text.secondary" component="span" sx={{ display: 'block' }}>
          {tr('One small action you can repeat. Make it easy to do on a bad day.')}
        </Typography>
        <Textarea id="inner-blocker-practice" value={practice} onChange={(event) => setPractice(event.target.value)} maxLength={PRACTICE_MAX} rows={2} required />
      </label>
      <label>
        {tr('Linked chain')}
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': tr('Linked chain') }} displayEmpty value={chainKey}
            onChange={(event) => setChainKey(event.target.value as ChainScoreKey | '')}>
            <MenuItem value="">{tr('None')}</MenuItem>
            {CHAINS.map((chain) => <MenuItem value={chain.key} key={chain.key}>{tr(chain.label)}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      {dreams && (
        <label>
          {tr('Dream')}
          <FormControl fullWidth size="small">
            <Select SelectDisplayProps={{ 'aria-label': tr('Dream') }} displayEmpty value={dreamId === null ? '' : String(dreamId)}
              onChange={(event) => setDreamId(event.target.value === '' ? null : Number(event.target.value))}>
              <MenuItem value="">{tr('For all my dreams')}</MenuItem>
              {dreams.map((dream) => <MenuItem value={String(dream.id)} key={dream.id}>{dream.title}</MenuItem>)}
            </Select>
          </FormControl>
        </label>
      )}
      {error && <Alert severity="error">{error}</Alert>}
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        <Button type="submit" disabled={saving}>{saving ? tr('Saving…') : submitLabel ?? (editing ? tr('Save changes') : tr('Save blocker'))}</Button>
        <Button variant="secondary" onClick={onCancel}>{tr('Cancel')}</Button>
      </Box>
    </Box>
  );
}
