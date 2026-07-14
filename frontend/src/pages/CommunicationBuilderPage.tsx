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
import type { CommunicationMessage, CommunicationMessageRequest, CommunicationStatus, Dream, Goal, Partner, TaskItem } from '../types/vision';
import { communicationStatusLabels } from '../utils/enumLabels';
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
  // Search runs on the server too, so it spans every page, not just the loaded one.
  const [searchTerm, setSearchTerm] = useState('');
  const debouncedSearch = useDebouncedValue(searchTerm);
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
      );
      setTotalRows(result.totalElements);
      return result.content;
    },
    create: createCommunicationMessage,
    update: updateCommunicationMessage,
    archive: archiveCommunicationMessage,
    permanentlyDelete: permanentlyDeleteCommunicationMessage,
    restore: restoreCommunicationMessage,
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
  const [messageBody, setMessageBody] = useState('');
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
  }, [token, page, rowsPerPage, sort, debouncedSearch]);

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
    setMessageBody('');
    setStatus('DRAFT');
    setFollowUpDate('');
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
      render: (message) => (
        <RowActionsMenu
          onEdit={() => startEdit(message)}
          onArchive={() => void crud.archive(message.id)}
          onRestore={() => void crud.restore(message.id)}
          onDeletePermanently={() => void crud.permanentlyDelete(message.id)}
          archived={message.archived}
          label="Message actions"
        />
      ),
    },
  ];

  const formFields = (
    <>
      <label>
        Partner
        <FormControl fullWidth size="small">
          <Select displayEmpty value={partnerId} onChange={(event) => setPartnerId(event.target.value)}>
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
        Task
        <FormControl fullWidth size="small">
          <Select displayEmpty value={relatedTaskId} onChange={(event) => setRelatedTaskId(event.target.value)}>
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
          <Select value={status} onChange={(event) => setStatus(event.target.value as CommunicationStatus)}>
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
        Message
        <Textarea value={messageBody} onChange={(event) => setMessageBody(event.target.value)} />
      </label>
    </>
  );

  // FR-17.3: compose in a fixed persuasive order — Hook, Problem (with the
  // word picture folded in), Request, Benefit, Expected Outcome — including
  // only the parts the user actually filled, in respectful language. The
  // draft stays fully editable afterward.
  function handleGenerateMessage() {
    const partner = partners.find((item) => item.id === Number(partnerId));
    const recipient = audience || partner?.name || 'there';
    const paragraphs: string[] = [];

    if (hook.trim()) {
      paragraphs.push(hook.trim());
    }

    const contextSentences: string[] = [];
    if (purpose.trim()) {
      contextSentences.push(`I am working on ${purpose.trim()}.`);
    }
    if (problem.trim()) {
      contextSentences.push(`Right now, ${lowerFirst(problem.trim())}`);
    }
    if (wordPicture.trim()) {
      contextSentences.push(`To put it plainly: ${lowerFirst(wordPicture.trim())}`);
    }
    if (contextSentences.length > 0) {
      paragraphs.push(contextSentences.join(' '));
    }

    if (request.trim()) {
      paragraphs.push(`I would be grateful for your help with ${lowerFirst(request.trim())}`);
    }

    const closingSentences: string[] = [];
    if (expectedOutcome.trim()) {
      closingSentences.push(`Your support would help ${lowerFirst(expectedOutcome.trim())}`);
    }
    if (benefitToPartner.trim()) {
      closingSentences.push(`I also hope this could be worthwhile for you: ${lowerFirst(benefitToPartner.trim())}`);
    }
    if (closingSentences.length > 0) {
      paragraphs.push(closingSentences.join(' '));
    }

    paragraphs.push('Would you be open to a short conversation about this? No pressure either way, and thank you for considering it.');

    setMessageBody(`Dear ${recipient},\n\n${paragraphs.join('\n\n')}\n\nBest regards`);
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
        extraActions={<Button type="button" variant="secondary" onClick={handleGenerateMessage}>Generate message</Button>}
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
          entityLabel="messages"
        />
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      <Card>
        <CardContent>
        <DataTable
          rows={crud.items}
          columns={columns}
          emptyMessage={searchTerm ? 'No messages match this search.' : 'No messages yet.'}
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
    </PageSection>
  );
}

function optionalNumber(value: string) {
  return value ? Number(value) : undefined;
}

// Lowercases the first letter so a user's field (often written as its own
// sentence) reads naturally mid-sentence in the generated body. Leaves the
// standalone pronoun "I" capitalized, since lowering it to "i" reads wrong.
function lowerFirst(value: string) {
  if (/^I(\s|'|$)/.test(value)) {
    return value;
  }
  return value.charAt(0).toLowerCase() + value.slice(1);
}
