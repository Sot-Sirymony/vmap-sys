import { FormEvent, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { archiveDream, permanentlyDeleteDream, createDream, getDreamArchiveImpact, listDreams, restoreDream, updateDream } from '../api/dreamApi';
import { listVisionAreas } from '../api/visionAreaApi';
import MuiButton from '@mui/material/Button';
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
import { FilterSelect, optionsFromEntities, optionsFromLabels } from '../components/common/FilterSelect';
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
import { useUrlFilter, useUrlFlag } from '../hooks/useUrlFilter';
import type { Dream, DreamRequest, DreamStatus, DreamType, Priority, VisionArea } from '../types/vision';
import { dreamStatusLabels, dreamTypeLabels, priorityLabels } from '../utils/enumLabels';
import { isOverdue } from '../utils/overdue';
import { matchesSearch } from '../utils/search';
import { priorityRank } from '../utils/sortRank';
import { PageSection } from './PageSection';

export function DreamsPage() {
  const { token } = useAuth();
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

  useEffect(() => {
    if (!token) {
      return;
    }
    void crud.reload();
    void listVisionAreas(token).then((areaData) => {
      setVisionAreas(areaData.filter((area) => area.status !== 'ARCHIVED'));
      setVisionAreaId((current) => current || String(areaData[0]?.id ?? ''));
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

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

  const columns: DataTableColumn<Dream>[] = [
    { key: 'code', label: 'Code', sortValue: (dream) => dream.code, render: (dream) => dream.code },
    { key: 'title', label: 'Dream', sortValue: (dream) => dream.title, sx: { fontWeight: 500 }, render: (dream) => dream.title },
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
      key: 'targetDate',
      label: 'Target',
      sortValue: (dream) => dream.targetDate,
      render: (dream) => dream.targetDate ?? '-',
    },
    {
      key: 'actions',
      label: 'Action',
      className: 'row-actions',
      render: (dream) => (
        <>
          <MuiButton component={Link} to={`/dreams/${dream.id}`} variant="contained" color="secondary" size="small" disableElevation>View Map</MuiButton>
          <RowActionsMenu
            onEdit={() => startEdit(dream)}
            onArchive={() => void crud.archive(dream.id)}
            onRestore={() => void crud.restore(dream.id)}
            onDeletePermanently={() => void crud.permanentlyDelete(dream.id)}
            archived={dream.archived}
            confirmArchive={() => archiveImpactMessage(dream)}
            label="Dream actions"
          />
        </>
      ),
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
          <Select displayEmpty value={visionAreaId} onChange={(event) => setVisionAreaId(event.target.value)}>
            <MenuItem value="" disabled><em>Select a vision area</em></MenuItem>
            {visionAreas.map((area) => <MenuItem value={String(area.id)} key={area.id}>{area.name}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Title
        <Input value={title} onChange={(event) => setTitle(event.target.value)} required />
      </label>
      <label>
        Type
        <FormControl fullWidth size="small">
          <Select value={dreamType} onChange={(event) => setDreamType(event.target.value as DreamType)}>
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
          <Select value={status} onChange={(event) => setStatus(event.target.value as DreamStatus)}>
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
    <PageSection title="Dreams" subtitle="Capture meaningful outcomes and prepare them for goals.">
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Create dream"
        editTitle="Edit Dream"
        saving={crud.saving}
        disabled={visionAreas.length === 0}
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {crud.loading && <Loading />}
      {crud.error && <ErrorMessage message={crud.error} />}
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
          options={optionsFromLabels(dreamStatusLabels)}
        />
        <label className="checkbox-field">
          <Checkbox checked={filterOverdueOnly} onChange={(event) => setFilterOverdueOnly(event.target.checked)} />
          Overdue only
        </label>
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      <Card>
        <CardContent>
        <DataTable
          rows={filteredDreams}
          columns={columns}
          emptyMessage={hasFilters ? 'No dreams match these filters.' : 'No dreams yet.'}
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
    </PageSection>
  );
}
