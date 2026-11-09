import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pencil, RotateCcw, X } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import FormControl from '@mui/material/FormControl';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { getChainAssessments } from '../api/chainAssessmentApi';
import { listDreams } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import {
  archiveSelfAssessmentItem,
  createSelfAssessmentItem,
  getStrengthsProfile,
  listSelfAssessmentItems,
  removeGapCover,
  restoreSelfAssessmentItem,
  saveStrengthsProfile,
  setGapCover,
  updateSelfAssessmentItem,
} from '../api/strengthsApi';
import { getWorkStyleProfile } from '../api/workStyleProfileApi';
import { Button } from '../components/common/Button';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { Modal } from '../components/common/Modal';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { PageProblemsPanel } from '../components/diagnostics/PageProblemsPanel';
import { GapCoverDialog } from '../components/strengths/GapCoverDialog';
import { EMPTY_STRENGTH_FORM, StrengthFormValues, StrengthItemForm } from '../components/strengths/StrengthItemForm';
import { useAuth } from '../context/AuthContext';
import { useClientErrorLog } from '../hooks/useClientErrorLog';
import { useI18n } from '../i18n/I18nContext';
import { tr } from '../i18n/translate';
import type {
  ChainAssessment,
  Dream,
  GapCoverMethod,
  GapCoverRequest,
  Goal,
  SelfAssessmentItem,
  SelfAssessmentItemRequest,
  StrengthsProfile,
  StrengthsProfileChange,
  WorkStyleProfile,
} from '../types/vision';
import { gapCoverMethodLabels, relevanceLabels, strengthKindLabels } from '../utils/enumLabels';
import { StrengthSuggestion, strengthSuggestions } from '../utils/strengthSuggestions';
import { PageSection } from './PageSection';

/** What a cover created, named so the remove question is clear. */
const LINKED_RECORD: Partial<Record<GapCoverMethod, string>> = {
  PARTNER: tr('the partner it added'),
  LEARN: tr('the learning step it added'),
  DELEGATE: tr('the leverage map row it added'),
  RESOURCE: tr('the resource gap row it added'),
};

/** The top dream first: lettered dreams by letter, then the rest in their usual order. */
function byRank(dreams: Dream[]) {
  return [...dreams].sort((a, b) => {
    if (a.letterRank && b.letterRank) return a.letterRank.localeCompare(b.letterRank);
    if (a.letterRank) return -1;
    if (b.letterRank) return 1;
    return 0;
  });
}

/** The API paths this page calls, so only its own failures are logged under it. */
const PAGE_API_PATHS = ['/strengths-profiles', '/self-assessment-items', '/dreams', '/goals', '/work-style-profile', '/chain-assessments'] as const;

type PendingRemoval = { item: SelfAssessmentItem; action: 'archive' | 'uncover' };

/**
 * V8.1 FR-79 to FR-84: an honest look at what you bring and what you lack for
 * one dream. Strengths come first and there is no score (NFR-11). Each gap
 * that matters a lot gets one way to cover it; the rest are recorded only.
 */
export function StrengthsPage() {
  const { t } = useI18n();
  const { token } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [dreams, setDreams] = useState<Dream[] | null>(null);
  const [profile, setProfile] = useState<StrengthsProfile | null>(null);
  const [archived, setArchived] = useState<SelfAssessmentItem[]>([]);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [workStyle, setWorkStyle] = useState<WorkStyleProfile | null>(null);
  const [latestAssessment, setLatestAssessment] = useState<ChainAssessment | undefined>(undefined);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [notice, setNotice] = useState('');
  const [saving, setSaving] = useState(false);
  const [formValues, setFormValues] = useState<StrengthFormValues>(EMPTY_STRENGTH_FORM);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [covering, setCovering] = useState<SelfAssessmentItem | null>(null);
  const [coverError, setCoverError] = useState('');
  const [pendingRemoval, setPendingRemoval] = useState<PendingRemoval | null>(null);
  const [showArchived, setShowArchived] = useState(false);
  const { failures } = useClientErrorLog(token, 'strengths', PAGE_API_PATHS);

  const ranked = useMemo(() => (dreams ? byRank(dreams) : null), [dreams]);
  const requestedId = Number(searchParams.get('dreamId'));
  const dreamId = ranked?.some((dream) => dream.id === requestedId) ? requestedId : ranked?.[0]?.id;

  useEffect(() => {
    if (!token) {
      return;
    }
    listDreams(token)
      .then(setDreams)
      .catch((loadError: unknown) => setError(loadError instanceof Error ? loadError.message : t('Unable to load your dreams.')));
    // Suggestions are a help, not a requirement: if either fails, there are simply fewer.
    getWorkStyleProfile(token).then(setWorkStyle).catch(() => setWorkStyle(null));
    getChainAssessments(token)
      .then((summary) => setLatestAssessment(summary.assessments[summary.assessments.length - 1]))
      .catch(() => setLatestAssessment(undefined));
  }, [token]);

  const load = useCallback(async () => {
    if (!token || !dreamId) {
      return;
    }
    setError('');
    try {
      const [nextProfile, nextGoals, nextItems] = await Promise.all([
        getStrengthsProfile(token, dreamId),
        listGoals(token),
        showArchived ? listSelfAssessmentItems(token, dreamId, true) : Promise.resolve([] as SelfAssessmentItem[]),
      ]);
      setProfile(nextProfile);
      setGoals(nextGoals.filter((goal) => goal.dreamId === dreamId && !goal.archived));
      setArchived(nextItems.filter((item) => item.archived));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : t('Unable to load your strengths and gaps.'));
    }
  }, [token, dreamId, showArchived]);

  useEffect(() => {
    void load();
  }, [load]);

  const activeItems = profile ? [...profile.whatIBring, ...profile.whatILack, ...profile.recordedOnly] : [];
  const suggestions = profile
    ? strengthSuggestions(workStyle, latestAssessment, [...activeItems, ...archived].map((item) => item.description))
    : [];

  function resetForm() {
    setEditingId(null);
    setFormValues({ ...EMPTY_STRENGTH_FORM });
  }

  function startEdit(item: SelfAssessmentItem) {
    setEditingId(item.id);
    setFormValues({
      kind: item.kind,
      description: item.description,
      evidence: item.evidence,
      relevance: item.relevance,
      source: item.source,
      outsideBy: item.outsideBy ?? '',
      outsideView: item.outsideView ?? '',
      outsideAgrees: item.outsideAgrees ?? null,
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function applySuggestion(suggestion: StrengthSuggestion) {
    setEditingId(null);
    setFormValues({ ...EMPTY_STRENGTH_FORM, kind: suggestion.kind, description: suggestion.description, source: suggestion.source });
  }

  async function run(action: () => Promise<unknown>, failure: string) {
    setActionError('');
    setNotice('');
    try {
      await action();
      await load();
      return true;
    } catch (actionFailure) {
      setActionError(actionFailure instanceof Error ? actionFailure.message : failure);
      return false;
    }
  }

  async function handleSubmit(request: SelfAssessmentItemRequest) {
    if (!token) {
      return;
    }
    setSaving(true);
    const saved = await run(
      () => (editingId === null ? createSelfAssessmentItem(token, request) : updateSelfAssessmentItem(token, editingId, request)),
      t('Unable to save this item.'),
    );
    setSaving(false);
    if (saved) {
      resetForm();
    }
  }

  async function handleCover(request: GapCoverRequest) {
    if (!token || !covering) {
      return;
    }
    setSaving(true);
    setCoverError('');
    try {
      await setGapCover(token, covering.id, request);
      setCovering(null);
      await load();
    } catch (coverFailure) {
      setCoverError(coverFailure instanceof Error ? coverFailure.message : t('Unable to save how this gap is covered.'));
    } finally {
      setSaving(false);
    }
  }

  /** Asks about the linked record only when there is one (AC-10). */
  function requestRemoval(item: SelfAssessmentItem, action: PendingRemoval['action']) {
    if (item.coverMethod && LINKED_RECORD[item.coverMethod]) {
      setPendingRemoval({ item, action });
    } else {
      void finishRemoval({ item, action }, false);
    }
  }

  async function finishRemoval(removal: PendingRemoval, removeLinked: boolean) {
    if (!token) {
      return;
    }
    setPendingRemoval(null);
    let note: string | null | undefined;
    await run(async () => {
      const result = removal.action === 'archive'
        ? await archiveSelfAssessmentItem(token, removal.item.id, removeLinked)
        : await removeGapCover(token, removal.item.id, removeLinked);
      note = result.note;
    }, t('Unable to remove this.'));
    if (note) {
      setNotice(t(note));
    }
  }

  async function handleSaveProfile() {
    if (!token || !dreamId) {
      return;
    }
    setSaving(true);
    await run(() => saveStrengthsProfile(token, dreamId), t('Unable to save your profile.'));
    setSaving(false);
  }

  function itemRow(item: SelfAssessmentItem, showCover: boolean) {
    return (
      <Box key={item.id} sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>{item.description}</Typography>
          <Typography variant="body2" color="text.secondary">{t('Evidence: {text}', { text: item.evidence })}</Typography>
          {item.outsideView && (
            <Typography variant="body2" color="text.secondary">
              {item.outsideBy
                ? t('{name} said: {text}', { name: item.outsideBy, text: item.outsideView })
                : t('Someone said: {text}', { text: item.outsideView })}
            </Typography>
          )}
          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mt: 0.5 }}>
            {!showCover && <Chip size="small" label={t(strengthKindLabels[item.kind])} />}
            {!showCover && <Chip size="small" variant="outlined" label={t(relevanceLabels[item.relevance])} />}
            {item.outsideMismatch && <Chip size="small" color="warning" label={t('Outside view differs')} />}
            {showCover && item.coverMethod && (
              <Chip
                size="small"
                color="success"
                label={item.coverNote
                  ? t('{method}: {note}', { method: t(gapCoverMethodLabels[item.coverMethod]), note: item.coverNote })
                  : t(gapCoverMethodLabels[item.coverMethod])}
                onDelete={() => requestRemoval(item, 'uncover')}
                deleteIcon={<X size={14} aria-label={t('Change how {name} is covered', { name: item.description })} />}
              />
            )}
          </Box>
          {showCover && !item.coverMethod && (
            <Box sx={{ mt: 1 }}>
              <Button variant="secondary" onClick={() => { setCoverError(''); setCovering(item); }}>{t('Choose how to cover it')}</Button>
            </Box>
          )}
        </Box>
        <IconButton size="small" aria-label={t('Edit {name}', { name: item.description })} onClick={() => startEdit(item)}>
          <Pencil size={14} />
        </IconButton>
        <IconButton size="small" aria-label={t('Remove {name}', { name: item.description })} onClick={() => requestRemoval(item, 'archive')}>
          <X size={14} />
        </IconButton>
      </Box>
    );
  }

  function changeText(change: StrengthsProfileChange) {
    if (change.type === 'ADDED') return t('Added: {name}', { name: change.description });
    if (change.type === 'REMOVED') return t('No longer listed: {name}', { name: change.description });
    const parts: string[] = [];
    if (change.relevanceBefore !== change.relevanceAfter && change.relevanceBefore && change.relevanceAfter) {
      parts.push(t('{before} → {after}', { before: t(relevanceLabels[change.relevanceBefore]), after: t(relevanceLabels[change.relevanceAfter]) }));
    }
    if (change.coverBefore !== change.coverAfter) {
      const before = change.coverBefore ? t(gapCoverMethodLabels[change.coverBefore]) : t('Not covered');
      const after = change.coverAfter ? t(gapCoverMethodLabels[change.coverAfter]) : t('Not covered');
      parts.push(t('{before} → {after}', { before, after }));
    }
    return t('Changed: {name} ({detail})', { name: change.description, detail: parts.join(', ') });
  }

  return (
    <PageSection
      title={t('Strengths and Gaps')}
      subtitle={t('An honest look at what you bring and what you lack for one dream')}
      actions={dreamId ? <Button variant="secondary" onClick={() => navigate(`/plan?dreamId=${dreamId}`)}>{t('Back to the plan')}</Button> : undefined}
    >
      {error && <ErrorMessage message={error} onRetry={() => void load()} />}
      {!dreams && !error && <Loading />}
      {dreams && dreams.length === 0 && (
        <EmptyState action={<Button onClick={() => navigate('/dreams?create=dream')}>{t('Create your first dream')}</Button>}>
          {t('Strengths and gaps are judged against a dream.')}
        </EmptyState>
      )}
      {ranked && ranked.length > 0 && dreamId && (
        <Stack spacing={2}>
          <Alert severity="info">
            {t('You are not trying to fix every weakness. Give each item a real example, and act only on what matters a lot to this dream.')}
          </Alert>
          <label>
            {t('Dream')}
            <FormControl fullWidth size="small">
              <Select
                SelectDisplayProps={{ 'aria-label': t('Dream') }}
                value={String(dreamId)}
                onChange={(event) => { resetForm(); setSearchParams({ dreamId: event.target.value }); }}
              >
                {ranked.map((dream) => (
                  <MenuItem value={String(dream.id)} key={dream.id}>{dream.letterRank ? `${dream.letterRank} · ${dream.title}` : dream.title}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </label>
          {actionError && <Alert severity="error">{t(actionError)}</Alert>}
          {notice && <Alert severity="info" onClose={() => setNotice('')}>{notice}</Alert>}

          {!profile && <Loading />}
          {profile && profile.dreamId === dreamId && (
            <>
              {profile.retakeSuggested && (
                <Alert severity="info">{t('It has been 12 weeks or more since you saved this profile. Look at it again and save a retake.')}</Alert>
              )}

              <Card>
                <CardHeader
                  title={t(editingId === null ? tr('Add a strength or gap') : tr('Edit this item'))}
                  subheader={t('Strengths first. A gap is something to cover, not a failing.')}
                />
                <CardContent>
                  <StrengthItemForm
                    dreamId={dreamId}
                    initial={formValues}
                    editing={editingId !== null}
                    saving={saving}
                    onSubmit={(request) => void handleSubmit(request)}
                    onCancel={resetForm}
                  />
                </CardContent>
              </Card>

              {suggestions.length > 0 && (
                <Card>
                  <CardHeader title={t('Suggestions')} subheader={t('From your Work Style and your latest assessment. Nothing is saved until you add your own evidence.')} />
                  <CardContent>
                    <Stack spacing={1}>
                      {suggestions.map((suggestion) => (
                        <Box key={suggestion.description} sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                          <Chip size="small" label={t(strengthKindLabels[suggestion.kind])} />
                          <Typography variant="body2" sx={{ flex: 1 }}>{suggestion.description}</Typography>
                          <Button variant="secondary" onClick={() => applySuggestion(suggestion)}>{t('Use this')}</Button>
                        </Box>
                      ))}
                    </Stack>
                  </CardContent>
                </Card>
              )}

              <Card>
                <CardHeader
                  title={t('Your profile')}
                  subheader={profile.complete
                    ? t('Complete. Every gap that matters a lot has a way to cover it.')
                    : profile.uncoveredGaps > 0
                      ? t('{count} gap(s) that matter a lot still need a way to cover them.', { count: profile.uncoveredGaps })
                      : t('Add at least one strength and one gap.')}
                  action={<ShowArchivedToggle checked={showArchived} onToggle={() => setShowArchived((current) => !current)} />}
                />
                <CardContent>
                  <Stack spacing={2.5}>
                    <Box>
                      <Typography variant="subtitle2" sx={{ mb: 1 }}>{t('What I bring')}</Typography>
                      <Stack spacing={1.5}>
                        {profile.whatIBring.length === 0
                          ? <EmptyState>{t('No strengths that matter a lot yet.')}</EmptyState>
                          : profile.whatIBring.map((item) => itemRow(item, false))}
                      </Stack>
                    </Box>
                    <Box>
                      <Typography variant="subtitle2" sx={{ mb: 1 }}>{t('What I lack, and how each gap is covered')}</Typography>
                      <Stack spacing={1.5}>
                        {profile.whatILack.length === 0
                          ? <EmptyState>{t('No gaps that matter a lot yet.')}</EmptyState>
                          : profile.whatILack.map((item) => itemRow(item, true))}
                      </Stack>
                      <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 1 }}>
                        {t('Learning it yourself: {count} of {limit}', { count: profile.learnInUse, limit: profile.learnLimit })}
                      </Typography>
                    </Box>
                    {profile.recordedOnly.length > 0 && (
                      <Box>
                        <Typography variant="subtitle2" sx={{ mb: 1 }}>{t('Recorded only')}</Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{t('These matter less to this dream, so they need no action.')}</Typography>
                        <Stack spacing={1.5}>{profile.recordedOnly.map((item) => itemRow(item, false))}</Stack>
                      </Box>
                    )}
                    {showArchived && archived.length > 0 && (
                      <Box>
                        <Typography variant="subtitle2" sx={{ mb: 1 }}>{t('Removed')}</Typography>
                        <Stack spacing={1}>
                          {archived.map((item) => (
                            <Box key={item.id} sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                              <Typography variant="body2" sx={{ flex: 1 }}>{item.description}</Typography>
                              <IconButton size="small" aria-label={t('Restore {name}', { name: item.description })}
                                onClick={() => void run(() => restoreSelfAssessmentItem(token as string, item.id), t('Unable to restore this.'))}>
                                <RotateCcw size={14} />
                              </IconButton>
                            </Box>
                          ))}
                        </Stack>
                      </Box>
                    )}
                    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                      <Button onClick={() => void handleSaveProfile()} disabled={saving || activeItems.length === 0}>
                        {profile.lastSavedOn ? t('Save a retake') : t('Save this profile')}
                      </Button>
                      {profile.lastSavedOn && (
                        <Typography variant="caption" color="text.secondary">{t('Last saved {date}', { date: profile.lastSavedOn })}</Typography>
                      )}
                    </Box>
                  </Stack>
                </CardContent>
              </Card>

              {profile.comparedWith && (
                <Card>
                  <CardHeader title={t('What changed since {date}', { date: profile.comparedWith })} />
                  <CardContent>
                    {profile.changes.length === 0 ? (
                      <EmptyState>{t('Nothing has changed.')}</EmptyState>
                    ) : (
                      <Stack spacing={0.5}>
                        {profile.changes.map((change, index) => (
                          <Typography variant="body2" key={`${change.type}-${change.description}-${index}`}>{changeText(change)}</Typography>
                        ))}
                      </Stack>
                    )}
                  </CardContent>
                </Card>
              )}
            </>
          )}
        </Stack>
      )}

      {failures.length > 0 && (
        <Box sx={{ mt: 2 }}>
          <PageProblemsPanel failures={failures} />
        </Box>
      )}

      {covering && profile && (
        <GapCoverDialog
          item={covering}
          goals={goals}
          learnInUse={profile.learnInUse}
          learnLimit={profile.learnLimit}
          saving={saving}
          error={coverError}
          onSubmit={(request) => void handleCover(request)}
          onClose={() => setCovering(null)}
        />
      )}

      {pendingRemoval && pendingRemoval.item.coverMethod && (
        <Modal
          title={pendingRemoval.action === 'archive' ? t('Remove this item?') : t('Change how this gap is covered?')}
          onClose={() => setPendingRemoval(null)}
        >
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Typography variant="body2">
              {t('Do you also want to remove {what}?', { what: t(LINKED_RECORD[pendingRemoval.item.coverMethod] ?? '') })}
            </Typography>
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              <Button variant="danger" onClick={() => void finishRemoval(pendingRemoval, true)}>{t('Remove it too')}</Button>
              <Button onClick={() => void finishRemoval(pendingRemoval, false)}>{t('Keep it')}</Button>
              <Button variant="secondary" onClick={() => setPendingRemoval(null)}>{t('Cancel')}</Button>
            </Box>
          </Stack>
        </Modal>
      )}
    </PageSection>
  );
}
