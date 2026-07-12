import { FormEvent, useEffect, useState } from 'react';
import { Rocket } from 'lucide-react';
import { listDreams } from '../api/dreamApi';
import { archiveGoal, permanentlyDeleteGoal, createGoal, getGoalArchiveImpact, listGoals, restoreGoal, updateGoal, updateGoalStatus } from '../api/goalApi';
import { listVisionAreas } from '../api/visionAreaApi';
import { EmptyState } from '../components/common/EmptyState';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import Tooltip from '@mui/material/Tooltip';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import { Button } from '../components/common/Button';
import { CrudModalForm } from '../components/common/CrudModalForm';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { ProgressBar } from '../components/common/ProgressBar';
import { RowActionsMenu } from '../components/common/RowActionsMenu';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { StatusBadge } from '../components/common/StatusBadge';
import { Textarea } from '../components/common/Textarea';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useCrudEntity } from '../hooks/useCrudEntity';
import type { Dream, Goal, GoalRequest, Priority, VisionArea, WorkStatus } from '../types/vision';
import { isOverdue } from '../utils/overdue';
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
  const [dreamId, setDreamId] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [successCriteria, setSuccessCriteria] = useState('');
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [targetDate, setTargetDate] = useState('');
  const [status, setStatus] = useState<WorkStatus>('NOT_STARTED');
  const [moonshot, setMoonshot] = useState(false);
  const [moonshotVision, setMoonshotVision] = useState('');
  const [filterVisionAreaId, setFilterVisionAreaId] = useState('');
  const [filterDreamId, setFilterDreamId] = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [filterPriority, setFilterPriority] = useState('');
  const [filterOverdueOnly, setFilterOverdueOnly] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [bulkStatus, setBulkStatus] = useState<WorkStatus>('IN_PROGRESS');
  const [bulkApplying, setBulkApplying] = useState(false);
  const { showToast } = useToast();

  useEffect(() => {
    if (!token) {
      return;
    }
    void crud.reload();
    void Promise.all([listDreams(token), listVisionAreas(token)]).then(([dreamData, areaData]) => {
      setDreams(dreamData.filter((dream) => dream.status !== 'ARCHIVED'));
      setVisionAreas(areaData);
      setDreamId((current) => current || String(dreamData[0]?.id ?? ''));
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
    return true;
  });

  const allFilteredSelected = filteredGoals.length > 0 && filteredGoals.every((goal) => selectedIds.has(goal.id));

  function toggleSelected(id: number) {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  function toggleSelectAll() {
    setSelectedIds(allFilteredSelected ? new Set() : new Set(filteredGoals.map((goal) => goal.id)));
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

  const formFields = (
    <>
      <label>
        Dream
        <FormControl fullWidth size="small" required>
          <Select value={dreamId} onChange={(event) => setDreamId(event.target.value)}>
            {dreams.map((dream) => <MenuItem value={String(dream.id)} key={dream.id}>{dream.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Title
        <Input value={title} onChange={(event) => setTitle(event.target.value)} required />
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
    <PageSection title="Goals" subtitle="Define specific results for each dream.">
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Create goal"
        editTitle="Edit Goal"
        saving={crud.saving}
        disabled={dreams.length === 0}
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {crud.loading && <Loading />}
      {crud.error && <ErrorMessage message={crud.error} />}
      <Card className="filter-bar flex-row">
        <label>
          Vision Area
          <FormControl fullWidth size="small">
            <Select displayEmpty value={filterVisionAreaId} onChange={(event) => setFilterVisionAreaId(event.target.value)}>
              <MenuItem value="">All</MenuItem>
              {visionAreas.map((area) => <MenuItem value={String(area.id)} key={area.id}>{area.name}</MenuItem>)}
            </Select>
          </FormControl>
        </label>
        <label>
          Dream
          <FormControl fullWidth size="small">
            <Select displayEmpty value={filterDreamId} onChange={(event) => setFilterDreamId(event.target.value)}>
              <MenuItem value="">All</MenuItem>
              {dreams.map((dream) => <MenuItem value={String(dream.id)} key={dream.id}>{dream.title}</MenuItem>)}
            </Select>
          </FormControl>
        </label>
        <label>
          Status
          <FormControl fullWidth size="small">
            <Select displayEmpty value={filterStatus} onChange={(event) => setFilterStatus(event.target.value)}>
              <MenuItem value="">All</MenuItem>
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
          Priority
          <FormControl fullWidth size="small">
            <Select displayEmpty value={filterPriority} onChange={(event) => setFilterPriority(event.target.value)}>
              <MenuItem value="">All</MenuItem>
              <MenuItem value="LOW">Low</MenuItem>
              <MenuItem value="MEDIUM">Medium</MenuItem>
              <MenuItem value="HIGH">High</MenuItem>
              <MenuItem value="CRITICAL">Critical</MenuItem>
            </Select>
          </FormControl>
        </label>
        <label className="checkbox-field">
          <Checkbox checked={filterOverdueOnly} onChange={(event) => setFilterOverdueOnly(event.target.checked)} />
          Overdue only
        </label>
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      {selectedIds.size > 0 && (
        <Card className="bulk-actions-bar flex-row">
          <span className="bulk-count">{selectedIds.size} selected</span>
          <label>
            Set status
            <FormControl fullWidth size="small">
              <Select value={bulkStatus} onChange={(event) => setBulkStatus(event.target.value as WorkStatus)}>
                {statusOptions.map((option) => <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>)}
              </Select>
            </FormControl>
          </label>
          <Button type="button" onClick={() => void handleBulkApply()} disabled={bulkApplying}>
            {bulkApplying ? 'Applying...' : 'Apply'}
          </Button>
          <Button type="button" variant="secondary" onClick={() => setSelectedIds(new Set())}>Clear selection</Button>
        </Card>
      )}
      <Card>
        <CardContent>
        {filteredGoals.length === 0 ? (
          <EmptyState>No goals match these filters.</EmptyState>
        ) : (
          <TableContainer>
          <Table className="data-table">
            <TableHead>
              <TableRow>
                <TableCell>
                  <Checkbox checked={allFilteredSelected} onChange={toggleSelectAll} aria-label="Select all goals" />
                </TableCell>
                <TableCell>Code</TableCell>
                <TableCell>Goal</TableCell>
                <TableCell>Priority</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Progress</TableCell>
                <TableCell>Action</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredGoals.map((goal) => (
                <TableRow key={goal.id} className={goal.archived ? 'row-archived' : isOverdue(goal.targetDate, goal.status) ? 'row-overdue' : ''}>
                  <TableCell>
                    <Checkbox checked={selectedIds.has(goal.id)} onChange={() => toggleSelected(goal.id)} aria-label={`Select ${goal.title}`} />
                  </TableCell>
                  <TableCell>{goal.code}</TableCell>
                  <TableCell sx={{ fontWeight: 500 }}>
                    <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75 }}>
                      {goal.moonshot && (
                        <Tooltip title={goal.moonshotVision || 'Moonshot goal'} arrow>
                          <Box component="span" sx={{ display: 'inline-flex', color: '#7c3aed' }} aria-label="Moonshot goal">
                            <Rocket size={15} />
                          </Box>
                        </Tooltip>
                      )}
                      {goal.title}
                    </Box>
                  </TableCell>
                  <TableCell><PriorityBadge priority={goal.priority} /></TableCell>
                  <TableCell><StatusBadge status={goal.status} /></TableCell>
                  <TableCell><ProgressBar value={Number(goal.progressPercent)} /></TableCell>
                  <TableCell className="row-actions">
                    <RowActionsMenu
                      onEdit={() => startEdit(goal)}
                      onArchive={() => void crud.archive(goal.id)}
                      onRestore={() => void crud.restore(goal.id)}
                      onDeletePermanently={() => void crud.permanentlyDelete(goal.id)}
                      archived={goal.archived}
                      confirmArchive={() => archiveImpactMessage(goal)}
                      label="Goal actions"
                    />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          </TableContainer>
        )}
        </CardContent>
      </Card>
    </PageSection>
  );
}
