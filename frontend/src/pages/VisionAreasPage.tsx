import { FormEvent, useEffect, useState } from 'react';
import { archiveVisionArea, permanentlyDeleteVisionArea, createVisionArea, getVisionAreaArchiveImpact, listVisionAreas, restoreVisionArea, updateVisionArea } from '../api/visionAreaApi';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import { BulkArchiveAction } from '../components/common/BulkArchiveAction';
import { CrudModalForm } from '../components/common/CrudModalForm';
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
import { Textarea } from '../components/common/Textarea';
import { useAuth } from '../context/AuthContext';
import { useCrudEntity } from '../hooks/useCrudEntity';
import type { LifecycleStatus, Priority, VisionArea, VisionAreaRequest } from '../types/vision';
import { lifecycleStatusLabels, priorityLabels } from '../utils/enumLabels';
import { matchesSearch } from '../utils/search';
import { priorityRank } from '../utils/sortRank';
import { PageSection } from './PageSection';

export function VisionAreasPage() {
  const { token } = useAuth();
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
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [status, setStatus] = useState<LifecycleStatus>('ACTIVE');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [searchTerm, setSearchTerm] = useState('');
  const [filterPriority, setFilterPriority] = useState('');
  const [filterStatus, setFilterStatus] = useState('');

  useEffect(() => {
    void crud.reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const success = await crud.save({ name, description, priority, status });
    if (success) {
      setName('');
      setDescription('');
    }
    return success;
  }

  function startEdit(area: VisionArea) {
    crud.startEdit(area.id);
    setName(area.name);
    setDescription(area.description ?? '');
    setPriority(area.priority);
    setStatus(area.status);
  }

  function cancelEdit() {
    crud.cancelEdit();
    setName('');
    setDescription('');
    setPriority('HIGH');
    setStatus('ACTIVE');
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

  const columns: DataTableColumn<VisionArea>[] = [
    { key: 'code', label: 'Code', sortValue: (area) => area.code, render: (area) => area.code },
    { key: 'name', label: 'Name', sortValue: (area) => area.name, sx: { fontWeight: 500 }, render: (area) => area.name },
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
      key: 'actions',
      label: 'Action',
      className: 'row-actions',
      render: (area) => (
        <RowActionsMenu
          onEdit={() => startEdit(area)}
          onArchive={() => void crud.archive(area.id)}
          onRestore={() => void crud.restore(area.id)}
          onDeletePermanently={() => void crud.permanentlyDelete(area.id)}
          archived={area.archived}
          confirmArchive={() => archiveImpactMessage(area)}
          label="Vision area actions"
        />
      ),
    },
  ];

  const formFields = (
    <>
      <label>
        Name
        <Input value={name} onChange={(event) => setName(event.target.value)} required />
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
          <Select value={status} onChange={(event) => setStatus(event.target.value as LifecycleStatus)}>
            <MenuItem value="ACTIVE">Active</MenuItem>
            <MenuItem value="PAUSED">Paused</MenuItem>
            <MenuItem value="COMPLETED">Completed</MenuItem>
            <MenuItem value="ARCHIVED">Archived</MenuItem>
          </Select>
        </FormControl>
      </label>
      <label className="field-full">
        Description
        <Textarea value={description} onChange={(event) => setDescription(event.target.value)} />
      </label>
    </>
  );

  return (
    <PageSection title="Vision Areas" subtitle="Organize the major areas of life or work.">
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Create vision area"
        editTitle="Edit Vision Area"
        saving={crud.saving}
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {crud.loading && <Loading />}
      {crud.error && <ErrorMessage message={crud.error} />}
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
          options={optionsFromLabels(lifecycleStatusLabels)}
        />
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      <Card>
        <CardContent>
          <DataTable
            rows={filteredAreas}
            columns={columns}
            emptyMessage={hasFilters ? 'No vision areas match these filters.' : 'No vision areas yet.'}
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
    </PageSection>
  );
}
