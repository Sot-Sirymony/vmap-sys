import { FormEvent, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { listDreams } from '../api/dreamApi';
import { archiveVisionArea, permanentlyDeleteVisionArea, createVisionArea, getVisionAreaArchiveImpact, listVisionAreas, reorderVisionAreas, restoreVisionArea, updateVisionArea } from '../api/visionAreaApi';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import { BulkArchiveAction } from '../components/common/BulkArchiveAction';
import { Button } from '../components/common/Button';
import { Compass } from 'lucide-react';
import { CrudModalForm } from '../components/common/CrudModalForm';
import { EmptyState } from '../components/common/EmptyState';
import { VisionAreaWizard } from '../components/forms/VisionAreaWizard';
import { DataTable, type DataTableColumn } from '../components/common/DataTable';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { FilterPanel } from '../components/common/FilterPanel';
import { FilterSelect, optionsFromLabels } from '../components/common/FilterSelect';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { RowActionsMenu } from '../components/common/RowActionsMenu';
import { SearchBar } from '../components/common/SearchBar';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { StatusBadge } from '../components/common/StatusBadge';
import { StatusBoard } from '../components/common/StatusBoard';
import { Textarea } from '../components/common/Textarea';
import { ViewToggle, type ViewMode } from '../components/common/ViewToggle';
import { AiVisionAreaAssistant } from '../components/ai/AiStartAssistant';
import { useAuth } from '../context/AuthContext';
import { visionAreaDotColor } from '../theme';
import { useCrudEntity } from '../hooks/useCrudEntity';
import { useStoredState } from '../hooks/useStoredState';
import type { LifecycleStatus, Priority, VisionArea, VisionAreaRequest } from '../types/vision';
import { lifecycleStatusLabels, priorityLabels } from '../utils/enumLabels';
import { matchesSearch } from '../utils/search';
import { priorityRank, letterRankFilterOptions } from '../utils/sortRank';
import { PageSection } from './PageSection';
import { tr } from '../i18n/translate';

export function VisionAreasPage() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const crud = useCrudEntity<VisionArea, VisionAreaRequest>({
    token,
    entityLabel: 'vision areas',
    list: listVisionAreas,
    create: createVisionArea,
    update: updateVisionArea,
    archive: archiveVisionArea,
    permanentlyDelete: permanentlyDeleteVisionArea,
    restore: restoreVisionArea,
  });
  // FR-33.2: creation goes through the setup wizard by default; the flat
  // form remains for edits and for "skip the guide".
  const [wizardOpen, setWizardOpen] = useState(false);
  const [flatCreateOpen, setFlatCreateOpen] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();

  // The step-by-step guide is for first-timers only — once someone already
  // has a vision area, they know the drill and the flat form is faster.
  function openCreateFlow() {
    if (crud.items.length > 0) {
      setFlatCreateOpen(true);
    } else {
      setWizardOpen(true);
    }
  }

  // Arrived from the dashboard's getting-started checklist: open the guide
  // (new users) or the flat form (returning users), then strip the param so
  // a refresh doesn't reopen it. Waits for the list to load first so an
  // existing user's areas aren't still empty when the decision is made.
  useEffect(() => {
    if (searchParams.get('create') !== 'area' || crud.loading) {
      return;
    }
    openCreateFlow();
    const next = new URLSearchParams(searchParams);
    next.delete('create');
    setSearchParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams, crud.loading]);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [visionStatement, setVisionStatement] = useState('');
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [letterRank, setLetterRank] = useState('');
  const [status, setStatus] = useState<LifecycleStatus>('ACTIVE');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [searchTerm, setSearchTerm] = useState('');
  const [filterPriority, setFilterPriority] = useState('');
  const [filterLetterRank, setFilterLetterRank] = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [viewMode, setViewMode] = useStoredState<ViewMode>('vms-view-vision-areas', 'list');
  // Count of live dreams per area, for the Dreams column. Loaded here rather than
  // added to the vision-area response, which the shared mapper builds in several
  // places that have no count to hand.
  const [dreamCounts, setDreamCounts] = useState<Map<number, number>>(new Map());

  useEffect(() => {
    void crud.reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  async function reloadDreamCounts(activeToken: string) {
    const dreams = await listDreams(activeToken);
    const counts = new Map<number, number>();
    for (const dream of dreams) {
      counts.set(dream.visionAreaId, (counts.get(dream.visionAreaId) ?? 0) + 1);
    }
    setDreamCounts(counts);
  }

  useEffect(() => {
    if (!token) {
      return;
    }
    void reloadDreamCounts(token);
  }, [token]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const success = await crud.save({ name, description, visionStatement, priority, letterRank: letterRank || undefined, status });
    if (success) {
      setName('');
      setDescription('');
      setVisionStatement('');
      setLetterRank('');
    }
    return success;
  }

  function startEdit(area: VisionArea) {
    crud.startEdit(area.id);
    setName(area.name);
    setDescription(area.description ?? '');
    setVisionStatement(area.visionStatement ?? '');
    setPriority(area.priority);
    setLetterRank(area.letterRank ?? '');
    setStatus(area.status);
  }

  function cancelEdit() {
    crud.cancelEdit();
    setName('');
    setDescription('');
    setVisionStatement('');
    setPriority('HIGH');
    setLetterRank('');
    setStatus('ACTIVE');
  }

  // Board drag/dropdown move. There is no status PATCH endpoint for vision
  // areas, so the move sends a full update built from the loaded entity.
  async function handleMove(area: VisionArea, nextStatus: LifecycleStatus) {
    if (!token || area.status === nextStatus) {
      return;
    }
    try {
      await updateVisionArea(token, area.id, {
        name: area.name,
        description: area.description,
        visionStatement: area.visionStatement,
        priority: area.priority,
        letterRank: area.letterRank ?? undefined,
        status: nextStatus,
      });
      await crud.reload();
    } catch (moveError) {
      crud.setError(moveError instanceof Error ? moveError.message : tr('Unable to update vision area status.'));
    }
  }

  // Drag-and-drop reorder (List view only, see DataTableReorder): siblings
  // are every non-archived area, ordered by its current position.
  async function handleReorderAreas(draggedId: number, targetId: number) {
    if (!token) {
      return;
    }
    const orderedIds = crud.items
      .filter((area) => !area.archived)
      .sort((a, b) => (a.sortOrder ?? Number.MAX_SAFE_INTEGER) - (b.sortOrder ?? Number.MAX_SAFE_INTEGER) || a.id - b.id)
      .map((area) => area.id);
    const fromIndex = orderedIds.indexOf(draggedId);
    const toIndex = orderedIds.indexOf(targetId);
    if (fromIndex === -1 || toIndex === -1) {
      return;
    }
    const nextOrder = [...orderedIds];
    nextOrder.splice(fromIndex, 1);
    nextOrder.splice(toIndex, 0, draggedId);
    try {
      await reorderVisionAreas(token, nextOrder);
      await crud.reload();
    } catch (reorderError) {
      crud.setError(reorderError instanceof Error ? reorderError.message : tr('Unable to reorder vision areas.'));
    }
  }

  async function archiveImpactMessage(area: VisionArea) {
    if (!token) {
      return 'Archive this vision area?';
    }
    const impact = await getVisionAreaArchiveImpact(token, area.id);
    return tr('Archiving "{title}" also archives {dreams} dream(s), {goals} goal(s), {steps} step(s), and {tasks} task(s). Everything can be restored later with "Show archived".', { title: area.name, dreams: impact.dreams, goals: impact.goals, steps: impact.steps, tasks: impact.tasks });
  }

  const filteredAreas = crud.items.filter((area) => {
    if (filterPriority && area.priority !== filterPriority) {
      return false;
    }
    if (filterLetterRank && area.letterRank !== filterLetterRank) {
      return false;
    }
    if (filterStatus && area.status !== filterStatus) {
      return false;
    }
    return matchesSearch(searchTerm, area.code, area.name, area.description);
  });

  const hasFilters = Boolean(searchTerm || filterPriority || filterLetterRank || filterStatus);

  // FR-57.4-style: only letters actually in use are offered, so the dropdown
  // doesn't show 26 mostly-empty options.
  const letterRankOptions = letterRankFilterOptions(crud.items);

  // Shared by the table's action column and the list view's cards, so both
  // offer the same row actions.
  function renderAreaActions(area: VisionArea) {
    return (
      <RowActionsMenu
        onEdit={() => startEdit(area)}
        onArchive={() => void crud.archive(area.id)}
        onRestore={() => void crud.restore(area.id)}
        onDeletePermanently={() => void crud.permanentlyDelete(area.id)}
        archived={area.archived}
        confirmArchive={() => archiveImpactMessage(area)}
        extraActions={[
          { label: tr('View dreams'), onClick: () => navigate(`/dreams?visionAreaId=${area.id}`) },
          { label: tr('Add dream'), onClick: () => navigate(`/dreams?create=dream&parent=${area.id}`) },
        ]}
        label={tr('Vision area actions')}
      />
    );
  }

  const columns: DataTableColumn<VisionArea>[] = [
    {
      key: 'position',
      label: tr('Order'),
      sortValue: (area) => area.sortOrder ?? Number.MAX_SAFE_INTEGER,
      render: (area) => (area.sortOrder != null ? area.sortOrder + 1 : '—'),
    },
    { key: 'code', label: tr('Code'), sortValue: (area) => area.code, render: (area) => area.code },
    {
      key: 'name',
      label: tr('Name'),
      sortValue: (area) => area.name,
      sx: { fontWeight: 500 },
      render: (area) => (
        <span className="area-dot-label">
          <span className="area-dot" style={{ backgroundColor: visionAreaDotColor(area.id) }} aria-hidden="true" />
          {area.name}
        </span>
      ),
    },
    {
      key: 'priority',
      label: tr('Priority'),
      sortValue: (area) => priorityRank(area.priority),
      render: (area) => <PriorityBadge priority={area.priority} />,
    },
    {
      key: 'letterRank',
      label: tr('Rank'),
      sortValue: (area) => area.letterRank ?? '',
      render: (area) => area.letterRank ?? '—',
    },
    {
      key: 'status',
      label: tr('Status'),
      sortValue: (area) => area.status,
      render: (area) => <StatusBadge status={area.status} />,
    },
    {
      key: 'dreams',
      label: tr('Dreams'),
      sortValue: (area) => dreamCounts.get(area.id) ?? 0,
      render: (area) => dreamCounts.get(area.id) ?? 0,
    },
    {
      key: 'actions',
      label: tr('Action'),
      className: 'row-actions',
      render: (area) => renderAreaActions(area),
    },
  ];

  const formFields = (
    <>
      <label>
        {tr('Name')}
        <Input value={name} onChange={(event) => setName(event.target.value)} required autoFocus />
      </label>
      <label>
        {tr('Priority')}
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Priority" }} value={priority} onChange={(event) => setPriority(event.target.value as Priority)}>
            <MenuItem value="LOW">{tr('Low')}</MenuItem>
            <MenuItem value="MEDIUM">{tr('Medium')}</MenuItem>
            <MenuItem value="HIGH">{tr('High')}</MenuItem>
            <MenuItem value="CRITICAL">{tr('Critical')}</MenuItem>
          </Select>
        </FormControl>
      </label>
      <label>
        {tr('Rank')}
        <Input
          value={letterRank}
          onChange={(event) => setLetterRank(event.target.value.slice(-1).toUpperCase().replace(/[^A-Z]/, ''))}
          placeholder={tr('e.g. A')}
          maxLength={1}
        />
        <span className="field-hint">
          {tr('Optional. A, B, C… — assigned after listing everything, to mark which areas come first. Ties are fine; nothing enforces a unique order.')}
        </span>
      </label>
      <label>
        {tr('Status')}
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Status" }} value={status} onChange={(event) => setStatus(event.target.value as LifecycleStatus)}>
            <MenuItem value="ACTIVE">{tr('Active')}</MenuItem>
            <MenuItem value="PAUSED">{tr('Paused')}</MenuItem>
            <MenuItem value="COMPLETED">{tr('Completed')}</MenuItem>
            <MenuItem value="ARCHIVED">{tr('Archived')}</MenuItem>
          </Select>
        </FormControl>
      </label>
      <label className="field-full">
        {tr('Vision Statement')}
        <Textarea value={visionStatement} onChange={(event) => setVisionStatement(event.target.value)} />
        <span className="field-hint">
          {tr('What does this area look like when it\'s going well? Optional, but writing it down first makes the dreams underneath it easier to judge.')}
        </span>
      </label>
      <label className="field-full">
        Description
        <Textarea value={description} onChange={(event) => setDescription(event.target.value)} />
      </label>
    </>
  );

  return (
    <PageSection
      title={tr('Vision Areas')}
      subtitle={tr('Organize the major areas of life or work.')}
      actions={<Button type="button" onClick={openCreateFlow}>{tr('Create vision area')}</Button>}
    >
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel={tr('Create vision area')}
        editTitle="Edit Vision Area"
        saving={crud.saving}
        creating={flatCreateOpen}
        onCreatingChange={setFlatCreateOpen}
        hideTrigger
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {wizardOpen && (
        <VisionAreaWizard
          token={token ?? ''}
          existingNames={crud.items.filter((area) => !area.archived).map((area) => area.name)}
          onClose={() => setWizardOpen(false)}
          onSkip={() => {
            setWizardOpen(false);
            setFlatCreateOpen(true);
          }}
          onCreated={() => {
            void crud.reload();
            if (token) {
              void reloadDreamCounts(token);
            }
          }}
        />
      )}
      {crud.loading && <Loading variant="table" />}
      {crud.error && <ErrorMessage message={crud.error} onRetry={() => void crud.reload()} />}
      {token && <AiVisionAreaAssistant token={token} onDataChange={() => crud.reload()} />}
      <FilterPanel activeCount={[searchTerm, filterPriority, filterLetterRank, filterStatus].filter(Boolean).length}>
        <SearchBar value={searchTerm} onChange={setSearchTerm} entityLabel={tr('vision areas')} />
        <FilterSelect
          label={tr('Priority')}
          value={filterPriority}
          onChange={setFilterPriority}
          options={optionsFromLabels(priorityLabels)}
        />
        {letterRankOptions.length > 0 && (
          <FilterSelect
            label={tr('Rank')}
            value={filterLetterRank}
            onChange={setFilterLetterRank}
            options={letterRankOptions}
          />
        )}
        <FilterSelect
          label={tr('Status')}
          value={filterStatus}
          onChange={setFilterStatus}
          // "Archived" can only ever match hidden rows, so the option appears
          // only while "Show archived" is on — otherwise it filters to nothing.
          options={optionsFromLabels(lifecycleStatusLabels).filter(
            (option) => crud.showArchived || option.value !== 'ARCHIVED',
          )}
        />
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </FilterPanel>
      <div className="view-toggle-row">
        <ViewToggle value={viewMode} onChange={setViewMode} label={tr('Vision area view')} />
      </div>
      {!crud.loading && crud.items.length === 0 && !searchTerm && !filterPriority && !filterLetterRank && !filterStatus ? (
        <EmptyState
          headline={tr('No vision areas yet')}
          icon={Compass}
          action={<Button type="button" onClick={() => setWizardOpen(true)}>{tr('Create your first vision area')}</Button>}
        >
          {tr('A vision area is a major part of your life or work — like Career, Health, or Family. Everything else builds under it.')}
        </EmptyState>
      ) : viewMode === 'list' ? (
        <Card>
          <CardContent>
            <DataTable
              storageKey="vision-areas"
              rows={filteredAreas}
              columns={columns}
              emptyMessage={hasFilters ? tr('No vision areas match these filters.') : tr('No vision areas yet.')}
              defaultSortKey="position"
              defaultSortDirection="asc"
              reorder={{ columnKey: 'position', onReorder: (draggedId, targetId) => void handleReorderAreas(draggedId, targetId) }}
              pageResetKey={`${searchTerm}|${filterPriority}|${filterLetterRank}|${filterStatus}`}
              rowClassName={(area) => (area.archived ? 'row-archived' : '')}
              selection={{
                selectedIds,
                onChange: setSelectedIds,
                rowLabel: (area) => area.name,
                actions: (
                  <BulkArchiveAction
                    selectedIds={selectedIds}
                    items={crud.items}
                    entityLabel={tr('vision area(s)')}
                    onArchive={async (ids) => {
                      await crud.archiveMany(ids);
                      setSelectedIds(new Set());
                    }}
                    onRestore={async (ids) => {
                      await crud.restoreMany(ids);
                      setSelectedIds(new Set());
                    }}
                    onDeletePermanently={async (ids) => {
                      await crud.permanentlyDeleteMany(ids);
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
          items={filteredAreas}
          columns={Object.entries(lifecycleStatusLabels).map(([value, label]) => ({ value: value as LifecycleStatus, label }))}
          statusOf={(area) => area.status}
          entityLabel={tr('vision areas')}
          onMove={(area, nextStatus) => void handleMove(area, nextStatus)}
          renderCard={(area) => (
            <>
              <strong>{area.name}</strong>
              <p>{area.code} · {dreamCounts.get(area.id) ?? 0} dream(s)</p>
              <div className="inline-meta">
                <PriorityBadge priority={area.priority} />
              </div>
            </>
          )}
          cardActions={(area) => renderAreaActions(area)}
        />
      )}
    </PageSection>
  );
}
