import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { archiveDream, permanentlyDeleteDream, createDream, getDreamArchiveImpact, listDreams, restoreDream, updateDream } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import { listVisionAreas } from '../api/visionAreaApi';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import { Sparkles } from 'lucide-react';
import { BulkArchiveAction } from '../components/common/BulkArchiveAction';
import { Button } from '../components/common/Button';
import { CrudModalForm } from '../components/common/CrudModalForm';
import { EmptyState } from '../components/common/EmptyState';
import { DreamWizard } from '../components/forms/DreamWizard';
import { DataTable, type DataTableColumn } from '../components/common/DataTable';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { FilterSelect, optionsFromEntities, optionsFromLabels } from '../components/common/FilterSelect';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { RowActionsMenu } from '../components/common/RowActionsMenu';
import { SearchBar } from '../components/common/SearchBar';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { RelativeDate } from '../components/common/RelativeDate';
import { StatusBadge } from '../components/common/StatusBadge';
import { SummaryStrip } from '../components/common/SummaryStrip';
import { StatusBoard } from '../components/common/StatusBoard';
import { Textarea } from '../components/common/Textarea';
import { ViewToggle, type ViewMode } from '../components/common/ViewToggle';
import { useAuth } from '../context/AuthContext';
import { useCrudEntity } from '../hooks/useCrudEntity';
import { useStoredState } from '../hooks/useStoredState';
import { useUrlFilter, useUrlFlag } from '../hooks/useUrlFilter';
import type { Dream, DreamRequest, DreamStatus, DreamType, Priority, VisionArea } from '../types/vision';
import { dreamStatusLabels, dreamTypeLabels, priorityLabels } from '../utils/enumLabels';
import { isOverdue } from '../utils/overdue';
import { matchesSearch } from '../utils/search';
import { priorityRank } from '../utils/sortRank';
import { PageSection } from './PageSection';

export function DreamsPage() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const crud = useCrudEntity<Dream, DreamRequest>({
    token,
    entityLabel: 'dreams',
    list: listDreams,
    create: createDream,
    update: updateDream,
    archive: archiveDream,
    permanentlyDelete: permanentlyDeleteDream,
    restore: restoreDream,
  });
  const [visionAreas, setVisionAreas] = useState<VisionArea[]>([]);
  // Count of live goals per dream, for the Goals column.
  const [goalCounts, setGoalCounts] = useState<Map<number, number>>(new Map());
  const [visionAreaId, setVisionAreaId] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [whyImportant, setWhyImportant] = useState('');
  const [successDefinition, setSuccessDefinition] = useState('');
  const [dreamType, setDreamType] = useState<DreamType>('LONG_TERM');
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [targetDate, setTargetDate] = useState('');
  const [status, setStatus] = useState<DreamStatus>('ACTIVE');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [searchTerm, setSearchTerm] = useState('');
  // In the URL, not component state: the dashboard links straight into a
  // filtered view, and a filtered list stays shareable and bookmarkable.
  const [filterVisionAreaId, setFilterVisionAreaId] = useUrlFilter('visionAreaId');
  const [filterDreamType, setFilterDreamType] = useUrlFilter('type');
  const [filterPriority, setFilterPriority] = useUrlFilter('priority');
  const [filterStatus, setFilterStatus] = useUrlFilter('status');
  const [filterOverdueOnly, setFilterOverdueOnly] = useUrlFlag('overdue');
  const [searchParams, setSearchParams] = useSearchParams();
  // FR-21.3: creation goes through the coaching wizard by default; the flat
  // form remains for edits and for "skip the guide".
  const [wizardOpen, setWizardOpen] = useState(false);
  const [flatCreateOpen, setFlatCreateOpen] = useState(false);
  const [viewMode, setViewMode] = useStoredState<ViewMode>('vms-view-dreams', 'list');

  // Arrived from a vision area's "Add dream" shortcut or the dashboard's
  // getting-started checklist: pre-select the area and open the wizard, then
  // strip the params so a refresh doesn't reopen it.
  useEffect(() => {
    if (searchParams.get('create') !== 'dream') {
      return;
    }
    const parent = searchParams.get('parent');
    if (parent) {
      setVisionAreaId(parent);
    }
    setWizardOpen(true);
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
    void listVisionAreas(token).then((areaData) => {
      setVisionAreas(areaData.filter((area) => area.status !== 'ARCHIVED'));
      setVisionAreaId((current) => current || String(areaData[0]?.id ?? ''));
    });
    void reloadGoalCounts(token);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  async function reloadGoalCounts(activeToken: string) {
    const goals = await listGoals(activeToken);
    const counts = new Map<number, number>();
    for (const goal of goals) {
      counts.set(goal.dreamId, (counts.get(goal.dreamId) ?? 0) + 1);
    }
    setGoalCounts(counts);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!visionAreaId) {
      return false;
    }
    const success = await crud.save({
      visionAreaId: Number(visionAreaId),
      title,
      description,
      whyImportant,
      successDefinition,
      dreamType,
      priority,
      targetDate: targetDate || undefined,
      status,
    });
    if (success) {
      setTitle('');
      setDescription('');
      setWhyImportant('');
      setSuccessDefinition('');
    }
    return success;
  }

  function startEdit(dream: Dream) {
    crud.startEdit(dream.id);
    setVisionAreaId(String(dream.visionAreaId));
    setTitle(dream.title);
    setDescription(dream.description ?? '');
    setWhyImportant(dream.whyImportant ?? '');
    setSuccessDefinition(dream.successDefinition ?? '');
    setDreamType(dream.dreamType);
    setPriority(dream.priority);
    setTargetDate(dream.targetDate ?? '');
    setStatus(dream.status);
  }

  function cancelEdit() {
    crud.cancelEdit();
    setTitle('');
    setDescription('');
    setWhyImportant('');
    setSuccessDefinition('');
    setDreamType('LONG_TERM');
    setPriority('HIGH');
    setTargetDate('');
    setStatus('ACTIVE');
  }

  // Board drag/dropdown move. There is no status PATCH endpoint for dreams, so
  // the move sends a full update built from the loaded entity.
  async function handleMove(dream: Dream, nextStatus: DreamStatus) {
    if (!token || dream.status === nextStatus) {
      return;
    }
    try {
      await updateDream(token, dream.id, {
        visionAreaId: dream.visionAreaId,
        title: dream.title,
        description: dream.description,
        whyImportant: dream.whyImportant,
        successDefinition: dream.successDefinition,
        dreamType: dream.dreamType,
        priority: dream.priority,
        targetDate: dream.targetDate,
        status: nextStatus,
      });
      await crud.reload();
    } catch (moveError) {
      crud.setError(moveError instanceof Error ? moveError.message : 'Unable to update dream status.');
    }
  }

  async function archiveImpactMessage(dream: Dream) {
    if (!token) {
      return 'Archive this dream?';
    }
    const impact = await getDreamArchiveImpact(token, dream.id);
    return `Archiving "${dream.title}" also archives ${impact.goals} goal(s), ${impact.steps} step(s), and ${impact.tasks} task(s). Everything can be restored later with "Show archived".`;
  }

  const filteredDreams = crud.items.filter((dream) => {
    if (filterVisionAreaId && String(dream.visionAreaId) !== filterVisionAreaId) {
      return false;
    }
    if (filterDreamType && dream.dreamType !== filterDreamType) {
      return false;
    }
    if (filterPriority && dream.priority !== filterPriority) {
      return false;
    }
    if (filterStatus && dream.status !== filterStatus) {
      return false;
    }
    if (filterOverdueOnly && !isOverdue(dream.targetDate, dream.status)) {
      return false;
    }
    return matchesSearch(searchTerm, dream.code, dream.title, dream.description, dream.whyImportant, dream.successDefinition);
  });

  const hasFilters = Boolean(
    searchTerm || filterVisionAreaId || filterDreamType || filterPriority || filterStatus || filterOverdueOnly,
  );

  // Shared by the table's action column and the board's cards, so both offer
  // the same row actions.
  function renderDreamActions(dream: Dream) {
    return (
      <RowActionsMenu
        onEdit={() => startEdit(dream)}
        onArchive={() => void crud.archive(dream.id)}
        onRestore={() => void crud.restore(dream.id)}
        onDeletePermanently={() => void crud.permanentlyDelete(dream.id)}
        archived={dream.archived}
        confirmArchive={() => archiveImpactMessage(dream)}
        extraActions={[
          { label: 'View map', onClick: () => navigate(`/dreams/${dream.id}`) },
          { label: 'View goals', onClick: () => navigate(`/goals?dreamId=${dream.id}`) },
          { label: 'Add goal', onClick: () => navigate(`/goals?create=goal&parent=${dream.id}`) },
        ]}
        label="Dream actions"
      />
    );
  }

  const columns: DataTableColumn<Dream>[] = [
    { key: 'code', label: 'Code', sortValue: (dream) => dream.code, sx: { fontSize: 'var(--font-caption)', color: 'var(--text-label)' }, render: (dream) => dream.code },
    {
      key: 'title',
      label: 'Dream',
      sortValue: (dream) => dream.title,
      sx: { fontWeight: 500 },
      // FR-24.4: the map is a dream's landing surface — its title goes there.
      render: (dream) => <Link className="table-title-link" to={`/dreams/${dream.id}`}>{dream.title}</Link>,
    },
    {
      key: 'priority',
      label: 'Priority',
      sortValue: (dream) => priorityRank(dream.priority),
      render: (dream) => <PriorityBadge priority={dream.priority} />,
    },
    {
      key: 'status',
      label: 'Status',
      sortValue: (dream) => dream.status,
      render: (dream) => <StatusBadge status={dream.status} />,
    },
    {
      key: 'goals',
      label: 'Goals',
      sortValue: (dream) => goalCounts.get(dream.id) ?? 0,
      render: (dream) => goalCounts.get(dream.id) ?? 0,
    },
    {
      key: 'targetDate',
      label: 'Target',
      sortValue: (dream) => dream.targetDate,
      render: (dream) => <RelativeDate date={dream.targetDate} completed={dream.status === 'COMPLETED'} />,
    },
    {
      key: 'actions',
      label: 'Action',
      className: 'row-actions',
      render: (dream) => renderDreamActions(dream),
    },
  ];

  const clarityChecks = [
    { label: 'What exactly do you want to achieve?', met: title.trim().length >= 8 },
    { label: 'Why does this matter to you?', met: whyImportant.trim().length >= 15 },
    { label: 'What will success look like?', met: successDefinition.trim().length >= 15 },
    { label: 'When do you want to achieve it?', met: Boolean(targetDate) },
    { label: 'Which area of life or work does this belong to?', met: Boolean(visionAreaId) },
  ];
  const isVague = clarityChecks.some((check) => !check.met);

  const formFields = (
    <>
      <label>
        Vision Area
        <FormControl fullWidth size="small" required>
          <Select SelectDisplayProps={{ 'aria-label': "Vision Area" }} displayEmpty value={visionAreaId} onChange={(event) => setVisionAreaId(event.target.value)}>
            <MenuItem value="" disabled><em>Select a vision area</em></MenuItem>
            {visionAreas.map((area) => <MenuItem value={String(area.id)} key={area.id}>{area.name}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Title
        <Input value={title} onChange={(event) => setTitle(event.target.value)} required autoFocus />
      </label>
      <label>
        Type
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Type" }} value={dreamType} onChange={(event) => setDreamType(event.target.value as DreamType)}>
            <MenuItem value="SHORT_TERM">Short Term</MenuItem>
            <MenuItem value="LONG_TERM">Long Term</MenuItem>
            <MenuItem value="LIFETIME">Lifetime</MenuItem>
          </Select>
        </FormControl>
      </label>
      <label>
        Target Date
        <Input type="date" value={targetDate} onChange={(event) => setTargetDate(event.target.value)} />
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
          <Select SelectDisplayProps={{ 'aria-label': "Status" }} value={status} onChange={(event) => setStatus(event.target.value as DreamStatus)}>
            <MenuItem value="IDEA">Idea</MenuItem>
            <MenuItem value="ACTIVE">Active</MenuItem>
            <MenuItem value="PAUSED">Paused</MenuItem>
            <MenuItem value="COMPLETED">Completed</MenuItem>
          </Select>
        </FormControl>
      </label>
      {isVague && (
        <div className="field-full coaching-panel">
          <strong>Make this dream clearer</strong>
          <p>A vague dream is hard to turn into goals. Answer these before saving:</p>
          <ul>
            {clarityChecks.map((check) => (
              <li key={check.label} className={check.met ? 'coaching-item-done' : ''}>
                <span aria-hidden="true">{check.met ? '✓' : '○'}</span> {check.label}
              </li>
            ))}
          </ul>
        </div>
      )}
      <label className="field-full">
        Why Important
        <Textarea value={whyImportant} onChange={(event) => setWhyImportant(event.target.value)} />
      </label>
      <label className="field-full">
        Success Definition
        <Textarea value={successDefinition} onChange={(event) => setSuccessDefinition(event.target.value)} />
      </label>
      <label className="field-full">
        Description
        <Textarea value={description} onChange={(event) => setDescription(event.target.value)} />
      </label>
    </>
  );

  return (
    <PageSection
      title="Dreams"
      subtitle="Capture meaningful outcomes and prepare them for goals."
      actions={
        <Button type="button" onClick={() => setWizardOpen(true)} disabled={visionAreas.length === 0}>
          Create dream
        </Button>
      }
    >
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Create dream"
        editTitle="Edit Dream"
        saving={crud.saving}
        disabled={visionAreas.length === 0}
        creating={flatCreateOpen}
        onCreatingChange={setFlatCreateOpen}
        hideTrigger
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {wizardOpen && (
        <DreamWizard
          token={token ?? ''}
          visionAreas={visionAreas}
          initialVisionAreaId={visionAreaId || undefined}
          onClose={() => setWizardOpen(false)}
          onSkip={() => {
            setWizardOpen(false);
            setFlatCreateOpen(true);
          }}
          onCreated={() => {
            void crud.reload();
            if (token) {
              void reloadGoalCounts(token);
            }
          }}
        />
      )}
      {crud.loading && <Loading variant="table" />}
      {crud.error && <ErrorMessage message={crud.error} onRetry={() => void crud.reload()} />}
      <SummaryStrip
        chips={[
          { key: 'total', label: crud.items.length === 1 ? 'dream' : 'dreams', count: crud.items.length },
          { key: 'overdue', label: 'overdue', count: crud.items.filter((dream) => isOverdue(dream.targetDate, dream.status)).length, tone: 'critical', active: filterOverdueOnly, onClick: () => setFilterOverdueOnly(!filterOverdueOnly) },
          { key: 'active', label: 'active', count: crud.items.filter((dream) => dream.status === 'ACTIVE').length, active: filterStatus === 'ACTIVE', onClick: () => setFilterStatus(filterStatus === 'ACTIVE' ? '' : 'ACTIVE') },
          { key: 'completed', label: 'completed', count: crud.items.filter((dream) => dream.status === 'COMPLETED').length, tone: 'positive', active: filterStatus === 'COMPLETED', onClick: () => setFilterStatus(filterStatus === 'COMPLETED' ? '' : 'COMPLETED') },
        ]}
      />
      <Card className="filter-bar flex-row">
        <SearchBar value={searchTerm} onChange={setSearchTerm} entityLabel="dreams" />
        <FilterSelect
          label="Vision Area"
          value={filterVisionAreaId}
          onChange={setFilterVisionAreaId}
          options={optionsFromEntities(visionAreas, (area) => area.name)}
        />
        <FilterSelect
          label="Type"
          value={filterDreamType}
          onChange={setFilterDreamType}
          options={optionsFromLabels(dreamTypeLabels)}
        />
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
          // "Archived" can only ever match hidden rows, so the option appears
          // only while "Show archived" is on — otherwise it filters to nothing.
          options={optionsFromLabels(dreamStatusLabels).filter(
            (option) => crud.showArchived || option.value !== 'ARCHIVED',
          )}
        />
        <label className="checkbox-field">
          <Checkbox checked={filterOverdueOnly} onChange={(event) => setFilterOverdueOnly(event.target.checked)} />
          Overdue only
        </label>
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      <div className="view-toggle-row">
        <ViewToggle value={viewMode} onChange={setViewMode} label="Dream view" />
      </div>
      {!crud.loading && crud.items.length === 0 && !hasFilters ? (
        <EmptyState
          headline="No dreams yet"
          icon={Sparkles}
          action={
            visionAreas.length === 0 ? (
              <Button type="button" onClick={() => navigate('/vision-areas?create=area')}>Create a vision area first</Button>
            ) : (
              <Button type="button" onClick={() => setWizardOpen(true)}>Start your first dream</Button>
            )
          }
        >
          A dream is a meaningful outcome you want to reach — the guide asks a few questions to make it clear enough to plan.
        </EmptyState>
      ) : viewMode === 'list' ? (
        <Card>
          <CardContent>
          <DataTable
            storageKey="dreams"
            rows={filteredDreams}
            columns={columns}
            emptyMessage={hasFilters ? 'No dreams match these filters.' : 'No dreams yet.'}
            defaultSortKey="priority"
            defaultSortDirection="desc"
            pageResetKey={`${searchTerm}|${filterVisionAreaId}|${filterDreamType}|${filterPriority}|${filterStatus}|${filterOverdueOnly}`}
            rowClassName={(dream) => (dream.archived ? 'row-archived' : '')}
            selection={{
              selectedIds,
              onChange: setSelectedIds,
              rowLabel: (dream) => dream.title,
              actions: (
                <BulkArchiveAction
                  selectedIds={selectedIds}
                  entityLabel="dream(s)"
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
          items={filteredDreams}
          columns={Object.entries(dreamStatusLabels).map(([value, label]) => ({ value: value as DreamStatus, label }))}
          statusOf={(dream) => dream.status}
          entityLabel="dreams"
          onMove={(dream, nextStatus) => void handleMove(dream, nextStatus)}
          cardClassName={(dream) => (isOverdue(dream.targetDate, dream.status) ? 'list-card--overdue' : '')}
          renderCard={(dream) => (
            <>
              <strong>{dream.title}</strong>
              <p>{dream.code} · {goalCounts.get(dream.id) ?? 0} goal(s){dream.targetDate ? <> · Target <RelativeDate date={dream.targetDate} completed={dream.status === 'COMPLETED'} /></> : ''}</p>
              <div className="inline-meta">
                <PriorityBadge priority={dream.priority} />
                <span>{dreamTypeLabels[dream.dreamType]}</span>
              </div>
            </>
          )}
          cardActions={(dream) => renderDreamActions(dream)}
        />
      )}
    </PageSection>
  );
}
