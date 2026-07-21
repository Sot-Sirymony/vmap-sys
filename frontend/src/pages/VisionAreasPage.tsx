import { FormEvent, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { listDreams } from '../api/dreamApi';
import { archiveVisionArea, permanentlyDeleteVisionArea, createVisionArea, getVisionAreaArchiveImpact, listVisionAreas, restoreVisionArea, updateVisionArea } from '../api/visionAreaApi';
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
import { useAuth } from '../context/AuthContext';
import { visionAreaDotColor } from '../theme';
import { useCrudEntity } from '../hooks/useCrudEntity';
import { useStoredState } from '../hooks/useStoredState';
import type { LifecycleStatus, Priority, VisionArea, VisionAreaRequest } from '../types/vision';
import { lifecycleStatusLabels, priorityLabels } from '../utils/enumLabels';
import { matchesSearch } from '../utils/search';
import { priorityRank } from '../utils/sortRank';
import { PageSection } from './PageSection';

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

  // Arrived from the dashboard's getting-started checklist: open the wizard,
  // then strip the param so a refresh doesn't reopen it.
  useEffect(() => {
    if (searchParams.get('create') !== 'area') {
      return;
    }
    setWizardOpen(true);
    const next = new URLSearchParams(searchParams);
    next.delete('create');
    setSearchParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [visionStatement, setVisionStatement] = useState('');
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [status, setStatus] = useState<LifecycleStatus>('ACTIVE');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [searchTerm, setSearchTerm] = useState('');
  const [filterPriority, setFilterPriority] = useState('');
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
    const success = await crud.save({ name, description, visionStatement, priority, status });
    if (success) {
      setName('');
      setDescription('');
      setVisionStatement('');
    }
    return success;
  }

  function startEdit(area: VisionArea) {
    crud.startEdit(area.id);
    setName(area.name);
    setDescription(area.description ?? '');
    setVisionStatement(area.visionStatement ?? '');
    setPriority(area.priority);
    setStatus(area.status);
  }

  function cancelEdit() {
    crud.cancelEdit();
    setName('');
    setDescription('');
    setVisionStatement('');
    setPriority('HIGH');
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
        status: nextStatus,
      });
      await crud.reload();
    } catch (moveError) {
      crud.setError(moveError instanceof Error ? moveError.message : 'Unable to update vision area status.');
    }
  }

  async function archiveImpactMessage(area: VisionArea) {
    if (!token) {
      return 'Archive this vision area?';
    }
    const impact = await getVisionAreaArchiveImpact(token, area.id);
    return `Archiving "${area.name}" also archives ${impact.dreams} dream(s), ${impact.goals} goal(s), ${impact.steps} step(s), and ${impact.tasks} task(s). Everything can be restored later with "Show archived".`;
  }

  const filteredAreas = crud.items.filter((area) => {
    if (filterPriority && area.priority !== filterPriority) {
      return false;
    }
    if (filterStatus && area.status !== filterStatus) {
      return false;
    }
    return matchesSearch(searchTerm, area.code, area.name, area.description);
  });

  const hasFilters = Boolean(searchTerm || filterPriority || filterStatus);

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
          { label: 'View dreams', onClick: () => navigate(`/dreams?visionAreaId=${area.id}`) },
          { label: 'Add dream', onClick: () => navigate(`/dreams?create=dream&parent=${area.id}`) },
        ]}
        label="Vision area actions"
      />
    );
  }

  const columns: DataTableColumn<VisionArea>[] = [
    { key: 'code', label: 'Code', sortValue: (area) => area.code, render: (area) => area.code },
    {
      key: 'name',
      label: 'Name',
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
      label: 'Priority',
      sortValue: (area) => priorityRank(area.priority),
      render: (area) => <PriorityBadge priority={area.priority} />,
    },
    {
      key: 'status',
      label: 'Status',
      sortValue: (area) => area.status,
      render: (area) => <StatusBadge status={area.status} />,
    },
    {
      key: 'dreams',
      label: 'Dreams',
      sortValue: (area) => dreamCounts.get(area.id) ?? 0,
      render: (area) => dreamCounts.get(area.id) ?? 0,
    },
    {
      key: 'actions',
      label: 'Action',
      className: 'row-actions',
      render: (area) => renderAreaActions(area),
    },
  ];

  const formFields = (
    <>
      <label>
        Name
        <Input value={name} onChange={(event) => setName(event.target.value)} required autoFocus />
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
          <Select SelectDisplayProps={{ 'aria-label': "Status" }} value={status} onChange={(event) => setStatus(event.target.value as LifecycleStatus)}>
            <MenuItem value="ACTIVE">Active</MenuItem>
            <MenuItem value="PAUSED">Paused</MenuItem>
            <MenuItem value="COMPLETED">Completed</MenuItem>
            <MenuItem value="ARCHIVED">Archived</MenuItem>
          </Select>
        </FormControl>
      </label>
      <label className="field-full">
        Vision Statement
        <Textarea value={visionStatement} onChange={(event) => setVisionStatement(event.target.value)} />
        <span className="field-hint">
          What does this area look like when it's going well? Optional, but writing it down first makes the dreams
          underneath it easier to judge.
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
      title="Vision Areas"
      subtitle="Organize the major areas of life or work."
      actions={<Button type="button" onClick={() => setWizardOpen(true)}>Create vision area</Button>}
    >
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Create vision area"
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
      <Card className="filter-bar flex-row">
        <SearchBar value={searchTerm} onChange={setSearchTerm} entityLabel="vision areas" />
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
          options={optionsFromLabels(lifecycleStatusLabels).filter(
            (option) => crud.showArchived || option.value !== 'ARCHIVED',
          )}
        />
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      <div className="view-toggle-row">
        <ViewToggle value={viewMode} onChange={setViewMode} label="Vision area view" />
      </div>
      {!crud.loading && crud.items.length === 0 && !searchTerm && !filterPriority && !filterStatus ? (
        <EmptyState
          headline="No vision areas yet"
          icon={Compass}
          action={<Button type="button" onClick={() => setWizardOpen(true)}>Create your first vision area</Button>}
        >
          A vision area is a major part of your life or work — like Career, Health, or Family. Everything else builds under it.
        </EmptyState>
      ) : viewMode === 'list' ? (
        <Card>
          <CardContent>
            <DataTable
              storageKey="vision-areas"
              rows={filteredAreas}
              columns={columns}
              emptyMessage={hasFilters ? 'No vision areas match these filters.' : 'No vision areas yet.'}
              defaultSortKey="priority"
              defaultSortDirection="desc"
              pageResetKey={`${searchTerm}|${filterPriority}|${filterStatus}`}
              rowClassName={(area) => (area.archived ? 'row-archived' : '')}
              selection={{
                selectedIds,
                onChange: setSelectedIds,
                rowLabel: (area) => area.name,
                actions: (
                  <BulkArchiveAction
                    selectedIds={selectedIds}
                    entityLabel="vision area(s)"
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
          items={filteredAreas}
          columns={Object.entries(lifecycleStatusLabels).map(([value, label]) => ({ value: value as LifecycleStatus, label }))}
          statusOf={(area) => area.status}
          entityLabel="vision areas"
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
