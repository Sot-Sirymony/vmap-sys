import { FormEvent, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import FormControl from '@mui/material/FormControl';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import Select from '@mui/material/Select';
import Typography from '@mui/material/Typography';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { Modal } from '../common/Modal';
import { Textarea } from '../common/Textarea';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { GapCoverMethod, GapCoverRequest, Goal, PartnerSupportType, SelfAssessmentItem } from '../../types/vision';
import { gapCoverMethodLabels, partnerSupportTypeLabels } from '../../utils/enumLabels';

// Where each way sends the gap, so the user knows what will be created.
const METHOD_HINTS: Record<GapCoverMethod, string> = {
  PARTNER: tr('Adds someone to your Partners page, ready to contact.'),
  DELEGATE: tr('Adds a row to your leverage map (the Multiply Your Effort module).'),
  RESOURCE: tr('Adds a row to your resource gap list (the Close Your Resource Gaps module).'),
  LEARN: tr('Adds a dated learning step to one of this dream\'s goals.'),
  ACCEPT: tr('Nothing is added. Write why this gap does not matter enough.'),
};

const METHODS: GapCoverMethod[] = ['PARTNER', 'DELEGATE', 'RESOURCE', 'LEARN', 'ACCEPT'];

type GapCoverDialogProps = {
  item: SelfAssessmentItem;
  goals: Goal[];
  learnInUse: number;
  learnLimit: number;
  saving: boolean;
  error: string;
  onSubmit: (request: GapCoverRequest) => void;
  onClose: () => void;
};

/**
 * FR-82: choose exactly one way to cover a gap that matters a lot. "Learn it
 * myself" is offered only while fewer than the limit are being learned
 * (BR-62), so the user covers most gaps another way instead of fixing them all.
 */
export function GapCoverDialog({ item, goals, learnInUse, learnLimit, saving, error, onSubmit, onClose }: GapCoverDialogProps) {
  const { t } = useI18n();
  const learnFull = learnInUse >= learnLimit;
  const [method, setMethod] = useState<GapCoverMethod>('PARTNER');
  const [note, setNote] = useState('');
  const [partnerName, setPartnerName] = useState('');
  const [supportType, setSupportType] = useState<PartnerSupportType>('OTHER');
  const [goalId, setGoalId] = useState<number | ''>(goals.length === 1 ? goals[0].id : '');
  const [targetDate, setTargetDate] = useState('');

  const ready = (() => {
    switch (method) {
      case 'PARTNER':
        return Boolean(partnerName.trim());
      case 'LEARN':
        return !learnFull && goalId !== '' && Boolean(targetDate);
      default:
        return Boolean(note.trim());
    }
  })();

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!ready) {
      return;
    }
    const trimmedNote = note.trim() || undefined;
    switch (method) {
      case 'PARTNER':
        onSubmit({ method, partnerName: partnerName.trim(), partnerSupportType: supportType, note: trimmedNote });
        break;
      case 'LEARN':
        onSubmit({ method, goalId: Number(goalId), targetDate, note: trimmedNote });
        break;
      default:
        onSubmit({ method, note: trimmedNote });
    }
  }

  const noteLabel: Record<GapCoverMethod, string> = {
    PARTNER: tr('What will they help with? (optional)'),
    DELEGATE: tr('Who or what will carry it?'),
    RESOURCE: tr('Who or what could supply it?'),
    LEARN: tr('How will you learn it? (optional)'),
    ACCEPT: tr('Why does it not matter enough?'),
  };

  return (
    <Modal title={t('How will you cover this gap?')} onClose={onClose}>
      <Box component="form" onSubmit={handleSubmit} sx={{ display: 'grid', gap: 1.5, pt: 1 }}>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>{item.description}</Typography>
        <Typography variant="body2" color="text.secondary">
          {t('Choose one way. You do not have to fix every gap yourself.')}
        </Typography>
        <RadioGroup value={method} onChange={(event) => setMethod(event.target.value as GapCoverMethod)} aria-label={t('Way to cover it')}>
          {METHODS.map((value) => (
            <FormControlLabel
              key={value}
              value={value}
              disabled={value === 'LEARN' && learnFull}
              control={<Radio size="small" />}
              label={
                <Box>
                  <Typography variant="body2">{t(gapCoverMethodLabels[value])}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {value === 'LEARN' && learnFull
                      ? t('You are already learning {count} gaps. Cover this one another way.', { count: learnLimit })
                      : t(METHOD_HINTS[value])}
                  </Typography>
                </Box>
              }
            />
          ))}
        </RadioGroup>

        {method === 'PARTNER' && (
          <>
            <label>
              {t('Partner name')}
              <Input value={partnerName} onChange={(event) => setPartnerName(event.target.value)} maxLength={180} required />
            </label>
            <label>
              {t('Support type')}
              <FormControl fullWidth size="small">
                <Select
                  SelectDisplayProps={{ 'aria-label': t('Support type') }}
                  value={supportType}
                  onChange={(event) => setSupportType(event.target.value as PartnerSupportType)}
                >
                  {(Object.keys(partnerSupportTypeLabels) as PartnerSupportType[]).map((value) => (
                    <MenuItem value={value} key={value}>{t(partnerSupportTypeLabels[value])}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </label>
          </>
        )}

        {method === 'LEARN' && !learnFull && (
          goals.length === 0 ? (
            <Alert severity="info">{t('This dream has no goals yet. Add a goal on the Vision Map first, or cover this gap another way.')}</Alert>
          ) : (
            <>
              <label>
                {t('Goal')}
                <FormControl fullWidth size="small">
                  <Select
                    SelectDisplayProps={{ 'aria-label': t('Goal') }}
                    value={goalId === '' ? '' : String(goalId)}
                    displayEmpty
                    onChange={(event) => setGoalId(Number(event.target.value))}
                  >
                    <MenuItem value="" disabled>{t('Choose one')}</MenuItem>
                    {goals.map((goal) => <MenuItem value={String(goal.id)} key={goal.id}>{goal.title}</MenuItem>)}
                  </Select>
                </FormControl>
              </label>
              <label>
                {t('Learn it by')}
                <Input type="date" value={targetDate} onChange={(event) => setTargetDate(event.target.value)} required />
              </label>
            </>
          )
        )}

        <label>
          {t(noteLabel[method])}
          <Textarea value={note} onChange={(event) => setNote(event.target.value)} required={method === 'DELEGATE' || method === 'RESOURCE' || method === 'ACCEPT'} />
        </label>

        {error && <Alert severity="error">{t(error)}</Alert>}
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button type="submit" disabled={saving || !ready}>{saving ? t('Saving…') : t('Save')}</Button>
          <Button variant="secondary" onClick={onClose}>{t('Cancel')}</Button>
        </Box>
      </Box>
    </Modal>
  );
}
