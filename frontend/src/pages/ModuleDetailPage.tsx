import { FormEvent, useCallback, useEffect, useState } from 'react';
import { X } from 'lucide-react';
import { useNavigate, useParams } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { completeModule, getModule } from '../api/moduleApi';
import { Breadcrumbs } from '../components/common/Breadcrumbs';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { useAuth } from '../context/AuthContext';
import type { LearningModuleDetail, ModuleField, ModuleRow } from '../types/vision';
import { useI18n } from '../i18n/I18nContext';
import { PitchCoachCard } from '../components/ai/PitchCoachCard';
import { ModuleHelpCard } from '../components/ai/ModuleHelpCard';
import { PageSection } from './PageSection';

/** v0.1 FR-11: module 11, Make a Clear Ask, holds the pitch (AI-FR-16 practises it). */
const PITCH_MODULE = 11;

function emptyRow(fields: ModuleField[]): ModuleRow {
  return Object.fromEntries(fields.map((field) => [field.key, '']));
}

/**
 * FR-72: one module. A module with fields saves its own exercise; one without
 * is proven by the user's existing data, so it links there and says what is
 * still missing rather than asking for a second copy.
 */
export function ModuleDetailPage() {
  const { t, language } = useI18n();
  const { token } = useAuth();
  const navigate = useNavigate();
  const number = Number(useParams().moduleNumber);
  const [module, setModule] = useState<LearningModuleDetail | null>(null);
  const [rows, setRows] = useState<ModuleRow[]>([]);
  const [loadError, setLoadError] = useState('');
  const [saveError, setSaveError] = useState('');
  const [saving, setSaving] = useState(false);
  const [justSaved, setJustSaved] = useState(false);

  const load = useCallback(async () => {
    if (!token) {
      return;
    }
    setLoadError('');
    try {
      const next = await getModule(token, number, language);
      setModule(next);
      setRows(next.rows.length > 0 ? next.rows : [emptyRow(next.fields)]);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : t('Unable to load this module.'));
    }
  }, [token, number, language]);

  useEffect(() => {
    setJustSaved(false);
    void load();
  }, [load]);

  function updateRow(index: number, key: string, value: string) {
    setRows((current) => current.map((row, rowIndex) => (rowIndex === index ? { ...row, [key]: value } : row)));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token || !module) {
      return;
    }
    setSaving(true);
    setSaveError('');
    setJustSaved(false);
    try {
      const saved = await completeModule(token, number, module.fields.length > 0 ? rows : [], language);
      setModule(saved);
      setJustSaved(true);
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : t('Unable to save this exercise.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <PageSection title={module?.title ?? t('Module')} subtitle={module?.summary ?? ''}>
      <Breadcrumbs crumbs={[{ label: t('Modules'), to: '/modules' }, { label: module?.title ?? t('Module') }]} />
      {loadError && <ErrorMessage message={t(loadError)} />}
      {!module && !loadError && <Loading />}
      {module && (
        <Stack spacing={2}>
          <Card>
            <CardHeader title={t('The idea')} />
            <CardContent>
              <Typography variant="body1">{module.guidance}</Typography>
            </CardContent>
          </Card>

          {module.coachRating != null && (
            <Alert severity="info">
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {t('{name} rated this {score} of 5', { name: module.coachName ?? t('Your coach'), score: module.coachRating })}
              </Typography>
              {module.coachFeedback && <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{module.coachFeedback}</Typography>}
            </Alert>
          )}

          {token && (
            <ModuleHelpCard
              token={token}
              module={module}
              rows={rows}
              onUseDraft={(draft) => {
                setJustSaved(false);
                setRows(draft.length > 0 ? draft : [emptyRow(module.fields)]);
              }}
            />
          )}

          {module.number === PITCH_MODULE && token && (
            <PitchCoachCard token={token} initialPitch={module.rows[0]?.pitch ?? ''} />
          )}

          <Card>
            <CardHeader title={t('Your exercise')} subheader={module.exerciseInstructions} />
            <CardContent>
              <Box component="form" onSubmit={(event) => void handleSubmit(event)} sx={{ display: 'grid', gap: 2 }}>
                {module.fields.length > 0 && rows.map((row, index) => (
                  <Box key={index} sx={{ display: 'grid', gap: 1, pb: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
                    {module.fields.map((field) => (
                      <label key={field.key}>
                        {field.label}
                        <Input
                          type={field.type === 'rating' ? 'number' : 'text'}
                          min={field.type === 'rating' ? '1' : undefined}
                          max={field.type === 'rating' ? '5' : undefined}
                          aria-label={field.label}
                          value={row[field.key] ?? ''}
                          onChange={(event) => updateRow(index, field.key, event.target.value)}
                          required
                        />
                      </label>
                    ))}
                    {rows.length > 1 && (
                      <Box>
                        <IconButton
                          size="small"
                          aria-label={t('Remove row {number}', { number: index + 1 })}
                          onClick={() => setRows((current) => current.filter((_row, rowIndex) => rowIndex !== index))}
                        >
                          <X size={14} />
                        </IconButton>
                      </Box>
                    )}
                  </Box>
                ))}
                {module.fields.length === 0 && module.requirementMessage && (
                  <Alert severity="info">{t(module.requirementMessage)}</Alert>
                )}
                {saveError && <Alert severity="error">{t(saveError)}</Alert>}
                {justSaved && <Alert severity="success">{t('Saved. The next module is unlocked if this was your first time through.')}</Alert>}
                <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                  {module.fields.length > 0 && (
                    <Button variant="secondary" onClick={() => setRows((current) => [...current, emptyRow(module.fields)])}>{t('Add another')}</Button>
                  )}
                  {module.linkPath && (
                    <Button variant="secondary" onClick={() => navigate(module.linkPath as string)}>{t('Go there')}</Button>
                  )}
                  {(module.fields.length > 0 || module.status !== 'COMPLETED') && (
                    <Button type="submit" disabled={saving || Boolean(module.requirementMessage)}>
                      {saving ? t('Saving…') : module.fields.length > 0 ? t('Save exercise') : t('Mark as done')}
                    </Button>
                  )}
                  {justSaved && <Button variant="secondary" onClick={() => navigate('/modules')}>{t('Back to modules')}</Button>}
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Stack>
      )}
    </PageSection>
  );
}
