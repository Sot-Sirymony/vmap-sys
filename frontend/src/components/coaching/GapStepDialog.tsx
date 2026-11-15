import { useState, type FormEvent } from 'react';
import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import { createGapStep } from '../../api/stepApi';
import { convertObstacleToStep } from '../../api/obstacleApi';
import { Button } from '../common/Button';
import { ErrorMessage } from '../common/ErrorMessage';
import { Input } from '../common/Input';
import { Modal } from '../common/Modal';
import type { GapStepResult, Goal, Obstacle, ObstacleType, PartnerSupportType, Priority, VisionStep } from '../../types/vision';
import { obstacleTypeLabels, partnerSupportTypeLabels } from '../../utils/enumLabels';
import { GAP_KINDS, gapStepDate, gapStepTitle, partnerByDefault, supportTypeFor } from '../../utils/gapStep';
import { tr } from '../../i18n/translate';

export type GapSource =
  | { kind: 'step'; step: VisionStep }
  /** anchorStep: the live step the obstacle sits on, if any; otherwise the participant picks a goal. */
  | { kind: 'obstacle'; obstacle: Obstacle; anchorStep?: VisionStep };

/**
 * V17 BL-FR-19, BL-FR-20: "Turn this gap into a step". Pre-fills a "find
 * help" step (title, date before the current step, owner) and, for a missing
 * skill, knowledge or money, a To Contact partner. Everything is editable;
 * nothing is saved until the participant presses Save.
 */
export function GapStepDialog({ token, source, goals, ownerName, onClose, onSaved }: {
  token: string;
  source: GapSource;
  goals: Goal[];
  ownerName: string;
  onClose: () => void;
  onSaved: (result: GapStepResult) => void;
}) {
  const fromStep = source.kind === 'step';
  const gapText = fromStep ? source.step.resources ?? '' : source.obstacle.title;
  const initialKind: ObstacleType | '' = fromStep ? source.step.gapKind ?? '' : source.obstacle.obstacleType;
  const beforeStep = fromStep ? source.step : source.anchorStep;
  const needsGoal = !fromStep && !source.anchorStep;

  const [gapKind, setGapKind] = useState<ObstacleType | ''>(initialKind);
  const [title, setTitle] = useState(gapStepTitle(gapText, initialKind || null));
  const [titleEdited, setTitleEdited] = useState(false);
  const [owner, setOwner] = useState(ownerName);
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [targetDate, setTargetDate] = useState(gapStepDate(beforeStep?.targetDate));
  const [goalId, setGoalId] = useState(
    needsGoal ? String(source.obstacle.relatedGoalId ?? goals.find((goal) => goal.dreamId === source.obstacle.relatedDreamId)?.id ?? '') : '',
  );
  const [createPartner, setCreatePartner] = useState(partnerByDefault(initialKind || null));
  const [partnerName, setPartnerName] = useState('');
  const [supportType, setSupportType] = useState<PartnerSupportType>(supportTypeFor(initialKind || null));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  function changeKind(next: ObstacleType | '') {
    setGapKind(next);
    setSupportType(supportTypeFor(next || null));
    setCreatePartner(partnerByDefault(next || null));
    if (!titleEdited) {
      setTitle(gapStepTitle(gapText, next || null));
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (needsGoal && !goalId) {
      setError(tr('Choose the goal this step belongs to.'));
      return;
    }
    setSaving(true);
    setError('');
    const request = {
      goalId: needsGoal ? Number(goalId) : undefined,
      title: title.trim(),
      owner: owner.trim(),
      priority,
      targetDate,
      gapKind,
      createPartner,
      partnerName: createPartner && partnerName.trim() ? partnerName.trim() : undefined,
      partnerSupportType: createPartner ? supportType : undefined,
    };
    try {
      const result = fromStep
        ? await createGapStep(token, source.step.id, request)
        : await convertObstacleToStep(token, source.obstacle.id, request);
      onSaved(result);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : tr('Unable to create the step.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal title={fromStep ? tr('Turn this gap into a step') : tr('Turn into a step')} onClose={onClose}>
      <form className="stack-list" onSubmit={(event) => void handleSubmit(event)}>
        <p className="field-hint">
          {tr('A gap is a signal to find help. This adds a step to find it, placed before the work that needs it.')}
        </p>
        <p className="field-hint"><strong>{tr('Gap:')}</strong> {gapText}</p>
        {needsGoal && (
          <label>
            {tr('Goal')}
            <FormControl fullWidth size="small" required>
              <Select SelectDisplayProps={{ 'aria-label': tr('Goal') }} value={goalId} onChange={(event) => setGoalId(event.target.value)} displayEmpty>
                <MenuItem value="">{tr('Choose a goal')}</MenuItem>
                {goals.filter((goal) => !goal.archived).map((goal) => (
                  <MenuItem value={String(goal.id)} key={goal.id}>{goal.title}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </label>
        )}
        <label>
          {tr('Kind of gap')}
          <FormControl fullWidth size="small">
            <Select SelectDisplayProps={{ 'aria-label': tr('Kind of gap') }} value={gapKind} displayEmpty
              onChange={(event) => changeKind(event.target.value as ObstacleType | '')}>
              <MenuItem value="">{tr('Not sure')}</MenuItem>
              {GAP_KINDS.map((kind) => <MenuItem value={kind} key={kind}>{obstacleTypeLabels[kind]}</MenuItem>)}
            </Select>
          </FormControl>
        </label>
        <label>
          {tr('New step')}
          <Input value={title} maxLength={220} required onChange={(event) => { setTitle(event.target.value); setTitleEdited(true); }} />
        </label>
        <label>
          {tr('Owner')}
          <Input value={owner} maxLength={160} required onChange={(event) => setOwner(event.target.value)} />
        </label>
        <label>
          {tr('Target Date')}
          <Input type="date" value={targetDate} required onChange={(event) => setTargetDate(event.target.value)} />
        </label>
        <label>
          {tr('Priority')}
          <FormControl fullWidth size="small">
            <Select SelectDisplayProps={{ 'aria-label': tr('Priority') }} value={priority} onChange={(event) => setPriority(event.target.value as Priority)}>
              <MenuItem value="LOW">{tr('Low')}</MenuItem>
              <MenuItem value="MEDIUM">{tr('Medium')}</MenuItem>
              <MenuItem value="HIGH">{tr('High')}</MenuItem>
              <MenuItem value="CRITICAL">{tr('Critical')}</MenuItem>
            </Select>
          </FormControl>
        </label>
        <label className="checkbox-field">
          <Checkbox checked={createPartner} onChange={(event) => setCreatePartner(event.target.checked)} />
          {tr('Also add a partner to contact')}
        </label>
        {createPartner && (
          <>
            <label>
              {tr('Partner name (optional)')}
              <Input value={partnerName} maxLength={180} placeholder={tr('Leave blank if you don\'t know who yet')}
                onChange={(event) => setPartnerName(event.target.value)} />
            </label>
            <label>
              {tr('Support type')}
              <FormControl fullWidth size="small">
                <Select SelectDisplayProps={{ 'aria-label': tr('Support type') }} value={supportType}
                  onChange={(event) => setSupportType(event.target.value as PartnerSupportType)}>
                  {Object.entries(partnerSupportTypeLabels).map(([value, label]) => (
                    <MenuItem value={value} key={value}>{label}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </label>
          </>
        )}
        {!fromStep && (
          <p className="field-hint">{tr('The blocker will sit on the new step and move to In Progress.')}</p>
        )}
        {error && <ErrorMessage message={error} />}
        <div className="row-actions">
          <Button type="submit" disabled={saving}>{saving ? tr('Saving...') : tr('Save step')}</Button>
          <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>{tr('Cancel')}</Button>
        </div>
      </form>
    </Modal>
  );
}
