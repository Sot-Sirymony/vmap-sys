import { type ReactNode, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { suggestDreams, suggestVisionAreas } from '../../api/aiPlanApi';
import { createDream } from '../../api/dreamApi';
import { createVisionArea } from '../../api/visionAreaApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { AiStatus, DreamSuggestion, VisionArea, VisionAreaSuggestion } from '../../types/vision';
import { Button } from '../common/Button';
import { Loading } from '../common/Loading';

type AreaRow = VisionAreaSuggestion & { picked: boolean };
type DreamRow = DreamSuggestion & { picked: boolean };

/**
 * The plan assistant for the start of a plan: vision areas, then dreams under
 * one area. Like AiPlanAssistant, every suggestion can be edited and is only
 * saved when the participant adds it (AI-FR-04). Hidden while AI is off.
 */
export function AiVisionAreaAssistant({ token, onDataChange }: { token: string; onDataChange: () => Promise<void> | void }) {
  const { language } = useI18n();
  const assistant = useAssistant(token);
  const [rows, setRows] = useState<AreaRow[] | null>(null);

  const ask = () => assistant.run(async () => {
    setRows(null);
    const answer = await suggestVisionAreas(token, language);
    setRows(answer.areas.map((area) => ({ ...area, picked: true })));
  });

  const add = () => assistant.save('Unable to save vision area.', async () => {
    const picked = (rows ?? []).filter((row) => row.picked && row.name.trim());
    for (const row of picked) {
      await createVisionArea(token, {
        name: row.name.trim(),
        description: row.description ?? undefined,
        visionStatement: row.visionStatement ?? undefined,
        priority: row.priority,
        status: 'ACTIVE',
      });
    }
    setRows(null);
    await onDataChange();
    return tr('{count} vision area(s) added.', { count: picked.length });
  });

  const update = (index: number, patch: Partial<AreaRow>) =>
    setRows((current) => current?.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)) ?? null);
  const picked = (rows ?? []).filter((row) => row.picked && row.name.trim()).length;

  if (!assistant.visible) {
    return null;
  }
  return (
    <AssistantCard assistant={assistant}>
      <Box>
        <Button disabled={assistant.busy || !assistant.ready} onClick={() => void ask()}>{tr('Suggest vision areas')}</Button>
      </Box>
      {rows?.length === 0 && <Typography variant="body2" color="text.secondary">{tr('No new vision areas this time.')}</Typography>}
      {rows?.map((row, index) => (
        <SuggestionBox key={index} title={row.name} picked={row.picked} onPick={(value) => update(index, { picked: value })}>
          <TextField size="small" label={tr('Vision area')} value={row.name} onChange={(event) => update(index, { name: event.target.value })} />
          <TextField size="small" label={tr('Description')} value={row.description ?? ''} multiline
            onChange={(event) => update(index, { description: event.target.value })} />
          <TextField size="small" label={tr('Vision statement')} value={row.visionStatement ?? ''} multiline
            onChange={(event) => update(index, { visionStatement: event.target.value })} />
          {row.reason && <Typography variant="caption" color="text.secondary">{row.reason}</Typography>}
        </SuggestionBox>
      ))}
      {rows && rows.length > 0 && (
        <Box>
          <Button disabled={assistant.busy || picked === 0} onClick={() => void add()}>
            {tr('Add {count} selected vision area(s)', { count: picked })}
          </Button>
        </Box>
      )}
    </AssistantCard>
  );
}

export function AiDreamAssistant({ token, visionAreas, initialVisionAreaId, onDataChange }: {
  token: string;
  visionAreas: VisionArea[];
  initialVisionAreaId?: string;
  onDataChange: () => Promise<void> | void;
}) {
  const { language } = useI18n();
  const assistant = useAssistant(token);
  const activeAreas = visionAreas.filter((area) => !area.archived);
  const [areaId, setAreaId] = useState(initialVisionAreaId ?? '');
  const [result, setResult] = useState<{ areaId: number; rows: DreamRow[]; question?: string | null } | null>(null);

  const ask = (stretch: boolean) => assistant.run(async () => {
    setResult(null);
    const id = Number(areaId);
    const answer = await suggestDreams(token, id, language, stretch);
    setResult({ areaId: id, rows: answer.dreams.map((dream) => ({ ...dream, picked: true })), question: answer.clarifyingQuestion });
  });

  const add = (forArea: number, rows: DreamRow[]) => assistant.save('Unable to save dream.', async () => {
    for (const row of rows) {
      // Added as ideas: the participant commits to a dream, and clears any moonshot gate, from the dream itself.
      await createDream(token, {
        visionAreaId: forArea,
        title: row.title.trim(),
        description: row.description ?? undefined,
        whyImportant: row.whyImportant ?? undefined,
        successDefinition: row.successDefinition ?? undefined,
        dreamType: row.dreamType,
        priority: row.priority,
        targetDate: row.targetDate || undefined,
        status: 'IDEA',
        moonshot: row.moonshot,
        moonshotVision: row.moonshot ? row.reason ?? undefined : undefined,
        scheduleMode: 'BOTTOM_UP',
      });
    }
    setResult(null);
    await onDataChange();
    return tr('{count} dream(s) added as ideas.', { count: rows.length });
  });

  const update = (index: number, patch: Partial<DreamRow>) =>
    setResult((current) => current && {
      ...current,
      rows: current.rows.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)),
    });
  const ready = (result?.rows ?? []).filter((row) => row.picked && row.title.trim());

  if (!assistant.visible || activeAreas.length === 0) {
    return null;
  }
  return (
    <AssistantCard assistant={assistant}>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
        <Select
          size="small"
          displayEmpty
          value={areaId}
          onChange={(event) => setAreaId(event.target.value)}
          sx={{ minWidth: 220, maxWidth: '100%' }}
          SelectDisplayProps={{ 'aria-label': tr('Vision area to plan') }}
        >
          <MenuItem value="">{tr('Choose a vision area')}</MenuItem>
          {activeAreas.map((area) => <MenuItem key={area.id} value={String(area.id)}>{area.name}</MenuItem>)}
        </Select>
        <Button disabled={assistant.busy || !assistant.ready || !areaId} onClick={() => void ask(false)}>{tr('Suggest dreams')}</Button>
        <Button variant="secondary" disabled={assistant.busy || !assistant.ready || !areaId} onClick={() => void ask(true)}>
          {tr('Suggest a moonshot dream')}
        </Button>
      </Box>
      {result?.question && (
        <Alert severity="info">
          <Typography variant="body2" sx={{ fontWeight: 600 }}>{result.question}</Typography>
          <Typography variant="body2">{tr('Add the answer to your vision area, or talk it through with the Dream Coach, then ask again.')}</Typography>
        </Alert>
      )}
      {result?.rows.map((row, index) => (
        <SuggestionBox key={index} title={row.title} picked={row.picked} onPick={(value) => update(index, { picked: value })}
          badge={row.moonshot ? tr('Moonshot dream') : undefined}>
          <TextField size="small" label={tr('Dream')} value={row.title} onChange={(event) => update(index, { title: event.target.value })} />
          <TextField size="small" label={tr('Why it matters')} value={row.whyImportant ?? ''} multiline
            onChange={(event) => update(index, { whyImportant: event.target.value })} />
          <TextField size="small" label={tr('What success looks like')} value={row.successDefinition ?? ''} multiline
            onChange={(event) => update(index, { successDefinition: event.target.value })} />
          <TextField size="small" type="date" label={tr('Target date')} value={row.targetDate ?? ''}
            onChange={(event) => update(index, { targetDate: event.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
          {row.reason && <Typography variant="caption" color="text.secondary">{row.reason}</Typography>}
        </SuggestionBox>
      ))}
      {result && result.rows.length > 0 && (
        <Box>
          <Button disabled={assistant.busy || ready.length === 0} onClick={() => void add(result.areaId, ready)}>
            {tr('Add {count} selected dream(s)', { count: ready.length })}
          </Button>
        </Box>
      )}
    </AssistantCard>
  );
}

type Assistant = ReturnType<typeof useAssistant>;

function useAssistant(token: string) {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    getAiStatus(token).then(setStatus).catch(() => setStatus(null));
  }, [token]);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await action();
    } catch (runError) {
      setError(runError instanceof Error ? tr(runError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
      getAiStatus(token).then(setStatus).catch(() => undefined);
    }
  }

  async function save(failure: string, action: () => Promise<string>) {
    setBusy(true);
    setError('');
    try {
      setNotice(await action());
    } catch (saveError) {
      setError(saveError instanceof Error ? tr(saveError.message) : tr(failure));
    } finally {
      setBusy(false);
    }
  }

  return {
    status,
    busy,
    error,
    notice,
    ready: status?.available === true,
    visible: status !== null && status.reason !== 'OFF',
    run,
    save,
  };
}

function AssistantCard({ assistant, children }: { assistant: Assistant; children: ReactNode }) {
  return (
    <Card>
      <CardHeader
        title={tr('Plan assistant')}
        subheader={tr('AI suggestions you can edit before adding. Nothing is saved until you add it.')}
      />
      <CardContent>
        <Stack spacing={1.5}>
          {assistant.status && !assistant.ready && (
            <Typography variant="body2" color="text.secondary">
              {assistant.status.reason === 'NO_CONSENT'
                ? tr('Turn on AI consent in Account settings to get suggestions.')
                : tr('AI suggestions are unavailable right now.')}
            </Typography>
          )}
          {children}
          {assistant.busy && (
            <Box>
              <Loading />
              <Typography variant="caption" color="text.secondary">{tr('This can take up to 30 seconds.')}</Typography>
            </Box>
          )}
          {assistant.error && <Alert severity="error">{assistant.error}</Alert>}
          {assistant.notice && <Alert severity="success">{assistant.notice}</Alert>}
        </Stack>
      </CardContent>
    </Card>
  );
}

function SuggestionBox({ title, picked, onPick, badge, children }: {
  title: string;
  picked: boolean;
  onPick: (picked: boolean) => void;
  badge?: string;
  children: ReactNode;
}) {
  return (
    <Box sx={{ display: 'grid', gap: 1, p: 1.5, border: 1, borderColor: 'divider', borderRadius: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
        <Checkbox checked={picked} onChange={(event) => onPick(event.target.checked)}
          slotProps={{ input: { 'aria-label': tr('Add {title}', { title }) } }} />
        <Chip size="small" label={tr('AI suggestion')} />
        {badge && <Chip size="small" color="secondary" label={badge} />}
      </Box>
      {children}
    </Box>
  );
}
