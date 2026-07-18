import { FormEvent, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Flag, Rocket } from 'lucide-react';
import { listDreams } from '../api/dreamApi';
import { listSteps } from '../api/stepApi';
import { archiveGoal, permanentlyDeleteGoal, createGoal, getGoalArchiveImpact, listGoals, restoreGoal, updateGoal, updateGoalStatus } from '../api/goalApi';
import { listVisionAreas } from '../api/visionAreaApi';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import Tooltip from '@mui/material/Tooltip';
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
import { QuickAddRow } from '../components/common/QuickAddRow';
import { RelativeDate } from '../components/common/RelativeDate';
import { ProgressBar } from '../components/common/ProgressBar';
import { RowActionsMenu } from '../components/common/RowActionsMenu';
import { SearchBar } from '../components/common/SearchBar';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { StatusBadge } from '../components/common/StatusBadge';
import { SummaryStrip } from '../components/common/SummaryStrip';
import { StatusBoard } from '../components/common/StatusBoard';
import { Textarea } from '../components/common/Textarea';
import { ViewToggle, type ViewMode } from '../components/common/ViewToggle';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useCrudEntity } from '../hooks/useCrudEntity';
import { useStoredState } from '../hooks/useStoredState';
import { FilterSelect, optionsFromEntities, optionsFromLabels } from '../components/common/FilterSelect';
import { useUrlFilter, useUrlFlag } from '../hooks/useUrlFilter';
import type { Dream, Goal, GoalRequest, Priority, VisionArea, WorkStatus } from '../types/vision';
import { moonshotViolet } from '../theme';
import { goalRequest } from '../utils/entityRequests';
import { priorityLabels } from '../utils/enumLabels';
import { isOverdue } from '../utils/overdue';
import { matchesSearch } from '../utils/search';
import { priorityRank, workStatusRank } from '../utils/sortRank';
import { PageSection } from './PageSection';

const statusOptions: { value: WorkStatus; label: string }[] = [
  { value: 'NOT_STARTED', label: 'Not Started' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'WAITING', label: 'Waiting' },
  { value: 'BLOCKED', label: 'Blocked' },
  { value: 'PAUSED', label: 'Paused' },
  { value: 'COMPLETED', label: 'Completed' },
];

export function GoalsPage() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const [autoOpenCreate, setAutoOpenCreate] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const crud = useCrudEntity<Goal, GoalRequest>({
    token,
    entityLabel: 'goals',
    list: listGoals,
    create: createGoal,
    update: updateGoal,
    archive: archiveGoal,
    permanentlyDelete: permanentlyDeleteGoal,
    restore: restoreGoal,
  });
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [visionAreas, setVisionAreas] = useState<VisionArea[]>([]);
  // Count of live steps per goal, for the Steps column.
  const [stepCounts, setStepCounts] = useState<Map<number, number>>(new Map());
  const [dreamId, setDreamId] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [successCriteria, setSuccessCriteria] = useState('');
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [targetDate, setTargetDate] = useState('');
  const [status, setStatus] = useState<WorkStatus>('NOT_STARTED');
  const [moonshot, setMoonshot] = useState(false);
  const [moonshotVision, setMoonshotVision] = useState('');
  // In the URL, not component state: the dashboard links straight into a
  // filtered view, and a filtered list stays shareable and bookmarkable.
  const [filterVisionAreaId, setFilterVisionAreaId] = useUrlFilter('visionAreaId');
  const [filterDreamId, setFilterDreamId] = useUrlFilter('dreamId');
  const [filterStatus, setFilterStatus] = useUrlFilter('status');
  const [filterPriority, setFilterPriority] = useUrlFilter('priority');
  const [filterOverdueOnly, setFilterOverdueOnly] = useUrlFlag('overdue');
  const [filterMoonshotOnly, setFilterMoonshotOnly] = useUrlFlag('moonshot');
  // Target-date range (BRD C-6). Inclusive on both ends; either bound may be
  // empty. Goals without a target date drop out while a bound is set.
  const [filterTargetFrom, setFilterTargetFrom] = useUrlFilter('targetFrom');
  const [filterTargetTo, setFilterTargetTo] = useUrlFilter('targetTo');
  const [quickParentId, setQuickParentId] = useState('');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [bulkStatus, setBulkStatus] = useState<WorkStatus>('IN_PROGRESS');
  const [bulkPriority, setBulkPriority] = useState<Priority>('HIGH');
  const [bulkApplying, setBulkApplying] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [viewMode, setViewMode] = useStoredState<ViewMode>('vms-view-goals', 'list');
  const [searchParams, setSearchParams] = useSearchParams();
  const { showToast } = useToast();

  // Arrived from a dream's "Add goal" shortcut: pre-select that dream and open
  // the create form. The create params are then stripped so a refresh or back
  // doesn't reopen an empty form. Runs once on mount.
  useEffect(() => {
    if (searchParams.get('create') !== 'goal') {
      return;
    }
    const parent = searchParams.get('parent');
    if (parent) {
      setDreamId(parent);
    }
    setAutoOpenCreate(true);
    const next = new URLSearchParams(searchParams);
    next.delete('create');
    next.delete('parent');
    setSearchParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  useEffect(() => {
    if (!token) {
      return;
    }
    void crud.reload();
    void Promise.all([listDreams(token), listVisionAreas(token)]).then(([dreamData, areaData]) => {
      setDreams(dreamData.filter((dream) => dream.status !== 'ARCHIVED'));
      setVisionAreas(areaData);
      setDreamId((current) => current || String(dreamData[0]?.id ?? ''));
      setQuickParentId((current) => current || String(dreamData[0]?.id ?? ''));
    });
    void listSteps(token).then((steps) => {
      const counts = new Map<number, number>();
      for (const step of steps) {
        counts.set(step.goalId, (counts.get(step.goalId) ?? 0) + 1);
      }
      setStepCounts(counts);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!dreamId) {
      return false;
    }
    const success = await crud.save({
      dreamId: Number(dreamId),
      title,
      description,
      successCriteria,
      priority,
      targetDate: targetDate || undefined,
      status,
      moonshot,
      moonshotVision: moonshot ? moonshotVision : undefined,
    });
    if (success) {
      setTitle('');
      setDescription('');
      setSuccessCriteria('');
      setMoonshot(false);
      setMoonshotVision('');
    }
    return success;
  }

  function startEdit(goal: Goal) {
    crud.startEdit(goal.id);
    setDreamId(String(goal.dreamId));
    setTitle(goal.title);
    setDescription(goal.description ?? '');
    setSuccessCriteria(goal.successCriteria ?? '');
    setPriority(goal.priority);
    setTargetDate(goal.targetDate ?? '');
    setStatus(goal.status);
    setMoonshot(goal.moonshot);
    setMoonshotVision(goal.moonshotVision ?? '');
  }

  function cancelEdit() {
    crud.cancelEdit();
    setTitle('');
    setDescription('');
    setSuccessCriteria('');
    setPriority('HIGH');
    setTargetDate('');
    setStatus('NOT_STARTED');
    setMoonshot(false);
    setMoonshotVision('');
  }

  async function archiveImpactMessage(goal: Goal) {
    if (!token) {
      return 'Archive this goal?';
    }
    const impact = await getGoalArchiveImpact(token, goal.id);
    return `Archiving "${goal.title}" also archives ${impact.steps} step(s) and ${impact.tasks} task(s). Everything can be restored later with "Show archived".`;
  }

  const filteredGoals = crud.items.filter((goal) => {
    if (filterDreamId && String(goal.dreamId) !== filterDreamId) {
      return false;
    }
    if (filterVisionAreaId) {
      const dream = dreams.find((item) => item.id === goal.dreamId);
      if (String(dream?.visionAreaId ?? '') !== filterVisionAreaId) {
        return false;
      }
    }
    if (filterStatus && goal.status !== filterStatus) {
      return false;
    }
    if (filterPriority && goal.priority !== filterPriority) {
      return false;
    }
    if (filterOverdueOnly && !isOverdue(goal.targetDate, goal.status)) {
      return false;
    }
    if (filterMoonshotOnly && !goal.moonshot) {
      return false;
    }
    // Target-date range: string compare is safe because targetDate is ISO yyyy-MM-dd.
    if (filterTargetFrom && (!goal.targetDate || goal.targetDate < filterTargetFrom)) {
      return false;
    }
    if (filterTargetTo && (!goal.targetDate || goal.targetDate > filterTargetTo)) {
      return false;
    }
    if (!matchesSearch(searchTerm, goal.code, goal.title, goal.description, goal.successCriteria)) {
      return false;
    }
    return true;
  });

  // FR-22.1 quick-add: title + parent only; defaults keep BR-16 satisfied.
  async function handleQuickAdd(title: string) {
    if (!token || !quickParentId) {
      return;
    }
    await createGoal(token, {
      dreamId: Number(quickParentId),
      title,
      priority: 'MEDIUM',
      status: 'NOT_STARTED',
      moonshot: false,
    });
    await crud.reload();
  }

  // Board drag/dropdown move, using the goal status PATCH endpoint.
  async function handleMove(goal: Goal, nextStatus: WorkStatus) {
    if (!token || goal.status === nextStatus) {
      return;
    }
    try {
      await updateGoalStatus(token, goal.id, nextStatus);
      await crud.reload();
    } catch (moveError) {
      crud.setError(moveError instanceof Error ? moveError.message : 'Unable to update goal status.');
    }
  }

  async function handleBulkPriority() {
    if (!token || selectedIds.size === 0) {
      return;
    }
    setBulkApplying(true);
    try {
      await Promise.all([...selectedIds].map((id) => {
        const goal = crud.items.find((item) => item.id === id);
        return goal ? updateGoal(token, goal.id, { ...goalRequest(goal), priority: bulkPriority }) : Promise.resolve();
      }));
      await crud.reload();
      showToast(`Updated ${selectedIds.size} goal${selectedIds.size === 1 ? '' : 's'}.`);
      setSelectedIds(new Set());
    } catch (bulkError) {
      crud.setError(bulkError instanceof Error ? bulkError.message : 'Unable to update selected goals.');
    } finally {
      setBulkApplying(false);
    }
  }

  async function handleBulkApply() {
    if (!token || selectedIds.size === 0) {
      return;
    }
    setBulkApplying(true);
    try {
      await Promise.all([...selectedIds].map((id) => updateGoalStatus(token, id, bulkStatus)));
      await crud.reload();
      showToast(`Updated ${selectedIds.size} goal${selectedIds.size === 1 ? '' : 's'}.`);
      setSelectedIds(new Set());
    } catch (bulkError) {
      crud.setError(bulkError instanceof Error ? bulkError.message : 'Unable to update selected goals.');
    } finally {
      setBulkApplying(false);
    }
  }

  // FR-23.1: each goal row shows its ancestry, every segment navigable.
  function goalCrumbs(goal: Goal) {
    const dream = dreams.find((item) => item.id === goal.dreamId);
    const area = visionAreas.find((item) => item.id === dream?.visionAreaId);
    return [
      area && { label: area.name, to: `/dreams?visionAreaId=${area.id}` },
      dream && { label: dream.title, to: `/dreams/${dream.id}` },
    ].filter(Boolean) as { label: string; to: string }[];
  }

  // Shared by the table's action column and the board's cards, so both offer
  // the same row actions.
  function renderGoalActions(goal: Goal) {
    return (
      <RowActionsMenu
        onEdit={() => startEdit(goal)}
        onArchive={() => void crud.archive(goal.id)}
        onRestore={() => void crud.restore(goal.id)}
        onDeletePermanently={() => void crud.permanentlyDelete(goal.id)}
        archived={goal.archived}
        confirmArchive={() => archiveImpactMessage(goal)}
        extraActions={[
          { label: 'View steps', onClick: () => navigate(`/steps?goalId=${goal.id}`) },
          { label: 'Add step', onClick: () => navigate(`/steps?create=step&parent=${goal.id}`) },
        ]}
        label="Goal actions"
      />
    );
  }

  const columns: DataTableColumn<Goal>[] = [
    { key: 'code', label: 'Code', sortValue: (goal) => goal.code, sx: { fontSize: 'var(--font-caption)', color: 'var(--text-label)' }, render: (goal) => goal.code },
    {
      key: 'title',
      label: 'Goal',
      sortValue: (goal) => goal.title,
      sx: { fontWeight: 500 },
      render: (goal) => (
        <>
          <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75 }}>
            {goal.moonshot && (
              <Tooltip title={goal.moonshotVision || 'Moonshot goal'} arrow>
                <Box component="span" sx={{ display: 'inline-flex', color: moonshotViolet }} role="img" aria-label="Moonshot goal">
                  <Rocket size={16} />
                </Box>
              </Tooltip>
            )}
            {goal.title}
          </Box>
          <Breadcrumbs crumbs={goalCrumbs(goal)} />
        </>
      ),
    },
    {
      key: 'priority',
      label: 'Priority',
      sortValue: (goal) => priorityRank(goal.priority),
      render: (goal) => <PriorityBadge priority={goal.priority} />,
    },
    {
      key: 'status',
      label: 'Status',
      sortValue: (goal) => workStatusRank(goal.status),
      render: (goal) => <StatusBadge status={goal.status} />,
    },
    {
      key: 'steps',
      label: 'Steps',
      sortValue: (goal) => stepCounts.get(goal.id) ?? 0,
      render: (goal) => stepCounts.get(goal.id) ?? 0,
    },
    {
      key: 'progress',
      label: 'Progress',
      sortValue: (goal) => Number(goal.progressPercent),
      render: (goal) => <ProgressBar value={Number(goal.progressPercent)} />,
    },
    {
      key: 'actions',
      label: 'Action',
      className: 'row-actions',
      render: (goal) => renderGoalActions(goal),
    },
  ];

  const formFields = (
    <>
      <label>
        Dream
        <FormControl fullWidth size="small" required>
          <Select SelectDisplayProps={{ 'aria-label': "Dream" }} value={dreamId} onChange={(event) => setDreamId(event.target.value)}>
            {dreams.map((dream) => <MenuItem value={String(dream.id)} key={dream.id}>{dream.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Title
        <Input value={title} onChange={(event) => setTitle(event.target.value)} required autoFocus />
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
            <MenuItem value="NOT_STARTED">Not Started</MenuItem>
            <MenuItem value="IN_PROGRESS">In Progress</MenuItem>
            <MenuItem value="WAITING">Waiting</MenuItem>
            <MenuItem value="BLOCKED">Blocked</MenuItem>
            <MenuItem value="PAUSED">Paused</MenuItem>
            <MenuItem value="COMPLETED">Completed</MenuItem>
          </Select>
        </FormControl>
      </label>
      <label>
        Target Date
        <Input type="date" value={targetDate} onChange={(event) => setTargetDate(event.target.value)} />
      </label>
      <label className="field-full">
        Success Criteria
        <Textarea value={successCriteria} onChange={(event) => setSuccessCriteria(event.target.value)} />
      </label>
      <label className="field-full">
        <span className="inline-meta">
          <Checkbox checked={moonshot} onChange={(event) => setMoonshot(event.target.checked)} />
          Moonshot goal
        </span>
      </label>
      {moonshot && (
        <label className="field-full">
          Moonshot vision
          <Textarea value={moonshotVision} onChange={(event) => setMoonshotVision(event.target.value)} />
          <span className="field-hint">
            If resources were no limit, what would the ideal result look like? Aim beyond what feels achievable — this
            is aspirational only and never changes the goal's progress or completion.
          </span>
        </label>
      )}
      <label className="field-full">
        Description
        <Textarea value={description} onChange={(event) => setDescription(event.target.value)} />
      </label>
    </>
  );

  return (
    <PageSection
      title="Goals"
      subtitle="Define specific results for each dream."
      actions={<Button type="button" onClick={() => setCreateOpen(true)} disabled={dreams.length === 0}>Create goal</Button>}
    >
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Create goal"
        editTitle="Edit Goal"
        saving={crud.saving}
        disabled={dreams.length === 0}
        autoOpenCreate={autoOpenCreate}
        creating={createOpen}
        onCreatingChange={setCreateOpen}
        hideTrigger
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {crud.loading && <Loading variant="table" />}
      {crud.error && <ErrorMessage message={crud.error} onRetry={() => void crud.reload()} />}
      <SummaryStrip
        chips={[
          { key: 'total', label: crud.items.length === 1 ? 'goal' : 'goals', count: crud.items.length },
          { key: 'overdue', label: 'overdue', count: crud.items.filter((goal) => isOverdue(goal.targetDate, goal.status)).length, tone: 'critical', active: filterOverdueOnly, onClick: () => setFilterOverdueOnly(!filterOverdueOnly) },
          { key: 'blocked', label: 'blocked', count: crud.items.filter((goal) => goal.status === 'BLOCKED').length, tone: 'warning', active: filterStatus === 'BLOCKED', onClick: () => setFilterStatus(filterStatus === 'BLOCKED' ? '' : 'BLOCKED') },
          { key: 'completed', label: 'completed', count: crud.items.filter((goal) => goal.status === 'COMPLETED').length, tone: 'positive', active: filterStatus === 'COMPLETED', onClick: () => setFilterStatus(filterStatus === 'COMPLETED' ? '' : 'COMPLETED') },
        ]}
      />
      <Card className="filter-bar flex-row">
        <SearchBar value={searchTerm} onChange={setSearchTerm} entityLabel="goals" />
        <FilterSelect
          label="Vision Area"
          value={filterVisionAreaId}
          onChange={setFilterVisionAreaId}
          options={optionsFromEntities(visionAreas, (area) => area.name)}
        />
        <FilterSelect
          label="Dream"
          value={filterDreamId}
          onChange={setFilterDreamId}
          options={optionsFromEntities(dreams, (dream) => dream.title)}
        />
        <FilterSelect
          label="Status"
          value={filterStatus}
          onChange={setFilterStatus}
          options={statusOptions.map((option) => ({ value: option.value, label: option.label }))}
        />
        <FilterSelect
          label="Priority"
          value={filterPriority}
          onChange={setFilterPriority}
          options={optionsFromLabels(priorityLabels)}
        />
        <label>
          Target from
          <Input type="date" value={filterTargetFrom} onChange={(event) => setFilterTargetFrom(event.target.value)} />
        </label>
        <label>
          Target to
          <Input type="date" value={filterTargetTo} onChange={(event) => setFilterTargetTo(event.target.value)} />
        </label>
        <label className="checkbox-field">
          <Checkbox checked={filterOverdueOnly} onChange={(event) => setFilterOverdueOnly(event.target.checked)} />
          Overdue only
        </label>
        <label className="checkbox-field">
          <Checkbox checked={filterMoonshotOnly} onChange={(event) => setFilterMoonshotOnly(event.target.checked)} />
          Moonshots only
        </label>
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      <div className="view-toggle-row">
        <ViewToggle value={viewMode} onChange={setViewMode} label="Goal view" />
      </div>
      {crud.items.length > 0 && dreams.length > 0 && (
        <QuickAddRow
          parentLabel="Dream"
          parents={dreams.map((dream) => ({ value: String(dream.id), label: dream.title }))}
          parentValue={filterDreamId || quickParentId}
          onParentChange={setQuickParentId}
          placeholder="New goal title — Enter to add"
          onAdd={handleQuickAdd}
        />
      )}
      {!crud.loading && crud.items.length === 0 ? (
        <EmptyState
          headline="No goals yet"
          icon={Flag}
          action={
            dreams.length === 0 ? (
              <Button type="button" onClick={() => navigate('/dreams?create=dream')}>Create a dream first</Button>
            ) : (
              <Button type="button" onClick={() => setCreateOpen(true)}>Create your first goal</Button>
            )
          }
        >
          Goals are the major results that make a dream real. Each goal breaks down into steps and tasks.
        </EmptyState>
      ) : null}
      {crud.items.length > 0 && viewMode === 'board' && (
        <StatusBoard
          items={filteredGoals}
          columns={statusOptions}
          statusOf={(goal) => goal.status}
          entityLabel="goals"
          onMove={(goal, nextStatus) => void handleMove(goal, nextStatus)}
          cardClassName={(goal) => (isOverdue(goal.targetDate, goal.status) ? 'list-card--overdue' : '')}
          renderCard={(goal) => (
            <>
              <strong>
                {goal.moonshot && (
                  <Tooltip title={goal.moonshotVision || 'Moonshot goal'} arrow>
                    <Box component="span" sx={{ display: 'inline-flex', color: moonshotViolet, mr: 0.75, verticalAlign: 'middle' }} role="img" aria-label="Moonshot goal">
                      <Rocket size={16} />
                    </Box>
                  </Tooltip>
                )}
                {goal.title}
              </strong>
              <p>{goal.code} · {stepCounts.get(goal.id) ?? 0} step(s){goal.targetDate ? <> · Target <RelativeDate date={goal.targetDate} completed={goal.status === 'COMPLETED'} /></> : ''}</p>
              <div className="inline-meta">
                <PriorityBadge priority={goal.priority} />
                <span>{goal.progressPercent}%</span>
              </div>
              <ProgressBar value={Number(goal.progressPercent)} />
            </>
          )}
          cardActions={(goal) => renderGoalActions(goal)}
        />
      )}
      {crud.items.length > 0 && viewMode === 'list' && (
      <Card>
        <CardContent>
          <DataTable
            storageKey="goals"
            rows={filteredGoals}
            columns={columns}
            emptyMessage="No goals match these filters."
            defaultSortKey="priority"
            defaultSortDirection="desc"
            pageResetKey={`${searchTerm}|${filterVisionAreaId}|${filterDreamId}|${filterStatus}|${filterPriority}|${filterOverdueOnly}|${filterMoonshotOnly}|${filterTargetFrom}|${filterTargetTo}`}
            rowClassName={(goal) => (goal.archived ? 'row-archived' : isOverdue(goal.targetDate, goal.status) ? 'row-overdue' : '')}
            selection={{
              selectedIds,
              onChange: setSelectedIds,
              rowLabel: (goal) => goal.title,
              actions: (
                <>
                  <label>
                    Set status
                    <FormControl fullWidth size="small">
                      <Select SelectDisplayProps={{ 'aria-label': "Set status" }} value={bulkStatus} onChange={(event) => setBulkStatus(event.target.value as WorkStatus)}>
                        {statusOptions.map((option) => <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>)}
                      </Select>
                    </FormControl>
                  </label>
                  <Button type="button" onClick={() => void handleBulkApply()} disabled={bulkApplying}>
                    {bulkApplying ? 'Applying...' : 'Apply'}
                  </Button>
                  <label>
                    Set priority
                    <FormControl fullWidth size="small">
                      <Select SelectDisplayProps={{ 'aria-label': 'Set priority' }} value={bulkPriority} onChange={(event) => setBulkPriority(event.target.value as Priority)}>
                        <MenuItem value="LOW">Low</MenuItem>
                        <MenuItem value="MEDIUM">Medium</MenuItem>
                        <MenuItem value="HIGH">High</MenuItem>
                        <MenuItem value="CRITICAL">Critical</MenuItem>
                      </Select>
                    </FormControl>
                  </label>
                  <Button type="button" onClick={() => void handleBulkPriority()} disabled={bulkApplying}>Apply</Button>
                  <BulkArchiveAction
                    selectedIds={selectedIds}
                    entityLabel="goal(s)"
                    onArchive={async (ids) => {
                      await crud.archiveMany(ids);
                      setSelectedIds(new Set());
                    }}
                  />
                </>
              ),
            }}
          />
        </CardContent>
      </Card>
      )}
    </PageSection>
  );
}
