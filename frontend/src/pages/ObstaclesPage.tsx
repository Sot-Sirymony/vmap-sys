import { FormEvent, useEffect, useState } from 'react';
import { listDreams } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import { archiveObstacle, permanentlyDeleteObstacle, createObstacle, listObstacles, restoreObstacle, updateObstacle } from '../api/obstacleApi';
import { listPartners } from '../api/partnerApi';
import { listSteps } from '../api/stepApi';
import { listTasks } from '../api/taskApi';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import { BulkArchiveAction } from '../components/common/BulkArchiveAction';
import { Button } from '../components/common/Button';
import { CrudModalForm } from '../components/common/CrudModalForm';
import { DataTable, type DataTableColumn } from '../components/common/DataTable';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { FilterSelect, optionsFromEntities, optionsFromLabels } from '../components/common/FilterSelect';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { RowActionsMenu } from '../components/common/RowActionsMenu';
import { SearchBar } from '../components/common/SearchBar';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { StatusBadge } from '../components/common/StatusBadge';
import { SummaryStrip } from '../components/common/SummaryStrip';
import { StatusBoard } from '../components/common/StatusBoard';
import { Textarea } from '../components/common/Textarea';
import { ViewToggle, type ViewMode } from '../components/common/ViewToggle';
import { useAuth } from '../context/AuthContext';
import { useCrudEntity } from '../hooks/useCrudEntity';
import { useUrlFilter } from '../hooks/useUrlFilter';
import type { Dream, Goal, Obstacle, ObstacleRequest, ObstacleStatus, ObstacleType, Partner, Severity, TaskItem, VisionStep } from '../types/vision';
import { suggestPartnerFor } from '../utils/partnerSuggestion';
import { obstacleStatusLabels, obstacleTypeLabels, priorityLabels, severityLabels } from '../utils/enumLabels';
import { matchesSearch } from '../utils/search';
import { severityRank } from '../utils/sortRank';
import { PageSection } from './PageSection';

const obstacleTypes: ObstacleType[] = ['KNOWLEDGE', 'SKILL', 'TIME', 'MONEY', 'MOTIVATION', 'PARTNER', 'SYSTEM', 'DECISION', 'OTHER'];
const severities: Severity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const statuses: ObstacleStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'ACCEPTED'];

export function ObstaclesPage() {
  const { token } = useAuth();
  const crud = useCrudEntity<Obstacle, ObstacleRequest>({
    token,
    entityLabel: 'obstacles',
    list: listObstacles,
    create: createObstacle,
    update: updateObstacle,
    archive: archiveObstacle,
    permanentlyDelete: permanentlyDeleteObstacle,
    archiveMessage: 'Archived.',
  });
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [steps, setSteps] = useState<VisionStep[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [partners, setPartners] = useState<Partner[]>([]);
  const [relatedDreamId, setRelatedDreamId] = useState('');
  const [relatedGoalId, setRelatedGoalId] = useState('');
  const [relatedStepId, setRelatedStepId] = useState('');
  const [relatedTaskId, setRelatedTaskId] = useState('');
  const [requiredPartnerId, setRequiredPartnerId] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [solution, setSolution] = useState('');
  const [obstacleType, setObstacleType] = useState<ObstacleType>('KNOWLEDGE');
  const [severity, setSeverity] = useState<Severity>('MEDIUM');
  const [status, setStatus] = useState<ObstacleStatus>('OPEN');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [searchTerm, setSearchTerm] = useState('');
  // In the URL, not component state: the dashboard's "Top obstacles" chart
  // links straight into a type-filtered view, and a filtered list stays
  // shareable and bookmarkable.
  const [filterObstacleType, setFilterObstacleType] = useUrlFilter('type');
  const [filterSeverity, setFilterSeverity] = useUrlFilter('severity');
  const [filterStatus, setFilterStatus] = useUrlFilter('status');
  const [filterDreamId, setFilterDreamId] = useUrlFilter('dreamId');
  const [viewMode, setViewMode] = useState<ViewMode>('list');

  useEffect(() => {
    if (!token) {
      return;
    }
    void crud.reload();
    void Promise.all([listDreams(token), listGoals(token), listSteps(token), listTasks(token), listPartners(token, 0, 500)]).then(
      ([dreamData, goalData, stepData, taskData, partnerPage]) => {
        setDreams(dreamData);
        setGoals(goalData);
        setSteps(stepData);
        setTasks(taskData);
        setPartners(partnerPage.content);
      },
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const success = await crud.save({
      relatedDreamId: optionalNumber(relatedDreamId),
      relatedGoalId: optionalNumber(relatedGoalId),
      relatedStepId: optionalNumber(relatedStepId),
      relatedTaskId: optionalNumber(relatedTaskId),
      title,
      description,
      obstacleType,
      severity,
      solution,
      requiredPartnerId: optionalNumber(requiredPartnerId),
      status,
    });
    if (success) {
      setTitle('');
      setDescription('');
      setSolution('');
    }
    return success;
  }

  function startEdit(obstacle: Obstacle) {
    crud.startEdit(obstacle.id);
    setRelatedDreamId(obstacle.relatedDreamId ? String(obstacle.relatedDreamId) : '');
    setRelatedGoalId(obstacle.relatedGoalId ? String(obstacle.relatedGoalId) : '');
    setRelatedStepId(obstacle.relatedStepId ? String(obstacle.relatedStepId) : '');
    setRelatedTaskId(obstacle.relatedTaskId ? String(obstacle.relatedTaskId) : '');
    setRequiredPartnerId(obstacle.requiredPartnerId ? String(obstacle.requiredPartnerId) : '');
    setTitle(obstacle.title);
    setDescription(obstacle.description ?? '');
    setSolution(obstacle.solution ?? '');
    setObstacleType(obstacle.obstacleType);
    setSeverity(obstacle.severity);
    setStatus(obstacle.status);
  }

  function cancelEdit() {
    crud.cancelEdit();
    setRelatedDreamId('');
    setRelatedGoalId('');
    setRelatedStepId('');
    setRelatedTaskId('');
    setRequiredPartnerId('');
    setTitle('');
    setDescription('');
    setSolution('');
    setObstacleType('KNOWLEDGE');
    setSeverity('MEDIUM');
    setStatus('OPEN');
  }

  const partnerSuggestion = suggestPartnerFor(obstacleType);

  // Board drag/dropdown move. There is no status PATCH endpoint for obstacles,
  // so the move sends a full update built from the loaded entity.
  async function handleMove(obstacle: Obstacle, nextStatus: ObstacleStatus) {
    if (!token || obstacle.status === nextStatus) {
      return;
    }
    try {
      await updateObstacle(token, obstacle.id, {
        relatedDreamId: obstacle.relatedDreamId,
        relatedGoalId: obstacle.relatedGoalId,
        relatedStepId: obstacle.relatedStepId,
        relatedTaskId: obstacle.relatedTaskId,
        title: obstacle.title,
        description: obstacle.description,
        obstacleType: obstacle.obstacleType,
        severity: obstacle.severity,
        solution: obstacle.solution,
        requiredPartnerId: obstacle.requiredPartnerId,
        status: nextStatus,
      });
      await crud.reload();
    } catch (moveError) {
      crud.setError(moveError instanceof Error ? moveError.message : 'Unable to update obstacle status.');
    }
  }

  // Shared by the table's action column and the board's cards, so both offer
  // the same row actions.
  function renderObstacleActions(obstacle: Obstacle) {
    return (
      <RowActionsMenu
        onEdit={() => startEdit(obstacle)}
        onArchive={() => void crud.archive(obstacle.id)}
        onRestore={() => void crud.restore(obstacle.id)}
        onDeletePermanently={() => void crud.permanentlyDelete(obstacle.id)}
        archived={obstacle.archived}
        label="Obstacle actions"
      />
    );
  }

  const filteredObstacles = crud.items.filter((obstacle) => {
    if (filterObstacleType && obstacle.obstacleType !== filterObstacleType) {
      return false;
    }
    if (filterSeverity && obstacle.severity !== filterSeverity) {
      return false;
    }
    if (filterStatus && obstacle.status !== filterStatus) {
      return false;
    }
    if (filterDreamId && String(obstacle.relatedDreamId ?? '') !== filterDreamId) {
      return false;
    }
    return matchesSearch(
      searchTerm,
      obstacle.title,
      obstacle.description,
      obstacle.solution,
      obstacleTypeLabels[obstacle.obstacleType],
    );
  });

  const hasFilters = Boolean(
    searchTerm || filterObstacleType || filterSeverity || filterStatus || filterDreamId,
  );

  const columns: DataTableColumn<Obstacle>[] = [
    { key: 'title', label: 'Title', sortValue: (obstacle) => obstacle.title, sx: { fontWeight: 500 }, render: (obstacle) => obstacle.title },
    {
      key: 'obstacleType',
      label: 'Type',
      sortValue: (obstacle) => obstacleTypeLabels[obstacle.obstacleType],
      render: (obstacle) => obstacleTypeLabels[obstacle.obstacleType],
    },
    {
      key: 'suggestedPartner',
      label: 'Suggested Partner',
      sortValue: (obstacle) => suggestPartnerFor(obstacle.obstacleType)?.label,
      render: (obstacle) => suggestPartnerFor(obstacle.obstacleType)?.label ?? '-',
    },
    {
      key: 'severity',
      label: 'Severity',
      sortValue: (obstacle) => severityRank(obstacle.severity),
      render: (obstacle) => <StatusBadge status={obstacle.severity} />,
    },
    {
      key: 'status',
      label: 'Status',
      sortValue: (obstacle) => obstacle.status,
      render: (obstacle) => <StatusBadge status={obstacle.status} />,
    },
    {
      key: 'solution',
      label: 'Solution',
      sortValue: (obstacle) => obstacle.solution,
      render: (obstacle) => obstacle.solution || '-',
    },
    {
      key: 'actions',
      label: 'Action',
      className: 'row-actions',
      render: (obstacle) => renderObstacleActions(obstacle),
    },
  ];

  const formFields = (
    <>
      <label>
        Title
        <Input value={title} onChange={(event) => setTitle(event.target.value)} required autoFocus />
      </label>
      <label>
        Type
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Type" }} value={obstacleType} onChange={(event) => setObstacleType(event.target.value as ObstacleType)}>
            {obstacleTypes.map((value) => <MenuItem value={value} key={value}>{obstacleTypeLabels[value]}</MenuItem>)}
          </Select>
        </FormControl>
        {partnerSuggestion && <span className="field-hint">Suggested support: {partnerSuggestion.label}</span>}
      </label>
      <label>
        Severity
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Severity" }} value={severity} onChange={(event) => setSeverity(event.target.value as Severity)}>
            {severities.map((value) => <MenuItem value={value} key={value}>{priorityLabels[value]}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Status
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Status" }} value={status} onChange={(event) => setStatus(event.target.value as ObstacleStatus)}>
            {statuses.map((value) => <MenuItem value={value} key={value}>{obstacleStatusLabels[value]}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Dream
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Dream" }} displayEmpty value={relatedDreamId} onChange={(event) => setRelatedDreamId(event.target.value)}>
            <MenuItem value="">None</MenuItem>
            {dreams.map((dream) => <MenuItem value={String(dream.id)} key={dream.id}>{dream.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Goal
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Goal" }} displayEmpty value={relatedGoalId} onChange={(event) => setRelatedGoalId(event.target.value)}>
            <MenuItem value="">None</MenuItem>
            {goals.map((goal) => <MenuItem value={String(goal.id)} key={goal.id}>{goal.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Step
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Step" }} displayEmpty value={relatedStepId} onChange={(event) => setRelatedStepId(event.target.value)}>
            <MenuItem value="">None</MenuItem>
            {steps.map((step) => <MenuItem value={String(step.id)} key={step.id}>{step.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Task
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Task" }} displayEmpty value={relatedTaskId} onChange={(event) => setRelatedTaskId(event.target.value)}>
            <MenuItem value="">None</MenuItem>
            {tasks.map((task) => <MenuItem value={String(task.id)} key={task.id}>{task.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Required Partner
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Required Partner" }} displayEmpty value={requiredPartnerId} onChange={(event) => setRequiredPartnerId(event.target.value)}>
            <MenuItem value="">None</MenuItem>
            {partners.map((partner) => <MenuItem value={String(partner.id)} key={partner.id}>{partner.name}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label className="field-full">
        Description
        <Textarea value={description} onChange={(event) => setDescription(event.target.value)} />
      </label>
      <label className="field-full">
        Solution
        <Textarea value={solution} onChange={(event) => setSolution(event.target.value)} />
      </label>
    </>
  );

  return (
    <PageSection
      title="Obstacles"
      subtitle="Track blockers and the support needed to resolve them."
      actions={<Button type="button" onClick={() => setCreateOpen(true)}>Create obstacle</Button>}
    >
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Create obstacle"
        editTitle="Edit Obstacle"
        saving={crud.saving}
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
          { key: 'total', label: crud.items.length === 1 ? 'obstacle' : 'obstacles', count: crud.items.length },
          { key: 'open', label: 'open', count: crud.items.filter((obstacle) => obstacle.status === 'OPEN').length, tone: 'warning', active: filterStatus === 'OPEN', onClick: () => setFilterStatus(filterStatus === 'OPEN' ? '' : 'OPEN') },
          { key: 'critical', label: 'critical', count: crud.items.filter((obstacle) => obstacle.severity === 'CRITICAL').length, tone: 'critical', active: filterSeverity === 'CRITICAL', onClick: () => setFilterSeverity(filterSeverity === 'CRITICAL' ? '' : 'CRITICAL') },
          { key: 'resolved', label: 'resolved', count: crud.items.filter((obstacle) => obstacle.status === 'RESOLVED').length, tone: 'positive', active: filterStatus === 'RESOLVED', onClick: () => setFilterStatus(filterStatus === 'RESOLVED' ? '' : 'RESOLVED') },
        ]}
      />
      <Card className="filter-bar flex-row">
        <SearchBar value={searchTerm} onChange={setSearchTerm} entityLabel="obstacles" />
        <FilterSelect
          label="Type"
          value={filterObstacleType}
          onChange={setFilterObstacleType}
          options={optionsFromLabels(obstacleTypeLabels)}
        />
        <FilterSelect
          label="Severity"
          value={filterSeverity}
          onChange={setFilterSeverity}
          options={optionsFromLabels(severityLabels)}
        />
        <FilterSelect
          label="Status"
          value={filterStatus}
          onChange={setFilterStatus}
          options={optionsFromLabels(obstacleStatusLabels)}
        />
        <FilterSelect
          label="Dream"
          value={filterDreamId}
          onChange={setFilterDreamId}
          options={optionsFromEntities(dreams, (dream) => dream.title)}
        />
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      <div className="view-toggle-row">
        <ViewToggle value={viewMode} onChange={setViewMode} label="Obstacle view" />
      </div>
      {viewMode === 'board' && (
        <StatusBoard
          items={filteredObstacles}
          columns={statuses.map((value) => ({ value, label: obstacleStatusLabels[value] }))}
          statusOf={(obstacle) => obstacle.status}
          entityLabel="obstacles"
          onMove={(obstacle, nextStatus) => void handleMove(obstacle, nextStatus)}
          renderCard={(obstacle) => (
            <>
              <strong>{obstacle.title}</strong>
              <p>{obstacleTypeLabels[obstacle.obstacleType]}{suggestPartnerFor(obstacle.obstacleType) ? ` · Suggested: ${suggestPartnerFor(obstacle.obstacleType)?.label}` : ''}</p>
              <div className="inline-meta">
                <StatusBadge status={obstacle.severity} />
              </div>
            </>
          )}
          cardActions={(obstacle) => renderObstacleActions(obstacle)}
        />
      )}
      {viewMode === 'list' && (
      <Card>
        <CardContent>
        <DataTable
          rows={filteredObstacles}
          columns={columns}
          emptyMessage={hasFilters ? 'No obstacles match these filters.' : 'No obstacles yet.'}
          defaultSortKey="severity"
          defaultSortDirection="desc"
          pageResetKey={`${searchTerm}|${filterObstacleType}|${filterSeverity}|${filterStatus}|${filterDreamId}`}
          rowClassName={(obstacle) => (obstacle.archived ? 'row-archived' : '')}
          selection={{
            selectedIds,
            onChange: setSelectedIds,
            rowLabel: (obstacle) => obstacle.title,
            actions: (
              <BulkArchiveAction
                selectedIds={selectedIds}
                entityLabel="obstacle(s)"
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
    </PageSection>
  );
}

function optionalNumber(value: string) {
  return value ? Number(value) : undefined;
}
