import { useCallback, useEffect, useRef, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Step from '@mui/material/Step';
import StepButton from '@mui/material/StepButton';
import Stepper from '@mui/material/Stepper';
import Typography from '@mui/material/Typography';
import { Link as RouterLink, useParams } from 'react-router';
import { getAiStatus } from '../api/aiApi';
import { addPlanDraft, discardPlanDraft, getPlanCapacity, getPlanDraft, savePlanDraft, undoPlan } from '../api/aiPlanApi';
import { listDreams } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import { listSteps } from '../api/stepApi';
import { listTasks } from '../api/taskApi';
import { Breadcrumbs } from '../components/common/Breadcrumbs';
import { Button } from '../components/common/Button';
import { ConfirmDialog } from '../components/common/ConfirmDialog';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { ClarifyStage } from '../components/plan-builder/ClarifyStage';
import { GoalsStage } from '../components/plan-builder/GoalsStage';
import { HelpersStage } from '../components/plan-builder/HelpersStage';
import { type NewContact, NewContactsOutreach } from '../components/plan-builder/NewContactsOutreach';
import { ReviewStage } from '../components/plan-builder/ReviewStage';
import { StepsStage } from '../components/plan-builder/StepsStage';
import { TasksStage } from '../components/plan-builder/TasksStage';
import { type DraftUpdate, STAGES, applyAssistantChange, countNew, readiness, syncWithPlan } from '../components/plan-builder/draftModel';
import { HelpWithSectionButton, PlanAssistantProvider } from '../components/ai/plan-assistant/PlanAssistant';
import { useAuth } from '../context/AuthContext';
import { useI18n } from '../i18n/I18nContext';
import { tr } from '../i18n/translate';
import type { Dream, Goal, PlanAddResult, PlanCapacity, PlanDraftContent, PlanStage, TaskItem, VisionStep } from '../types/vision';
import { PageSection } from './PageSection';

const SAVE_DELAY_MS = 800;
type SaveState = 'idle' | 'saving' | 'saved' | 'error';

/**
 * V11 Phase 2: Build my plan (PB-FR-01 to PB-FR-05, PB-FR-30). Six stages on
 * one screen, any of which can be skipped or revisited. The draft starts from
 * the dream's plan, is saved on the server as the participant works, and is
 * picked up again on any device. Nothing becomes plan data until Add plan,
 * which saves everything at once (PB-FR-31) and can be undone (PB-FR-32).
 * Without AI the same stages work as a guided form.
 */
export function PlanBuilderPage() {
  const { dreamId } = useParams();
  const id = Number(dreamId);
  const { token } = useAuth();
  const { language } = useI18n();

  const [dream, setDream] = useState<Dream | null>(null);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [steps, setSteps] = useState<VisionStep[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [aiReady, setAiReady] = useState(false);
  const [content, setContent] = useState<PlanDraftContent>({ goals: [] });
  const [stage, setStage] = useState<PlanStage>('CLARIFY');
  const [resumed, setResumed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saveState, setSaveState] = useState<SaveState>('idle');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState('');
  const [added, setAdded] = useState<PlanAddResult | null>(null);
  const [notice, setNotice] = useState('');
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  // PB-FR-27: the new To Contact partners from the last add, for outreach drafts.
  const [newContacts, setNewContacts] = useState<NewContact[]>([]);
  // PB-FR-16: hours a week and the saved plan's load, for the capacity check.
  const [capacity, setCapacity] = useState<PlanCapacity | null>(null);
  // V11 Phase 7: bumped after each draft save, so the plan assistant reads the saved draft again.
  const [savedVersion, setSavedVersion] = useState(0);
  const dirty = useRef(false);
  const stageTop = useRef<HTMLDivElement>(null);
  const latest = useRef({ content, stage });
  latest.current = { content, stage };

  const loadPlan = useCallback(async () => {
    if (!token) {
      return null;
    }
    const [dreams, goalData, stepData, taskData] = await Promise.all([
      listDreams(token), listGoals(token), listSteps(token), listTasks(token),
    ]);
    const found = dreams.find((item) => item.id === id) ?? null;
    setDream(found);
    setGoals(goalData);
    setSteps(stepData);
    setTasks(taskData);
    return { found, goalData };
  }, [token, id]);

  useEffect(() => {
    if (!token) {
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const [plan, draft] = await Promise.all([loadPlan(), getPlanDraft(token, id)]);
        if (cancelled || !plan) {
          return;
        }
        setContent(syncWithPlan(draft?.content ?? { goals: [] }, id, plan.goalData));
        setStage(draft?.stage ?? 'CLARIFY');
        setResumed(draft !== null);
        setError(plan.found ? '' : tr('Dream not found.'));
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load your plan.'));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();
    getAiStatus(token).then((status) => !cancelled && setAiReady(status.available)).catch(() => undefined);
    getPlanCapacity(token).then((value) => !cancelled && setCapacity(value)).catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [token, id, loadPlan]);

  const save = useCallback(async () => {
    if (!token) {
      return;
    }
    dirty.current = false;
    setSaveState('saving');
    try {
      await savePlanDraft(token, id, latest.current.stage, latest.current.content);
      setSaveState(dirty.current ? 'saving' : 'saved');
      setSavedVersion((current) => current + 1);
    } catch {
      setSaveState('error');
    }
  }, [token, id]);

  // PB-FR-30: save shortly after each change.
  useEffect(() => {
    if (!dirty.current) {
      return undefined;
    }
    const timer = window.setTimeout(() => void save(), SAVE_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [content, stage, save]);

  function change(update: DraftUpdate) {
    dirty.current = true;
    setAdded(null);
    setNotice('');
    setContent((current) => (typeof update === 'function' ? update(current) : update));
  }

  function goTo(next: PlanStage) {
    dirty.current = true;
    setStage(next);
    stageTop.current?.scrollIntoView?.({ block: 'start', behavior: 'smooth' });
  }

  async function addPlan() {
    if (!token) {
      return;
    }
    setBusy(true);
    setActionError('');
    try {
      // The server adds what it has saved, so save the latest edits first.
      await savePlanDraft(token, id, latest.current.stage, latest.current.content);
      dirty.current = false;
      const result = await addPlanDraft(token, id);
      // New partners are created in the order of the draft's helpers that link no partner.
      const fresh = (latest.current.content.helpers ?? []).filter((helper) => helper.partnerId == null);
      setNewContacts((result.partnerIds ?? []).map((partnerId, index) => ({
        partnerId,
        name: fresh[index]?.name?.trim() || fresh[index]?.role || tr('New contact'),
        role: fresh[index]?.role ?? '',
      })));
      const plan = await loadPlan();
      setContent(syncWithPlan({ goals: [], clarifications: latest.current.content.clarifications }, id, plan?.goalData ?? []));
      setAdded(result);
      setNotice(tr('Your plan is added: {goals} goal(s), {steps} step(s) and {tasks} task(s).', {
        goals: result.goalIds.length, steps: result.stepIds.length, tasks: result.taskIds?.length ?? 0,
      }));
      setSaveState('idle');
    } catch (addError) {
      setActionError(addError instanceof Error ? tr(addError.message) : tr('Unable to add your plan.'));
    } finally {
      setBusy(false);
    }
  }

  async function undo() {
    if (!token || !added) {
      return;
    }
    setBusy(true);
    setActionError('');
    try {
      await undoPlan(token, added);
      setAdded(null);
      setNewContacts([]);
      setNotice(tr('Undone. What was just added is archived.'));
      const plan = await loadPlan();
      setContent(syncWithPlan({ goals: [], clarifications: latest.current.content.clarifications }, id, plan?.goalData ?? []));
    } catch (undoError) {
      setActionError(undoError instanceof Error ? tr(undoError.message) : tr('Unable to undo.'));
    } finally {
      setBusy(false);
    }
  }

  async function discard() {
    setConfirmDiscard(false);
    if (!token) {
      return;
    }
    try {
      await discardPlanDraft(token, id);
      dirty.current = false;
      setContent(syncWithPlan({ goals: [] }, id, goals));
      setStage('CLARIFY');
      setResumed(false);
      setSaveState('idle');
      setNotice(tr('Draft discarded.'));
    } catch (discardError) {
      setActionError(discardError instanceof Error ? tr(discardError.message) : tr('Unable to discard the draft.'));
    }
  }

  if (loading) {
    return <PageSection title={tr('Build my plan')} subtitle=""><Loading /></PageSection>;
  }
  if (error || !dream) {
    return <PageSection title={tr('Build my plan')} subtitle=""><ErrorMessage message={error || tr('Dream not found.')} /></PageSection>;
  }

  const counts = countNew(content);
  const problems = readiness(content, steps).filter(({ item }) => item.level === 'error').length;
  const index = STAGES.findIndex((item) => item.id === stage);
  const stageCount = (target: PlanStage) => {
    if (target === 'GOALS') return counts.goals;
    if (target === 'STEPS') return counts.steps;
    if (target === 'TASKS') return counts.tasks;
    if (target === 'HELPERS') return counts.helpers + counts.risks;
    return 0;
  };

  const currentTopic = { section: stage };

  return (
    <PageSection
      title={tr('Build my plan')}
      subtitle={dream.title}
      actions={counts.total > 0 || resumed
        ? <Button variant="secondary" onClick={() => setConfirmDiscard(true)}>{tr('Discard draft')}</Button>
        : undefined}
    >
      <Breadcrumbs crumbs={[
        { label: tr('All dreams'), to: '/vision-map' },
        { label: dream.title, to: `/dreams/${dream.id}` },
        { label: tr('Build my plan') },
      ]} />
      <PlanAssistantProvider token={token ?? ''} surface="BUILDER" dreamId={dream.id} current={currentTopic} version={savedVersion}
        onDraftChange={(assistantChange) => change((current) => applyAssistantChange(current, assistantChange))}
        onDreamSaved={setDream}>
      <Stack spacing={2}>
        {resumed && !notice && <Alert severity="info" onClose={() => setResumed(false)}>{tr('Picked up where you left off.')}</Alert>}
        {!aiReady && (
          <Alert severity="info">{tr('AI suggestions are off. You can still build your plan step by step with the questions at each stage.')}</Alert>
        )}
        <Box ref={stageTop} sx={{ overflowX: 'auto', pb: 1, scrollMarginTop: 80 }}>
          <Stepper nonLinear activeStep={index} sx={{ minWidth: 640 }}>
            {STAGES.map((item) => (
              <Step key={item.id} completed={stageCount(item.id) > 0}>
                <StepButton onClick={() => goTo(item.id)}
                  optional={stageCount(item.id) > 0
                    ? <Typography variant="caption">{tr('{count} new', { count: stageCount(item.id) })}</Typography>
                    : undefined}>
                  {tr(item.label)}
                </StepButton>
              </Step>
            ))}
          </Stepper>
        </Box>
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center' }}>
          <Typography variant="body2" color="text.secondary">
            {tr('{count} new item(s)', { count: counts.total })}
            {problems > 0 ? ` · ${tr('{count} need attention', { count: problems })}` : ''}
          </Typography>
          <Typography variant="caption" color={saveState === 'error' ? 'error' : 'text.secondary'} aria-live="polite">
            {saveState === 'saving' && tr('Saving draft…')}
            {saveState === 'saved' && tr('Draft saved')}
            {saveState === 'error' && tr('Could not save the draft. Your changes are still here; try again.')}
          </Typography>
        </Box>
        {notice && (
          <Alert severity="success" action={added
            ? <Button variant="secondary" disabled={busy} onClick={() => void undo()}>{tr('Undo')}</Button>
            : undefined}>
            {notice}{' '}
            {added && <Link component={RouterLink} to={`/dreams/${dream.id}`}>{tr('See it on the vision map')}</Link>}
          </Alert>
        )}
        {actionError && <Alert severity="error">{actionError}</Alert>}
        {added && aiReady && newContacts.length > 0 && (
          <Card>
            <CardContent>
              <NewContactsOutreach contacts={newContacts} dreamId={dream.id} dreamTitle={dream.title} token={token ?? ''} language={language} />
            </CardContent>
          </Card>
        )}
        <Card>
          <CardContent>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 1, flexWrap: 'wrap', mb: 2 }}>
              <Typography variant="h6">{`${index + 1}. ${tr(STAGES[index].label)}`}</Typography>
              <HelpWithSectionButton section={stage} />
            </Box>
            {stage === 'CLARIFY' && (
              <ClarifyStage dream={dream} content={content} onChange={change} onDreamChange={setDream} token={token ?? ''}
                language={language} aiReady={aiReady} onDone={() => goTo('GOALS')} />
            )}
            {stage === 'GOALS' && (
              <GoalsStage dream={dream} content={content} onChange={change} token={token ?? ''} language={language} aiReady={aiReady} />
            )}
            {stage === 'STEPS' && (
              <StepsStage dream={dream} content={content} onChange={change} existingSteps={steps} token={token ?? ''}
                language={language} aiReady={aiReady} />
            )}
            {stage === 'TASKS' && (
              <TasksStage dream={dream} content={content} onChange={change} existingSteps={steps} existingTasks={tasks}
                token={token ?? ''} language={language} aiReady={aiReady} />
            )}
            {stage === 'HELPERS' && (
              <HelpersStage dream={dream} content={content} onChange={change} token={token ?? ''} language={language} aiReady={aiReady} />
            )}
            {stage === 'REVIEW' && (
              <ReviewStage content={content} onChange={change} existingSteps={steps} busy={busy} onGoTo={goTo} onAdd={() => void addPlan()}
                dreamId={dream.id} token={token ?? ''} language={language} aiReady={aiReady}
                saveDraft={async () => {
                  if (token) {
                    await savePlanDraft(token, id, latest.current.stage, latest.current.content);
                    dirty.current = false;
                    setSaveState('saved');
                  }
                }}
                capacity={capacity} onCapacityChange={setCapacity} />
            )}
          </CardContent>
        </Card>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
          <Button variant="secondary" disabled={index === 0} onClick={() => goTo(STAGES[index - 1].id)}>{tr('Back')}</Button>
          {index < STAGES.length - 1 && <Button onClick={() => goTo(STAGES[index + 1].id)}>{tr('Next')}</Button>}
        </Box>
      </Stack>
      </PlanAssistantProvider>
      {confirmDiscard && (
        <ConfirmDialog
          title={tr('Discard this draft?')}
          message={tr('Everything new in this draft will be deleted. Your saved plan is not changed.')}
          confirmLabel={tr('Discard draft')}
          danger
          onConfirm={() => void discard()}
          onClose={() => setConfirmDiscard(false)}
        />
      )}
    </PageSection>
  );
}
