import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CheckCircle2, Flag, Sparkles } from 'lucide-react';
import FormControl from '@mui/material/FormControl';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import { createDream } from '../../api/dreamApi';
import { createGoal } from '../../api/goalApi';
import { Button } from '../common/Button';
import { ErrorMessage } from '../common/ErrorMessage';
import { Input } from '../common/Input';
import { Modal } from '../common/Modal';
import { Textarea } from '../common/Textarea';
import type { Dream, DreamType, Priority, VisionArea } from '../../types/vision';

type DreamWizardProps = {
  token: string;
  visionAreas: VisionArea[];
  /** Pre-selected area when arriving from a vision area's "Add dream" shortcut. */
  initialVisionAreaId?: string;
  onClose: () => void;
  /** "Skip the guide" — the page opens its flat create form instead. */
  onSkip: () => void;
  /** Fired after the dream (and any first goals) are saved, so the page reloads. */
  onCreated: (dream: Dream) => void;
};

/**
 * FR-21.3 dream coaching wizard: the method's clarifying questions as a
 * guided flow — What exactly? Why does it matter / what does success look
 * like? When, and how important? — then first goals inline, so a dream and
 * its first goal exist without leaving the page. Only title and area are
 * required (the backend's rules); the rest is coaching, not validation
 * (FR-21.4).
 */
export function DreamWizard({ token, visionAreas, initialVisionAreaId, onClose, onSkip, onCreated }: DreamWizardProps) {
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [visionAreaId, setVisionAreaId] = useState(initialVisionAreaId ?? String(visionAreas[0]?.id ?? ''));
  const [title, setTitle] = useState('');
  const [dreamType, setDreamType] = useState<DreamType>('LONG_TERM');
  const [whyImportant, setWhyImportant] = useState('');
  const [successDefinition, setSuccessDefinition] = useState('');
  const [targetDate, setTargetDate] = useState('');
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [createdDream, setCreatedDream] = useState<Dream | null>(null);
  const [goalTitle, setGoalTitle] = useState('');
  const [addedGoals, setAddedGoals] = useState<string[]>([]);
  const [addingGoal, setAddingGoal] = useState(false);

  // The wizard can mount (via ?create=dream) before the page's area list has
  // loaded — default the selection once areas arrive so Continue isn't stuck.
  useEffect(() => {
    if (!visionAreaId && visionAreas.length > 0) {
      setVisionAreaId(String(visionAreas[0].id));
    }
  }, [visionAreas, visionAreaId]);

  async function handleCreateDream() {
    setSaving(true);
    setError('');
    try {
      const dream = await createDream(token, {
        visionAreaId: Number(visionAreaId),
        title: title.trim(),
        whyImportant: whyImportant.trim() || undefined,
        successDefinition: successDefinition.trim() || undefined,
        dreamType,
        priority,
        targetDate: targetDate || undefined,
        status: 'ACTIVE',
        moonshot: false,
      });
      setCreatedDream(dream);
      setStep(3);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Unable to create the dream.');
    } finally {
      setSaving(false);
    }
  }

  function handleStepSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');
    if (step < 2) {
      setStep(step + 1);
      return;
    }
    void handleCreateDream();
  }

  async function handleAddGoal(event: FormEvent) {
    event.preventDefault();
    if (!createdDream || !goalTitle.trim()) {
      return;
    }
    setAddingGoal(true);
    setError('');
    try {
      await createGoal(token, {
        dreamId: createdDream.id,
        title: goalTitle.trim(),
        priority: 'MEDIUM',
        status: 'NOT_STARTED',
        moonshot: false,
      });
      setAddedGoals((goals) => [...goals, goalTitle.trim()]);
      setGoalTitle('');
    } catch (goalError) {
      setError(goalError instanceof Error ? goalError.message : 'Unable to add the goal.');
    } finally {
      setAddingGoal(false);
    }
  }

  function finish() {
    if (createdDream) {
      onCreated(createdDream);
    }
    onClose();
  }

  // Closing after the dream exists must still report it, or the list misses it.
  const handleClose = createdDream ? finish : onClose;

  const stepTitles = ['New dream — the what', 'New dream — the why', 'New dream — the when', 'Break it into goals'];

  return (
    <Modal title={stepTitles[step]} onClose={handleClose}>
      {step < 3 && <p className="wizard-progress">Step {step + 1} of 3</p>}
      {error && <ErrorMessage message={error} />}

      {step === 0 && (
        <form className="form-grid" onSubmit={handleStepSubmit}>
          <label className="field-full">
            What exactly do you want to achieve?
            <Input value={title} onChange={(event) => setTitle(event.target.value)} required autoFocus placeholder="e.g. Become a confident public speaker" />
            <span className="field-hint">One sentence, specific enough that a stranger would know what done looks like.</span>
          </label>
          <label>
            Which area of life or work does this belong to?
            <FormControl fullWidth size="small" required>
              <Select SelectDisplayProps={{ 'aria-label': "Which area of life or work does this belong to?" }} displayEmpty value={visionAreaId} onChange={(event) => setVisionAreaId(event.target.value)}>
                <MenuItem value="" disabled><em>Select a vision area</em></MenuItem>
                {visionAreas.map((area) => <MenuItem value={String(area.id)} key={area.id}>{area.name}</MenuItem>)}
              </Select>
            </FormControl>
          </label>
          <label>
            How far out does it reach?
            <FormControl fullWidth size="small">
              <Select SelectDisplayProps={{ 'aria-label': "How far out does it reach?" }} value={dreamType} onChange={(event) => setDreamType(event.target.value as DreamType)}>
                <MenuItem value="SHORT_TERM">Short Term</MenuItem>
                <MenuItem value="LONG_TERM">Long Term</MenuItem>
                <MenuItem value="LIFETIME">Lifetime</MenuItem>
              </Select>
            </FormControl>
          </label>
          <div className="field-full row-actions">
            <Button type="submit" disabled={!title.trim() || !visionAreaId}>Continue</Button>
            <Button type="button" variant="secondary" onClick={onSkip}>Skip the guide</Button>
          </div>
        </form>
      )}

      {step === 1 && (
        <form className="form-grid" onSubmit={handleStepSubmit}>
          <label className="field-full">
            Why does this matter to you?
            <Textarea value={whyImportant} onChange={(event) => setWhyImportant(event.target.value)} autoFocus />
            <span className="field-hint">The reason you'll come back to on hard days. A dream without a why gets abandoned first.</span>
          </label>
          <label className="field-full">
            What will success look like?
            <Textarea value={successDefinition} onChange={(event) => setSuccessDefinition(event.target.value)} />
            <span className="field-hint">Describe the finish line so clearly you could recognize the day you cross it.</span>
          </label>
          <div className="field-full row-actions">
            <Button type="submit">Continue</Button>
            <Button type="button" variant="secondary" onClick={() => setStep(0)}>Back</Button>
          </div>
        </form>
      )}

      {step === 2 && (
        <form className="form-grid" onSubmit={handleStepSubmit}>
          <label>
            When do you want to achieve it?
            <Input type="date" value={targetDate} onChange={(event) => setTargetDate(event.target.value)} autoFocus />
            <span className="field-hint">A date turns a wish into a plan. You can revise it any time.</span>
          </label>
          <label>
            How important is it right now?
            <FormControl fullWidth size="small">
              <Select SelectDisplayProps={{ 'aria-label': "How important is it right now?" }} value={priority} onChange={(event) => setPriority(event.target.value as Priority)}>
                <MenuItem value="LOW">Low</MenuItem>
                <MenuItem value="MEDIUM">Medium</MenuItem>
                <MenuItem value="HIGH">High</MenuItem>
                <MenuItem value="CRITICAL">Critical</MenuItem>
              </Select>
            </FormControl>
          </label>
          <div className="field-full row-actions">
            <Button type="submit" disabled={saving}>{saving ? 'Creating...' : 'Create dream'}</Button>
            <Button type="button" variant="secondary" onClick={() => setStep(1)}>Back</Button>
          </div>
        </form>
      )}

      {step === 3 && createdDream && (
        <div className="form-grid">
          <p className="field-full wizard-created">
            <Sparkles size={16} /> <strong>{createdDream.title}</strong> is on the map. What major results would make it real?
          </p>
          {addedGoals.length > 0 && (
            <ul className="field-full wizard-goal-list">
              {addedGoals.map((goal) => (
                <li key={goal}><CheckCircle2 size={14} /> {goal}</li>
              ))}
            </ul>
          )}
          <form className="field-full quick-add" onSubmit={(event) => void handleAddGoal(event)}>
            <Input
              value={goalTitle}
              onChange={(event) => setGoalTitle(event.target.value)}
              placeholder="e.g. Deliver three talks at local meetups"
              autoFocus
            />
            <Button type="submit" variant="secondary" disabled={addingGoal || !goalTitle.trim()}>
              {addingGoal ? 'Adding...' : 'Add goal'}
            </Button>
          </form>
          <span className="field-full field-hint"><Flag size={14} /> Aim for 2–4 goals; each one becomes steps and tasks on the Vision Map.</span>
          <div className="field-full row-actions">
            <Button type="button" onClick={() => { finish(); navigate(`/dreams/${createdDream.id}`); }}>Open the Vision Map</Button>
            <Button type="button" variant="secondary" onClick={finish}>Done</Button>
          </div>
        </div>
      )}
    </Modal>
  );
}
