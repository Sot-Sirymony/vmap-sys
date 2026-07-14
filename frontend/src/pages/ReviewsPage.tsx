import { FormEvent, useEffect, useState } from 'react';
import { listDreams } from '../api/dreamApi';
import { archiveReview, permanentlyDeleteReview, createReview, listReviews, restoreReview, updateReview } from '../api/reviewApi';
import { listVisionAreas } from '../api/visionAreaApi';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
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
import type { Dream, Review, ReviewRequest, ReviewType, VisionArea } from '../types/vision';
import { reviewTypeLabels } from '../utils/enumLabels';
import { matchesSearch } from '../utils/search';
import { PageSection } from './PageSection';

// FR-16.3: guided questions per review type, shown alongside the form.
const REVIEW_GUIDES: Record<ReviewType, string[]> = {
  DAILY: [
    'What moved forward today?',
    'What is delayed or blocked?',
    'What is the single next action for tomorrow?',
  ],
  WEEKLY: [
    'Which goals moved this week?',
    'Which tasks are overdue or blocked?',
    'Which partner needs a follow-up?',
    'What are the top 3 priorities for next week?',
  ],
  MONTHLY: [
    'Which dreams still matter?',
    'Which dreams should be revised up, paused, or removed?',
    'What new dreams, partners, or resources are missing?',
  ],
  QUARTERLY: [
    'Are these still the right vision areas?',
    'What worked and what failed this quarter?',
    'What changes for next quarter?',
  ],
};

// FR-16.1: original wording; answered all-or-none (enforced here and server-side).
const DILIGENCE_QUESTIONS = [
  { key: 'diligenceClearVision', label: 'My vision for this area is still clear and specific' },
  { key: 'diligenceWorkedPlan', label: 'I worked from a plan instead of just reacting' },
  { key: 'diligenceUsedLeverage', label: 'I used tools or partners where they beat solo effort' },
  { key: 'diligencePriorityFirst', label: 'The highest-priority work got my best hours' },
  { key: 'diligenceSmarterRoute', label: 'Nothing I am doing the hard way has an obvious smarter route' },
] as const;

type DiligenceKey = (typeof DILIGENCE_QUESTIONS)[number]['key'];

const EMPTY_DILIGENCE: Record<DiligenceKey, boolean | null> = {
  diligenceClearVision: null,
  diligenceWorkedPlan: null,
  diligenceUsedLeverage: null,
  diligencePriorityFirst: null,
  diligenceSmarterRoute: null,
};

export function ReviewsPage() {
  const { token } = useAuth();
  const crud = useCrudEntity<Review, ReviewRequest>({
    token,
    entityLabel: 'reviews',
    list: listReviews,
    create: createReview,
    update: updateReview,
    archive: archiveReview,
    permanentlyDelete: permanentlyDeleteReview,
    restore: restoreReview,
  });
  const [visionAreas, setVisionAreas] = useState<VisionArea[]>([]);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [reviewType, setReviewType] = useState<ReviewType>('WEEKLY');
  const [reviewDate, setReviewDate] = useState(new Date().toISOString().slice(0, 10));
  const [relatedVisionAreaId, setRelatedVisionAreaId] = useState('');
  const [relatedDreamId, setRelatedDreamId] = useState('');
  const [summary, setSummary] = useState('');
  const [completedTasks, setCompletedTasks] = useState('');
  const [blockedTasks, setBlockedTasks] = useState('');
  const [lessonsLearned, setLessonsLearned] = useState('');
  const [nextActions, setNextActions] = useState('');
  const [diligence, setDiligence] = useState<Record<DiligenceKey, boolean | null>>(EMPTY_DILIGENCE);
  const [diligenceNote, setDiligenceNote] = useState('');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    if (!token) {
      return;
    }
    void crud.reload();
    void Promise.all([listVisionAreas(token), listDreams(token)]).then(([areaData, dreamData]) => {
      setVisionAreas(areaData);
      setDreams(dreamData);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  const checklistApplies = reviewType === 'WEEKLY' || reviewType === 'MONTHLY';

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const answers = DILIGENCE_QUESTIONS.map((question) => diligence[question.key]);
    const answeredCount = answers.filter((answer) => answer !== null).length;
    if (checklistApplies && answeredCount > 0 && answeredCount < answers.length) {
      crud.setError('Answer every diligence question, or skip the whole checklist.');
      return false;
    }
    const includeChecklist = checklistApplies && answeredCount === answers.length;
    const success = await crud.save({
      reviewType,
      reviewDate,
      relatedVisionAreaId: optionalNumber(relatedVisionAreaId),
      relatedDreamId: optionalNumber(relatedDreamId),
      summary,
      completedTasks,
      blockedTasks,
      lessonsLearned,
      nextActions,
      diligenceClearVision: includeChecklist ? diligence.diligenceClearVision : undefined,
      diligenceWorkedPlan: includeChecklist ? diligence.diligenceWorkedPlan : undefined,
      diligenceUsedLeverage: includeChecklist ? diligence.diligenceUsedLeverage : undefined,
      diligencePriorityFirst: includeChecklist ? diligence.diligencePriorityFirst : undefined,
      diligenceSmarterRoute: includeChecklist ? diligence.diligenceSmarterRoute : undefined,
      diligenceNote: includeChecklist ? diligenceNote || undefined : undefined,
    });
    if (success) {
      setSummary('');
      setCompletedTasks('');
      setBlockedTasks('');
      setLessonsLearned('');
      setNextActions('');
      setDiligence(EMPTY_DILIGENCE);
      setDiligenceNote('');
    }
    return success;
  }

  function startEdit(review: Review) {
    crud.startEdit(review.id);
    setReviewType(review.reviewType);
    setReviewDate(review.reviewDate);
    setRelatedVisionAreaId(review.relatedVisionAreaId ? String(review.relatedVisionAreaId) : '');
    setRelatedDreamId(review.relatedDreamId ? String(review.relatedDreamId) : '');
    setSummary(review.summary ?? '');
    setCompletedTasks(review.completedTasks ?? '');
    setBlockedTasks(review.blockedTasks ?? '');
    setLessonsLearned(review.lessonsLearned ?? '');
    setNextActions(review.nextActions ?? '');
    setDiligence({
      diligenceClearVision: review.diligenceClearVision ?? null,
      diligenceWorkedPlan: review.diligenceWorkedPlan ?? null,
      diligenceUsedLeverage: review.diligenceUsedLeverage ?? null,
      diligencePriorityFirst: review.diligencePriorityFirst ?? null,
      diligenceSmarterRoute: review.diligenceSmarterRoute ?? null,
    });
    setDiligenceNote(review.diligenceNote ?? '');
  }

  function cancelEdit() {
    crud.cancelEdit();
    setReviewType('WEEKLY');
    setReviewDate(new Date().toISOString().slice(0, 10));
    setRelatedVisionAreaId('');
    setRelatedDreamId('');
    setSummary('');
    setCompletedTasks('');
    setBlockedTasks('');
    setLessonsLearned('');
    setNextActions('');
    setDiligence(EMPTY_DILIGENCE);
    setDiligenceNote('');
  }

  const filteredReviews = crud.items.filter((review) =>
    matchesSearch(
      searchTerm,
      reviewTypeLabels[review.reviewType],
      review.reviewDate,
      review.summary,
      review.completedTasks,
      review.delayedTasks,
      review.blockedTasks,
      review.lessonsLearned,
      review.nextActions,
    ),
  );

  const columns: DataTableColumn<Review>[] = [
    {
      key: 'reviewType',
      label: 'Type',
      sortValue: (review) => reviewTypeLabels[review.reviewType],
      render: (review) => <StatusBadge status={review.reviewType} />,
    },
    { key: 'reviewDate', label: 'Date', sortValue: (review) => review.reviewDate, render: (review) => review.reviewDate },
    {
      key: 'summary',
      label: 'Summary',
      sortValue: (review) => review.summary,
      render: (review) => review.summary || '-',
    },
    {
      key: 'diligence',
      label: 'Diligence',
      sortValue: (review) => review.diligenceClearVision != null,
      render: (review) => (review.diligenceClearVision != null ? 'Done' : '-'),
    },
    {
      key: 'status',
      label: 'Status',
      sortValue: (review) => review.archived,
      render: (review) => <StatusBadge status={review.archived ? 'ARCHIVED' : 'ACTIVE'} />,
    },
    {
      key: 'actions',
      label: 'Action',
      className: 'row-actions',
      render: (review) => (
        <RowActionsMenu
          onEdit={() => startEdit(review)}
          onArchive={() => void crud.archive(review.id)}
          onRestore={() => void crud.restore(review.id)}
          onDeletePermanently={() => void crud.permanentlyDelete(review.id)}
          archived={review.archived}
          label="Review actions"
        />
      ),
    },
  ];

  const formFields = (
    <>
      <label>
        Type
        <FormControl fullWidth size="small">
          <Select value={reviewType} onChange={(event) => setReviewType(event.target.value as ReviewType)}>
            {(['DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY'] as const).map((value) => (
              <MenuItem value={value} key={value}>{reviewTypeLabels[value]}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </label>
      <label>
        Date
        <Input type="date" value={reviewDate} onChange={(event) => setReviewDate(event.target.value)} required />
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
      <div className="field-full coaching-panel">
        <strong>{reviewTypeLabels[reviewType]} review questions</strong>
        <ul>
          {REVIEW_GUIDES[reviewType].map((question) => (
            <li key={question}><span aria-hidden="true">-</span> {question}</li>
          ))}
        </ul>
      </div>
      {checklistApplies && (
        <div className="field-full diligence-checklist">
          <strong>Diligence checkup</strong>
          <p>Answer all five, or leave the whole checkup empty to skip it.</p>
          {DILIGENCE_QUESTIONS.map((question) => (
            <div className="diligence-row" key={question.key}>
              <span>{question.label}</span>
              <ToggleButtonGroup
                size="small"
                exclusive
                value={diligence[question.key] === null ? null : diligence[question.key] ? 'yes' : 'no'}
                onChange={(_event, value) => setDiligence((current) => ({
                  ...current,
                  [question.key]: value === null ? null : value === 'yes',
                }))}
                aria-label={question.label}
              >
                <ToggleButton value="yes">Yes</ToggleButton>
                <ToggleButton value="no">No</ToggleButton>
              </ToggleButtonGroup>
            </div>
          ))}
          <label>
            Checkup note
            <Textarea value={diligenceNote} onChange={(event) => setDiligenceNote(event.target.value)} />
          </label>
        </div>
      )}
      <label className="field-full">
        Summary
        <Textarea value={summary} onChange={(event) => setSummary(event.target.value)} />
      </label>
      <label className="field-full">
        Completed Tasks
        <Textarea value={completedTasks} onChange={(event) => setCompletedTasks(event.target.value)} />
      </label>
      <label className="field-full">
        Blocked Tasks
        <Textarea value={blockedTasks} onChange={(event) => setBlockedTasks(event.target.value)} />
      </label>
      <label className="field-full">
        Lessons Learned
        <Textarea value={lessonsLearned} onChange={(event) => setLessonsLearned(event.target.value)} />
      </label>
      <label className="field-full">
        Next Actions
        <Textarea value={nextActions} onChange={(event) => setNextActions(event.target.value)} />
      </label>
    </>
  );

  return (
    <PageSection title="Reviews" subtitle="Record daily, weekly, monthly, and quarterly reflections.">
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel="Create review"
        editTitle="Edit Review"
        saving={crud.saving}
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {crud.loading && <Loading />}
      {crud.error && <ErrorMessage message={crud.error} />}
      <Card className="filter-bar flex-row">
        <SearchBar value={searchTerm} onChange={setSearchTerm} entityLabel="reviews" />
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </Card>
      <Card>
        <CardContent>
        <DataTable
          rows={filteredReviews}
          columns={columns}
          emptyMessage={searchTerm ? 'No reviews match this search.' : 'No reviews yet.'}
          pageResetKey={searchTerm}
          defaultSortKey="reviewDate"
          defaultSortDirection="desc"
          rowClassName={(review) => (review.archived ? 'row-archived' : '')}
          selection={{
            selectedIds,
            onChange: setSelectedIds,
            rowLabel: (review) => `${reviewTypeLabels[review.reviewType]} review ${review.reviewDate}`,
            actions: (
              <BulkArchiveAction
                selectedIds={selectedIds}
                entityLabel="review(s)"
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
