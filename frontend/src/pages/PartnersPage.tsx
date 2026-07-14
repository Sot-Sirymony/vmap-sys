import { FormEvent, useEffect, useState } from 'react';
import { listDreams } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import { archivePartner, permanentlyDeletePartner, createPartner, listPartners, restorePartner, updatePartner } from '../api/partnerApi';
import { listSteps } from '../api/stepApi';
import { listTasks } from '../api/taskApi';
import { listVisionAreas } from '../api/visionAreaApi';
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
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import type { Dream, Goal, Partner, PartnerRequest, PartnerStatus, PartnerSupportType, TaskItem, VisionArea, VisionStep } from '../types/vision';
import { partnerStatusLabels, partnerSupportTypeLabels } from '../utils/enumLabels';
import { PageSection } from './PageSection';

export function PartnersPage() {
  const { token } = useAuth();
  // Partners are paged and sorted by the server, so the table only renders what
  // the current request returned.
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [sort, setSort] = useState('id,desc');
  const [totalRows, setTotalRows] = useState(0);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  // Search runs on the server too, so it spans every page, not just the loaded one.
  const [searchTerm, setSearchTerm] = useState('');
  const debouncedSearch = useDebouncedValue(searchTerm);
  const crud = useCrudEntity<Partner, PartnerRequest>({
    token,
    entityLabel: 'partners',
    list: async (currentToken, includeArchived) => {
      const result = await listPartners(currentToken, page, rowsPerPage, includeArchived, sort, debouncedSearch);
      setTotalRows(result.totalElements);
      return result.content;
    },
    create: createPartner,
    update: updatePartner,
    archive: archivePartner,
    permanentlyDelete: permanentlyDeletePartner,
    restore: restorePartner,
  });
  const [visionAreas, setVisionAreas] = useState<VisionArea[]>([]);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [steps, setSteps] = useState<VisionStep[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [name, setName] = useState('');
  const [role, setRole] = useState('');
  const [organization, setOrganization] = useState('');
  const [email, setEmail] = useState('');
  const [strength, setStrength] = useState('');
  const [supportType, setSupportType] = useState<PartnerSupportType>('MENTOR');
  const [status, setStatus] = useState<PartnerStatus>('TO_CONTACT');
  const [relatedVisionAreaId, setRelatedVisionAreaId] = useState('');
  const [relatedDreamId, setRelatedDreamId] = useState('');
  const [relatedGoalId, setRelatedGoalId] = useState('');
  const [relatedStepId, setRelatedStepId] = useState('');
  const [relatedTaskId, setRelatedTaskId] = useState('');
  const [notes, setNotes] = useState('');

  useEffect(() => {
    if (!token) {
      return;
    }
    void crud.reload();
    void Promise.all([listVisionAreas(token), listDreams(token), listGoals(token), listSteps(token), listTasks(token)]).then(
      ([areaData, dreamData, goalData, stepData, taskData]) => {
        setVisionAreas(areaData);
        setDreams(dreamData);
        setGoals(goalData);
        setSteps(stepData);
        setTasks(taskData);
      },
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, page, rowsPerPage, sort, debouncedSearch]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const success = await crud.save({
      name,
      role,
      organization,
      email: email || undefined,
      strength,
      supportType,
      status,
      relatedVisionAreaId: optionalNumber(relatedVisionAreaId),
      relatedDreamId: optionalNumber(relatedDreamId),
      relatedGoalId: optionalNumber(relatedGoalId),
      relatedStepId: optionalNumber(relatedStepId),
      relatedTaskId: optionalNumber(relatedTaskId),
      notes,
    });
    if (success) {
      setName('');
      setRole('');
      setOrganization('');
      setEmail('');
      setStrength('');
      setNotes('');
    }
    return success;
  }

  function startEdit(partner: Partner) {
    crud.startEdit(partner.id);
    setName(partner.name);
    setRole(partner.role ?? '');
    setOrganization(partner.organization ?? '');
    setEmail(partner.email ?? '');
    setStrength(partner.strength ?? '');
    setSupportType(partner.supportType);
    setStatus(partner.status);
    setRelatedVisionAreaId(partner.relatedVisionAreaId ? String(partner.relatedVisionAreaId) : '');
    setRelatedDreamId(partner.relatedDreamId ? String(partner.relatedDreamId) : '');
    setRelatedGoalId(partner.relatedGoalId ? String(partner.relatedGoalId) : '');
    setRelatedStepId(partner.relatedStepId ? String(partner.relatedStepId) : '');
    setRelatedTaskId(partner.relatedTaskId ? String(partner.relatedTaskId) : '');
    setNotes(partner.notes ?? '');
  }

  function cancelEdit() {
    crud.cancelEdit();
    setName('');
    setRole('');
    setOrganization('');
    setEmail('');
    setStrength('');
    setSupportType('MENTOR');
    setStatus('TO_CONTACT');
    setRelatedVisionAreaId('');
    setRelatedDreamId('');
    setRelatedGoalId('');
    setRelatedStepId('');
    setRelatedTaskId('');
    setNotes('');
  }

  // Column keys double as the server's sort fields, so they match Partner's JPA property names.
  const columns: DataTableColumn<Partner>[] = [
    { key: 'code', label: 'Code', sortValue: (partner) => partner.code, render: (partner) => partner.code },
    { key: 'name', label: 'Name', sortValue: (partner) => partner.name, sx: { fontWeight: 500 }, render: (partner) => partner.name },
    {
      key: 'supportType',
      label: 'Support',
      sortValue: (partner) => partner.supportType,
      render: (partner) => partnerSupportTypeLabels[partner.supportType],
    },
    {
      key: 'status',
      label: 'Status',
      sortValue: (partner) => partner.status,
      render: (partner) => <StatusBadge status={partner.status} />,
    },
    {
      key: 'actions',
      label: 'Action',
      className: 'row-actions',
      render: (partner) => (
        <RowActionsMenu
          onEdit={() => startEdit(partner)}
          onArchive={() => void crud.archive(partner.id)}
          onRestore={() => void crud.restore(partner.id)}
          onDeletePermanently={() => void crud.permanentlyDelete(partner.id)}
          archived={partner.archived}
          label="Partner actions"
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
        Role
        <Input value={role} onChange={(event) => setRole(event.target.value)} />
      </label>
      <label>
        Organization
        <Input value={organization} onChange={(event) => setOrganization(event.target.value)} />
      </label>
      <label>
        Email
        <Input type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
      </label>
      <label>
        Support Type
        <FormControl fullWidth size="small">
          <Select value={supportType} onChange={(event) => setSupportType(event.target.value as PartnerSupportType)}>
            {(['MENTOR', 'EXPERT', 'ADVISOR', 'COLLEAGUE', 'FINANCIAL', 'TECHNICAL', 'EMOTIONAL', 'OTHER'] as const).map((value) => (
              <MenuItem value={value} key={value}>{partnerSupportTypeLabels[value]}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </label>
      <label>
        Status
        <FormControl fullWidth size="small">
          <Select value={status} onChange={(event) => setStatus(event.target.value as PartnerStatus)}>
            {(['TO_CONTACT', 'CONTACTED', 'ACTIVE', 'WAITING', 'DECLINED', 'COMPLETED'] as const).map((value) => (
              <MenuItem value={value} key={value}>{partnerStatusLabels[value]}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </label>
      <label>
        Vision Area
        <FormControl fullWidth size="small">
          <Select displayEmpty value={relatedVisionAreaId} onChange={(event) => setRelatedVisionAreaId(event.target.value)}>
            <MenuItem value="">None</MenuItem>
            {visionAreas.map((area) => <MenuItem value={String(area.id)} key={area.id}>{area.name}</MenuItem>)}
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
        Strength
        <Input value={strength} onChange={(event) => setStrength(event.target.value)} />
      </label>
      <label className="field-full">
        Notes
        <Textarea value={notes} onChange={(event) => setNotes(event.target.value)} />
      </label>
    </>
  );

  return (
    <PageSection title="Partners" subtitle="Track mentors, experts, advisors, and resources.">
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Create partner"
        editTitle="Edit Partner"
        saving={crud.saving}
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {crud.loading && <Loading />}
      {crud.error && <ErrorMessage message={crud.error} />}
      <Card className="filter-bar flex-row">
        <SearchBar
          value={searchTerm}
          onChange={(value) => {
            setSearchTerm(value);
            setPage(0);
          }}
          entityLabel="partners"
        />
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      <Card>
        <CardContent>
        <DataTable
          rows={crud.items}
          columns={columns}
          emptyMessage={searchTerm ? 'No partners match this search.' : 'No partners yet.'}
          rowClassName={(partner) => (partner.archived ? 'row-archived' : '')}
          serverPaging={{
            page,
            rowsPerPage,
            totalRows,
            onPageChange: setPage,
            onRowsPerPageChange: (nextRowsPerPage) => {
              setRowsPerPage(nextRowsPerPage);
              setPage(0);
            },
            onSortChange: (key, direction) => {
              setSort(`${key},${direction}`);
              setPage(0);
            },
          }}
          selection={{
            selectedIds,
            onChange: setSelectedIds,
            rowLabel: (partner) => partner.name,
            actions: (
              <BulkArchiveAction
                selectedIds={selectedIds}
                entityLabel="partner(s)"
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
