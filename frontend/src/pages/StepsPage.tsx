import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { listGoals } from '../api/goalApi';
import { archiveStep, permanentlyDeleteStep, createStep, getStepArchiveImpact, listSteps, restoreStep, updateStep } from '../api/stepApi';
import { listTasks } from '../api/taskApi';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import { BulkArchiveAction } from '../components/common/BulkArchiveAction';
import { CrudModalForm } from '../components/common/CrudModalForm';
import { DataTable, type DataTableColumn } from '../components/common/DataTable';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { ProgressBar } from '../components/common/ProgressBar';
import { RowActionsMenu } from '../components/common/RowActionsMenu';
import { SearchBar } from '../components/common/SearchBar';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { StatusBadge } from '../components/common/StatusBadge';
import { Textarea } from '../components/common/Textarea';
import { useAuth } from '../context/AuthContext';
import { useCrudEntity } from '../hooks/useCrudEntity';
import { FilterSelect, optionsFromEntities, optionsFromLabels } from '../components/common/FilterSelect';
import { useUrlFilter, useUrlFlag } from '../hooks/useUrlFilter';
import type { Goal, Priority, TaskItem, VisionStep, VisionStepRequest, WorkStatus } from '../types/vision';
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
  const [goalId, setGoalId] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [sequenceNumber, setSequenceNumber] = useState(1);
  const [complex, setComplex] = useState(false);
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [targetDate, setTargetDate] = useState('');
  const [status, setStatus] = useState<WorkStatus>('NOT_STARTED');
  // In the URL, not component state: the dashboard links straight into a
  // filtered view, and a filtered list stays shareable and bookmarkable.
  const [filterGoalId, setFilterGoalId] = useUrlFilter('goalId');
  const [filterStatus, setFilterStatus] = useUrlFilter('status');
  const [filterPriority, setFilterPriority] = useUrlFilter('priority');
  const [filterOverdueOnly, setFilterOverdueOnly] = useUrlFlag('overdue');
  // The dashboard's "complex steps with no tasks" finding lands here.
  const [filterComplexOnly, setFilterComplexOnly] = useUrlFlag('complex');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [searchTerm, setSearchTerm] = useState('');
  const [searchParams, setSearchParams] = useSearchParams();
  const [autoOpenCreate, setAutoOpenCreate] = useState(false);

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
    void Promise.all([listGoals(token), listTasks(token)]).then(([goalData, taskData]) => {
      setGoals(goalData);
      setTasks(taskData);
      setGoalId((current) => current || String(goalData[0]?.id ?? ''));
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

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

  async function archiveImpactMessage(step: VisionStep) {
    if (!token) {
      return 'Archive this step?';
    }
    const impact = await getStepArchiveImpact(token, step.id);
    return `Archiving "${step.title}" also archives ${impact.tasks} task(s). Everything can be restored later with "Show archived".`;
  }

  const filteredSteps = crud.items.filter((step) => {
    if (filterGoalId && String(step.goalId) !== filterGoalId) {
      return false;
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
      render: (step) => (
        <RowActionsMenu
          onEdit={() => startEdit(step)}
          onArchive={() => void crud.archive(step.id)}
          onRestore={() => void crud.restore(step.id)}
          onDeletePermanently={() => void crud.permanentlyDelete(step.id)}
          archived={step.archived}
          confirmArchive={() => archiveImpactMessage(step)}
          extraActions={[{ label: 'Add task', onClick: () => navigate(`/tasks?create=task&parent=${step.id}`) }]}
          label="Step actions"
        />
      ),
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
        <Input value={title} onChange={(event) => setTitle(event.target.value)} required />
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
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {crud.loading && <Loading />}
      {crud.error && <ErrorMessage message={crud.error} />}
      <Card className="filter-bar flex-row">
        <SearchBar value={searchTerm} onChange={setSearchTerm} entityLabel="steps" />
        <FilterSelect
          label="Goal"
          value={filterGoalId}
          onChange={setFilterGoalId}
          options={optionsFromEntities(goals, (goal) => goal.title)}
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
      <Card>
        <CardContent>
          <DataTable
            rows={filteredSteps}
            columns={columns}
            emptyMessage="No steps match these filters."
            defaultSortKey="sequenceNumber"
            pageResetKey={`${searchTerm}|${filterGoalId}|${filterStatus}|${filterPriority}|${filterOverdueOnly}`}
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
    </PageSection>
  );
}
