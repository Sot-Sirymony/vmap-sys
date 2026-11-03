import { FormEvent, useCallback, useEffect, useState } from 'react';
import { ArrowDown, ArrowUp } from 'lucide-react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import FormControl from '@mui/material/FormControl';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { listModuleContent, reorderModules, updateModuleContent } from '../api/moduleContentApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { Textarea } from '../components/common/Textarea';
import { useAuth } from '../context/AuthContext';
import type { ModuleContent } from '../types/vision';
import { ModuleContentAiCard } from '../components/ai/ModuleContentAiCard';
import { WhenAiOn } from '../components/ai/WhenAiOn';
import { PageSection } from './PageSection';
import { tr } from '../i18n/translate';

const LANGUAGES = [{ value: 'en', label: 'English' }, { value: 'km', label: 'ខ្មែរ (Khmer)' }];

/** FR-78: admin-only editing of module text and order. The server enforces the role. */
export function ModuleContentPage() {
  const { token, user } = useAuth();
  const [language, setLanguage] = useState('km');
  const [modules, setModules] = useState<ModuleContent[] | null>(null);
  const [selectedNumber, setSelectedNumber] = useState<number | null>(null);
  const [title, setTitle] = useState('');
  const [summary, setSummary] = useState('');
  const [guidance, setGuidance] = useState('');
  const [instructions, setInstructions] = useState('');
  const [labels, setLabels] = useState<Record<string, string>>({});
  const [loadError, setLoadError] = useState('');
  const [actionError, setActionError] = useState('');
  const [saved, setSaved] = useState(false);

  const selected = modules?.find((module) => module.number === selectedNumber) ?? null;

  const load = useCallback(async () => {
    if (!token || user?.role !== 'ADMIN') {
      return;
    }
    setLoadError('');
    try {
      const next = await listModuleContent(token, language);
      setModules(next);
      setSelectedNumber((current) => current ?? next[0]?.number ?? null);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : tr('Unable to load module content.'));
    }
  }, [token, user?.role, language]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (selected) {
      setTitle(selected.title);
      setSummary(selected.summary);
      setGuidance(selected.guidance);
      setInstructions(selected.exerciseInstructions);
      setLabels(Object.fromEntries(selected.fields.map((field) => [field.key, field.label])));
    }
  }, [selected]);

  async function handleSave(event: FormEvent) {
    event.preventDefault();
    if (!token || !selected) {
      return;
    }
    setActionError('');
    setSaved(false);
    try {
      await updateModuleContent(token, selected.number, language, {
        title, summary, guidance, exerciseInstructions: instructions, fieldLabels: labels,
      });
      setSaved(true);
      await load();
    } catch (error) {
      setActionError(error instanceof Error ? error.message : tr('Unable to save this module.'));
    }
  }

  async function move(index: number, offset: number) {
    if (!token || !modules) {
      return;
    }
    const order = modules.map((module) => module.number);
    const [moved] = order.splice(index, 1);
    order.splice(index + offset, 0, moved);
    setActionError('');
    try {
      await reorderModules(token, order);
      await load();
    } catch (error) {
      setActionError(error instanceof Error ? error.message : tr('Unable to change the order.'));
    }
  }

  if (user?.role !== 'ADMIN') {
    return (
      <PageSection title={tr('Module content')} subtitle={tr('Edit the text and order of the modules')}>
        <Alert severity="info">{tr('Only an administrator can edit module content.')}</Alert>
      </PageSection>
    );
  }

  return (
    <PageSection title={tr('Module content')} subtitle={tr('Changes apply to every account on this server')}>
      {loadError && <ErrorMessage message={loadError} onRetry={() => void load()} />}
      {!modules && !loadError && <Loading />}
      {modules && (
        <Stack spacing={2}>
          {actionError && <Alert severity="error">{actionError}</Alert>}
          <label>
            {tr('Language')}
            <FormControl fullWidth size="small">
              <Select SelectDisplayProps={{ 'aria-label': 'Language' }} value={language} onChange={(event) => setLanguage(event.target.value)}>
                {LANGUAGES.map((option) => <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>)}
              </Select>
            </FormControl>
          </label>

          <Card>
            <CardHeader title={tr('Order')} subheader={tr('Modules unlock from top to bottom. Saved progress is kept.')} />
            <CardContent>
              <Stack spacing={0.5}>
                {modules.map((module, index) => (
                  <Box key={module.number} sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <IconButton size="small" disabled={index === 0} aria-label={tr('Move {title} up', { title: module.title })} onClick={() => void move(index, -1)}>
                      <ArrowUp size={14} />
                    </IconButton>
                    <IconButton size="small" disabled={index === modules.length - 1} aria-label={tr('Move {title} down', { title: module.title })} onClick={() => void move(index, 1)}>
                      <ArrowDown size={14} />
                    </IconButton>
                    <Button variant={module.number === selectedNumber ? 'primary' : 'secondary'} onClick={() => { setSaved(false); setSelectedNumber(module.number); }}>
                      {module.position}. {module.title}
                    </Button>
                  </Box>
                ))}
              </Stack>
            </CardContent>
          </Card>

          {selected && (
            <Card>
              <CardHeader title={tr('Edit text')} subheader={selected.translated ? undefined : tr('Showing English because this language has no text for this module yet.')} />
              <CardContent>
                {token && (
                  <WhenAiOn token={token}>
                    <Box sx={{ mb: 1.5 }}>
                      <ModuleContentAiCard
                        token={token}
                        module={selected}
                        language={language}
                        onFill={(fields) => {
                          setSaved(false);
                          if (fields.title) { setTitle(fields.title); }
                          if (fields.summary) { setSummary(fields.summary); }
                          if (fields.guidance) { setGuidance(fields.guidance); }
                          if (fields.exerciseInstructions) { setInstructions(fields.exerciseInstructions); }
                          if (fields.labels) { setLabels((current) => ({ ...current, ...fields.labels })); }
                        }}
                      />
                    </Box>
                  </WhenAiOn>
                )}
                <Box component="form" onSubmit={(event) => void handleSave(event)} sx={{ display: 'grid', gap: 1.5 }}>
                  <label>{tr('Title')}<Input aria-label={tr('Title')} value={title} onChange={(event) => setTitle(event.target.value)} required /></label>
                  <label>{tr('Summary')}<Textarea value={summary} onChange={(event) => setSummary(event.target.value)} required /></label>
                  <label>{tr('Guidance')}<Textarea value={guidance} onChange={(event) => setGuidance(event.target.value)} required /></label>
                  <label>{tr('Exercise instructions')}<Textarea value={instructions} onChange={(event) => setInstructions(event.target.value)} required /></label>
                  {selected.fields.length > 0 && <Typography variant="body2" sx={{ fontWeight: 600 }}>{tr('Form field labels')}</Typography>}
                  {selected.fields.map((field) => (
                    <Input
                      key={field.key}
                      aria-label={tr('Label for {field}', { field: field.key })}
                      value={labels[field.key] ?? ''}
                      onChange={(event) => setLabels((current) => ({ ...current, [field.key]: event.target.value }))}
                      required
                    />
                  ))}
                  {saved && <Alert severity="success">{tr('Saved.')}</Alert>}
                  <Box><Button type="submit">{tr('Save module')}</Button></Box>
                </Box>
              </CardContent>
            </Card>
          )}
        </Stack>
      )}
    </PageSection>
  );
}
