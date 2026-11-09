import { FormEvent, useCallback, useEffect, useState } from 'react';
import { CheckCircle2, Circle, X } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router';
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
import { downloadPlanPdf } from '../api/accountApi';
import { listDreams } from '../api/dreamApi';
import { archiveMilestone, createMilestone, getPlanStatus, listMilestones } from '../api/planApi';
import { Button } from '../components/common/Button';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { useAuth } from '../context/AuthContext';
import type { Dream, Milestone, PlanStatus } from '../types/vision';
import { localDateIso } from '../utils/localDate';
import { saveBlob } from '../utils/saveBlob';
import { useI18n } from '../i18n/I18nContext';
import { PageSection } from './PageSection';
import { HelpWithSectionButton, PlanAssistantProvider } from '../components/ai/plan-assistant/PlanAssistant';
import type { AssistantSection } from '../types/vision';

const PLAN_SECTIONS = new Set<string>(['STATEMENT', 'GOALS', 'STEPS', 'DATES', 'RESOURCES', 'PEOPLE']);
// V11 Phase 7: the page itself is the plan assistant's default topic.
const WHOLE_PLAN = { section: 'WHOLE' as const };

function stagePath(key: string, dreamId: number) {
  switch (key) {
    case 'STRENGTHS':
      return `/strengths?dreamId=${dreamId}`;
    case 'DATES':
      return '/steps';
    case 'RESOURCES':
      return '/obstacles?create=obstacle';
    case 'PEOPLE':
      return '/partners?create=partner';
    default:
      return `/dreams/${dreamId}`;
  }
}

/**
 * FR-73 and FR-74: walks one dream from statement to people. The work itself
 * happens on the existing pages, so this page only shows what comes next and
 * goes there. Milestones record what has been reached along the way.
 */
export function PlanPage() {
  const { t } = useI18n();
  const { token } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [dreams, setDreams] = useState<Dream[] | null>(null);
  const [status, setStatus] = useState<PlanStatus | null>(null);
  const [milestones, setMilestones] = useState<Milestone[]>([]);
  const [error, setError] = useState('');
  const [title, setTitle] = useState('');
  const [achievedOn, setAchievedOn] = useState(() => localDateIso());
  const [actionError, setActionError] = useState('');
  // V11 Phase 7: bumped after each load, so the plan assistant reads the section check again.
  const [version, setVersion] = useState(0);

  const requestedId = Number(searchParams.get('dreamId'));
  const dreamId = dreams?.some((dream) => dream.id === requestedId) ? requestedId : dreams?.[0]?.id;

  useEffect(() => {
    if (!token) {
      return;
    }
    listDreams(token)
      .then(setDreams)
      .catch((loadError: unknown) => setError(loadError instanceof Error ? loadError.message : t('Unable to load your dreams.')));
  }, [token]);

  const loadDream = useCallback(async () => {
    if (!token || !dreamId) {
      return;
    }
    setError('');
    try {
      const [nextStatus, nextMilestones] = await Promise.all([getPlanStatus(token, dreamId), listMilestones(token, dreamId)]);
      setStatus(nextStatus);
      setMilestones(nextMilestones);
      setVersion((current) => current + 1);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : t('Unable to load this plan.'));
    }
  }, [token, dreamId]);

  useEffect(() => {
    void loadDream();
  }, [loadDream]);

  async function handleAddMilestone(event: FormEvent) {
    event.preventDefault();
    if (!token || !dreamId || !title.trim()) {
      return;
    }
    setActionError('');
    try {
      await createMilestone(token, { dreamId, title: title.trim(), achievedOn });
      setTitle('');
      await loadDream();
    } catch (saveError) {
      setActionError(saveError instanceof Error ? saveError.message : t('Unable to save this milestone.'));
    }
  }

  async function handleRemoveMilestone(id: number) {
    if (!token) {
      return;
    }
    setActionError('');
    try {
      await archiveMilestone(token, id);
      await loadDream();
    } catch (removeError) {
      setActionError(removeError instanceof Error ? removeError.message : t('Unable to remove this milestone.'));
    }
  }

  async function handleDownloadPdf() {
    if (!token || !dreamId) {
      return;
    }
    setActionError('');
    try {
      saveBlob(await downloadPlanPdf(token, dreamId), `dream-plan-${dreamId}.pdf`);
    } catch (downloadError) {
      setActionError(downloadError instanceof Error ? downloadError.message : t('Unable to download this plan.'));
    }
  }

  const nextStageKey = status?.stages.find((stage) => !stage.done)?.key;

  return (
    <PageSection
      title={t('Plan')}
      subtitle={t('Turn one dream into a written plan, stage by stage')}
      actions={dreamId ? <Button variant="secondary" onClick={() => void handleDownloadPdf()}>{t('Download PDF')}</Button> : undefined}
    >
      {error && <ErrorMessage message={error} onRetry={() => void loadDream()} />}
      {!dreams && !error && <Loading />}
      {dreams && dreams.length === 0 && (
        <EmptyState action={<Button onClick={() => navigate('/dreams?create=dream')}>{t('Create your first dream')}</Button>}>
          {t('A plan starts from a dream.')}
        </EmptyState>
      )}
      {dreams && dreams.length > 0 && dreamId && token && (
        <PlanAssistantProvider token={token} surface="PLAN" dreamId={dreamId} current={WHOLE_PLAN} version={version}
          onPlanChanged={() => void loadDream()}>
        <Stack spacing={2}>
          {actionError && <Alert severity="error">{actionError}</Alert>}
          <label>
            {t('Dream')}
            <FormControl fullWidth size="small">
              <Select
                SelectDisplayProps={{ 'aria-label': t('Dream') }}
                value={String(dreamId)}
                onChange={(event) => setSearchParams({ dreamId: event.target.value })}
              >
                {dreams.map((dream) => <MenuItem value={String(dream.id)} key={dream.id}>{dream.title}</MenuItem>)}
              </Select>
            </FormControl>
          </label>

          {status && status.dreamId === dreamId && (
            <Card>
              <CardHeader
                title={t('Your plan')}
                subheader={status.planReady ? t('The core of this plan is written. Keep adding what it still needs.') : t('Work through these in order.')}
              />
              <CardContent>
                <Stack spacing={1.5}>
                  {status.stages.map((stage) => (
                    <Box key={stage.key} sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
                      <Box sx={{ mt: 0.25, flexShrink: 0 }}>
                        {stage.done ? <CheckCircle2 size={18} aria-label={t('Done')} /> : <Circle size={18} aria-label={t('Not done')} />}
                      </Box>
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>{t(stage.label)}</Typography>
                        <Typography variant="body2" color="text.secondary">{t(stage.detail)}</Typography>
                      </Box>
                      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                        {PLAN_SECTIONS.has(stage.key) && <HelpWithSectionButton section={stage.key as AssistantSection} />}
                        {stage.key === nextStageKey && (
                          <Button variant="secondary" onClick={() => navigate(stagePath(stage.key, dreamId))}>{t('Do this next')}</Button>
                        )}
                      </Box>
                    </Box>
                  ))}
                </Stack>
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader title={t('Milestones')} subheader={t('Results you have reached on the way to this dream')}
              action={<HelpWithSectionButton section="MILESTONES" />} />
            <CardContent>
              <Stack spacing={1.5}>
                {milestones.length === 0 && <EmptyState>{t('No milestones yet. Your first one goes here when you reach it.')}</EmptyState>}
                {milestones.map((milestone) => (
                  <Box key={milestone.id} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="body2" sx={{ flex: 1 }}>
                      <strong>{milestone.achievedOn}</strong> · {milestone.title}
                    </Typography>
                    <IconButton size="small" aria-label={t('Remove {name}', { name: milestone.title })} onClick={() => void handleRemoveMilestone(milestone.id)}>
                      <X size={14} />
                    </IconButton>
                  </Box>
                ))}
                <Box component="form" onSubmit={(event) => void handleAddMilestone(event)} sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                  <Box sx={{ flex: 1, minWidth: 180 }}>
                    <Input value={title} onChange={(event) => setTitle(event.target.value)} placeholder={t('What did you reach?')} aria-label={t('Milestone')} />
                  </Box>
                  <Input type="date" value={achievedOn} onChange={(event) => setAchievedOn(event.target.value)} aria-label={t('Date reached')} required />
                  <Button type="submit" variant="secondary" disabled={!title.trim()}>{t('Add')}</Button>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Stack>
        </PlanAssistantProvider>
      )}
    </PageSection>
  );
}
