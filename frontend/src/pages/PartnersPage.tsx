import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { listDreams } from '../api/dreamApi';
import { listIdealPartnerProfiles } from '../api/idealPartnerProfileApi';
import { listGoals } from '../api/goalApi';
import { archivePartner, permanentlyDeletePartner, createPartner, listPartners, restorePartner, updatePartner } from '../api/partnerApi';
import { listSteps } from '../api/stepApi';
import { listTasks } from '../api/taskApi';
import { listVisionAreas } from '../api/visionAreaApi';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
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
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { useUrlFilter } from '../hooks/useUrlFilter';
import type { Dream, Goal, IdealPartnerProfile, OfferType, Partner, PartnerRequest, PartnerStatus, PartnerSupportType, TaskItem, VisionArea, VisionStep } from '../types/vision';
import { offerTypeLabels, partnerStatusLabels, partnerSupportTypeLabels } from '../utils/enumLabels';
import { PageSection } from './PageSection';

export function PartnersPage() {
  const { token } = useAuth();
  const navigate = useNavigate();
  // Partners are paged and sorted by the server, so the table only renders what
  // the current request returned.
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [sort, setSort] = useState('id,desc');
  const [totalRows, setTotalRows] = useState(0);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  // Search and the filters both run on the server, so they span every page, not
  // just the loaded one.
  const [searchTerm, setSearchTerm] = useState('');
  const debouncedSearch = useDebouncedValue(searchTerm);
  // In the URL, not component state: the dashboard's partner pipeline links
  // straight into a status-filtered view, and a filtered list stays shareable
  // and bookmarkable.
  const [filterSupportType, setFilterSupportType] = useUrlFilter('supportType');
  const [filterStatus, setFilterStatus] = useUrlFilter('status');
  const [filterDreamId, setFilterDreamId] = useUrlFilter('dreamId');
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const crud = useCrudEntity<Partner, PartnerRequest>({
    token,
    entityLabel: 'partners',
    list: async (currentToken, includeArchived) => {
      const result = await listPartners(currentToken, page, rowsPerPage, includeArchived, sort, debouncedSearch, {
        supportType: filterSupportType,
        status: filterStatus,
        dreamId: filterDreamId,
      });
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
  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState('');
  const [role, setRole] = useState('');
  const [organization, setOrganization] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [strength, setStrength] = useState('');
  const [supportType, setSupportType] = useState<PartnerSupportType>('MENTOR');
  const [offerType, setOfferType] = useState<OfferType | ''>('');
  const [status, setStatus] = useState<PartnerStatus>('TO_CONTACT');
  // Wanted-partner profiles defined on steps (FR-15.1), shown here so
  // recruitment starts from what's actually needed.
  const [profiles, setProfiles] = useState<IdealPartnerProfile[]>([]);
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
    void listIdealPartnerProfiles(token).then(setProfiles);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, page, rowsPerPage, sort, debouncedSearch, filterSupportType, filterStatus, filterDreamId]);

  // A filter change can shrink the result below the current page, which would
  // otherwise leave the user staring at an empty page 3.
  function applyFilter(setFilter: (value: string) => void) {
    return (value: string) => {
      setFilter(value);
      setPage(0);
    };
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const success = await crud.save({
      name,
      role,
      organization,
      email: email || undefined,
      phone: phone || undefined,
      strength,
      supportType,
      offerType: offerType || undefined,
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
      setPhone('');
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
    setPhone(partner.phone ?? '');
    setStrength(partner.strength ?? '');
    setSupportType(partner.supportType);
    setOfferType(partner.offerType ?? '');
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
    setPhone('');
    setStrength('');
    setSupportType('MENTOR');
    setOfferType('');
    setStatus('TO_CONTACT');
    setRelatedVisionAreaId('');
    setRelatedDreamId('');
    setRelatedGoalId('');
    setRelatedStepId('');
    setRelatedTaskId('');
    setNotes('');
  }

  // Board drag/dropdown move. There is no status PATCH endpoint for partners,
  // so the move sends a full update built from the loaded entity.
  async function handleMove(partner: Partner, nextStatus: PartnerStatus) {
    if (!token || partner.status === nextStatus) {
      return;
    }
    try {
      await updatePartner(token, partner.id, {
        name: partner.name,
        role: partner.role,
        organization: partner.organization,
        email: partner.email,
        phone: partner.phone,
        strength: partner.strength,
        supportType: partner.supportType,
        relatedVisionAreaId: partner.relatedVisionAreaId,
        relatedDreamId: partner.relatedDreamId,
        relatedGoalId: partner.relatedGoalId,
        relatedStepId: partner.relatedStepId,
        relatedTaskId: partner.relatedTaskId,
        status: nextStatus,
        notes: partner.notes,
      });
      await crud.reload();
    } catch (moveError) {
      crud.setError(moveError instanceof Error ? moveError.message : 'Unable to update partner status.');
    }
  }

  // Shared by the table's action column and the board's cards, so both offer
  // the same row actions.
  function renderPartnerActions(partner: Partner) {
    return (
      <RowActionsMenu
        onEdit={() => startEdit(partner)}
        onArchive={() => void crud.archive(partner.id)}
        onRestore={() => void crud.restore(partner.id)}
        onDeletePermanently={() => void crud.permanentlyDelete(partner.id)}
        archived={partner.archived}
        extraActions={[{ label: 'View details', onClick: () => navigate(`/partners/${partner.id}`) }]}
        label="Partner actions"
      />
    );
  }

  // Column keys double as the server's sort fields, so they match Partner's JPA property names.
  const columns: DataTableColumn<Partner>[] = [
    { key: 'code', label: 'Code', sortValue: (partner) => partner.code, sx: { fontSize: 'var(--font-caption)', color: 'var(--text-label)' }, render: (partner) => partner.code },
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
      render: (partner) => renderPartnerActions(partner),
    },
  ];

  const formFields = (
    <>
      <label>
        Name
        <Input value={name} onChange={(event) => setName(event.target.value)} required autoFocus />
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
        Phone
        <Input type="tel" value={phone} onChange={(event) => setPhone(event.target.value)} />
      </label>
      <label>
        Support Type
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Support Type" }} value={supportType} onChange={(event) => setSupportType(event.target.value as PartnerSupportType)}>
            {(['MENTOR', 'EXPERT', 'ADVISOR', 'COLLEAGUE', 'FINANCIAL', 'TECHNICAL', 'EMOTIONAL', 'OTHER'] as const).map((value) => (
              <MenuItem value={value} key={value}>{partnerSupportTypeLabels[value]}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </label>
      <label>
        Offer Type
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Offer Type" }} displayEmpty value={offerType} onChange={(event) => setOfferType(event.target.value as OfferType | '')}>
            <MenuItem value="">None</MenuItem>
            {(['MONEY', 'SHARED_VISION', 'RECOGNITION', 'EXPERIENCE', 'OTHER'] as const).map((value) => (
              <MenuItem value={value} key={value}>{offerTypeLabels[value]}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <span className="field-hint">What this partner most likely responds to in exchange for their help.</span>
      </label>
      <label>
        Status
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Status" }} value={status} onChange={(event) => setStatus(event.target.value as PartnerStatus)}>
            {(['TO_CONTACT', 'CONTACTED', 'ACTIVE', 'WAITING', 'DECLINED', 'COMPLETED'] as const).map((value) => (
              <MenuItem value={value} key={value}>{partnerStatusLabels[value]}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </label>
      <label>
        Vision Area
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Vision Area" }} displayEmpty value={relatedVisionAreaId} onChange={(event) => setRelatedVisionAreaId(event.target.value)}>
            <MenuItem value="">None</MenuItem>
            {visionAreas.map((area) => <MenuItem value={String(area.id)} key={area.id}>{area.name}</MenuItem>)}
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
    <PageSection
      title="Partners"
      subtitle="Track mentors, experts, advisors, and resources."
      actions={<Button type="button" onClick={() => setCreateOpen(true)}>Create partner</Button>}
    >
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Create partner"
        editTitle="Edit Partner"
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
          { key: 'total', label: crud.items.length === 1 ? 'partner' : 'partners', count: crud.items.length },
          { key: 'to-contact', label: 'to contact', count: crud.items.filter((partner) => partner.status === 'TO_CONTACT').length, tone: 'warning', active: filterStatus === 'TO_CONTACT', onClick: () => setFilterStatus(filterStatus === 'TO_CONTACT' ? '' : 'TO_CONTACT') },
          { key: 'active', label: 'active', count: crud.items.filter((partner) => partner.status === 'ACTIVE').length, tone: 'positive', active: filterStatus === 'ACTIVE', onClick: () => setFilterStatus(filterStatus === 'ACTIVE' ? '' : 'ACTIVE') },
          { key: 'waiting', label: 'waiting', count: crud.items.filter((partner) => partner.status === 'WAITING').length, active: filterStatus === 'WAITING', onClick: () => setFilterStatus(filterStatus === 'WAITING' ? '' : 'WAITING') },
        ]}
      />
      <Card className="filter-bar flex-row">
        <SearchBar
          value={searchTerm}
          onChange={(value) => {
            setSearchTerm(value);
            setPage(0);
          }}
          entityLabel="partners"
        />
        <FilterSelect
          label="Support Type"
          value={filterSupportType}
          onChange={applyFilter(setFilterSupportType)}
          options={optionsFromLabels(partnerSupportTypeLabels)}
        />
        <FilterSelect
          label="Status"
          value={filterStatus}
          onChange={applyFilter(setFilterStatus)}
          options={optionsFromLabels(partnerStatusLabels)}
        />
        <FilterSelect
          label="Dream"
          value={filterDreamId}
          onChange={applyFilter(setFilterDreamId)}
          options={optionsFromEntities(dreams, (dream) => dream.title)}
        />
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      <div className="view-toggle-row">
        <ViewToggle value={viewMode} onChange={setViewMode} label="Partner view" />
      </div>
      {viewMode === 'board' && (
        // Partners are server-paged, so the board shows the currently loaded page.
        <StatusBoard
          items={crud.items}
          columns={Object.entries(partnerStatusLabels).map(([value, label]) => ({ value: value as PartnerStatus, label }))}
          statusOf={(partner) => partner.status}
          entityLabel="partners"
          onMove={(partner, nextStatus) => void handleMove(partner, nextStatus)}
          renderCard={(partner) => (
            <>
              <strong>{partner.name}</strong>
              <p>{partner.code}{partner.role ? ` · ${partner.role}` : ''}{partner.organization ? ` · ${partner.organization}` : ''}</p>
              <div className="inline-meta">
                <span>{partnerSupportTypeLabels[partner.supportType]}</span>
              </div>
            </>
          )}
          cardActions={(partner) => renderPartnerActions(partner)}
        />
      )}
      {viewMode === 'list' && (
      <Card>
        <CardContent>
        <DataTable
          rows={crud.items}
          columns={columns}
          emptyMessage={
            searchTerm || filterSupportType || filterStatus || filterDreamId
              ? 'No partners match these filters.'
              : 'No partners yet.'
          }
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
      )}
      {profiles.length > 0 && (
        <Card>
          <CardHeader
            title="Ideal partner profiles"
            subheader="The partners your steps still need — defined on the Steps page, recruited from here"
          />
          <CardContent>
            <div className="stack-list">
              {profiles.map((profile) => {
                const step = steps.find((candidate) => candidate.id === profile.stepId);
                return (
                  <article className="list-card" key={profile.id}>
                    <strong>{step ? step.title : `Step #${profile.stepId}`}</strong>
                    {profile.requiredExperience && <p>Experience: {profile.requiredExperience}</p>}
                    {profile.characterTraits && <p>Traits: {profile.characterTraits}</p>}
                    {profile.motivation && <p>Motivation: {profile.motivation}</p>}
                    {profile.offerInReturn && <p>In return: {profile.offerInReturn}</p>}
                  </article>
                );
              })}
            </div>
          </CardContent>
        </Card>
      )}
    </PageSection>
  );
}

function optionalNumber(value: string) {
  return value ? Number(value) : undefined;
}
