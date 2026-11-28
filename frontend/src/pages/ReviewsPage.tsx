import { Link } from 'react-router';
import { FormEvent, useEffect, useRef, useState } from 'react';
import Alert from '@mui/material/Alert';
import { listDreams } from '../api/dreamApi';
import { archiveReview, permanentlyDeleteReview, createReview, listDueReviews, listReviews, restoreReview, updateReview } from '../api/reviewApi';
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
import { FilterPanel } from '../components/common/FilterPanel';
import { FilterSelect, optionsFromEntities, optionsFromLabels } from '../components/common/FilterSelect';
import { Input } from '../components/common/Input';
import { Loading } from '../components/common/Loading';
import { RowActionsMenu } from '../components/common/RowActionsMenu';
import { SearchBar } from '../components/common/SearchBar';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { StatusBadge } from '../components/common/StatusBadge';
import { Textarea } from '../components/common/Textarea';
import { useAuth } from '../context/AuthContext';
import { useCrudEntity } from '../hooks/useCrudEntity';
import type { DueReview, Dream, Review, ReviewRequest, ReviewType, VisionArea } from '../types/vision';
import { reviewTypeLabels } from '../utils/enumLabels';
import { matchesSearch } from '../utils/search';
import { PageSection } from './PageSection';
import { currentLanguage, tr } from '../i18n/translate';
import { draftWeeklyReview } from '../api/aiDailyApi';
import { Button } from '../components/common/Button';
import { WhenAiOn } from '../components/ai/WhenAiOn';
import { letterRankFilterOptions } from '../utils/sortRank';
import { saveInnerBlockerCheckins } from '../api/innerBlockerApi';
import { WeeklyInnerBlockerCheckins, type CheckinAnswers } from '../components/inner-blockers/WeeklyInnerBlockerCheckins';

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
    // V27 (BRD 27 §2.1): the monthly pointer to a Streamline review.
    'Which goal or step should you streamline this month?',
  ],
  QUARTERLY: [
    'Are these still the right vision areas?',
    'What worked and what failed this quarter?',
    'What changes for next quarter?',
  ],
};

// FR-16.1 / FR-53: original wording; answered all-or-none (enforced here and
// server-side). FR-53 widened the checklist from five checks to ten,
// covering execution quality alongside the original planning-quality ones.
const DILIGENCE_QUESTIONS = [
  { key: 'diligenceClearVision', label: tr('My vision for this area is still clear and specific') },
  { key: 'diligenceWorkedPlan', label: tr('I worked from a plan instead of just reacting') },
  { key: 'diligenceUsedLeverage', label: tr('I used tools or partners where they beat solo effort') },
  { key: 'diligencePriorityFirst', label: tr('The highest-priority work got my best hours') },
  { key: 'diligenceSmarterRoute', label: tr('Nothing I am doing the hard way has an obvious smarter route') },
  { key: 'diligenceRightlyPlanned', label: tr('I planned this properly before starting, with input where I needed it') },
  { key: 'diligenceRightlyPerformed', label: tr('I did the work to a standard I would stand behind') },
  { key: 'diligenceExpeditious', label: tr('I moved on this promptly rather than letting it sit') },
  { key: 'diligenceEfficient', label: tr('I used my time and tools well, without waste') },
  { key: 'diligenceQualityOutcome', label: 'The result is actually solid, not just finished' },
] as const;

type DiligenceKey = (typeof DILIGENCE_QUESTIONS)[number]['key'];

// datetime-local input value format, seconds included: "YYYY-MM-DDTHH:mm:ss".
// Built from local getters (not toISOString, which is UTC) so the picker
// shows the user's own wall-clock time.
function nowForDateTimeInput(): string {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
}

const EMPTY_DILIGENCE: Record<DiligenceKey, boolean | null> = {
  diligenceClearVision: null,
  diligenceWorkedPlan: null,
  diligenceUsedLeverage: null,
  diligencePriorityFirst: null,
  diligenceSmarterRoute: null,
  diligenceRightlyPlanned: null,
  diligenceRightlyPerformed: null,
  diligenceExpeditious: null,
  diligenceEfficient: null,
  diligenceQualityOutcome: null,
};

export function ReviewsPage() {
  const { token } = useAuth();
  // V34 (D-34-10): the id of the review just saved, so its inner blocker answers can be saved with it.
  const savedReviewId = useRef<number | null>(null);
  const [checkins, setCheckins] = useState<CheckinAnswers>({});
  const crud = useCrudEntity<Review, ReviewRequest>({
    token,
    entityLabel: 'reviews',
    list: listReviews,
    create: async (authToken, request) => {
      const saved = await createReview(authToken, request);
      savedReviewId.current = saved.id;
      return saved;
    },
    update: async (authToken, id, request) => {
      const saved = await updateReview(authToken, id, request);
      savedReviewId.current = saved.id;
      return saved;
    },
    archive: archiveReview,
    permanentlyDelete: permanentlyDeleteReview,
    restore: restoreReview,
    undoableArchive: true,
  });
  const [visionAreas, setVisionAreas] = useState<VisionArea[]>([]);
  // V31 v1 step 3 (DC-FR-50): reviews a dream's rhythm makes due, and the create form opened from one of them.
  const [dueReviews, setDueReviews] = useState<DueReview[]>([]);
  const [creating, setCreating] = useState(false);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [reviewType, setReviewType] = useState<ReviewType>('WEEKLY');
  const [reviewDate, setReviewDate] = useState(nowForDateTimeInput());
  const [letterRank, setLetterRank] = useState('');
  const [relatedVisionAreaId, setRelatedVisionAreaId] = useState('');
  const [relatedDreamId, setRelatedDreamId] = useState('');
  const [summary, setSummary] = useState('');
  const [completedTasks, setCompletedTasks] = useState('');
  const [blockedTasks, setBlockedTasks] = useState('');
  const [lessonsLearned, setLessonsLearned] = useState('');
  const [nextActions, setNextActions] = useState('');
  const [diligence, setDiligence] = useState<Record<DiligenceKey, boolean | null>>(EMPTY_DILIGENCE);
  const [diligenceNote, setDiligenceNote] = useState('');
  // FR-59.3: transient — never stored on the Review, so it always starts
  // blank (including on edit) and clears itself right after a successful save.
  const [gratitudeNote, setGratitudeNote] = useState('');
  // AI-FR-14: the draft fills the form; the participant edits and saves it.
  const [aiDrafting, setAiDrafting] = useState(false);
  const [aiDraftError, setAiDraftError] = useState('');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [searchTerm, setSearchTerm] = useState('');
  const [filterReviewType, setFilterReviewType] = useState('');
  const [filterLetterRank, setFilterLetterRank] = useState('');
  const [filterVisionAreaId, setFilterVisionAreaId] = useState('');
  const [filterDreamId, setFilterDreamId] = useState('');

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

  async function handleAiDraft() {
    if (!token) {
      return;
    }
    setAiDrafting(true);
    setAiDraftError('');
    try {
      const draft = await draftWeeklyReview(token, currentLanguage());
      const delayed = draft.delayedTasks ? `\n\n${tr('Delayed')}:\n${draft.delayedTasks}` : '';
      setSummary(`${draft.summary ?? ''}${delayed}`.trim());
      setCompletedTasks(draft.completedTasks ?? '');
      setBlockedTasks(draft.blockedTasks ?? '');
      setLessonsLearned(draft.lessonsLearned ?? '');
      setNextActions(draft.nextActions ?? '');
    } catch (draftError) {
      setAiDraftError(draftError instanceof Error ? tr(draftError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setAiDrafting(false);
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const answers = DILIGENCE_QUESTIONS.map((question) => diligence[question.key]);
    const answeredCount = answers.filter((answer) => answer !== null).length;
    if (checklistApplies && answeredCount > 0 && answeredCount < answers.length) {
      crud.setError(tr('Answer every diligence question, or skip the whole checklist.'));
      return false;
    }
    const includeChecklist = checklistApplies && answeredCount === answers.length;
    const editingWeekly = crud.editingId !== null;
    savedReviewId.current = null;
    const success = await crud.save({
      reviewType,
      reviewDate,
      letterRank: letterRank || undefined,
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
      diligenceRightlyPlanned: includeChecklist ? diligence.diligenceRightlyPlanned : undefined,
      diligenceRightlyPerformed: includeChecklist ? diligence.diligenceRightlyPerformed : undefined,
      diligenceExpeditious: includeChecklist ? diligence.diligenceExpeditious : undefined,
      diligenceEfficient: includeChecklist ? diligence.diligenceEfficient : undefined,
      diligenceQualityOutcome: includeChecklist ? diligence.diligenceQualityOutcome : undefined,
      diligenceNote: includeChecklist ? diligenceNote || undefined : undefined,
      gratitudeNote: gratitudeNote.trim() || undefined,
    });
    if (success && reviewType === 'WEEKLY' && token && savedReviewId.current !== null
        && (Object.keys(checkins).length > 0 || editingWeekly)) {
      try {
        await saveInnerBlockerCheckins(token, savedReviewId.current,
          Object.entries(checkins).map(([innerBlockerId, answer]) => ({ innerBlockerId: Number(innerBlockerId), answer })));
      } catch (checkinError) {
        crud.setError(checkinError instanceof Error ? tr(checkinError.message) : tr('The review was saved, but the inner blocker answers were not.'));
      }
    }
    if (success) {
      setCheckins({});
      setSummary('');
      setCompletedTasks('');
      setBlockedTasks('');
      setLessonsLearned('');
      setNextActions('');
      setDiligence(EMPTY_DILIGENCE);
      setDiligenceNote('');
      setGratitudeNote('');
    }
    return success;
  }

  function startEdit(review: Review) {
    crud.startEdit(review.id);
    setReviewType(review.reviewType);
    setReviewDate(review.reviewDate);
    setLetterRank(review.letterRank ?? '');
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
      diligenceRightlyPlanned: review.diligenceRightlyPlanned ?? null,
      diligenceRightlyPerformed: review.diligenceRightlyPerformed ?? null,
      diligenceExpeditious: review.diligenceExpeditious ?? null,
      diligenceEfficient: review.diligenceEfficient ?? null,
      diligenceQualityOutcome: review.diligenceQualityOutcome ?? null,
    });
    setDiligenceNote(review.diligenceNote ?? '');
    setGratitudeNote('');
  }

  function cancelEdit() {
    crud.cancelEdit();
    setReviewType('WEEKLY');
    setReviewDate(nowForDateTimeInput());
    setLetterRank('');
    setRelatedVisionAreaId('');
    setRelatedDreamId('');
    setSummary('');
    setCompletedTasks('');
    setBlockedTasks('');
    setLessonsLearned('');
    setNextActions('');
    setDiligence(EMPTY_DILIGENCE);
    setDiligenceNote('');
    setGratitudeNote('');
    setCheckins({});
  }

  useEffect(() => {
    if (!token) {
      return undefined;
    }
    let cancelled = false;
    listDueReviews(token).then((due) => !cancelled && setDueReviews(due ?? [])).catch(() => !cancelled && setDueReviews([]));
    return () => {
      cancelled = true;
    };
  }, [token, crud.items]);

  /** DC-FR-50: open the create form for a due review, with its type and dream filled in. */
  function startDueReview(review: DueReview) {
    setReviewType(review.reviewType);
    setReviewDate(nowForDateTimeInput());
    setRelatedDreamId(String(review.dreamId));
    const dream = dreams.find((item) => item.id === review.dreamId);
    setRelatedVisionAreaId(dream ? String(dream.visionAreaId) : '');
    setCreating(true);
  }

  const filteredReviews = crud.items.filter((review) => {
    if (filterReviewType && review.reviewType !== filterReviewType) {
      return false;
    }
    if (filterLetterRank && review.letterRank !== filterLetterRank) {
      return false;
    }
    if (filterVisionAreaId && String(review.relatedVisionAreaId ?? '') !== filterVisionAreaId) {
      return false;
    }
    if (filterDreamId && String(review.relatedDreamId ?? '') !== filterDreamId) {
      return false;
    }
    return matchesSearch(
      searchTerm,
      reviewTypeLabels[review.reviewType],
      review.reviewDate,
      review.summary,
      review.completedTasks,
      review.delayedTasks,
      review.blockedTasks,
      review.lessonsLearned,
      review.nextActions,
    );
  });

  const hasFilters = Boolean(searchTerm || filterReviewType || filterLetterRank || filterVisionAreaId || filterDreamId);

  // FR-57.4-style: only letters actually in use are offered, so the dropdown
  // doesn't show 26 mostly-empty options.
  const letterRankOptions = letterRankFilterOptions(crud.items);

  const columns: DataTableColumn<Review>[] = [
    {
      key: 'reviewType',
      label: tr('Type'),
      sortValue: (review) => reviewTypeLabels[review.reviewType],
      render: (review) => <StatusBadge status={review.reviewType} />,
    },
    { key: 'reviewDate', label: tr('Date'), sortValue: (review) => review.reviewDate, render: (review) => review.reviewDate },
    {
      key: 'letterRank',
      label: tr('Rank'),
      sortValue: (review) => review.letterRank ?? '',
      render: (review) => review.letterRank ?? '—',
    },
    {
      key: 'summary',
      label: tr('Summary'),
      sortValue: (review) => review.summary,
      render: (review) => review.summary || '-',
    },
    {
      key: 'diligence',
      label: tr('Diligence'),
      sortValue: (review) => review.diligenceClearVision != null,
      render: (review) => {
        if (review.diligenceClearVision == null) {
          return '-';
        }
        return review.diligenceScorePercent != null ? tr('Done ({percent}%)', { percent: review.diligenceScorePercent }) : tr('Done');
      },
    },
    {
      key: 'status',
      label: tr('Status'),
      sortValue: (review) => review.archived,
      render: (review) => <StatusBadge status={review.archived ? 'ARCHIVED' : 'ACTIVE'} />,
    },
    {
      key: 'actions',
      label: tr('Action'),
      className: 'row-actions',
      render: (review) => (
        <RowActionsMenu
          onEdit={() => startEdit(review)}
          onArchive={() => void crud.archive(review.id)}
          onRestore={() => void crud.restore(review.id)}
          onDeletePermanently={() => void crud.permanentlyDelete(review.id)}
          archived={review.archived}
          label={tr('Review actions')}
        />
      ),
    },
  ];

  const formDreams = relatedVisionAreaId
    ? dreams.filter((dream) => String(dream.visionAreaId) === relatedVisionAreaId)
    : dreams;

  // FR-53: a live count, mirroring the "N of 3" hint FR-32 gave the
  // creative-alternatives field — feedback while filling the checklist in,
  // not just a rejection after submit.
  const diligenceAnsweredCount = DILIGENCE_QUESTIONS.filter((question) => diligence[question.key] !== null).length;

  function handleVisionAreaChange(nextAreaId: string) {
    setRelatedVisionAreaId(nextAreaId);
    if (nextAreaId && relatedDreamId) {
      const selectedDream = dreams.find((dream) => String(dream.id) === relatedDreamId);
      if (selectedDream && String(selectedDream.visionAreaId) !== nextAreaId) {
        setRelatedDreamId('');
      }
    }
  }

  const formFields = (
    <>
      <label>
        {tr('Type')}
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Type" }} value={reviewType} onChange={(event) => setReviewType(event.target.value as ReviewType)}>
            {(['DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY'] as const).map((value) => (
              <MenuItem value={value} key={value}>{reviewTypeLabels[value]}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </label>
      {reviewType === 'WEEKLY' && (
        // AI-NFR-02: no AI controls while AI is off.
        <WhenAiOn token={token}>
          <div className="field-full">
            <Button type="button" variant="secondary" disabled={aiDrafting} onClick={() => void handleAiDraft()}>
              {aiDrafting ? tr('The Dream Coach is thinking…') : tr('Draft with AI')}
            </Button>
            <span className="field-hint">{tr('Fills the form from your last seven days. Check and edit it before you save.')}</span>
            {aiDraftError && <Alert severity="error" sx={{ mt: 1 }}>{aiDraftError}</Alert>}
          </div>
        </WhenAiOn>
      )}
      <label>
        {tr('Date')}
        <Input type="datetime-local" step={1} value={reviewDate} onChange={(event) => setReviewDate(event.target.value)} required />
      </label>
      <label>
        {tr('Rank')}
        <Input
          value={letterRank}
          onChange={(event) => setLetterRank(event.target.value.slice(-1).toUpperCase().replace(/[^A-Z]/, ''))}
          placeholder={tr('e.g. A')}
          maxLength={1}
        />
        <span className="field-hint">
          Optional. A, B, C… — assigned after listing everything. Ties are fine; nothing enforces a unique order.
        </span>
      </label>
      <label>
        {tr('Vision Area')}
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Vision Area" }} displayEmpty value={relatedVisionAreaId} onChange={(event) => handleVisionAreaChange(event.target.value)}>
            <MenuItem value="">{tr('None')}</MenuItem>
            {visionAreas.map((area) => <MenuItem value={String(area.id)} key={area.id}>{area.name}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        {tr('Dream')}
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Dream" }} displayEmpty value={relatedDreamId} onChange={(event) => setRelatedDreamId(event.target.value)}>
            <MenuItem value="">{tr('None')}</MenuItem>
            {formDreams.map((dream) => <MenuItem value={String(dream.id)} key={dream.id}>{dream.title}</MenuItem>)}
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
        {reviewType === 'MONTHLY' && (
          <Link to="/streamline">{tr('Start a Streamline review')}</Link>
        )}
      </div>
      {checklistApplies && (
        <div className="field-full diligence-checklist">
          <strong>{tr('Diligence checkup')}</strong>
          <p>
            {tr('Answer all ten, or leave the whole checkup empty to skip it.')}
            {diligenceAnsweredCount > 0 && diligenceAnsweredCount < DILIGENCE_QUESTIONS.length && (
              <> <span className="field-hint">({diligenceAnsweredCount} of {DILIGENCE_QUESTIONS.length} answered so far)</span></>
            )}
          </p>
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
                <ToggleButton value="yes">{tr('Yes')}</ToggleButton>
                <ToggleButton value="no">{tr('No')}</ToggleButton>
              </ToggleButtonGroup>
            </div>
          ))}
          <label>
            {tr('Checkup note')}
            <Textarea value={diligenceNote} onChange={(event) => setDiligenceNote(event.target.value)} />
          </label>
        </div>
      )}
      <label className="field-full">
        {tr('Summary')}
        <Textarea value={summary} onChange={(event) => setSummary(event.target.value)} />
      </label>
      <label className="field-full">
        {tr('Completed Tasks')}
        <Textarea value={completedTasks} onChange={(event) => setCompletedTasks(event.target.value)} />
      </label>
      <label className="field-full">
        {tr('Blocked Tasks')}
        <Textarea value={blockedTasks} onChange={(event) => setBlockedTasks(event.target.value)} />
      </label>
      <label className="field-full">
        {tr('Lessons Learned')}
        <Textarea value={lessonsLearned} onChange={(event) => setLessonsLearned(event.target.value)} />
      </label>
      <label className="field-full">
        {tr('Next Actions')}
        <Textarea value={nextActions} onChange={(event) => setNextActions(event.target.value)} />
      </label>
      <label className="field-full">
        {tr('What\'s something you\'re grateful for this period?')}
        <Textarea value={gratitudeNote} onChange={(event) => setGratitudeNote(event.target.value)} />
        <span className="field-hint">
          Optional. Answering logs it to your gratitude list; leaving it blank changes nothing about this review.
        </span>
      </label>
      {reviewType === 'WEEKLY' && token && (
        <WeeklyInnerBlockerCheckins token={token} reviewId={crud.editingId} value={checkins} onChange={setCheckins} />
      )}
    </>
  );

  return (
    <PageSection title={tr('Reviews')} subtitle={tr('Record daily, weekly, monthly, and quarterly reflections.')}>
      {dueReviews.length > 0 && (
        <Card sx={{ mb: 2 }} component="section" aria-label={tr('Reviews due')}>
          <CardContent>
            <strong>{tr('Reviews due')}</strong>
            <ul style={{ margin: '8px 0 0', paddingLeft: 20 }}>
              {dueReviews.map((review) => (
                <li key={`${review.dreamId}:${review.reviewType}`} style={{ marginBottom: 6 }}>
                  {tr('{type} review of "{dream}"', { type: reviewTypeLabels[review.reviewType], dream: review.dreamTitle })}{' '}
                  <Button variant="secondary" onClick={() => startDueReview(review)}>{tr('Start this review')}</Button>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
      <CrudModalForm
        creating={creating}
        onCreatingChange={setCreating}
        editing={crud.editingId !== null}
        createLabel={tr('Create review')}
        editTitle="Edit Review"
        saving={crud.saving}
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {crud.loading && <Loading variant="table" />}
      {crud.error && <ErrorMessage message={crud.error} onRetry={() => void crud.reload()} />}
      <FilterPanel activeCount={[searchTerm, filterReviewType, filterLetterRank, filterVisionAreaId, filterDreamId].filter(Boolean).length}>
        <SearchBar value={searchTerm} onChange={setSearchTerm} entityLabel="reviews" />
        <FilterSelect
          label={tr('Review Type')}
          value={filterReviewType}
          onChange={setFilterReviewType}
          options={optionsFromLabels(reviewTypeLabels)}
        />
        {letterRankOptions.length > 0 && (
          <FilterSelect
            label={tr('Rank')}
            value={filterLetterRank}
            onChange={setFilterLetterRank}
            options={letterRankOptions}
          />
        )}
        <FilterSelect
          label={tr('Vision Area')}
          value={filterVisionAreaId}
          onChange={setFilterVisionAreaId}
          options={optionsFromEntities(visionAreas, (area) => area.name)}
        />
        <FilterSelect
          label={tr('Dream')}
          value={filterDreamId}
          onChange={setFilterDreamId}
          options={optionsFromEntities(dreams, (dream) => dream.title)}
        />
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </FilterPanel>
      <Card>
        <CardContent>
        <DataTable
          rows={filteredReviews}
          columns={columns}
          emptyMessage={hasFilters ? tr('No reviews match these filters.') : tr('No reviews yet.')}
          pageResetKey={`${searchTerm}|${filterReviewType}|${filterLetterRank}|${filterVisionAreaId}|${filterDreamId}`}
          defaultSortKey="reviewDate"
          defaultSortDirection="desc"
          rowClassName={(review) => (review.archived ? 'row-archived' : '')}
          selection={{
            selectedIds,
            onChange: setSelectedIds,
            rowLabel: (review) => tr('{type} review {date}', { type: reviewTypeLabels[review.reviewType], date: review.reviewDate }),
            actions: (
              <BulkArchiveAction
                selectedIds={selectedIds}
                items={crud.items}
                entityLabel={tr('review(s)')}
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
    </PageSection>
  );
}

function optionalNumber(value: string) {
  return value ? Number(value) : undefined;
}
