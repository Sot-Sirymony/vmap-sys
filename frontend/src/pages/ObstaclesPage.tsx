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
import { CrudModalForm } from '../components/common/CrudModalForm';
import { DataTable, type DataTableColumn } from '../components/common/DataTable';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { RowActionsMenu } from '../components/common/RowActionsMenu';
import { SearchBar } from '../components/common/SearchBar';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { StatusBadge } from '../components/common/StatusBadge';
import { Textarea } from '../components/common/Textarea';
import { useAuth } from '../context/AuthContext';
import { useCrudEntity } from '../hooks/useCrudEntity';
import type { Dream, Goal, Obstacle, ObstacleRequest, ObstacleStatus, ObstacleType, Partner, Severity, TaskItem, VisionStep } from '../types/vision';
import { suggestPartnerFor } from '../utils/partnerSuggestion';
import { obstacleStatusLabels, obstacleTypeLabels, priorityLabels } from '../utils/enumLabels';
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
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [solution, setSolution] = useState('');
  const [obstacleType, setObstacleType] = useState<ObstacleType>('KNOWLEDGE');
  const [severity, setSeverity] = useState<Severity>('MEDIUM');
  const [status, setStatus] = useState<ObstacleStatus>('OPEN');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [searchTerm, setSearchTerm] = useState('');

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

  const filteredObstacles = crud.items.filter((obstacle) =>
    matchesSearch(
      searchTerm,
      obstacle.title,
      obstacle.description,
      obstacle.solution,
      obstacleTypeLabels[obstacle.obstacleType],
    ),
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
      render: (obstacle) => (
        <RowActionsMenu
          onEdit={() => startEdit(obstacle)}
          onArchive={() => void crud.archive(obstacle.id)}
          onRestore={() => void crud.restore(obstacle.id)}
          onDeletePermanently={() => void crud.permanentlyDelete(obstacle.id)}
          archived={obstacle.archived}
          label="Obstacle actions"
        />
      ),
    },
  ];

  const formFields = (
    <>
      <label>
        Title
        <Input value={title} onChange={(event) => setTitle(event.target.value)} required />
      </label>
      <label>
        Type
        <FormControl fullWidth size="small">
          <Select value={obstacleType} onChange={(event) => setObstacleType(event.target.value as ObstacleType)}>
            {obstacleTypes.map((value) => <MenuItem value={value} key={value}>{obstacleTypeLabels[value]}</MenuItem>)}
          </Select>
        </FormControl>
        {partnerSuggestion && <span className="field-hint">Suggested support: {partnerSuggestion.label}</span>}
      </label>
      <label>
        Severity
        <FormControl fullWidth size="small">
          <Select value={severity} onChange={(event) => setSeverity(event.target.value as Severity)}>
            {severities.map((value) => <MenuItem value={value} key={value}>{priorityLabels[value]}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Status
        <FormControl fullWidth size="small">
          <Select value={status} onChange={(event) => setStatus(event.target.value as ObstacleStatus)}>
            {statuses.map((value) => <MenuItem value={value} key={value}>{obstacleStatusLabels[value]}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Dream
        <FormControl fullWidth size="small">
          <Select displayEmpty value={relatedDreamId} onChange={(event) => setRelatedDreamId(event.target.value)}>
            <MenuItem value="">None</MenuItem>
            {dreams.map((dream) => <MenuItem value={String(dream.id)} key={dream.id}>{dream.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Goal
        <FormControl fullWidth size="small">
          <Select displayEmpty value={relatedGoalId} onChange={(event) => setRelatedGoalId(event.target.value)}>
            <MenuItem value="">None</MenuItem>
            {goals.map((goal) => <MenuItem value={String(goal.id)} key={goal.id}>{goal.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Step
        <FormControl fullWidth size="small">
          <Select displayEmpty value={relatedStepId} onChange={(event) => setRelatedStepId(event.target.value)}>
            <MenuItem value="">None</MenuItem>
            {steps.map((step) => <MenuItem value={String(step.id)} key={step.id}>{step.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Task
        <FormControl fullWidth size="small">
          <Select displayEmpty value={relatedTaskId} onChange={(event) => setRelatedTaskId(event.target.value)}>
            <MenuItem value="">None</MenuItem>
            {tasks.map((task) => <MenuItem value={String(task.id)} key={task.id}>{task.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Required Partner
        <FormControl fullWidth size="small">
          <Select displayEmpty value={requiredPartnerId} onChange={(event) => setRequiredPartnerId(event.target.value)}>
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
    <PageSection title="Obstacles" subtitle="Track blockers and the support needed to resolve them.">
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Create obstacle"
        editTitle="Edit Obstacle"
        saving={crud.saving}
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {crud.loading && <Loading />}
      {crud.error && <ErrorMessage message={crud.error} />}
      <Card className="filter-bar flex-row">
        <SearchBar value={searchTerm} onChange={setSearchTerm} entityLabel="obstacles" />
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      <Card>
        <CardContent>
        <DataTable
          rows={filteredObstacles}
          columns={columns}
          emptyMessage={searchTerm ? 'No obstacles match this search.' : 'No obstacles yet.'}
          pageResetKey={searchTerm}
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
    </PageSection>
  );
}

function optionalNumber(value: string) {
  return value ? Number(value) : undefined;
}
