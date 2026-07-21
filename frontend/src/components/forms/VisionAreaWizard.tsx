import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CheckCircle2, Compass, Sparkles } from 'lucide-react';
import FormControl from '@mui/material/FormControl';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import { createVisionArea } from '../../api/visionAreaApi';
import { createDream } from '../../api/dreamApi';
import { Button } from '../common/Button';
import { ErrorMessage } from '../common/ErrorMessage';
import { Input } from '../common/Input';
import { Modal } from '../common/Modal';
import { Textarea } from '../common/Textarea';
import type { Priority, VisionArea } from '../../types/vision';

type VisionAreaWizardProps = {
  token: string;
  onClose: () => void;
  /** "Skip the guide" — the page opens its flat create form instead. */
  onSkip: () => void;
  /** Fired after the area (and any first dream) are saved, so the page reloads. */
  onCreated: (area: VisionArea) => void;
};

/**
 * FR-33 vision area setup wizard: mirrors the FR-21.3 Dream Coaching Wizard
 * one level up — a short guided flow that asks for the area's name and, as
 * an optional coaching prompt, its written vision statement ("what does this
 * look like when it's going well?") before offering to break it into a
 * first Dream inline. Only name is required (the backend's rule); the vision
 * statement is coaching, never a save gate (FR-33.3).
 */
export function VisionAreaWizard({ token, onClose, onSkip, onCreated }: VisionAreaWizardProps) {
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [name, setName] = useState('');
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [visionStatement, setVisionStatement] = useState('');
  const [createdArea, setCreatedArea] = useState<VisionArea | null>(null);
  const [dreamTitle, setDreamTitle] = useState('');
  const [addedDreams, setAddedDreams] = useState<string[]>([]);
  const [addingDream, setAddingDream] = useState(false);

  async function handleCreateArea() {
    setSaving(true);
    setError('');
    try {
      const area = await createVisionArea(token, {
        name: name.trim(),
        visionStatement: visionStatement.trim() || undefined,
        priority,
        status: 'ACTIVE',
      });
      setCreatedArea(area);
      setStep(2);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Unable to create the vision area.');
    } finally {
      setSaving(false);
    }
  }

  function handleStepSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');
    if (step < 1) {
      setStep(step + 1);
      return;
    }
    void handleCreateArea();
  }

  async function handleAddDream(event: FormEvent) {
    event.preventDefault();
    if (!createdArea || !dreamTitle.trim()) {
      return;
    }
    setAddingDream(true);
    setError('');
    try {
      await createDream(token, {
        visionAreaId: createdArea.id,
        title: dreamTitle.trim(),
        dreamType: 'LONG_TERM',
        priority: 'MEDIUM',
        status: 'IDEA',
        moonshot: false,
      });
      setAddedDreams((dreams) => [...dreams, dreamTitle.trim()]);
      setDreamTitle('');
    } catch (dreamError) {
      setError(dreamError instanceof Error ? dreamError.message : 'Unable to add the dream.');
    } finally {
      setAddingDream(false);
    }
  }

  function finish() {
    if (createdArea) {
      onCreated(createdArea);
    }
    onClose();
  }

  // Closing after the area exists must still report it, or the list misses it.
  const handleClose = createdArea ? finish : onClose;

  const stepTitles = ['New vision area — the what', 'New vision area — the vision', 'Break it into dreams'];

  return (
    <Modal title={stepTitles[step]} onClose={handleClose}>
      {step < 2 && <p className="wizard-progress">Step {step + 1} of 2</p>}
      {error && <ErrorMessage message={error} />}

      {step === 0 && (
        <form className="form-grid" onSubmit={handleStepSubmit}>
          <label className="field-full">
            What area of life or work is this?
            <Input value={name} onChange={(event) => setName(event.target.value)} required autoFocus placeholder="e.g. Career, Health, Family" />
            <span className="field-hint">A major, ongoing part of your life or work — everything else builds under it.</span>
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
            <Button type="submit" disabled={!name.trim()}>Continue</Button>
            <Button type="button" variant="secondary" onClick={onSkip}>Skip the guide</Button>
          </div>
        </form>
      )}

      {step === 1 && (
        <form className="form-grid" onSubmit={handleStepSubmit}>
          <label className="field-full">
            What does this area look like when it's going well?
            <Textarea value={visionStatement} onChange={(event) => setVisionStatement(event.target.value)} autoFocus />
            <span className="field-hint">
              Optional, but writing this down first makes every dream you add underneath easier to judge — does it
              actually move you toward this, or not?
            </span>
          </label>
          <div className="field-full row-actions">
            <Button type="submit" disabled={saving}>{saving ? 'Creating...' : 'Create vision area'}</Button>
            <Button type="button" variant="secondary" onClick={() => setStep(0)}>Back</Button>
          </div>
        </form>
      )}

      {step === 2 && createdArea && (
        <div className="form-grid">
          <p className="field-full wizard-created">
            <Compass size={16} /> <strong>{createdArea.name}</strong> is on the map. What dreams belong under it?
          </p>
          {addedDreams.length > 0 && (
            <ul className="field-full wizard-goal-list">
              {addedDreams.map((dream) => (
                <li key={dream}><CheckCircle2 size={14} /> {dream}</li>
              ))}
            </ul>
          )}
          <form className="field-full quick-add" onSubmit={(event) => void handleAddDream(event)}>
            <Input
              value={dreamTitle}
              onChange={(event) => setDreamTitle(event.target.value)}
              placeholder="e.g. Become a confident public speaker"
              autoFocus
            />
            <Button type="submit" variant="secondary" disabled={addingDream || !dreamTitle.trim()}>
              {addingDream ? 'Adding...' : 'Add dream'}
            </Button>
          </form>
          <span className="field-full field-hint"><Sparkles size={14} /> A dream added here can be fleshed out with its own why and success definition any time.</span>
          <div className="field-full row-actions">
            <Button type="button" onClick={() => { finish(); navigate('/dreams'); }}>View Dreams</Button>
            <Button type="button" variant="secondary" onClick={finish}>Done</Button>
          </div>
        </div>
      )}
    </Modal>
  );
}
