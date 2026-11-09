import { useEffect, useMemo, useState } from 'react';
import { ChartGantt, LayoutGrid, ListTree, MoonStar, Rocket, SlidersHorizontal } from 'lucide-react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { listDreams } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import { listSteps } from '../api/stepApi';
import { listTasks } from '../api/taskApi';
import { listVisionAreas } from '../api/visionAreaApi';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardContent from '@mui/material/CardContent';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { Breadcrumbs } from '../components/common/Breadcrumbs';
import { Button } from '../components/common/Button';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { FilterSelect, optionsFromEntities, optionsFromLabels } from '../components/common/FilterSelect';
import { Loading } from '../components/common/Loading';
import { ProgressBar } from '../components/common/ProgressBar';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { StatusBadge } from '../components/common/StatusBadge';
import { PlanTimeline, VisionMapTimeline } from '../components/vision-map/VisionMapTimeline';
import { VisionMapTree } from '../components/vision-map/VisionMapTree';
import { useAuth } from '../context/AuthContext';
import { useStoredState } from '../hooks/useStoredState';
import { useTimelineMarkers } from '../hooks/useTimelineMarkers';
import { useTimelineReschedule } from '../hooks/useTimelineReschedule';
import { useUrlFilter } from '../hooks/useUrlFilter';
import type { Dream, Goal, TaskItem, VisionArea, VisionStep } from '../types/vision';
import { priorityLabels, workStatusLabels } from '../utils/enumLabels';
import { DreamCommitmentCard } from '../components/vision-map/DreamCommitmentCard';
import { PlanShareCard } from '../components/vision-map/PlanShareCard';
import { AiPlanAssistant } from '../components/ai/AiPlanAssistant';
import { PageSection } from './PageSection';
import { tr } from '../i18n/translate';
import { progressTone } from '../utils/progressTone';
import { addLinkFor, editLinkFor, parseEditTarget, type TimelineKind } from '../utils/timeline';

/** V16 (TL-FR-01): how one dream is shown. */
type DreamView = 'tree' | 'timeline';

/** Per-dream rollup for the overview cards. */
type DreamStats = { goals: number; steps: number; tasks: number; progress: number };

export function DreamDetailPage() {
  const { token, user } = useAuth();
  const { dreamId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [visionAreas, setVisionAreas] = useState<VisionArea[]>([]);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [steps, setSteps] = useState<VisionStep[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  // Off by default: an archived dream/goal/step/task is a dead end you view to
  // restore or permanently remove, not part of the working map.
  const [showArchived, setShowArchived] = useState(false);
  // Filters narrow which goals/steps/tasks the tree renders (the dream itself
  // always shows — it's already the one thing the Dream picker above selects).
  // A branch stays visible if it matches directly or leads to a descendant
  // that does, so a filtered view never orphans a matching row.
  // A Vision Area scope narrows the Dream picker to that area (and keeps the
  // shown dream inside it). In the URL, so a scoped map can be bookmarked and
  // shared — the same `visionAreaId` key the Dashboard and Dreams pages use.
  const [filterVisionAreaId, setFilterVisionAreaId] = useUrlFilter('visionAreaId');
  const [filterPriority, setFilterPriority] = useUrlFilter('priority');
  const [filterStatus, setFilterStatus] = useUrlFilter('status');
  // On a phone the filter card would fill the first screen, so only the Dream
  // picker stays out; the rest sit behind a "Filters" button.
  const isPhone = useMediaQuery('(max-width:600px)');
  const [filtersOpen, setFiltersOpen] = useState(false);
  // V16 (TL-FR-01, TL-FR-04): Tree or Timeline. The URL wins, so a link opens
  // the view it names; otherwise the view last used in this browser.
  const [urlView, setUrlView] = useUrlFilter('view');
  const [storedView, setStoredView] = useStoredState<DreamView>('vision-map:dream-view', 'tree');
  const dreamView: DreamView = urlView === 'timeline' || urlView === 'tree' ? urlView : storedView;
  const [editRequest, setEditRequest] = useState<{ kind: 'dream' | 'goal' | 'step' | 'task'; id: number; nonce: number } | null>(null);
  // TL-FR-02: the overview as cards or as one timeline of every dream.
  const [storedOverviewView, setStoredOverviewView] = useStoredState<DreamView | 'cards'>('vision-map:overview-view', 'cards');
  const overviewView = urlView === 'timeline' || urlView === 'cards' ? urlView : storedOverviewView === 'timeline' ? 'timeline' : 'cards';
  // A bar on the overview or full timeline arrives here as ?edit=goal-10 (TL-FR-27),
  // and its "+" as ?add=goal-10 (TL-FR-31).
  const [editParam, setEditParam] = useUrlFilter('edit');
  const [addParam, setAddParam] = useUrlFilter('add');
  const [addRequest, setAddRequest] = useState<{ kind: 'dream' | 'goal' | 'step'; id: number; nonce: number } | null>(null);

  /** TL-FR-31: adding happens in the tree's own add field, so show the tree and focus it. */
  function quickAdd(kind: TimelineKind, id: number) {
    if (kind === 'task') return;
    changeDreamView('tree');
    setAddRequest((current) => ({ kind, id, nonce: (current?.nonce ?? 0) + 1 }));
  }

  function changeOverviewView(next: 'cards' | 'timeline' | null) {
    if (!next) return;
    setStoredOverviewView(next);
    setUrlView(next === 'timeline' ? 'timeline' : '');
  }

  function changeDreamView(next: DreamView | null) {
    if (!next) return;
    setStoredView(next);
    setUrlView(next === 'timeline' ? 'timeline' : '');
  }

  async function load() {
    if (!token) {
      return;
    }
    setLoading(true);
    try {
      const [areaData, dreamData, goalData, stepData, taskData] = await Promise.all([
        listVisionAreas(token),
        listDreams(token, showArchived),
        listGoals(token, showArchived),
        listSteps(token, showArchived),
        listTasks(token, showArchived),
      ]);
      setVisionAreas(areaData);
      setDreams(dreamData);
      setGoals(goalData);
      setSteps(stepData);
      setTasks(taskData);
      setError('');
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : tr('Unable to load vision map.'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, showArchived]);

  // Dreams within the active area scope (all dreams when no area is chosen).
  const areaDreams = useMemo(() => (
    filterVisionAreaId
      ? dreams.filter((dream) => String(dream.visionAreaId) === filterVisionAreaId)
      : dreams
  ), [dreams, filterVisionAreaId]);

  // Overview when no dream is routed: every dream as a card, instead of
  // silently auto-picking the first one — which made /vision-map look like a
  // single-dream product and hid the rest of the map.
  const overviewMode = !dreamId;

  const selectedDream = useMemo(
    () => (dreamId ? dreams.find((dream) => dream.id === Number(dreamId)) : undefined),
    [dreamId, dreams],
  );

  // Rollups for the overview cards. Dream progress follows the business rule
  // the tree uses: the average of its goals' progress (BR-10).
  const dreamStats = useMemo(() => {
    const stats = new Map<number, DreamStats>();
    for (const dream of dreams) {
      const dreamGoals = goals.filter((goal) => goal.dreamId === dream.id);
      const goalIds = new Set(dreamGoals.map((goal) => goal.id));
      const dreamSteps = steps.filter((step) => goalIds.has(step.goalId));
      const stepIds = new Set(dreamSteps.map((step) => step.id));
      const taskCount = tasks.filter((task) => stepIds.has(task.stepId)).length;
      const progress = dreamGoals.length
        ? Math.round(dreamGoals.reduce((sum, goal) => sum + goal.progressPercent, 0) / dreamGoals.length)
        : 0;
      stats.set(dream.id, { goals: dreamGoals.length, steps: dreamSteps.length, tasks: taskCount, progress });
    }
    return stats;
  }, [dreams, goals, steps, tasks]);

  // Keep the shown dream inside the active area scope. If the routed dream
  // falls outside it (area picked from the filter, or a deep link), jump to
  // that area's first dream — replace, so it doesn't stack in history. Other
  // query params (priority, status) are preserved. When the area has no
  // dreams there's nothing to jump to; the empty state below handles it.
  useEffect(() => {
    // Overview has no routed dream to keep in scope — the grid just filters.
    if (!dreamId || !filterVisionAreaId || dreams.length === 0) {
      return;
    }
    const current = dreamId ? dreams.find((dream) => dream.id === Number(dreamId)) : undefined;
    if (current && String(current.visionAreaId) === filterVisionAreaId) {
      return;
    }
    const first = areaDreams[0];
    if (first && first.id !== current?.id) {
      navigate(`/dreams/${first.id}${location.search}`, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterVisionAreaId, dreamId, dreams]);

  // V16 (TL-FR-13): marks for whichever timeline is showing; nothing is fetched otherwise.
  const singleDreamList = useMemo(() => (selectedDream ? [selectedDream] : []), [selectedDream]);
  const dreamMarkers = useTimelineMarkers(token, singleDreamList, goals, steps, tasks, Boolean(selectedDream) && dreamView === 'timeline');
  // TL-FR-39 to TL-FR-41: dragged dates save like a form, then reload the map.
  const reschedule = useTimelineReschedule(token, steps, tasks, load);
  const overviewMarkers = useTimelineMarkers(token, areaDreams, goals, steps, tasks, overviewMode && overviewView === 'timeline');

  // Open the form named in ?edit= once the dream's records are loaded, then
  // drop it from the URL so a reload does not reopen it.
  useEffect(() => {
    const target = parseEditTarget(editParam || null);
    if (!target || loading || !selectedDream) return;
    setEditRequest((current) => ({ ...target, nonce: (current?.nonce ?? 0) + 1 }));
    setEditParam('');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editParam, loading, selectedDream?.id]);

  useEffect(() => {
    const target = parseEditTarget(addParam || null);
    if (!target || loading || !selectedDream) return;
    quickAdd(target.kind, target.id);
    setAddParam('');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [addParam, loading, selectedDream?.id]);

  const selectedArea = visionAreas.find((area) => area.id === selectedDream?.visionAreaId);
  const areaHasNoDreams = Boolean(filterVisionAreaId) && areaDreams.length === 0 && !loading;
  const collapseFilters = isPhone && !overviewMode;
  const showMoreFilters = !collapseFilters || filtersOpen;
  const activeFilterCount = [filterVisionAreaId, filterPriority, filterStatus].filter(Boolean).length + (showArchived ? 1 : 0);

  const supportCards = selectedDream && (
    <>
      {!selectedDream.archived && (
        <Box sx={{ mb: 2 }}>
          <DreamCommitmentCard
            key={selectedDream.id}
            dream={selectedDream}
            token={token ?? ''}
            defaultName={user?.fullName ?? ''}
            onChanged={load}
          />
        </Box>
      )}
      {!selectedDream.archived && token && (
        <Box sx={{ mb: 2 }}>
          <PlanShareCard key={selectedDream.id} dream={selectedDream} token={token} />
        </Box>
      )}
      {!selectedDream.archived && (
        // V11 (PB-FR-01): the guided path from this dream to a full plan.
        <Box className="map-build-cta" sx={{ mb: 2, display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
          <Button onClick={() => navigate(`/dreams/${selectedDream.id}/build`)}>{tr('Build my plan')}</Button>
          <Typography variant="body2" color="text.secondary">
            {tr('A step-by-step guide from this dream to goals, steps and tasks.')}
          </Typography>
        </Box>
      )}
      {!selectedDream.archived && token && (
        <Box sx={{ mb: 2 }}>
          <AiPlanAssistant key={selectedDream.id} dream={selectedDream} goals={goals} steps={steps} token={token} onDataChange={load} />
        </Box>
      )}
    </>
  );

  return (
    <PageSection
      title={tr('Vision Map')}
      subtitle={overviewMode ? tr('Every dream at a glance — open one to work its tree.') : tr('View one dream from area to executable tasks.')}
    >
      {dreams.length > 0 && (
        <Card>
          <CardContent className="map-filter-bar" sx={{ display: 'flex', alignItems: 'flex-end', gap: 2, flexWrap: 'wrap' }}>
            {collapseFilters && (
              <Button
                type="button"
                variant="secondary"
                className="map-filter-toggle"
                aria-expanded={filtersOpen}
                onClick={() => setFiltersOpen((open) => !open)}
              >
                <SlidersHorizontal size={16} aria-hidden="true" style={{ marginRight: 6 }} />
                {filtersOpen ? tr('Hide filters') : tr('Filters')}
                {!filtersOpen && activeFilterCount > 0 ? ` (${activeFilterCount})` : ''}
              </Button>
            )}
            {showMoreFilters && (
              <FilterSelect
                label={tr('Vision Area')}
                value={filterVisionAreaId}
                onChange={setFilterVisionAreaId}
                options={optionsFromEntities(visionAreas, (area) => area.name)}
                allLabel={tr('All areas')}
              />
            )}
            {!overviewMode && (
              <>
                <label className="map-filter-dream">
                  {tr('Dream')}
                  <FormControl fullWidth size="small">
                    <Select
                      SelectDisplayProps={{ 'aria-label': 'Dream' }}
                      value={areaDreams.some((dream) => String(dream.id) === String(selectedDream?.id)) ? String(selectedDream?.id ?? '') : ''}
                      displayEmpty
                      onChange={(event) => event.target.value && navigate(`/dreams/${event.target.value}${location.search}`)}
                    >
                      {areaDreams.length === 0 && <MenuItem value="" disabled><em>{tr('No dreams in this area')}</em></MenuItem>}
                      {areaDreams.map((dream) => <MenuItem value={String(dream.id)} key={dream.id}>{dream.title}{dream.archived ? ' (archived)' : ''}</MenuItem>)}
                    </Select>
                  </FormControl>
                </label>
                {showMoreFilters && (
                <>
                <FilterSelect
                  label={tr('Priority')}
                  value={filterPriority}
                  onChange={setFilterPriority}
                  options={optionsFromLabels(priorityLabels)}
                />
                <FilterSelect
                  label={tr('Status')}
                  value={filterStatus}
                  onChange={setFilterStatus}
                  options={optionsFromLabels(workStatusLabels)}
                />
                </>
                )}
              </>
            )}
            {showMoreFilters && (
              <ShowArchivedToggle checked={showArchived} onToggle={() => setShowArchived((current) => !current)} />
            )}
          </CardContent>
        </Card>
      )}
      {loading && <Loading variant={overviewMode ? 'cards' : 'tree'} />}
      {error && <ErrorMessage message={error} onRetry={() => void load()} />}

      {overviewMode && !loading && !error && dreams.length === 0 && (
        <EmptyState
          headline={tr('No dreams on the map yet')}
          icon={MoonStar}
          action={<Button type="button" onClick={() => navigate('/dreams?create=dream')}>{tr('Create your first dream')}</Button>}
        >
          {tr('A dream is the meaningful outcome everything else builds toward — create one and the map grows from there.')}
        </EmptyState>
      )}
      {overviewMode && areaHasNoDreams && <EmptyState>{tr('No dreams in this vision area yet.')}</EmptyState>}
      {overviewMode && !loading && dreams.length > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap', mb: 2, mt: 2 }}>
          <ToggleButtonGroup
            size="small"
            exclusive
            value={overviewView}
            onChange={(_, next: 'cards' | 'timeline' | null) => changeOverviewView(next)}
            aria-label={tr('How to show your dreams')}
          >
            <ToggleButton value="cards"><LayoutGrid size={16} aria-hidden="true" style={{ marginRight: 6 }} />{tr('Cards')}</ToggleButton>
            <ToggleButton value="timeline"><ChartGantt size={16} aria-hidden="true" style={{ marginRight: 6 }} />{tr('Timeline')}</ToggleButton>
          </ToggleButtonGroup>
          <Button
            variant="secondary"
            onClick={() => navigate(`/vision-map/timeline${filterVisionAreaId ? `?visionAreaId=${filterVisionAreaId}` : ''}`)}
          >
            {tr('View full timeline')}
          </Button>
        </Box>
      )}
      {overviewMode && !loading && areaDreams.length > 0 && overviewView === 'timeline' && (
        <PlanTimeline
          areas={visionAreas}
          dreams={areaDreams}
          goals={goals}
          steps={steps}
          tasks={tasks}
          markers={overviewMarkers}
          onOpen={(target) => navigate(editLinkFor(target))}
          onReschedule={reschedule}
          onQuickAdd={(target) => navigate(addLinkFor(target))}
        />
      )}
      {overviewMode && !loading && areaDreams.length > 0 && overviewView === 'cards' && (
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 2 }}>
          {areaDreams.map((dream) => {
            const stats = dreamStats.get(dream.id) ?? { goals: 0, steps: 0, tasks: 0, progress: 0 };
            const areaName = visionAreas.find((area) => area.id === dream.visionAreaId)?.name ?? tr('Unassigned');
            return (
              <Card key={dream.id} className="map-overview-card">
                <CardActionArea
                  onClick={() => navigate(`/dreams/${dream.id}${location.search}`)}
                  sx={{ height: '100%', alignItems: 'stretch' }}
                >
                  <CardContent sx={{ display: 'grid', gap: 1, height: '100%', alignContent: 'start' }}>
                    <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: '0.05em' }} noWrap>
                      {areaName}
                    </Typography>
                    <Typography sx={{ fontWeight: 600, lineHeight: 1.3 }}>
                      {dream.title}
                      {dream.archived ? ' (archived)' : ''}
                    </Typography>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                      <StatusBadge status={dream.status} />
                      {dream.moonshot && <Rocket size={14} aria-label={tr('Moonshot')} style={{ color: 'var(--moonshot-fg)' }} />}
                      {dream.targetDate && (
                        <Typography variant="caption" color="text.secondary">target {dream.targetDate}</Typography>
                      )}
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <ProgressBar value={stats.progress} colored />
                      <Typography variant="caption" className={`progress-text--${progressTone(stats.progress)}`} sx={{ flexShrink: 0, fontWeight: 600 }}>{stats.progress}%</Typography>
                    </Box>
                    <Typography variant="caption" color="text.secondary">
                      {stats.goals === 0
                        ? tr('No goals yet — open to add the first')
                        : tr('{goals} goal(s) · {steps} step(s) · {tasks} task(s)', { goals: stats.goals, steps: stats.steps, tasks: stats.tasks })}
                    </Typography>
                  </CardContent>
                </CardActionArea>
              </Card>
            );
          })}
        </Box>
      )}

      {!overviewMode && !areaHasNoDreams && !loading && !selectedDream && <EmptyState>{tr('No dream selected.')}</EmptyState>}
      {!overviewMode && !areaHasNoDreams && selectedDream && (
        <>
        <Breadcrumbs
          crumbs={[
            { label: tr('All dreams'), to: `/vision-map${location.search}` },
            selectedArea
              ? { label: selectedArea.name, to: `/dreams?visionAreaId=${selectedArea.id}` }
              : { label: tr('Unassigned') },
            { label: selectedDream.title },
          ]}
        />
        {/* On a phone the map itself comes first; the supporting cards
            follow it, so the tree isn't several screens down. */}
        {!isPhone && supportCards}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap', mb: 2 }}>
          <ToggleButtonGroup
            size="small"
            exclusive
            value={dreamView}
            onChange={(_, next: DreamView | null) => changeDreamView(next)}
            aria-label={tr('How to show this dream')}
          >
            <ToggleButton value="tree"><ListTree size={16} aria-hidden="true" style={{ marginRight: 6 }} />{tr('Tree')}</ToggleButton>
            <ToggleButton value="timeline"><ChartGantt size={16} aria-hidden="true" style={{ marginRight: 6 }} />{tr('Timeline')}</ToggleButton>
          </ToggleButtonGroup>
          <Button variant="secondary" onClick={() => navigate(`/vision-map/timeline?focus=${selectedDream.id}`)}>{tr('View full timeline')}</Button>
        </Box>
        {dreamView === 'timeline' && (
          <VisionMapTimeline
            key={selectedDream.id}
            dream={selectedDream}
            goals={goals}
            steps={steps}
            tasks={tasks}
            markers={dreamMarkers}
            onReschedule={reschedule}
            onQuickAdd={(target) => quickAdd(target.kind, target.id)}
            priorityFilter={filterPriority}
            statusFilter={filterStatus}
            onOpen={(kind, id) => setEditRequest((current) => ({ kind, id, nonce: (current?.nonce ?? 0) + 1 }))}
          />
        )}
        {/* The tree stays mounted under the timeline: its edit forms are the
            ones the timeline opens (TL-FR-27), so dates change in one place. */}
        <Box sx={{ display: dreamView === 'timeline' ? 'none' : 'block' }}>
        <VisionMapTree
          editRequest={editRequest}
          addRequest={addRequest}
          dream={selectedDream}
          visionAreaName={selectedArea?.name ?? tr('Unassigned')}
          goals={goals}
          steps={steps}
          tasks={tasks}
          token={token ?? ''}
          onDataChange={load}
          onDreamPermanentlyDeleted={() => navigate('/dreams')}
          priorityFilter={filterPriority}
          statusFilter={filterStatus}
        />
        </Box>
        {isPhone && <Box sx={{ mt: 2 }}>{supportCards}</Box>}
        </>
      )}
    </PageSection>
  );
}
