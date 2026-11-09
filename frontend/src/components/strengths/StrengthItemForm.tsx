import { FormEvent, useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import FormControl from '@mui/material/FormControl';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { Textarea } from '../common/Textarea';
import { useI18n } from '../../i18n/I18nContext';
import type { Relevance, SelfAssessmentItemRequest, SelfAssessmentSource, StrengthKind } from '../../types/vision';
import { relevanceLabels, strengthKindLabels } from '../../utils/enumLabels';

export type StrengthFormValues = {
  kind: StrengthKind;
  description: string;
  evidence: string;
  relevance: Relevance | '';
  source: SelfAssessmentSource;
  outsideBy: string;
  outsideView: string;
  outsideAgrees: boolean | null;
};

export const EMPTY_STRENGTH_FORM: StrengthFormValues = {
  kind: 'STRENGTH',
  description: '',
  evidence: '',
  relevance: '',
  source: 'MANUAL',
  outsideBy: '',
  outsideView: '',
  outsideAgrees: null,
};

type StrengthItemFormProps = {
  dreamId: number;
  initial: StrengthFormValues;
  editing: boolean;
  saving: boolean;
  onSubmit: (request: SelfAssessmentItemRequest) => void;
  onCancel: () => void;
};

/**
 * FR-79, FR-81 and FR-83: one strength or gap. Evidence is required before it
 * can be saved (AC-08); the outside view is optional, and asks whether the
 * user agrees with it so a difference can be flagged.
 */
export function StrengthItemForm({ dreamId, initial, editing, saving, onSubmit, onCancel }: StrengthItemFormProps) {
  const { t } = useI18n();
  const [values, setValues] = useState<StrengthFormValues>(initial);

  useEffect(() => setValues(initial), [initial]);

  const ready = Boolean(values.description.trim() && values.evidence.trim() && values.relevance);
  const hasOutsideView = Boolean(values.outsideView.trim());

  function update<K extends keyof StrengthFormValues>(key: K, value: StrengthFormValues[K]) {
    setValues((current) => ({ ...current, [key]: value }));
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!ready || !values.relevance) {
      return;
    }
    onSubmit({
      dreamId,
      kind: values.kind,
      description: values.description.trim(),
      evidence: values.evidence.trim(),
      relevance: values.relevance,
      source: values.source,
      outsideBy: hasOutsideView ? values.outsideBy.trim() : undefined,
      outsideView: hasOutsideView ? values.outsideView.trim() : undefined,
      outsideAgrees: hasOutsideView ? values.outsideAgrees : null,
    });
  }

  return (
    <Box component="form" onSubmit={handleSubmit} sx={{ display: 'grid', gap: 1.5 }}>
      <ToggleButtonGroup
        exclusive
        size="small"
        aria-label={t('Type')}
        value={values.kind}
        onChange={(_event, kind: StrengthKind | null) => kind && update('kind', kind)}
      >
        {(['STRENGTH', 'WEAKNESS'] as StrengthKind[]).map((kind) => (
          <ToggleButton value={kind} key={kind}>{t(strengthKindLabels[kind])}</ToggleButton>
        ))}
      </ToggleButtonGroup>
      <label>
        {values.kind === 'STRENGTH' ? t('What do you bring?') : t('What do you lack?')}
        <Input value={values.description} onChange={(event) => update('description', event.target.value)} maxLength={300} required />
      </label>
      <label>
        {t('Evidence: one real example')}
        <Textarea value={values.evidence} onChange={(event) => update('evidence', event.target.value)} required />
      </label>
      <label>
        {t('How much does it matter to this dream?')}
        <FormControl fullWidth size="small">
          <Select
            SelectDisplayProps={{ 'aria-label': t('How much does it matter to this dream?') }}
            value={values.relevance}
            displayEmpty
            onChange={(event) => update('relevance', event.target.value as Relevance)}
          >
            <MenuItem value="" disabled>{t('Choose one')}</MenuItem>
            {(Object.keys(relevanceLabels) as Relevance[]).map((value) => (
              <MenuItem value={value} key={value}>{t(relevanceLabels[value])}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </label>
      <Typography variant="body2" sx={{ fontWeight: 600, mt: 1 }}>{t('Outside view (optional)')}</Typography>
      <Typography variant="body2" color="text.secondary">{t('What has someone else said about this? Type it in yourself.')}</Typography>
      <label>
        {t('Who said it?')}
        <Input value={values.outsideBy} onChange={(event) => update('outsideBy', event.target.value)} maxLength={160} />
      </label>
      <label>
        {t('What did they say?')}
        <Textarea value={values.outsideView} onChange={(event) => update('outsideView', event.target.value)} />
      </label>
      {hasOutsideView && (
        <Box>
          <Typography variant="body2">{t('Do you agree with them?')}</Typography>
          <ToggleButtonGroup
            exclusive
            size="small"
            aria-label={t('Do you agree with them?')}
            value={values.outsideAgrees === null ? null : String(values.outsideAgrees)}
            onChange={(_event, value: string | null) => update('outsideAgrees', value === null ? null : value === 'true')}
          >
            <ToggleButton value="true">{t('Yes')}</ToggleButton>
            <ToggleButton value="false">{t('No')}</ToggleButton>
          </ToggleButtonGroup>
        </Box>
      )}
      <Box sx={{ display: 'flex', gap: 1 }}>
        <Button type="submit" disabled={saving || !ready}>{saving ? t('Saving…') : editing ? t('Save changes') : t('Add')}</Button>
        {(editing || values.description || values.evidence) && <Button variant="secondary" onClick={onCancel}>{t('Cancel')}</Button>}
      </Box>
    </Box>
  );
}
