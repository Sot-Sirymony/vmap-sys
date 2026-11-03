import { useCallback, useEffect, useMemo, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { listUiTextOverrides, resetUiTextOverride, saveUiTextOverride } from '../api/uiTextApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { SearchBar } from '../components/common/SearchBar';
import { useAuth } from '../context/AuthContext';
import { translateToKhmer } from '../api/aiAdminApi';
import { WhenAiOn } from '../components/ai/WhenAiOn';
import { km } from '../i18n/km';
import { currentLanguage, setOverrides, tr, type Language } from '../i18n/translate';
import type { UiTextOverride } from '../types/vision';
import { PageSection } from './PageSection';

const LANGUAGES: { value: Language; label: string }[] = [{ value: 'km', label: 'ខ្មែរ (Khmer)' }, { value: 'en', label: 'English' }];
const MAX_ROWS = 50;

/** The app's own text for a key in one language, before any admin change. */
function defaultText(language: Language, source: string) {
  return language === 'km' ? km[source] ?? source : source;
}

/** FR-20: admin-only editing of interface text, per language. The server enforces the role. */
export function InterfaceTextPage() {
  const { token, user } = useAuth();
  const [language, setLanguage] = useState<Language>(currentLanguage() === 'km' ? 'km' : 'en');
  const [changes, setChanges] = useState<UiTextOverride[] | null>(null);
  const [search, setSearch] = useState('');
  const [changedOnly, setChangedOnly] = useState(false);
  const [loadError, setLoadError] = useState('');

  const load = useCallback(async () => {
    if (!token || user?.role !== 'ADMIN') {
      return;
    }
    setLoadError('');
    try {
      setChanges(await listUiTextOverrides(token, language));
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : tr('Unable to load interface text.'));
    }
  }, [token, user?.role, language]);

  useEffect(() => {
    void load();
  }, [load]);

  const changeBySource = useMemo(() => new Map((changes ?? []).map((change) => [change.sourceText, change])), [changes]);

  const matches = useMemo(() => {
    const needle = search.trim().toLowerCase();
    const sources = [...new Set([...Object.keys(km), ...changeBySource.keys()])].sort((a, b) => a.localeCompare(b));
    return sources.filter((source) => {
      if (changedOnly && !changeBySource.has(source)) {
        return false;
      }
      if (!needle) {
        return true;
      }
      const shown = changeBySource.get(source)?.text ?? defaultText(language, source);
      return source.toLowerCase().includes(needle) || shown.toLowerCase().includes(needle);
    });
  }, [search, changedOnly, changeBySource, language]);

  /** Keeps the cached changes for this device in step, when editing the language in use. */
  function applyHere(next: UiTextOverride[]) {
    setChanges(next);
    if (language === currentLanguage()) {
      setOverrides(Object.fromEntries(next.map((change) => [change.sourceText, change.text])));
    }
  }

  if (user?.role !== 'ADMIN') {
    return (
      <PageSection title={tr('Interface text')} subtitle={tr('Change the words the app uses')}>
        <Alert severity="info">{tr('Only an administrator can edit interface text.')}</Alert>
      </PageSection>
    );
  }

  return (
    <PageSection title={tr('Interface text')} subtitle={tr('Change the words the app uses, in each language, without a code change')}>
      <Stack spacing={2}>
        <Alert severity="info">
          {tr('Changes apply to every account on this server. Keep placeholders such as {count} exactly as they are. Pages already open show a change after they reload.', { count: '{count}' })}
        </Alert>
        <Card>
          <CardContent>
            <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center' }}>
              <Select
                size="small"
                value={language}
                onChange={(event) => setLanguage(event.target.value as Language)}
                SelectDisplayProps={{ 'aria-label': tr('Language') }}
              >
                {LANGUAGES.map((option) => <MenuItem key={option.value} value={option.value} lang={option.value}>{option.label}</MenuItem>)}
              </Select>
              <Box sx={{ flex: 1, minWidth: 200 }}>
                <SearchBar value={search} onChange={setSearch} entityLabel="text" />
              </Box>
              <FormControlLabel
                control={<Switch checked={changedOnly} onChange={(event) => setChangedOnly(event.target.checked)} />}
                label={tr('Changed only')}
              />
            </Box>
          </CardContent>
        </Card>
        {loadError && <ErrorMessage message={loadError} />}
        {!changes && !loadError && <Loading />}
        {changes && (
          <>
            <Typography variant="body2" color="text.secondary">
              {matches.length > MAX_ROWS
                ? tr('Showing {shown} of {count}. Search to narrow the list.', { shown: MAX_ROWS, count: matches.length })
                : tr('{count} found', { count: matches.length })}
            </Typography>
            {matches.slice(0, MAX_ROWS).map((source) => (
              <TextRow
                key={`${language}:${source}`}
                token={token}
                language={language}
                source={source}
                change={changeBySource.get(source)}
                onSaved={(saved) => applyHere([...(changes.filter((change) => change.sourceText !== saved.sourceText)), saved])}
                onReset={(id) => applyHere(changes.filter((change) => change.id !== id))}
              />
            ))}
          </>
        )}
      </Stack>
    </PageSection>
  );
}

type TextRowProps = {
  token: string | null;
  language: Language;
  source: string;
  change?: UiTextOverride;
  onSaved: (saved: UiTextOverride) => void;
  onReset: (id: number) => void;
};

function TextRow({ token, language, source, change, onSaved, onReset }: TextRowProps) {
  const fallback = defaultText(language, source);
  const [draft, setDraft] = useState(change?.text ?? fallback);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const dirty = draft.trim() !== (change?.text ?? fallback);

  // AI-FR-23: a draft in the box; the admin reviews it and saves.
  async function suggestKhmer() {
    if (!token) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      const [suggestion] = (await translateToKhmer(token, [source])).translations;
      if (suggestion) {
        setDraft(suggestion);
      } else {
        setError(tr('No usable draft this time. Try again, or write it yourself.'));
      }
    } catch (suggestError) {
      setError(suggestError instanceof Error ? tr(suggestError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  async function save() {
    if (!token) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      onSaved(await saveUiTextOverride(token, language, source, draft));
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : tr('Unable to save this text.'));
    } finally {
      setBusy(false);
    }
  }

  async function reset() {
    if (!token || !change) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      await resetUiTextOverride(token, change.id);
      setDraft(fallback);
      onReset(change.id);
    } catch (resetError) {
      setError(resetError instanceof Error ? resetError.message : tr('Unable to reset this text.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={1}>
          <Typography variant="caption" color="text.secondary" lang="en">{source}</Typography>
          {change && (
            <Typography variant="caption" color="text.secondary" lang={language}>
              {tr('Default: {text}', { text: fallback })}
            </Typography>
          )}
          <TextField
            size="small"
            fullWidth
            multiline
            minRows={1}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            slotProps={{ htmlInput: { 'aria-label': tr('Text for "{source}"', { source }), lang: language } }}
          />
          {error && <Alert severity="error">{error}</Alert>}
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            <Button type="button" disabled={busy || !dirty || !draft.trim()} onClick={() => void save()}>{tr('Save')}</Button>
            {language === 'km' && (
              <WhenAiOn token={token}>
                <Button type="button" variant="secondary" disabled={busy} onClick={() => void suggestKhmer()}>{tr('Suggest Khmer')}</Button>
              </WhenAiOn>
            )}
            {change && (
              <Button type="button" variant="secondary" disabled={busy} onClick={() => void reset()}>{tr('Reset to default')}</Button>
            )}
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
}
