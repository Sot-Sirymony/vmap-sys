import { FormEvent, useEffect, useState } from 'react';
import { CheckSquare } from 'lucide-react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { listDreams } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import { listIdealPartnerProfiles } from '../api/idealPartnerProfileApi';
import { listSteps } from '../api/stepApi';
import { listVisionAreas } from '../api/visionAreaApi';
import { archiveTask, permanentlyDeleteTask, createTask, listTasks, restoreTask, updateTask, updateTaskStatus } from '../api/taskApi';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import { Breadcrumbs } from '../components/common/Breadcrumbs';
import { BulkArchiveAction } from '../components/common/BulkArchiveAction';
import { Button } from '../components/common/Button';
import { CrudModalForm } from '../components/common/CrudModalForm';
import { DataTable, type DataTableColumn } from '../components/common/DataTable';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { ProgressBar } from '../components/common/ProgressBar';
import { QuickAddRow } from '../components/common/QuickAddRow';
import { RowActionsMenu } from '../components/common/RowActionsMenu';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { RelativeDate } from '../components/common/RelativeDate';
import { StatusBadge } from '../components/common/StatusBadge';
import { SummaryStrip } from '../components/common/SummaryStrip';
import { StatusBoard } from '../components/common/StatusBoard';
import { ViewToggle, type ViewMode } from '../components/common/ViewToggle';
import { Textarea } from '../components/common/Textarea';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useCrudEntity } from '../hooks/useCrudEntity';
import { useStoredState } from '../hooks/useStoredState';
import { FilterSelect, optionsFromEntities, optionsFromLabels } from '../components/common/FilterSelect';
import { useUrlFilter, useUrlFilterBatch, useUrlFlag } from '../hooks/useUrlFilter';
import type { Dream, Goal, IdealPartnerProfile, ObstacleType, Priority, TaskItem, TaskItemRequest, VisionArea, VisionStep, WorkStatus } from '../types/vision';
import { nudgeAfterTaskComplete } from '../utils/completionNudge';
import { isOverdue } from '../utils/overdue';
import { suggestPartnerFor } from '../utils/partnerSuggestion';
import { obstacleTypeLabels, priorityLabels, workStatusLabels } from '../utils/enumLabels';
import { priorityRank, workStatusRank } from '../utils/sortRank';
import { PageSection } from './PageSection';

const columns: WorkStatus[] = ['NOT_STARTED', 'IN_PROGRESS', 'WAITING', 'BLOCKED', 'COMPLETED', 'PAUSED'];
const blockerCategories: ObstacleType[] = ['KNOWLEDGE', 'SKILL', 'TIME', 'MONEY', 'DECISION', 'PARTNER', 'MOTIVATION'];

export function TasksBoardPage() {
  const { token, user } = useAuth();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const filterStepId = searchParams.get('stepId');
  const [autoOpenCreate, setAutoOpenCreate] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const crud = useCrudEntity<TaskItem, TaskItemRequest>({
    token,
    entityLabel: 'tasks',
    list: listTasks,
    create: createTask,
    update: updateTask,
    archive: archiveTask,
    permanentlyDelete: permanentlyDeleteTask,
    restore: restoreTask,
  });
  const [steps, setSteps] = useState<VisionStep[]>([]);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [visionAreas, setVisionAreas] = useState<VisionArea[]>([]);
  // Ideal partner profiles by step (FR-15.5): a blocked task whose step has one
  // points at it, so the fix is one click away instead of a memory exercise.
  const [profiles, setProfiles] = useState<IdealPartnerProfile[]>([]);
  const [stepId, setStepId] = useState('');
  const [quickParentId, setQuickParentId] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  // FR-22.2: owner defaults to the signed-in user; still editable.
  const [owner, setOwner] = useState(user?.fullName ?? '');
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [startDate, setStartDate] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [status, setStatus] = useState<WorkStatus>('NOT_STARTED');
  const [progressPercent, setProgressPercent] = useState(0);
  // Kept as strings so the inputs can be empty (hours are optional).
  const [estimatedHours, setEstimatedHours] = useState('');
  const [actualHours, setActualHours] = useState('');
  const [blockerReason, setBlockerReason] = useState('');
  const [blockerCategory, setBlockerCategory] = useState<ObstacleType | ''>('');
  const [nextAction, setNextAction] = useState('');
  // In the URL, not component state: the dashboard links straight into a
  // filtered board, and a filtered board stays shareable and bookmarkable.
  const [filterOwner, setFilterOwner] = useUrlFilter('owner');
  const [filterPriority, setFilterPriority] = useUrlFilter('priority');
  const [filterVisionAreaId] = useUrlFilter('visionAreaId');
  const [filterDreamId] = useUrlFilter('dreamId');
  const [filterGoalId, setFilterGoalId] = useUrlFilter('goalId');
  // One navigation for multi-key changes — chained setters undo each other.
  const setFilterBatch = useUrlFilterBatch();
  const [filterOverdueOnly, setFilterOverdueOnly] = useUrlFlag('overdue');
  // Due-date range (BRD C-6). Inclusive on both ends; either bound may be empty.
  // This is what makes the dashboard's "Due This Week" tile a link.
  const [filterDueFrom, setFilterDueFrom] = useUrlFilter('dueFrom');
  const [filterDueTo, setFilterDueTo] = useUrlFilter('dueTo');
  // In board view this narrows the board to a single column ("show me the
  // blocked ones" means showing one column); in list view it filters rows, so
  // a dashboard drill-down lands on the same set either way.
  const [filterStatus, setFilterStatus] = useUrlFilter('status');
  const visibleColumns = filterStatus
    ? columns.filter((column) => column === filterStatus)
    : columns;
  const [searchTerm, setSearchTerm] = useState('');
  const [viewMode, setViewMode] = useStoredState<ViewMode>('vms-view-tasks', 'list');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());

  // Arrived from a step's "Add task" shortcut: pre-select that step and open the
  // create form, then strip the params so a refresh doesn't reopen it.
  useEffect(() => {
    if (searchParams.get('create') !== 'task') {
      return;
    }
    const parent = searchParams.get('parent');
    if (parent) {
      setStepId(parent);
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
    void listSteps(token).then((stepData) => {
      setSteps(stepData);
      setStepId((current) => current || filterStepId || String(stepData[0]?.id ?? ''));
      setQuickParentId((current) => current || filterStepId || String(stepData[0]?.id ?? ''));
    });
    void listGoals(token).then(setGoals);
    void listDreams(token).then(setDreams);
    void listVisionAreas(token).then(setVisionAreas);
    void listIdealPartnerProfiles(token).then(setProfiles);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  // FR-22.1/22.2 quick-add: owner defaults to the signed-in user; the due
  // date — which no default can supply — is asked inline (BR-16).
  async function handleQuickAdd(title: string, dueDate?: string) {
    if (!token || !quickParentId || !dueDate) {
      return;
    }
    await createTask(token, {
      stepId: Number(quickParentId),
      title,
      owner: user?.fullName ?? 'Me',
      priority: 'MEDIUM',
      dueDate,
      status: 'NOT_STARTED',
      progressPercent: 0,
    });
    await crud.reload();
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!stepId) {
      return false;
    }
    const success = await crud.save({
      stepId: Number(stepId),
      title,
      description,
      owner,
      priority,
      startDate: startDate || undefined,
      dueDate,
      status,
      progressPercent,
      estimatedHours: estimatedHours ? Number(estimatedHours) : undefined,
      actualHours: actualHours ? Number(actualHours) : undefined,
      blockerReason: blockerReason || undefined,
      nextAction,
    });
    if (success) {
      if (token && crud.editingId !== null && status === 'COMPLETED') {
        nudgeAfterTaskComplete({
          token,
          completedTaskId: crud.editingId,
          tasks: crud.items,
          steps,
          goals,
          showToast,
          onApplied: () => void crud.reload(),
        });
      }
      setTitle('');
      setDescription('');
      setBlockerReason('');
      setBlockerCategory('');
      setNextAction('');
      setProgressPercent(0);
      setEstimatedHours('');
      setActualHours('');
    }
    return success;
  }

  async function handleMove(id: number, nextStatus: WorkStatus) {
    if (!token) {
      return;
    }
    try {
      await updateTaskStatus(token, id, nextStatus);
      await crud.reload();
      if (nextStatus === 'COMPLETED') {
        nudgeAfterTaskComplete({
          token,
          completedTaskId: id,
          tasks: crud.items,
          steps,
          goals,
          showToast,
          onApplied: () => void crud.reload(),
        });
      }
    } catch (moveError) {
      crud.setError(moveError instanceof Error ? moveError.message : 'Unable to update task status.');
    }
  }

  function startEdit(task: TaskItem) {
    crud.startEdit(task.id);
    setStepId(String(task.stepId));
    setTitle(task.title);
    setDescription(task.description ?? '');
    setOwner(task.owner);
    setPriority(task.priority);
    setStartDate(task.startDate ?? '');
    setDueDate(task.dueDate);
    setStatus(task.status);
    setProgressPercent(task.progressPercent);
    setEstimatedHours(task.estimatedHours != null ? String(task.estimatedHours) : '');
    setActualHours(task.actualHours != null ? String(task.actualHours) : '');
    setBlockerReason(task.blockerReason ?? '');
    setBlockerCategory('');
    setNextAction(task.nextAction ?? '');
  }

  function cancelEdit() {
    crud.cancelEdit();
    setTitle('');
    setDescription('');
    setOwner(user?.fullName ?? '');
    setPriority('HIGH');
    setStartDate('');
    setDueDate('');
    setStatus('NOT_STARTED');
    setProgressPercent(0);
    setEstimatedHours('');
    setActualHours('');
    setBlockerReason('');
    setBlockerCategory('');
    setNextAction('');
  }

  const stepById = new Map(steps.map((step) => [step.id, step]));
  const goalById = new Map(goals.map((goal) => [goal.id, goal]));
  const profileByStepId = new Map(profiles.map((profile) => [profile.stepId, profile]));
  const dreamById = new Map(dreams.map((dream) => [dream.id, dream]));
  const dreamsForFilter = filterVisionAreaId
    ? dreams.filter((dream) => String(dream.visionAreaId) === filterVisionAreaId)
    : dreams;
  const goalsForFilter = filterDreamId
    ? goals.filter((goal) => String(goal.dreamId) === filterDreamId)
    : goals;

  const visibleTasks = crud.items.filter((task) => {
    if (filterStepId && String(task.stepId) !== filterStepId) {
      return false;
    }
    if (filterOwner && !task.owner.toLowerCase().includes(filterOwner.toLowerCase())) {
      return false;
    }
    if (filterPriority && task.priority !== filterPriority) {
      return false;
    }
    if (filterStatus && task.status !== filterStatus) {
      return false;
    }
    if (filterOverdueOnly && !isOverdue(task.dueDate, task.status)) {
      return false;
    }
    // Due-date range: string compare is safe because dueDate is ISO yyyy-MM-dd.
    if (filterDueFrom && task.dueDate < filterDueFrom) {
      return false;
    }
    if (filterDueTo && task.dueDate > filterDueTo) {
      return false;
    }
    if (searchTerm) {
      const haystack = `${task.title} ${task.owner} ${task.description ?? ''} ${task.nextAction ?? ''} ${task.blockerReason ?? ''}`.toLowerCase();
      if (!haystack.includes(searchTerm.toLowerCase())) {
        return false;
      }
    }
    const step = stepById.get(task.stepId);
    if (filterGoalId && String(step?.goalId ?? '') !== filterGoalId) {
      return false;
    }
    if (filterDreamId) {
      const goal = step ? goalById.get(step.goalId) : undefined;
      if (String(goal?.dreamId ?? '') !== filterDreamId) {
        return false;
      }
    }
    if (filterVisionAreaId) {
      const goal = step ? goalById.get(step.goalId) : undefined;
      const dream = goal ? dreamById.get(goal.dreamId) : undefined;
      if (String(dream?.visionAreaId ?? '') !== filterVisionAreaId) {
        return false;
      }
    }
    return true;
  });

  // FR-23.1 acceptance: from any task row, its step, goal, dream, and area
  // are each one click away.
  function taskCrumbs(task: TaskItem) {
    const step = steps.find((item) => item.id === task.stepId);
    const goal = goals.find((item) => item.id === step?.goalId);
    const dream = dreams.find((item) => item.id === goal?.dreamId);
    const area = visionAreas.find((item) => item.id === dream?.visionAreaId);
    return [
      area && { label: area.name, to: `/dreams?visionAreaId=${area.id}` },
      dream && { label: dream.title, to: `/dreams/${dream.id}` },
      goal && { label: goal.title, to: `/goals?dreamId=${goal.dreamId}` },
      step && { label: step.title, to: `/steps?goalId=${step.goalId}` },
    ].filter(Boolean) as { label: string; to: string }[];
  }

  const taskColumns: DataTableColumn<TaskItem>[] = [
    {
      key: 'title',
      label: 'Task',
      sortValue: (task) => task.title,
      sx: { fontWeight: 500 },
      render: (task) => (
        <>
          {task.title}
          <Breadcrumbs crumbs={taskCrumbs(task)} />
        </>
      ),
    },
    { key: 'owner', label: 'Owner', sortValue: (task) => task.owner, render: (task) => task.owner },
    { key: 'dueDate', label: 'Due', sortValue: (task) => task.dueDate, render: (task) => <RelativeDate date={task.dueDate} completed={task.status === 'COMPLETED'} /> },
    {
      key: 'priority',
      label: 'Priority',
      sortValue: (task) => priorityRank(task.priority),
      render: (task) => <PriorityBadge priority={task.priority} />,
    },
    {
      key: 'status',
      label: 'Status',
      sortValue: (task) => workStatusRank(task.status),
      render: (task) => <StatusBadge status={task.status} />,
    },
    {
      key: 'progress',
      label: 'Progress',
      sortValue: (task) => Number(task.progressPercent),
      sx: { width: 160 },
      render: (task) => <ProgressBar value={Number(task.progressPercent)} />,
    },
    {
      key: 'hours',
      label: 'Hours',
      sortValue: (task) => Number(task.actualHours ?? 0),
      render: (task) => formatHours(task),
    },
    {
      key: 'actions',
      label: 'Action',
      className: 'row-actions',
      render: (task) => (
        <RowActionsMenu
          onEdit={() => startEdit(task)}
          onArchive={() => void crud.archive(task.id)}
          onRestore={() => void crud.restore(task.id)}
          onDeletePermanently={() => void crud.permanentlyDelete(task.id)}
          archived={task.archived}
          label="Task actions"
        />
      ),
    },
  ];

  // FR-22.4 field order: identity -> classification -> dates -> details,
  // with labeled dividers so the 14-field form scans as four short groups.
  const formFields = (
    <>
      <label>
        Step
        <FormControl fullWidth size="small" required>
          <Select SelectDisplayProps={{ 'aria-label': "Step" }} value={stepId} onChange={(event) => setStepId(event.target.value)}>
            {steps.map((step) => <MenuItem value={String(step.id)} key={step.id}>{step.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Title
        <Input value={title} onChange={(event) => setTitle(event.target.value)} required autoFocus />
      </label>
      <div className="field-full form-section">Ownership & priority</div>
      <label>
        Owner
        <Input value={owner} onChange={(event) => setOwner(event.target.value)} required />
      </label>
      <label>
        Priority
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Priority" }} value={priority} onChange={(event) => setPriority(event.target.value as Priority)}>
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
          <Select SelectDisplayProps={{ 'aria-label': "Status" }} value={status} onChange={(event) => setStatus(event.target.value as WorkStatus)}>
            {columns.map((column) => <MenuItem value={column} key={column}>{workStatusLabels[column]}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      {status === 'BLOCKED' && (
        <label>
          What's missing?
          <FormControl fullWidth size="small">
            <Select SelectDisplayProps={{ 'aria-label': "What's missing?" }} displayEmpty value={blockerCategory} onChange={(event) => setBlockerCategory(event.target.value as ObstacleType)}>
              <MenuItem value="" disabled><em>Select a category...</em></MenuItem>
              {blockerCategories.map((category) => <MenuItem value={category} key={category}>{obstacleTypeLabels[category]}</MenuItem>)}
            </Select>
          </FormControl>
          {blockerCategory && (
            <span className="field-hint">
              {suggestPartnerFor(blockerCategory)?.label ?? 'Look for a partner or connector who can help directly.'}
            </span>
          )}
          {profileByStepId.has(Number(stepId)) && (
            <span className="field-hint">
              This step already has an ideal partner profile — see the Partners page for who to recruit.
            </span>
          )}
        </label>
      )}
      {status === 'BLOCKED' && (
        <label className="field-full">
          Blocker Reason
          <Textarea value={blockerReason} onChange={(event) => setBlockerReason(event.target.value)} required />
        </label>
      )}
      <div className="field-full form-section">Dates</div>
      <label>
        Start Date
        <Input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
      </label>
      <label>
        Due Date
        <Input type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} required />
      </label>
      <div className="field-full form-section">Details</div>
      <label>
        Progress
        <Input type="number" min="0" max="100" value={progressPercent} onChange={(event) => setProgressPercent(Number(event.target.value))} required />
      </label>
      <label>
        Estimated Hours
        <Input type="number" min="0" step="0.5" value={estimatedHours} onChange={(event) => setEstimatedHours(event.target.value)} />
      </label>
      <label>
        Actual Hours
        <Input type="number" min="0" step="0.5" value={actualHours} onChange={(event) => setActualHours(event.target.value)} />
      </label>
      <label className="field-full">
        Next Action
        <Textarea value={nextAction} onChange={(event) => setNextAction(event.target.value)} />
      </label>
      <label className="field-full">
        Description
        <Textarea value={description} onChange={(event) => setDescription(event.target.value)} />
      </label>
    </>
  );

  return (
    <PageSection
      title="Tasks Board"
      subtitle="Manage executable work by status."
      actions={<Button type="button" onClick={() => setCreateOpen(true)} disabled={steps.length === 0}>Create task</Button>}
    >
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Create task"
        editTitle="Edit Task"
        saving={crud.saving}
        disabled={steps.length === 0}
        autoOpenCreate={autoOpenCreate}
        creating={createOpen}
        onCreatingChange={setCreateOpen}
        hideTrigger
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {crud.loading && <Loading variant="cards" />}
      {crud.error && <ErrorMessage message={crud.error} onRetry={() => void crud.reload()} />}
      <SummaryStrip
        chips={[
          { key: 'total', label: crud.items.length === 1 ? 'task' : 'tasks', count: crud.items.length },
          { key: 'overdue', label: 'overdue', count: crud.items.filter((task) => isOverdue(task.dueDate, task.status)).length, tone: 'critical', active: filterOverdueOnly, onClick: () => setFilterOverdueOnly(!filterOverdueOnly) },
          { key: 'blocked', label: 'blocked', count: crud.items.filter((task) => task.status === 'BLOCKED').length, tone: 'warning', active: filterStatus === 'BLOCKED', onClick: () => setFilterStatus(filterStatus === 'BLOCKED' ? '' : 'BLOCKED') },
          { key: 'completed', label: 'completed', count: crud.items.filter((task) => task.status === 'COMPLETED').length, tone: 'positive', active: filterStatus === 'COMPLETED', onClick: () => setFilterStatus(filterStatus === 'COMPLETED' ? '' : 'COMPLETED') },
        ]}
      />
      <Card className="filter-bar flex-row">
        <label>
          Owner
          <Input value={filterOwner} onChange={(event) => setFilterOwner(event.target.value)} placeholder="Any owner" />
        </label>
        <FilterSelect
          label="Priority"
          value={filterPriority}
          onChange={setFilterPriority}
          options={optionsFromLabels(priorityLabels)}
        />
        <FilterSelect
          label="Status"
          value={filterStatus}
          onChange={setFilterStatus}
          options={optionsFromLabels(workStatusLabels)}
        />
        <FilterSelect
          label="Vision Area"
          value={filterVisionAreaId}
          onChange={(value) => setFilterBatch({ visionAreaId: value, dreamId: '', goalId: '' })}
          options={optionsFromEntities(visionAreas, (area) => area.name)}
        />
        <FilterSelect
          label="Dream"
          value={filterDreamId}
          onChange={(value) => setFilterBatch({ dreamId: value, goalId: '' })}
          options={optionsFromEntities(dreamsForFilter, (dream) => dream.title)}
        />
        <FilterSelect
          label="Goal"
          value={filterGoalId}
          onChange={setFilterGoalId}
          options={optionsFromEntities(goalsForFilter, (goal) => goal.title)}
        />
        <label>
          Due from
          <Input type="date" value={filterDueFrom} onChange={(event) => setFilterDueFrom(event.target.value)} />
        </label>
        <label>
          Due to
          <Input type="date" value={filterDueTo} onChange={(event) => setFilterDueTo(event.target.value)} />
        </label>
        <label className="checkbox-field">
          <Checkbox checked={filterOverdueOnly} onChange={(event) => setFilterOverdueOnly(event.target.checked)} />
          Overdue only
        </label>
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      {filterStepId && (
        <Card className="filter-banner flex-row">
          <span>Showing tasks for step: <strong>{steps.find((step) => String(step.id) === filterStepId)?.title ?? filterStepId}</strong></span>
          <Link to="/tasks">Clear filter</Link>
        </Card>
      )}
      <div className="toolbar">
        <Input
          value={searchTerm}
          onChange={(event) => setSearchTerm(event.target.value)}
          placeholder="Search Here"
          aria-label="Search tasks"
        />
      </div>
      <div className="view-toggle-row">
        <ViewToggle value={viewMode} onChange={setViewMode} label="Task view" />
      </div>
      {crud.items.length > 0 && steps.length > 0 && (
        <QuickAddRow
          parentLabel="Step"
          parents={steps.map((step) => ({ value: String(step.id), label: step.title }))}
          parentValue={quickParentId}
          onParentChange={setQuickParentId}
          placeholder="New task title"
          withDueDate
          onAdd={handleQuickAdd}
        />
      )}
      {!crud.loading && crud.items.length === 0 ? (
        <EmptyState
          headline="No tasks yet"
          icon={CheckSquare}
          action={
            steps.length === 0 ? (
              <Button type="button" onClick={() => navigate('/steps?create=step')}>Create a step first</Button>
            ) : (
              <Button type="button" onClick={() => setCreateOpen(true)}>Create your first task</Button>
            )
          }
        >
          Tasks are the small executable actions that move a step forward — each with an owner, due date, and priority.
        </EmptyState>
      ) : null}
      {crud.items.length > 0 && (viewMode === 'list' ? (
        <Card>
          <CardContent>
            <DataTable
              storageKey="tasks"
              rows={visibleTasks}
              columns={taskColumns}
              emptyMessage="No tasks match these filters."
              defaultSortKey="priority"
              defaultSortDirection="desc"
              pageResetKey={`${searchTerm}|${filterOwner}|${filterPriority}|${filterStatus}|${filterVisionAreaId}|${filterDreamId}|${filterGoalId}|${filterDueFrom}|${filterDueTo}|${filterOverdueOnly}|${filterStepId ?? ''}`}
              rowClassName={(task) => (task.archived ? 'row-archived' : isOverdue(task.dueDate, task.status) ? 'row-overdue' : '')}
              selection={{
                selectedIds,
                onChange: setSelectedIds,
                rowLabel: (task) => task.title,
                actions: (
                  <BulkArchiveAction
                    selectedIds={selectedIds}
                    entityLabel="task(s)"
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
      ) : (
        <StatusBoard
          items={visibleTasks}
          columns={visibleColumns.map((column) => ({ value: column, label: workStatusLabels[column] }))}
          moveOptions={columns.map((column) => ({ value: column, label: workStatusLabels[column] }))}
          statusOf={(task) => task.status}
          entityLabel="tasks"
          onMove={(task, nextStatus) => void handleMove(task.id, nextStatus)}
          cardClassName={taskHighlightClass}
          renderCard={(task) => (
            <>
              <strong>{task.title}</strong>
              <p>{task.owner} · Due <RelativeDate date={task.dueDate} completed={task.status === 'COMPLETED'} /></p>
              <div className="inline-meta">
                <PriorityBadge priority={task.priority} />
                <span>{task.progressPercent}%</span>
              </div>
              <ProgressBar value={Number(task.progressPercent)} />
              {task.blockerReason && <p>{task.blockerReason}</p>}
              {task.status === 'BLOCKED' && profileByStepId.has(task.stepId) && (
                <p><Link to="/partners">Ideal partner profile defined for this step →</Link></p>
              )}
            </>
          )}
          cardActions={(task) => (
            <RowActionsMenu
              onEdit={() => startEdit(task)}
              onArchive={() => void crud.archive(task.id)}
              onRestore={() => void crud.restore(task.id)}
              onDeletePermanently={() => void crud.permanentlyDelete(task.id)}
              archived={task.archived}
              label="Task actions"
            />
          )}
        />
      ))}
    </PageSection>
  );
}

// "actual / estimated h" — either side may be missing.
function formatHours(task: TaskItem) {
  if (task.estimatedHours == null && task.actualHours == null) {
    return '-';
  }
  return `${task.actualHours ?? 0} / ${task.estimatedHours ?? '?'} h`;
}

function taskHighlightClass(task: TaskItem) {
  if (isOverdue(task.dueDate, task.status)) {
    return 'list-card--overdue';
  }
  if (task.status === 'BLOCKED') {
    return 'list-card--blocked';
  }
  return '';
}
