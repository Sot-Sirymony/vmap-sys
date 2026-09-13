import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { archiveDream, permanentlyDeleteDream, createDream, getDreamArchiveImpact, listDreams, restoreDream, updateDream } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import { listVisionAreas } from '../api/visionAreaApi';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import Tooltip from '@mui/material/Tooltip';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import { MoonStar, Rocket, TriangleAlert } from 'lucide-react';
import { Breadcrumbs } from '../components/common/Breadcrumbs';
import { BulkArchiveAction } from '../components/common/BulkArchiveAction';
import { Button } from '../components/common/Button';
import { CrudModalForm } from '../components/common/CrudModalForm';
import { EmptyState } from '../components/common/EmptyState';
import { DreamWizard } from '../components/forms/DreamWizard';
import { DataTable, type DataTableColumn } from '../components/common/DataTable';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { FilterPanel } from '../components/common/FilterPanel';
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
import type { Dream, DreamRequest, DreamStatus, DreamType, Priority, ScheduleMode, VisionArea } from '../types/vision';
import { moonshotViolet } from '../theme';
import { dreamRequest } from '../utils/entityRequests';
import {
  DECISION_CHECKLIST_QUESTIONS, dreamStatusLabels, dreamTypeLabels, EMPTY_DECISION_ANSWERS, isDecisionChecklistComplete,
  priorityLabels, scheduleModeLabels, type DecisionChecklistKey,
} from '../utils/enumLabels';
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
  const [moonshot, setMoonshot] = useState(false);
  const [moonshotVision, setMoonshotVision] = useState('');
  const [scheduleMode, setScheduleMode] = useState<ScheduleMode>('BOTTOM_UP');
  const [decisionAnswers, setDecisionAnswers] = useState<Record<DecisionChecklistKey, boolean | null>>(EMPTY_DECISION_ANSWERS);
  // FR-55.4: null until the BR-44 gate has cleared for the dream being
  // edited (or when creating a new one) — that's when the checklist below
  // is shown; once cleared, it never re-fires.
  const [editingDecisionGateClearedAt, setEditingDecisionGateClearedAt] = useState<string | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [searchTerm, setSearchTerm] = useState('');
  // In the URL, not component state: the dashboard links straight into a
  // filtered view, and a filtered list stays shareable and bookmarkable.
  const [filterVisionAreaId, setFilterVisionAreaId] = useUrlFilter('visionAreaId');
  const [filterDreamType, setFilterDreamType] = useUrlFilter('type');
  const [filterPriority, setFilterPriority] = useUrlFilter('priority');
  const [filterStatus, setFilterStatus] = useUrlFilter('status');
  const [filterOverdueOnly, setFilterOverdueOnly] = useUrlFlag('overdue');
  const [filterMoonshotOnly, setFilterMoonshotOnly] = useUrlFlag('moonshot');
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
  }, [searchParams]);

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

  // FR-55.1: the checklist only applies the first time a High/Critical
  // moonshot dream moves to Active — once cleared (editingDecisionGateClearedAt
  // set), it never re-fires, matching BR-44's "does not re-apply" behavior.
  // Gate B (two linked Advisor/Mentor partners) isn't checked here since the
  // frontend doesn't have that count cheaply on hand; the backend enforces
  // the real either/or rule and its message surfaces via crud.error on save.
  const showDecisionChecklist = !editingDecisionGateClearedAt
    && moonshot
    && (priority === 'HIGH' || priority === 'CRITICAL')
    && status === 'ACTIVE';

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
      moonshot,
      moonshotVision: moonshot ? moonshotVision : undefined,
      scheduleMode,
      decisionSkippedResearch: decisionAnswers.decisionSkippedResearch ?? undefined,
      decisionAssumedNoChange: decisionAnswers.decisionAssumedNoChange ?? undefined,
      decisionTrustedUnverifiedClaim: decisionAnswers.decisionTrustedUnverifiedClaim ?? undefined,
      decisionJudgedByAppearance: decisionAnswers.decisionJudgedByAppearance ?? undefined,
      decisionUnderTimePressure: decisionAnswers.decisionUnderTimePressure ?? undefined,
      decisionNoOutsideInput: decisionAnswers.decisionNoOutsideInput ?? undefined,
      decisionChasedEasyReward: decisionAnswers.decisionChasedEasyReward ?? undefined,
      decisionDismissedDisagreeingAdvice: decisionAnswers.decisionDismissedDisagreeingAdvice ?? undefined,
    });
    if (success) {
      setTitle('');
      setDescription('');
      setWhyImportant('');
      setSuccessDefinition('');
      setMoonshot(false);
      setMoonshotVision('');
      setScheduleMode('BOTTOM_UP');
      setDecisionAnswers(EMPTY_DECISION_ANSWERS);
      setEditingDecisionGateClearedAt(null);
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
    setMoonshot(dream.moonshot);
    setMoonshotVision(dream.moonshotVision ?? '');
    setScheduleMode(dream.scheduleMode);
    setDecisionAnswers({
      decisionSkippedResearch: dream.decisionSkippedResearch ?? null,
      decisionAssumedNoChange: dream.decisionAssumedNoChange ?? null,
      decisionTrustedUnverifiedClaim: dream.decisionTrustedUnverifiedClaim ?? null,
      decisionJudgedByAppearance: dream.decisionJudgedByAppearance ?? null,
      decisionUnderTimePressure: dream.decisionUnderTimePressure ?? null,
      decisionNoOutsideInput: dream.decisionNoOutsideInput ?? null,
      decisionChasedEasyReward: dream.decisionChasedEasyReward ?? null,
      decisionDismissedDisagreeingAdvice: dream.decisionDismissedDisagreeingAdvice ?? null,
    });
    setEditingDecisionGateClearedAt(dream.decisionGateClearedAt ?? null);
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
    setMoonshot(false);
    setMoonshotVision('');
    setScheduleMode('BOTTOM_UP');
    setDecisionAnswers(EMPTY_DECISION_ANSWERS);
    setEditingDecisionGateClearedAt(null);
  }

  // Board drag/dropdown move. There is no status PATCH endpoint for dreams, so
  // the move sends a full update built from the loaded entity.
  async function handleMove(dream: Dream, nextStatus: DreamStatus) {
    if (!token || dream.status === nextStatus) {
      return;
    }
    // FR-55.1: a High/Critical moonshot dream's first move to Active needs
    // either the checklist or two linked counselors, which a silent drag
    // can't collect — open the edit form (pre-set to Active) instead of
    // completing the move directly.
    const needsDecisionGate = nextStatus === 'ACTIVE' && !dream.decisionGateClearedAt
      && dream.moonshot && (dream.priority === 'HIGH' || dream.priority === 'CRITICAL');
    if (needsDecisionGate) {
      startEdit(dream);
      setStatus('ACTIVE');
      return;
    }
    try {
      await updateDream(token, dream.id, { ...dreamRequest(dream), status: nextStatus });
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
    if (filterMoonshotOnly && !dream.moonshot) {
      return false;
    }
    return matchesSearch(searchTerm, dream.code, dream.title, dream.description, dream.whyImportant, dream.successDefinition);
  });

  const hasFilters = Boolean(
    searchTerm || filterVisionAreaId || filterDreamType || filterPriority || filterStatus
      || filterOverdueOnly || filterMoonshotOnly,
  );

  // FR-23.1-style ancestry: shows which vision area a dream belongs to,
  // navigable, the same pattern the Tasks board uses under each row's title.
  function dreamCrumbs(dream: Dream) {
    const area = visionAreas.find((item) => item.id === dream.visionAreaId);
    return [
      area && { label: area.name, to: `/dreams?visionAreaId=${area.id}` },
    ].filter(Boolean) as { label: string; to: string }[];
  }

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
      render: (dream) => (
        <>
          <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75 }}>
            {dream.moonshot && (
              <Tooltip title={dream.moonshotVision || 'Moonshot dream'} arrow>
                <Box component="span" sx={{ display: 'inline-flex', color: moonshotViolet }} role="img" aria-label="Moonshot dream">
                  <Rocket size={16} />
                </Box>
              </Tooltip>
            )}
            {dream.scheduleOverrun && (
              // FR-51.2: fixed top-down deadline, but a goal now runs past it — informational only.
              <Tooltip title={dream.scheduleOverrunDetail || 'A goal runs past this dream\'s fixed target date.'} arrow>
                <Box component="span" sx={{ display: 'inline-flex', color: 'var(--palette-warning-dark)' }} role="img" aria-label="Schedule overrun">
                  <TriangleAlert size={16} />
                </Box>
              </Tooltip>
            )}
            <Link className="table-title-link" to={`/dreams/${dream.id}`}>{dream.title}</Link>
          </Box>
          <Breadcrumbs crumbs={dreamCrumbs(dream)} />
        </>
      ),
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
        <span className="inline-meta">
          <Checkbox checked={moonshot} onChange={(event) => setMoonshot(event.target.checked)} />
          Moonshot dream
        </span>
      </label>
      {moonshot && (
        <label className="field-full">
          Moonshot vision
          <Textarea value={moonshotVision} onChange={(event) => setMoonshotVision(event.target.value)} />
          <span className="field-hint">
            If resources were no limit, what would the ideal result look like? Aim beyond what feels achievable — this
            is aspirational only and never changes the dream's progress or completion.
          </span>
        </label>
      )}
      <label className="field-full">
        Target date scheduling
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': 'Target date scheduling' }} value={scheduleMode} onChange={(event) => setScheduleMode(event.target.value as ScheduleMode)}>
            <MenuItem value="BOTTOM_UP">{scheduleModeLabels.BOTTOM_UP}</MenuItem>
            <MenuItem value="TOP_DOWN_FIXED">{scheduleModeLabels.TOP_DOWN_FIXED}</MenuItem>
          </Select>
        </FormControl>
        <span className="field-hint">
          {scheduleMode === 'BOTTOM_UP'
            ? "This dream's target date must be on or after its goals' target dates — set from the bottom up."
            : 'A real external deadline that cannot move. Goals running past it are flagged, not blocked.'}
        </span>
      </label>
      {showDecisionChecklist && (
        <div className="field-full diligence-checklist">
          <strong>Before you move this moonshot dream to Active…</strong>
          <p>
            This is a high-priority moonshot dream. Answer all eight questions honestly, or instead link at least two
            Advisor or Mentor partners to it (directly or through one of its goals) on the Partners page. Either one
            clears this check — there's no right answer here, only an honest one.
          </p>
          {DECISION_CHECKLIST_QUESTIONS.map((question) => (
            <div className="diligence-row" key={question.key}>
              <span>{question.label}</span>
              <ToggleButtonGroup
                size="small"
                exclusive
                value={decisionAnswers[question.key] === null ? '' : decisionAnswers[question.key] ? 'yes' : 'no'}
                onChange={(_event, value) => {
                  if (value === null) {
                    return;
                  }
                  setDecisionAnswers((current) => ({ ...current, [question.key]: value === 'yes' }));
                }}
                aria-label={question.label}
              >
                <ToggleButton value="no">No</ToggleButton>
                <ToggleButton value="yes">Yes</ToggleButton>
              </ToggleButtonGroup>
            </div>
          ))}
          <span className="field-hint">
            {isDecisionChecklistComplete(decisionAnswers)
              ? 'All eight answered — this checklist clears the gate on save.'
              : `${DECISION_CHECKLIST_QUESTIONS.filter((question) => decisionAnswers[question.key] !== null).length} of ${DECISION_CHECKLIST_QUESTIONS.length} answered.`}
          </span>
        </div>
      )}
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
      <FilterPanel activeCount={[searchTerm, filterVisionAreaId, filterDreamType, filterPriority, filterStatus, filterOverdueOnly, filterMoonshotOnly].filter(Boolean).length}>
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
        <label className="checkbox-field">
          <Checkbox checked={filterMoonshotOnly} onChange={(event) => setFilterMoonshotOnly(event.target.checked)} />
          Moonshots only
        </label>
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </FilterPanel>
      <div className="view-toggle-row">
        <ViewToggle value={viewMode} onChange={setViewMode} label="Dream view" />
      </div>
      {!crud.loading && crud.items.length === 0 && !hasFilters ? (
        <EmptyState
          headline="No dreams yet"
          icon={MoonStar}
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
            pageResetKey={`${searchTerm}|${filterVisionAreaId}|${filterDreamType}|${filterPriority}|${filterStatus}|${filterOverdueOnly}|${filterMoonshotOnly}`}
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
              <strong>
                {dream.moonshot && (
                  <Tooltip title={dream.moonshotVision || 'Moonshot dream'} arrow>
                    <Box component="span" sx={{ display: 'inline-flex', color: moonshotViolet, mr: 0.75, verticalAlign: 'middle' }} role="img" aria-label="Moonshot dream">
                      <Rocket size={16} />
                    </Box>
                  </Tooltip>
                )}
                {dream.scheduleOverrun && (
                  <Tooltip title={dream.scheduleOverrunDetail || 'A goal runs past this dream\'s fixed target date.'} arrow>
                    <Box component="span" sx={{ display: 'inline-flex', color: 'var(--palette-warning-dark)', mr: 0.75, verticalAlign: 'middle' }} role="img" aria-label="Schedule overrun">
                      <TriangleAlert size={16} />
                    </Box>
                  </Tooltip>
                )}
                {dream.title}
              </strong>
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
