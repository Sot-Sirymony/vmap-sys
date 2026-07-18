import { FormEvent, useEffect, useState } from 'react';
import { ListChecks } from 'lucide-react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { listDreams } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import { archiveIdealPartnerProfile, createIdealPartnerProfile, listIdealPartnerProfiles, updateIdealPartnerProfile } from '../api/idealPartnerProfileApi';
import { archiveStep, permanentlyDeleteStep, createStep, getStepArchiveImpact, listSteps, restoreStep, updateStep } from '../api/stepApi';
import { listTasks } from '../api/taskApi';
import { listVisionAreas } from '../api/visionAreaApi';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import { BulkArchiveAction } from '../components/common/BulkArchiveAction';
import { Button } from '../components/common/Button';
import { CrudModalForm } from '../components/common/CrudModalForm';
import { Modal } from '../components/common/Modal';
import { DataTable, type DataTableColumn } from '../components/common/DataTable';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { QuickAddRow } from '../components/common/QuickAddRow';
import { ProgressBar } from '../components/common/ProgressBar';
import { RowActionsMenu } from '../components/common/RowActionsMenu';
import { SearchBar } from '../components/common/SearchBar';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { StatusBadge } from '../components/common/StatusBadge';
import { StatusBoard } from '../components/common/StatusBoard';
import { Textarea } from '../components/common/Textarea';
import { ViewToggle, type ViewMode } from '../components/common/ViewToggle';
import { useAuth } from '../context/AuthContext';
import { useCrudEntity } from '../hooks/useCrudEntity';
import { FilterSelect, optionsFromEntities, optionsFromLabels } from '../components/common/FilterSelect';
import { useUrlFilter, useUrlFilterBatch, useUrlFlag } from '../hooks/useUrlFilter';
import type { Dream, Goal, IdealPartnerProfile, Priority, TaskItem, VisionArea, VisionStep, VisionStepRequest, WorkStatus } from '../types/vision';
import { priorityLabels, workStatusLabels } from '../utils/enumLabels';
import { isOverdue } from '../utils/overdue';
import { matchesSearch } from '../utils/search';
import { priorityRank, workStatusRank } from '../utils/sortRank';
import { PageSection } from './PageSection';

export function StepsPage() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const crud = useCrudEntity<VisionStep, VisionStepRequest>({
    token,
    entityLabel: 'steps',
    list: listSteps,
    create: createStep,
    update: updateStep,
    archive: archiveStep,
    permanentlyDelete: permanentlyDeleteStep,
    restore: restoreStep,
  });
  const [goals, setGoals] = useState<Goal[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [visionAreas, setVisionAreas] = useState<VisionArea[]>([]);
  const [goalId, setGoalId] = useState('');
  const [quickParentId, setQuickParentId] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [sequenceNumber, setSequenceNumber] = useState(1);
  const [complex, setComplex] = useState(false);
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [targetDate, setTargetDate] = useState('');
  const [status, setStatus] = useState<WorkStatus>('NOT_STARTED');
  // In the URL, not component state: the dashboard links straight into a
  // filtered view, and a filtered list stays shareable and bookmarkable.
  const [filterVisionAreaId] = useUrlFilter('visionAreaId');
  const [filterGoalId, setFilterGoalId] = useUrlFilter('goalId');
  // One navigation for multi-key changes — chained setters undo each other.
  const setFilterBatch = useUrlFilterBatch();
  const [filterStatus, setFilterStatus] = useUrlFilter('status');
  const [filterPriority, setFilterPriority] = useUrlFilter('priority');
  const [filterOverdueOnly, setFilterOverdueOnly] = useUrlFlag('overdue');
  // The dashboard's "complex steps with no tasks" finding lands here.
  const [filterComplexOnly, setFilterComplexOnly] = useUrlFlag('complex');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [searchTerm, setSearchTerm] = useState('');
  const [searchParams, setSearchParams] = useSearchParams();
  const [autoOpenCreate, setAutoOpenCreate] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  // Ideal partner profiles (FR-15.1): one per step, edited in a small modal.
  const [profiles, setProfiles] = useState<IdealPartnerProfile[]>([]);
  const [profileStep, setProfileStep] = useState<VisionStep | null>(null);
  const [profileExperience, setProfileExperience] = useState('');
  const [profileTraits, setProfileTraits] = useState('');
  const [profileMotivation, setProfileMotivation] = useState('');
  const [profileOffer, setProfileOffer] = useState('');
  const [profileSaving, setProfileSaving] = useState(false);

  // Arrived from a goal's "Add step" shortcut: pre-select that goal and open the
  // create form, then strip the params so a refresh doesn't reopen it.
  useEffect(() => {
    if (searchParams.get('create') !== 'step') {
      return;
    }
    const parent = searchParams.get('parent');
    if (parent) {
      setGoalId(parent);
    }
    setAutoOpenCreate(true);
    const next = new URLSearchParams(searchParams);
    next.delete('create');
    next.delete('parent');
    setSearchParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!token) {
      return;
    }
    void crud.reload();
    void Promise.all([listGoals(token), listTasks(token), listDreams(token), listVisionAreas(token)]).then(
      ([goalData, taskData, dreamData, areaData]) => {
        setGoals(goalData);
        setTasks(taskData);
        setDreams(dreamData);
        setVisionAreas(areaData);
        setGoalId((current) => current || String(goalData[0]?.id ?? ''));
        setQuickParentId((current) => current || String(goalData[0]?.id ?? ''));
      },
    );
    void listIdealPartnerProfiles(token).then(setProfiles);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  const profileByStepId = new Map(profiles.map((profile) => [profile.stepId, profile]));

  function openProfileEditor(step: VisionStep) {
    const existing = profileByStepId.get(step.id);
    setProfileExperience(existing?.requiredExperience ?? '');
    setProfileTraits(existing?.characterTraits ?? '');
    setProfileMotivation(existing?.motivation ?? '');
    setProfileOffer(existing?.offerInReturn ?? '');
    setProfileStep(step);
  }

  async function handleProfileSave() {
    if (!token || !profileStep) {
      return;
    }
    setProfileSaving(true);
    const request = {
      stepId: profileStep.id,
      requiredExperience: profileExperience,
      characterTraits: profileTraits,
      motivation: profileMotivation,
      offerInReturn: profileOffer,
    };
    try {
      const existing = profileByStepId.get(profileStep.id);
      if (existing) {
        await updateIdealPartnerProfile(token, existing.id, request);
      } else {
        await createIdealPartnerProfile(token, request);
      }
      setProfiles(await listIdealPartnerProfiles(token));
      setProfileStep(null);
    } catch (profileError) {
      crud.setError(profileError instanceof Error ? profileError.message : 'Unable to save the ideal partner profile.');
    } finally {
      setProfileSaving(false);
    }
  }

  async function handleProfileArchive() {
    if (!token || !profileStep) {
      return;
    }
    const existing = profileByStepId.get(profileStep.id);
    if (!existing) {
      setProfileStep(null);
      return;
    }
    setProfileSaving(true);
    try {
      await archiveIdealPartnerProfile(token, existing.id);
      setProfiles(await listIdealPartnerProfiles(token));
      setProfileStep(null);
    } catch (profileError) {
      crud.setError(profileError instanceof Error ? profileError.message : 'Unable to remove the ideal partner profile.');
    } finally {
      setProfileSaving(false);
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!goalId) {
      return false;
    }
    const wasCreating = crud.editingId === null;
    const success = await crud.save({
      goalId: Number(goalId),
      title,
      description,
      sequenceNumber,
      complex,
      priority,
      targetDate: targetDate || undefined,
      status,
    });
    if (success) {
      setTitle('');
      setDescription('');
      if (wasCreating) {
        setSequenceNumber(sequenceNumber + 1);
      }
    }
    return success;
  }

  function startEdit(step: VisionStep) {
    crud.startEdit(step.id);
    setGoalId(String(step.goalId));
    setTitle(step.title);
    setDescription(step.description ?? '');
    setSequenceNumber(step.sequenceNumber);
    setComplex(step.complex);
    setPriority(step.priority);
    setTargetDate(step.targetDate ?? '');
    setStatus(step.status);
  }

  function cancelEdit() {
    crud.cancelEdit();
    setTitle('');
    setDescription('');
    setComplex(false);
    setPriority('HIGH');
    setTargetDate('');
    setStatus('NOT_STARTED');
  }

  // FR-22.1 quick-add: sequence number continues from the goal's last step;
  // defaults keep BR-16 satisfied.
  async function handleQuickAdd(title: string) {
    if (!token || !quickParentId) {
      return;
    }
    const goalSteps = crud.items.filter((step) => String(step.goalId) === quickParentId);
    const nextSequence = goalSteps.reduce((max, step) => Math.max(max, step.sequenceNumber), 0) + 1;
    await createStep(token, {
      goalId: Number(quickParentId),
      title,
      sequenceNumber: nextSequence,
      complex: false,
      priority: 'MEDIUM',
      status: 'NOT_STARTED',
    });
    await crud.reload();
  }

  // Board drag/dropdown move. There is no status PATCH endpoint for steps, so
  // the move sends a full update built from the loaded entity.
  async function handleMove(step: VisionStep, nextStatus: WorkStatus) {
    if (!token || step.status === nextStatus) {
      return;
    }
    try {
      await updateStep(token, step.id, {
        goalId: step.goalId,
        title: step.title,
        description: step.description,
        sequenceNumber: step.sequenceNumber,
        complex: step.complex,
        priority: step.priority,
        targetDate: step.targetDate,
        status: nextStatus,
      });
      await crud.reload();
    } catch (moveError) {
      crud.setError(moveError instanceof Error ? moveError.message : 'Unable to update step status.');
    }
  }

  async function archiveImpactMessage(step: VisionStep) {
    if (!token) {
      return 'Archive this step?';
    }
    const impact = await getStepArchiveImpact(token, step.id);
    return `Archiving "${step.title}" also archives ${impact.tasks} task(s). Everything can be restored later with "Show archived".`;
  }

  const dreamById = new Map(dreams.map((dream) => [dream.id, dream]));
  const goalsForFilter = filterVisionAreaId
    ? goals.filter((goal) => String(dreamById.get(goal.dreamId)?.visionAreaId ?? '') === filterVisionAreaId)
    : goals;

  const filteredSteps = crud.items.filter((step) => {
    if (filterGoalId && String(step.goalId) !== filterGoalId) {
      return false;
    }
    if (filterVisionAreaId) {
      const goal = goals.find((item) => item.id === step.goalId);
      const dream = goal ? dreamById.get(goal.dreamId) : undefined;
      if (String(dream?.visionAreaId ?? '') !== filterVisionAreaId) {
        return false;
      }
    }
    if (filterStatus && step.status !== filterStatus) {
      return false;
    }
    if (filterPriority && step.priority !== filterPriority) {
      return false;
    }
    if (filterOverdueOnly && !isOverdue(step.targetDate, step.status)) {
      return false;
    }
    if (filterComplexOnly && !step.complex) {
      return false;
    }
    if (!matchesSearch(searchTerm, step.code, step.title, step.description)) {
      return false;
    }
    return true;
  });

  function taskCountFor(step: VisionStep) {
    return tasks.filter((task) => task.stepId === step.id).length;
  }

  // Shared by the table's action column and the board's cards, so both offer
  // the same row actions.
  function renderStepActions(step: VisionStep) {
    return (
      <RowActionsMenu
        onEdit={() => startEdit(step)}
        onArchive={() => void crud.archive(step.id)}
        onRestore={() => void crud.restore(step.id)}
        onDeletePermanently={() => void crud.permanentlyDelete(step.id)}
        archived={step.archived}
        confirmArchive={() => archiveImpactMessage(step)}
        extraActions={[
          { label: 'View tasks', onClick: () => navigate(`/tasks?stepId=${step.id}`) },
          { label: 'Add task', onClick: () => navigate(`/tasks?create=task&parent=${step.id}`) },
          ...(step.complex
            ? [{
                label: profileByStepId.has(step.id) ? 'Edit ideal partner profile' : 'Define ideal partner profile',
                onClick: () => openProfileEditor(step),
              }]
            : []),
        ]}
        label="Step actions"
      />
    );
  }

  const columns: DataTableColumn<VisionStep>[] = [
    { key: 'code', label: 'Code', sortValue: (step) => step.code, render: (step) => step.code },
    {
      key: 'title',
      label: 'Step',
      sortValue: (step) => step.title,
      sx: { fontWeight: 500 },
      render: (step) => (
        <>
          {step.title}
          {step.complex && taskCountFor(step) === 0 && (
            <div className="coaching-panel step-needs-tasks">
              <strong>Complex step, no tasks yet</strong>
              <p>Break this into executable tasks so it can actually move forward.</p>
              <Link to={`/tasks?stepId=${step.id}`}>Break this into tasks →</Link>
            </div>
          )}
        </>
      ),
    },
    {
      key: 'sequenceNumber',
      label: 'Seq',
      sortValue: (step) => step.sequenceNumber,
      render: (step) => step.sequenceNumber,
    },
    {
      key: 'priority',
      label: 'Priority',
      sortValue: (step) => priorityRank(step.priority),
      render: (step) => <PriorityBadge priority={step.priority} />,
    },
    {
      key: 'status',
      label: 'Status',
      sortValue: (step) => workStatusRank(step.status),
      render: (step) => <StatusBadge status={step.status} />,
    },
    {
      key: 'progress',
      label: 'Progress',
      sortValue: (step) => Number(step.progressPercent),
      render: (step) => <ProgressBar value={Number(step.progressPercent)} />,
    },
    {
      key: 'taskCount',
      label: 'Tasks',
      sortValue: (step) => taskCountFor(step),
      render: (step) => taskCountFor(step),
    },
    {
      key: 'actions',
      label: 'Action',
      className: 'row-actions',
      render: (step) => renderStepActions(step),
    },
  ];

  const formFields = (
    <>
      <label>
        Goal
        <FormControl fullWidth size="small" required>
          <Select value={goalId} onChange={(event) => setGoalId(event.target.value)}>
            {goals.map((goal) => <MenuItem value={String(goal.id)} key={goal.id}>{goal.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Title
        <Input value={title} onChange={(event) => setTitle(event.target.value)} required autoFocus />
      </label>
      <label>
        Sequence
        <Input type="number" min="0" value={sequenceNumber} onChange={(event) => setSequenceNumber(Number(event.target.value))} required />
      </label>
      <label>
        Target Date
        <Input type="date" value={targetDate} onChange={(event) => setTargetDate(event.target.value)} />
      </label>
      <label>
        Priority
        <FormControl fullWidth size="small">
          <Select value={priority} onChange={(event) => setPriority(event.target.value as Priority)}>
            <MenuItem value="LOW">Low</MenuItem>
            <MenuItem value="MEDIUM">Medium</MenuItem>
            <MenuItem value="HIGH">High</MenuItem>
            <MenuItem value="CRITICAL">Critical</MenuItem>
          </Select>
        </FormControl>
      </label>
      <label>
        Status
        <FormControl fullWidth size="small">
          <Select value={status} onChange={(event) => setStatus(event.target.value as WorkStatus)}>
            <MenuItem value="NOT_STARTED">Not Started</MenuItem>
            <MenuItem value="IN_PROGRESS">In Progress</MenuItem>
            <MenuItem value="WAITING">Waiting</MenuItem>
            <MenuItem value="BLOCKED">Blocked</MenuItem>
            <MenuItem value="PAUSED">Paused</MenuItem>
            <MenuItem value="COMPLETED">Completed</MenuItem>
          </Select>
        </FormControl>
      </label>
      <label className="field-full">
        <span className="inline-meta">
          <Checkbox checked={complex} onChange={(event) => setComplex(event.target.checked)} />
          Complex step
        </span>
      </label>
      <label className="field-full">
        Description
        <Textarea value={description} onChange={(event) => setDescription(event.target.value)} />
      </label>
    </>
  );

  return (
    <PageSection title="Steps" subtitle="Break goals into ordered action stages.">
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Create step"
        editTitle="Edit Step"
        saving={crud.saving}
        disabled={goals.length === 0}
        autoOpenCreate={autoOpenCreate}
        creating={createOpen}
        onCreatingChange={setCreateOpen}
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {crud.loading && <Loading variant="table" />}
      {crud.error && <ErrorMessage message={crud.error} onRetry={() => void crud.reload()} />}
      <Card className="filter-bar flex-row">
        <SearchBar value={searchTerm} onChange={setSearchTerm} entityLabel="steps" />
        <FilterSelect
          label="Vision Area"
          value={filterVisionAreaId}
          onChange={(value) => setFilterBatch({ visionAreaId: value, goalId: '' })}
          options={optionsFromEntities(visionAreas, (area) => area.name)}
        />
        <FilterSelect
          label="Goal"
          value={filterGoalId}
          onChange={setFilterGoalId}
          options={optionsFromEntities(goalsForFilter, (goal) => goal.title)}
        />
        <FilterSelect
          label="Status"
          value={filterStatus}
          onChange={setFilterStatus}
          options={optionsFromLabels(workStatusLabels)}
        />
        <FilterSelect
          label="Priority"
          value={filterPriority}
          onChange={setFilterPriority}
          options={optionsFromLabels(priorityLabels)}
        />
        <label className="checkbox-field">
          <Checkbox checked={filterOverdueOnly} onChange={(event) => setFilterOverdueOnly(event.target.checked)} />
          Overdue only
        </label>
        <label className="checkbox-field">
          <Checkbox checked={filterComplexOnly} onChange={(event) => setFilterComplexOnly(event.target.checked)} />
          Complex only
        </label>
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      <div className="view-toggle-row">
        <ViewToggle value={viewMode} onChange={setViewMode} label="Step view" />
      </div>
      {crud.items.length > 0 && goals.length > 0 && (
        <QuickAddRow
          parentLabel="Goal"
          parents={goals.map((goal) => ({ value: String(goal.id), label: goal.title }))}
          parentValue={filterGoalId || quickParentId}
          onParentChange={setQuickParentId}
          placeholder="New step title — Enter to add"
          onAdd={handleQuickAdd}
        />
      )}
      {!crud.loading && crud.items.length === 0 ? (
        <EmptyState
          headline="No steps yet"
          icon={ListChecks}
          action={
            goals.length === 0 ? (
              <Button type="button" onClick={() => navigate('/goals?create=goal')}>Create a goal first</Button>
            ) : (
              <Button type="button" onClick={() => setCreateOpen(true)}>Create your first step</Button>
            )
          }
        >
          Steps are the action stages of a goal. Mark a step complex to break it into tasks.
        </EmptyState>
      ) : null}
      {crud.items.length > 0 && viewMode === 'board' && (
        <StatusBoard
          items={filteredSteps}
          columns={Object.entries(workStatusLabels).map(([value, label]) => ({ value: value as WorkStatus, label }))}
          statusOf={(step) => step.status}
          entityLabel="steps"
          onMove={(step, nextStatus) => void handleMove(step, nextStatus)}
          cardClassName={(step) => (isOverdue(step.targetDate, step.status) ? 'list-card--overdue' : '')}
          renderCard={(step) => (
            <>
              <strong>{step.title}</strong>
              <p>{step.code} · Seq {step.sequenceNumber} · {taskCountFor(step)} task(s)</p>
              <div className="inline-meta">
                <PriorityBadge priority={step.priority} />
                {step.complex && <span>Complex</span>}
                <span>{step.progressPercent}%</span>
              </div>
              <ProgressBar value={Number(step.progressPercent)} />
              {step.complex && taskCountFor(step) === 0 && (
                <p><Link to={`/tasks?stepId=${step.id}`}>Break this into tasks →</Link></p>
              )}
            </>
          )}
          cardActions={(step) => renderStepActions(step)}
        />
      )}
      {crud.items.length > 0 && viewMode === 'list' && (
      <Card>
        <CardContent>
          <DataTable
            rows={filteredSteps}
            columns={columns}
            emptyMessage="No steps match these filters."
            defaultSortKey="priority"
            defaultSortDirection="desc"
            pageResetKey={`${searchTerm}|${filterVisionAreaId}|${filterGoalId}|${filterStatus}|${filterPriority}|${filterOverdueOnly}`}
            rowClassName={(step) => (step.archived ? 'row-archived' : isOverdue(step.targetDate, step.status) ? 'row-overdue' : '')}
            selection={{
              selectedIds,
              onChange: setSelectedIds,
              rowLabel: (step) => step.title,
              actions: (
                <BulkArchiveAction
                  selectedIds={selectedIds}
                  entityLabel="step(s)"
                  onArchive={async (ids) => {
                    await crud.archiveMany(ids);
                    setSelectedIds(new Set());
                  }}
                />
              ),
            }}
          />
        </CardContent>
      </Card>
      )}
      {profileStep && (
        <Modal title={`Ideal partner for: ${profileStep.title}`} onClose={() => setProfileStep(null)}>
          <div className="stack-list">
            <p className="field-hint">
              Describe the person this step needs before looking for them — it turns recruiting
              from "who do I know?" into "who fits this?".
            </p>
            <label>
              Required experience
              <Textarea value={profileExperience} onChange={(event) => setProfileExperience(event.target.value)} />
            </label>
            <label>
              Character traits
              <Textarea value={profileTraits} onChange={(event) => setProfileTraits(event.target.value)} />
            </label>
            <label>
              What would motivate them
              <Textarea value={profileMotivation} onChange={(event) => setProfileMotivation(event.target.value)} />
            </label>
            <label>
              What you can offer in return
              <Textarea value={profileOffer} onChange={(event) => setProfileOffer(event.target.value)} />
            </label>
            <div className="row-actions">
              <Button type="button" onClick={() => void handleProfileSave()} disabled={profileSaving}>
                {profileSaving ? 'Saving...' : 'Save profile'}
              </Button>
              {profileByStepId.has(profileStep.id) && (
                <Button type="button" variant="secondary" onClick={() => void handleProfileArchive()} disabled={profileSaving}>
                  Remove profile
                </Button>
              )}
            </div>
          </div>
        </Modal>
      )}
    </PageSection>
  );
}
