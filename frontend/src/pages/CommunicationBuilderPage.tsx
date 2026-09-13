import { FormEvent, useEffect, useState } from 'react';
import { archiveCommunicationMessage, permanentlyDeleteCommunicationMessage, createCommunicationMessage, listCommunicationMessages, restoreCommunicationMessage, updateCommunicationMessage } from '../api/communicationApi';
import { listDreams } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import { listPartners } from '../api/partnerApi';
import { listTasks } from '../api/taskApi';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import { BulkArchiveAction } from '../components/common/BulkArchiveAction';
import { Button } from '../components/common/Button';
import { CrudModalForm } from '../components/common/CrudModalForm';
import { DataTable, type DataTableColumn } from '../components/common/DataTable';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { FilterPanel } from '../components/common/FilterPanel';
import { FilterSelect, optionsFromEntities, optionsFromLabels } from '../components/common/FilterSelect';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { RowActionsMenu } from '../components/common/RowActionsMenu';
import { SearchBar } from '../components/common/SearchBar';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { StatusBadge } from '../components/common/StatusBadge';
import { StatusBoard } from '../components/common/StatusBoard';
import { Textarea } from '../components/common/Textarea';
import { ViewToggle, type ViewMode } from '../components/common/ViewToggle';
import { useAuth } from '../context/AuthContext';
import { useCrudEntity } from '../hooks/useCrudEntity';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import type { CommunicationMessage, CommunicationMessageRequest, CommunicationStatus, Dream, Goal, Partner, TaskItem } from '../types/vision';
import { communicationStatusLabels } from '../utils/enumLabels';
import { generateMessageBody, type MessageTone } from '../utils/communicationMessageGenerator';
import { PageSection } from './PageSection';

export function CommunicationBuilderPage() {
  const { token } = useAuth();
  // Messages are paged and sorted by the server, so the table only renders what
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
  const [filterPartnerId, setFilterPartnerId] = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const crud = useCrudEntity<CommunicationMessage, CommunicationMessageRequest>({
    token,
    entityLabel: 'communication messages',
    list: async (currentToken, includeArchived) => {
      const result = await listCommunicationMessages(
        currentToken,
        page,
        rowsPerPage,
        includeArchived,
        sort,
        debouncedSearch,
        { partnerId: filterPartnerId, status: filterStatus },
      );
      setTotalRows(result.totalElements);
      return result.content;
    },
    create: createCommunicationMessage,
    update: updateCommunicationMessage,
    archive: archiveCommunicationMessage,
    permanentlyDelete: permanentlyDeleteCommunicationMessage,
    restore: restoreCommunicationMessage,
    undoableArchive: true,
  });
  const [partners, setPartners] = useState<Partner[]>([]);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [partnerId, setPartnerId] = useState('');
  const [relatedDreamId, setRelatedDreamId] = useState('');
  const [relatedGoalId, setRelatedGoalId] = useState('');
  const [relatedTaskId, setRelatedTaskId] = useState('');
  const [audience, setAudience] = useState('');
  const [purpose, setPurpose] = useState('');
  const [subject, setSubject] = useState('');
  const [hook, setHook] = useState('');
  const [problem, setProblem] = useState('');
  const [request, setRequest] = useState('');
  const [benefitToPartner, setBenefitToPartner] = useState('');
  const [wordPicture, setWordPicture] = useState('');
  const [expectedOutcome, setExpectedOutcome] = useState('');
  const [objectionsAndAnswers, setObjectionsAndAnswers] = useState('');
  const [socialProof, setSocialProof] = useState('');
  const [valueComparison, setValueComparison] = useState('');
  const [callToAction, setCallToAction] = useState('');
  const [messageBody, setMessageBody] = useState('');
  // FR-54.3: not persisted — chosen just before "Generate message" runs.
  const [tone, setTone] = useState<MessageTone>('DEFAULT');
  const [status, setStatus] = useState<CommunicationStatus>('DRAFT');
  const [followUpDate, setFollowUpDate] = useState('');

  useEffect(() => {
    if (!token) {
      return;
    }
    void crud.reload();
    void Promise.all([listPartners(token, 0, 500), listDreams(token), listGoals(token), listTasks(token)]).then(
      ([partnerPage, dreamData, goalData, taskData]) => {
        setPartners(partnerPage.content);
        setDreams(dreamData);
        setGoals(goalData);
        setTasks(taskData);
      },
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, page, rowsPerPage, sort, debouncedSearch, filterPartnerId, filterStatus]);

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
      partnerId: optionalNumber(partnerId),
      relatedDreamId: optionalNumber(relatedDreamId),
      relatedGoalId: optionalNumber(relatedGoalId),
      relatedTaskId: optionalNumber(relatedTaskId),
      audience,
      purpose,
      subject,
      hook,
      problem,
      request,
      benefitToPartner,
      wordPicture,
      expectedOutcome,
      objectionsAndAnswers,
      socialProof,
      valueComparison,
      callToAction,
      messageBody,
      status,
      followUpDate: followUpDate || undefined,
    });
    if (success) {
      setSubject('');
      setPurpose('');
      setHook('');
      setProblem('');
      setRequest('');
      setBenefitToPartner('');
      setWordPicture('');
      setExpectedOutcome('');
      setObjectionsAndAnswers('');
      setSocialProof('');
      setValueComparison('');
      setCallToAction('');
      setMessageBody('');
    }
    return success;
  }

  function startEdit(message: CommunicationMessage) {
    crud.startEdit(message.id);
    setPartnerId(message.partnerId ? String(message.partnerId) : '');
    setRelatedDreamId(message.relatedDreamId ? String(message.relatedDreamId) : '');
    setRelatedGoalId(message.relatedGoalId ? String(message.relatedGoalId) : '');
    setRelatedTaskId(message.relatedTaskId ? String(message.relatedTaskId) : '');
    setAudience(message.audience ?? '');
    setPurpose(message.purpose ?? '');
    setSubject(message.subject ?? '');
    setHook(message.hook ?? '');
    setProblem(message.problem ?? '');
    setRequest(message.request ?? '');
    setBenefitToPartner(message.benefitToPartner ?? '');
    setWordPicture(message.wordPicture ?? '');
    setExpectedOutcome(message.expectedOutcome ?? '');
    setObjectionsAndAnswers(message.objectionsAndAnswers ?? '');
    setSocialProof(message.socialProof ?? '');
    setValueComparison(message.valueComparison ?? '');
    setCallToAction(message.callToAction ?? '');
    setMessageBody(message.messageBody ?? '');
    setStatus(message.status);
    setFollowUpDate(message.followUpDate ?? '');
  }

  function cancelEdit() {
    crud.cancelEdit();
    setPartnerId('');
    setRelatedDreamId('');
    setRelatedGoalId('');
    setRelatedTaskId('');
    setAudience('');
    setPurpose('');
    setSubject('');
    setHook('');
    setProblem('');
    setRequest('');
    setBenefitToPartner('');
    setWordPicture('');
    setExpectedOutcome('');
    setObjectionsAndAnswers('');
    setSocialProof('');
    setValueComparison('');
    setCallToAction('');
    setMessageBody('');
    setStatus('DRAFT');
    setFollowUpDate('');
  }

  // Board drag/dropdown move. There is no status PATCH endpoint for messages,
  // so the move sends a full update built from the loaded entity.
  async function handleMove(message: CommunicationMessage, nextStatus: CommunicationStatus) {
    if (!token || message.status === nextStatus) {
      return;
    }
    try {
      await updateCommunicationMessage(token, message.id, {
        partnerId: message.partnerId,
        relatedDreamId: message.relatedDreamId,
        relatedGoalId: message.relatedGoalId,
        relatedTaskId: message.relatedTaskId,
        audience: message.audience,
        purpose: message.purpose,
        subject: message.subject,
        hook: message.hook,
        problem: message.problem,
        request: message.request,
        benefitToPartner: message.benefitToPartner,
        wordPicture: message.wordPicture,
        expectedOutcome: message.expectedOutcome,
        objectionsAndAnswers: message.objectionsAndAnswers,
        socialProof: message.socialProof,
        valueComparison: message.valueComparison,
        callToAction: message.callToAction,
        messageBody: message.messageBody,
        status: nextStatus,
        followUpDate: message.followUpDate,
      });
      await crud.reload();
    } catch (moveError) {
      crud.setError(moveError instanceof Error ? moveError.message : 'Unable to update message status.');
    }
  }

  // Shared by the table's action column and the board's cards, so both offer
  // the same row actions.
  function renderMessageActions(message: CommunicationMessage) {
    return (
      <RowActionsMenu
        onEdit={() => startEdit(message)}
        onArchive={() => void crud.archive(message.id)}
        onRestore={() => void crud.restore(message.id)}
        onDeletePermanently={() => void crud.permanentlyDelete(message.id)}
        archived={message.archived}
        label="Message actions"
      />
    );
  }

  // Column keys double as the server's sort fields, so they match CommunicationMessage's JPA property names.
  const columns: DataTableColumn<CommunicationMessage>[] = [
    {
      key: 'subject',
      label: 'Subject',
      sortValue: (message) => message.subject,
      sx: { fontWeight: 500 },
      render: (message) => message.subject || '-',
    },
    {
      key: 'audience',
      label: 'Audience',
      sortValue: (message) => message.audience,
      render: (message) => message.audience || '-',
    },
    {
      key: 'status',
      label: 'Status',
      sortValue: (message) => message.status,
      render: (message) => <StatusBadge status={message.status} />,
    },
    {
      key: 'followUpDate',
      label: 'Follow Up',
      sortValue: (message) => message.followUpDate,
      render: (message) => message.followUpDate ?? '-',
    },
    {
      key: 'actions',
      label: 'Action',
      className: 'row-actions',
      render: (message) => renderMessageActions(message),
    },
  ];

  const formFields = (
    <>
      <label>
        Partner
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Partner" }} displayEmpty value={partnerId} onChange={(event) => setPartnerId(event.target.value)}>
            <MenuItem value="">None</MenuItem>
            {partners.map((partner) => <MenuItem value={String(partner.id)} key={partner.id}>{partner.name}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Audience
        <Input value={audience} onChange={(event) => setAudience(event.target.value)} />
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
        Task
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Task" }} displayEmpty value={relatedTaskId} onChange={(event) => setRelatedTaskId(event.target.value)}>
            <MenuItem value="">None</MenuItem>
            {tasks.map((task) => <MenuItem value={String(task.id)} key={task.id}>{task.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        Follow Up
        <Input type="date" value={followUpDate} onChange={(event) => setFollowUpDate(event.target.value)} />
      </label>
      <label>
        Subject
        <Input value={subject} onChange={(event) => setSubject(event.target.value)} />
      </label>
      <label>
        Status
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Status" }} value={status} onChange={(event) => setStatus(event.target.value as CommunicationStatus)}>
            {(['DRAFT', 'SENT', 'FOLLOWED_UP', 'REPLIED', 'CLOSED'] as const).map((value) => (
              <MenuItem value={value} key={value}>{communicationStatusLabels[value]}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </label>
      <label className="field-full">
        Purpose
        <Textarea value={purpose} onChange={(event) => setPurpose(event.target.value)} />
      </label>
      <label className="field-full">
        Hook
        <Input value={hook} onChange={(event) => setHook(event.target.value)} placeholder="An opening line that earns attention" />
        <span className="field-hint">
          Open with something specific to them, not a generic greeting. Try one of: a genuine question about their
          work; a shared connection or moment ("We met at..."); or a concrete result you admire ("Your talk on X
          changed how I...").
        </span>
      </label>
      <label className="field-full">
        Problem
        <Textarea value={problem} onChange={(event) => setProblem(event.target.value)} />
      </label>
      <label className="field-full">
        Word Picture
        <Textarea value={wordPicture} onChange={(event) => setWordPicture(event.target.value)} />
        <span className="field-hint">
          Optional. A short, relatable scene that makes the ask concrete. Instead of "I need mentorship," paint it:
          "I have the pieces but keep taking wrong turns - like driving a new city without a map."
        </span>
      </label>
      <label className="field-full">
        Objections & Answers
        <Textarea value={objectionsAndAnswers} onChange={(event) => setObjectionsAndAnswers(event.target.value)} />
        <span className="field-hint">Optional. The pushback you expect, and how you'd answer it.</span>
      </label>
      <label className="field-full">
        Social Proof
        <Textarea value={socialProof} onChange={(event) => setSocialProof(event.target.value)} />
        <span className="field-hint">Optional. Credibility points — prior results, references.</span>
      </label>
      <label className="field-full">
        Value Comparison
        <Textarea value={valueComparison} onChange={(event) => setValueComparison(event.target.value)} />
        <span className="field-hint">Optional. Why this is worth more to them than it costs them.</span>
      </label>
      <label className="field-full">
        Request
        <Textarea value={request} onChange={(event) => setRequest(event.target.value)} />
      </label>
      <label className="field-full">
        Benefit to Partner
        <Textarea value={benefitToPartner} onChange={(event) => setBenefitToPartner(event.target.value)} />
      </label>
      <label className="field-full">
        Expected Outcome
        <Textarea value={expectedOutcome} onChange={(event) => setExpectedOutcome(event.target.value)} />
      </label>
      <label className="field-full">
        Call to Action
        <Textarea value={callToAction} onChange={(event) => setCallToAction(event.target.value)} />
        <span className="field-hint">
          Optional. The one specific next step you're asking for — replaces the generic closing line when filled in.
        </span>
      </label>
      <label className="field-full">
        Message
        <Textarea value={messageBody} onChange={(event) => setMessageBody(event.target.value)} />
      </label>
    </>
  );

  // FR-17.3 / FR-52.2: composition itself lives in generateMessageBody so
  // FR-52's "unchanged when the four new fields are blank" acceptance
  // criterion is unit-testable directly.
  function handleGenerateMessage() {
    const partner = partners.find((item) => item.id === Number(partnerId));
    setMessageBody(generateMessageBody({
      recipient: audience || partner?.name || 'there',
      hook,
      purpose,
      problem,
      wordPicture,
      objectionsAndAnswers,
      socialProof,
      valueComparison,
      request,
      expectedOutcome,
      benefitToPartner,
      callToAction,
    }, tone));
  }

  return (
    <PageSection title="Communication" subtitle="Prepare support requests and follow-up messages.">
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Save message"
        editTitle="Edit Message"
        saving={crud.saving}
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
        extraActions={
          <>
            <FormControl size="small" sx={{ minWidth: 200 }}>
              <Select SelectDisplayProps={{ 'aria-label': 'Generate tone' }} value={tone} onChange={(event) => setTone(event.target.value as MessageTone)}>
                <MenuItem value="DEFAULT">Default tone</MenuItem>
                <MenuItem value="CONSTRUCTIVE_FEEDBACK">Constructive feedback</MenuItem>
              </Select>
            </FormControl>
            <Button type="button" variant="secondary" onClick={handleGenerateMessage}>Generate message</Button>
          </>
        }
      >
        {formFields}
      </CrudModalForm>
      {crud.loading && <Loading variant="table" />}
      {crud.error && <ErrorMessage message={crud.error} onRetry={() => void crud.reload()} />}
      <FilterPanel activeCount={[searchTerm, filterPartnerId, filterStatus].filter(Boolean).length}>
        <SearchBar
          value={searchTerm}
          onChange={(value) => {
            setSearchTerm(value);
            setPage(0);
          }}
          entityLabel="messages"
        />
        <FilterSelect
          label="Partner"
          value={filterPartnerId}
          onChange={applyFilter(setFilterPartnerId)}
          options={optionsFromEntities(partners, (partner) => partner.name)}
        />
        <FilterSelect
          label="Status"
          value={filterStatus}
          onChange={applyFilter(setFilterStatus)}
          options={optionsFromLabels(communicationStatusLabels)}
        />
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </FilterPanel>
      <div className="view-toggle-row">
        <ViewToggle value={viewMode} onChange={setViewMode} label="Message view" />
      </div>
      {viewMode === 'board' && (
        // Messages are server-paged, so the board shows the currently loaded page.
        <StatusBoard
          items={crud.items}
          columns={Object.entries(communicationStatusLabels).map(([value, label]) => ({ value: value as CommunicationStatus, label }))}
          statusOf={(message) => message.status}
          entityLabel="messages"
          onMove={(message, nextStatus) => void handleMove(message, nextStatus)}
          renderCard={(message) => (
            <>
              <strong>{message.subject || '(No subject)'}</strong>
              <p>
                {message.audience || 'No audience'}
                {message.followUpDate ? ` · Follow up ${message.followUpDate}` : ''}
              </p>
            </>
          )}
          cardActions={(message) => renderMessageActions(message)}
        />
      )}
      {viewMode === 'list' && (
      <Card>
        <CardContent>
        <DataTable
          rows={crud.items}
          columns={columns}
          emptyMessage={
            searchTerm || filterPartnerId || filterStatus ? 'No messages match these filters.' : 'No messages yet.'
          }
          rowClassName={(message) => (message.archived ? 'row-archived' : '')}
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
            rowLabel: (message) => message.subject || 'message',
            actions: (
              <BulkArchiveAction
                selectedIds={selectedIds}
                entityLabel="message(s)"
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
    </PageSection>
  );
}

function optionalNumber(value: string) {
  return value ? Number(value) : undefined;
}
