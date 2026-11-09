import { FormEvent, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { HelpWithThis, PageAssistantProvider, PageHelpButton, type PageFormSource } from '../components/ai/page-assistant/PageAssistant';
import { MESSAGE_FIELD_LABELS } from '../components/ai/page-assistant/pageHelp';
import { CommunicationHelpers } from '../components/communication/CommunicationHelpers';
import { DraftPriorityCard, FollowUpDueCard } from '../components/communication/CommunicationListHelpers';
import { progressLine } from '../utils/messageTemplates';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
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
import type { CommunicationMessage, CommunicationMessageRequest, CommunicationStatus, Dream, Goal, NewMessageDraft, Partner, TaskItem } from '../types/vision';
import { communicationStatusLabels } from '../utils/enumLabels';
import { generateMessageBody, type MessageTone } from '../utils/communicationMessageGenerator';
import { PageSection } from './PageSection';
import { currentLanguage, tr } from '../i18n/translate';
import { draftOutreach } from '../api/aiDailyApi';
import { PitchCoachCard } from '../components/ai/PitchCoachCard';
import { WhenAiOn } from '../components/ai/WhenAiOn';

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
  // AI-FR-17: a draft the participant edits and sends themselves.
  const [aiDrafting, setAiDrafting] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();

  // V15 (AH-FR-36): "Draft a message to them" from an obstacle opens a new message filled from it.
  // Nothing is saved until the participant saves it here; the params are stripped so a refresh does not reopen it.
  useEffect(() => {
    if (searchParams.get('create') !== 'message') {
      return;
    }
    const fill: [string, (value: string) => void][] = [
      ['partnerId', setPartnerId], ['audience', setAudience], ['purpose', setPurpose], ['problem', setProblem], ['request', setRequest],
    ];
    for (const [key, setter] of fill) {
      const value = searchParams.get(key);
      if (value) {
        setter(value);
      }
    }
    setCreateOpen(true);
    const next = new URLSearchParams(searchParams);
    for (const key of ['create', ...fill.map(([key]) => key)]) {
      next.delete(key);
    }
    setSearchParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);
  const [aiDraftError, setAiDraftError] = useState('');

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

  async function handleAiDraft() {
    if (!token) {
      return;
    }
    if (!request.trim()) {
      setAiDraftError(tr('Write what you are asking for in Request first.'));
      return;
    }
    setAiDrafting(true);
    setAiDraftError('');
    try {
      const draft = await draftOutreach(token, {
        partnerId: optionalNumber(partnerId) ?? null,
        relatedDreamId: optionalNumber(relatedDreamId) ?? null,
        purpose,
        request,
      }, currentLanguage());
      if (draft.subject) {
        setSubject(draft.subject);
      }
      setMessageBody(draft.message ?? '');
    } catch (draftError) {
      setAiDraftError(draftError instanceof Error ? tr(draftError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setAiDrafting(false);
    }
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
      crud.setError(moveError instanceof Error ? moveError.message : tr('Unable to update message status.'));
    }
  }

  // Shared by the table's action column and the board's cards, so both offer
  // the same row actions.
  function renderMessageActions(message: CommunicationMessage) {
    return (
      <>
      <HelpWithThis compact area="item" itemId={message.id} title={message.subject || tr('Message')} />
      <RowActionsMenu
        onEdit={() => startEdit(message)}
        onArchive={() => void crud.archive(message.id)}
        onRestore={() => void crud.restore(message.id)}
        onDeletePermanently={() => void crud.permanentlyDelete(message.id)}
        archived={message.archived}
        label={tr('Message actions')}
      />
      </>
    );
  }

  // Column keys double as the server's sort fields, so they match CommunicationMessage's JPA property names.
  const columns: DataTableColumn<CommunicationMessage>[] = [
    {
      key: 'subject',
      label: tr('Subject'),
      sortValue: (message) => message.subject,
      sx: { fontWeight: 500 },
      render: (message) => message.subject || '-',
    },
    {
      key: 'audience',
      label: tr('Audience'),
      sortValue: (message) => message.audience,
      render: (message) => message.audience || '-',
    },
    {
      key: 'status',
      label: tr('Status'),
      sortValue: (message) => message.status,
      render: (message) => <StatusBadge status={message.status} />,
    },
    {
      key: 'followUpDate',
      label: tr('Follow Up'),
      sortValue: (message) => message.followUpDate,
      render: (message) => message.followUpDate ?? '-',
    },
    {
      key: 'actions',
      label: tr('Action'),
      className: 'row-actions',
      render: (message) => renderMessageActions(message),
    },
  ];

  // V15 (AH-FR-05, AH-FR-06): while the form is open, the assistant reads it as typed and a card fills one field.
  const formOpen = createOpen || crud.editingId !== null;
  const readForm = () => ({
    partnerId, relatedDreamId, relatedGoalId, relatedTaskId, audience, purpose, subject, hook, problem, request,
    benefitToPartner, wordPicture, expectedOutcome, objectionsAndAnswers, socialProof, valueComparison, callToAction,
    messageBody, status, followUpDate,
  });
  const applyField = (field: string, value: string) => {
    const setters: Record<string, (next: string) => void> = {
      audience: setAudience, purpose: setPurpose, subject: setSubject, hook: setHook, problem: setProblem, request: setRequest,
      benefitToPartner: setBenefitToPartner, wordPicture: setWordPicture, expectedOutcome: setExpectedOutcome,
      objectionsAndAnswers: setObjectionsAndAnswers, socialProof: setSocialProof, valueComparison: setValueComparison,
      callToAction: setCallToAction, messageBody: setMessageBody,
    };
    if (field === 'status' && (['DRAFT', 'SENT', 'FOLLOWED_UP', 'REPLIED', 'CLOSED'] as string[]).includes(value)) {
      // AH-FR-52: a proposed status only fills the form; the participant saves it.
      setStatus(value as CommunicationStatus);
    } else {
      setters[field]?.(value);
    }
  };
  const formSource: PageFormSource | null = formOpen ? { itemId: crud.editingId, read: readForm, apply: applyField } : null;

  // V15 (AH-FR-49, AH-FR-65): a follow-up or thank-you opens as a new message to the same partner, to edit and save.
  function openNewMessage(draft: NewMessageDraft) {
    cancelEdit();
    setPartnerId(draft.partnerId ? String(draft.partnerId) : '');
    setSubject(draft.subject);
    setRequest(draft.request);
    setMessageBody(draft.messageBody);
    setRelatedDreamId(draft.relatedDreamId ? String(draft.relatedDreamId) : '');
    setRelatedGoalId(draft.relatedGoalId ? String(draft.relatedGoalId) : '');
    setRelatedTaskId(draft.relatedTaskId ? String(draft.relatedTaskId) : '');
    setStatus('DRAFT');
    setCreateOpen(true);
  }

  // AH-FR-65: progress for the thank-you, from the linked task, else the linked goal.
  const linkedTask = tasks.find((task) => String(task.id) === relatedTaskId);
  const linkedGoal = goals.find((goal) => String(goal.id) === relatedGoalId);
  const progressText = linkedTask ? progressLine(linkedTask.title, linkedTask.progressPercent)
    : progressLine(linkedGoal?.title, linkedGoal?.progressPercent);

  const formTopic = formOpen ? { area: 'message', itemId: crud.editingId, title: subject || tr('New message') } : null;

  const formFields = (
    <>
      <div className="field-full">
        <HelpWithThis area="message" itemId={crud.editingId} title={subject || tr('New message')} label={tr('your message')} />
      </div>
      {token && (
        // V15 Phase 3 (AH-FR-44 to AH-FR-65): helpers that fill this form or open a new message or task.
        <CommunicationHelpers token={token} messageId={crud.editingId} status={status}
          partnerName={partners.find((partner) => String(partner.id) === partnerId)?.name ?? null} progressText={progressText}
          read={readForm} apply={applyField} onRebuildWithTemplate={handleGenerateMessage} onNewMessage={openNewMessage}
          partnerStyle={partners.find((partner) => String(partner.id) === partnerId)?.workStyleType ?? null}
          partnerMotivator={partners.find((partner) => String(partner.id) === partnerId)?.primaryMotivator ?? null} />
      )}
      <label>
        {tr('Partner')}
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Partner" }} displayEmpty value={partnerId} onChange={(event) => setPartnerId(event.target.value)}>
            <MenuItem value="">{tr('None')}</MenuItem>
            {partners.map((partner) => <MenuItem value={String(partner.id)} key={partner.id}>{partner.name}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label htmlFor="message-audience">
        {tr('Audience')}
        <HelpWithThis compact area="field:audience" itemId={crud.editingId} label={tr(MESSAGE_FIELD_LABELS.audience)} />
        <Input value={audience} id="message-audience" onChange={(event) => setAudience(event.target.value)} />
      </label>
      <label>
        {tr('Dream')}
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Dream" }} displayEmpty value={relatedDreamId} onChange={(event) => setRelatedDreamId(event.target.value)}>
            <MenuItem value="">{tr('None')}</MenuItem>
            {dreams.map((dream) => <MenuItem value={String(dream.id)} key={dream.id}>{dream.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        {tr('Goal')}
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Goal" }} displayEmpty value={relatedGoalId} onChange={(event) => setRelatedGoalId(event.target.value)}>
            <MenuItem value="">{tr('None')}</MenuItem>
            {goals.map((goal) => <MenuItem value={String(goal.id)} key={goal.id}>{goal.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        {tr('Task')}
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Task" }} displayEmpty value={relatedTaskId} onChange={(event) => setRelatedTaskId(event.target.value)}>
            <MenuItem value="">{tr('None')}</MenuItem>
            {tasks.map((task) => <MenuItem value={String(task.id)} key={task.id}>{task.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        {tr('Follow Up')}
        <Input type="date" value={followUpDate} onChange={(event) => setFollowUpDate(event.target.value)} />
      </label>
      <label htmlFor="message-subject">
        {tr('Subject')}
        <HelpWithThis compact area="field:subject" itemId={crud.editingId} label={tr(MESSAGE_FIELD_LABELS.subject)} />
        <Input value={subject} id="message-subject" onChange={(event) => setSubject(event.target.value)} />
      </label>
      <label>
        {tr('Status')}
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Status" }} value={status} onChange={(event) => setStatus(event.target.value as CommunicationStatus)}>
            {(['DRAFT', 'SENT', 'FOLLOWED_UP', 'REPLIED', 'CLOSED'] as const).map((value) => (
              <MenuItem value={value} key={value}>{communicationStatusLabels[value]}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </label>
      <label className="field-full" htmlFor="message-purpose">
        Purpose
        <HelpWithThis compact area="field:purpose" itemId={crud.editingId} label={tr(MESSAGE_FIELD_LABELS.purpose)} />
        <Textarea value={purpose} id="message-purpose" onChange={(event) => setPurpose(event.target.value)} />
      </label>
      <label className="field-full" htmlFor="message-hook">
        Hook
        <HelpWithThis compact area="field:hook" itemId={crud.editingId} label={tr(MESSAGE_FIELD_LABELS.hook)} />
        <Input value={hook} id="message-hook" onChange={(event) => setHook(event.target.value)} placeholder={tr('An opening line that earns attention')} />
        <span className="field-hint">
          {tr('Open with something specific to them, not a generic greeting. Try one of: a genuine question about their work; a shared connection or moment ("We met at..."); or a concrete result you admire ("Your talk on X changed how I...").')}
        </span>
      </label>
      <label className="field-full" htmlFor="message-problem">
        Problem
        <HelpWithThis compact area="field:problem" itemId={crud.editingId} label={tr(MESSAGE_FIELD_LABELS.problem)} />
        <Textarea value={problem} id="message-problem" onChange={(event) => setProblem(event.target.value)} />
      </label>
      <label className="field-full" htmlFor="message-wordPicture">
        {tr('Word Picture')}
        <HelpWithThis compact area="field:wordPicture" itemId={crud.editingId} label={tr(MESSAGE_FIELD_LABELS.wordPicture)} />
        <Textarea value={wordPicture} id="message-wordPicture" onChange={(event) => setWordPicture(event.target.value)} />
        <span className="field-hint">
          {tr('Optional. A short, relatable scene that makes the ask concrete. Instead of "I need mentorship," paint it: "I have the pieces but keep taking wrong turns - like driving a new city without a map."')}
        </span>
      </label>
      <label className="field-full" htmlFor="message-objectionsAndAnswers">
        {tr('Objections & Answers')}
        <HelpWithThis compact area="field:objectionsAndAnswers" itemId={crud.editingId} label={tr(MESSAGE_FIELD_LABELS.objectionsAndAnswers)} />
        <Textarea value={objectionsAndAnswers} id="message-objectionsAndAnswers" onChange={(event) => setObjectionsAndAnswers(event.target.value)} />
        <span className="field-hint">{tr('Optional. The pushback you expect, and how you\'d answer it.')}</span>
      </label>
      <label className="field-full" htmlFor="message-socialProof">
        {tr('Social Proof')}
        <HelpWithThis compact area="field:socialProof" itemId={crud.editingId} label={tr(MESSAGE_FIELD_LABELS.socialProof)} />
        <Textarea value={socialProof} id="message-socialProof" onChange={(event) => setSocialProof(event.target.value)} />
        <span className="field-hint">{tr('Optional. Credibility points — prior results, references.')}</span>
      </label>
      <label className="field-full" htmlFor="message-valueComparison">
        {tr('Value Comparison')}
        <HelpWithThis compact area="field:valueComparison" itemId={crud.editingId} label={tr(MESSAGE_FIELD_LABELS.valueComparison)} />
        <Textarea value={valueComparison} id="message-valueComparison" onChange={(event) => setValueComparison(event.target.value)} />
        <span className="field-hint">{tr('Optional. Why this is worth more to them than it costs them.')}</span>
      </label>
      <label className="field-full" htmlFor="message-request">
        Request
        <HelpWithThis compact area="field:request" itemId={crud.editingId} label={tr(MESSAGE_FIELD_LABELS.request)} />
        <Textarea value={request} id="message-request" onChange={(event) => setRequest(event.target.value)} />
      </label>
      <label className="field-full" htmlFor="message-benefitToPartner">
        {tr('Benefit to Partner')}
        <HelpWithThis compact area="field:benefitToPartner" itemId={crud.editingId} label={tr(MESSAGE_FIELD_LABELS.benefitToPartner)} />
        <Textarea value={benefitToPartner} id="message-benefitToPartner" onChange={(event) => setBenefitToPartner(event.target.value)} />
      </label>
      <label className="field-full" htmlFor="message-expectedOutcome">
        {tr('Expected Outcome')}
        <HelpWithThis compact area="field:expectedOutcome" itemId={crud.editingId} label={tr(MESSAGE_FIELD_LABELS.expectedOutcome)} />
        <Textarea value={expectedOutcome} id="message-expectedOutcome" onChange={(event) => setExpectedOutcome(event.target.value)} />
      </label>
      <label className="field-full" htmlFor="message-callToAction">
        {tr('Call to Action')}
        <HelpWithThis compact area="field:callToAction" itemId={crud.editingId} label={tr(MESSAGE_FIELD_LABELS.callToAction)} />
        <Textarea value={callToAction} id="message-callToAction" onChange={(event) => setCallToAction(event.target.value)} />
        <span className="field-hint">
          {tr('Optional. The one specific next step you\'re asking for — replaces the generic closing line when filled in.')}
        </span>
      </label>
      <label className="field-full" htmlFor="message-messageBody">
        Message
        <HelpWithThis compact area="field:messageBody" itemId={crud.editingId} label={tr(MESSAGE_FIELD_LABELS.messageBody)} />
        <Textarea value={messageBody} id="message-messageBody" onChange={(event) => setMessageBody(event.target.value)} />
        {aiDraftError && <Alert severity="error" sx={{ mt: 1 }}>{aiDraftError}</Alert>}
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
    // V15 (AH-FR-01): the page assistant on the Communication page.
    <PageAssistantProvider page="COMMUNICATION" form={formSource} current={formTopic}>
    <PageSection title={tr('Communication')} subtitle={tr('Prepare support requests and follow-up messages.')} actions={<PageHelpButton />}>
      <CrudModalForm
        creating={createOpen}
        onCreatingChange={setCreateOpen}
        editing={crud.editingId !== null}
        createLabel={tr('Save message')}
        editTitle="Edit Message"
        saving={crud.saving}
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
        extraActions={
          <>
            <FormControl size="small" sx={{ minWidth: 200 }}>
              <Select SelectDisplayProps={{ 'aria-label': 'Generate tone' }} value={tone} onChange={(event) => setTone(event.target.value as MessageTone)}>
                <MenuItem value="DEFAULT">{tr('Default tone')}</MenuItem>
                <MenuItem value="CONSTRUCTIVE_FEEDBACK">{tr('Constructive feedback')}</MenuItem>
              </Select>
            </FormControl>
            <Button type="button" variant="secondary" onClick={handleGenerateMessage}>{tr('Generate message')}</Button>
            {/* AI-NFR-02: no AI controls while AI is off. */}
            <WhenAiOn token={token}>
              <Button type="button" variant="secondary" disabled={aiDrafting} onClick={() => void handleAiDraft()}>
                {aiDrafting ? tr('The Dream Coach is thinking…') : tr('Draft with AI')}
              </Button>
            </WhenAiOn>
          </>
        }
      >
        {formFields}
      </CrudModalForm>
      {token && <Box sx={{ my: 2 }}><PitchCoachCard token={token} /></Box>}
      {crud.loading && <Loading variant="table" />}
      {crud.error && <ErrorMessage message={crud.error} onRetry={() => void crud.reload()} />}
      {token && <FollowUpDueCard token={token} reloadKey={totalRows + (crud.saving ? 1 : 0)} onNewMessage={openNewMessage} />}
      {token && <DraftPriorityCard token={token} />}
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
          label={tr('Partner')}
          value={filterPartnerId}
          onChange={applyFilter(setFilterPartnerId)}
          options={optionsFromEntities(partners, (partner) => partner.name)}
        />
        <FilterSelect
          label={tr('Status')}
          value={filterStatus}
          onChange={applyFilter(setFilterStatus)}
          options={optionsFromLabels(communicationStatusLabels)}
        />
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </FilterPanel>
      <div className="view-toggle-row">
        <ViewToggle value={viewMode} onChange={setViewMode} label={tr('Message view')} />
        <HelpWithThis area="list" label={tr('your messages')} />
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
              <strong>{message.subject || tr('(No subject)')}</strong>
              <p>
                {message.audience || tr('No audience')}
                {message.followUpDate ? ` · ${tr('Follow up {date}', { date: message.followUpDate })}` : ''}
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
            searchTerm || filterPartnerId || filterStatus ? tr('No messages match these filters.') : tr('No messages yet.')
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
                items={crud.items}
                entityLabel={tr('message(s)')}
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
      )}
    </PageSection>
    </PageAssistantProvider>
  );
}

function optionalNumber(value: string) {
  return value ? Number(value) : undefined;
}
