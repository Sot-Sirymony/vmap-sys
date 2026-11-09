import { useEffect, useMemo, useState } from 'react';
import { MoonStar } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import { listDreams } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import { listSteps } from '../api/stepApi';
import { listTasks } from '../api/taskApi';
import { listVisionAreas } from '../api/visionAreaApi';
import { Button } from '../components/common/Button';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { FilterSelect, optionsFromEntities, optionsFromLabels } from '../components/common/FilterSelect';
import { Loading } from '../components/common/Loading';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { PlanTimeline, type TimelineZoom } from '../components/vision-map/VisionMapTimeline';
import { useAuth } from '../context/AuthContext';
import { useTimelineMarkers } from '../hooks/useTimelineMarkers';
import { useTimelineActions } from '../hooks/useTimelineActions';
import { useTimelineReschedule } from '../hooks/useTimelineReschedule';
import { tr } from '../i18n/translate';
import type { Dream, Goal, TaskItem, VisionArea, VisionStep } from '../types/vision';
import { priorityLabels, workStatusLabels } from '../utils/enumLabels';
import { MARKER_TYPES, buildPlanTimeline, editLinkFor, expandedForDream, fromDay, toDay, type TimelineMarkerType } from '../utils/timeline';
import { PageSection } from './PageSection';

const ZOOMS: TimelineZoom[] = ['week', 'month', 'quarter', 'year'];

/**
 * V16 (BRD 16.0.0, TL-FR-32 to TL-FR-37): every dream on one calendar,
 * grouped by vision area. The view lives in the URL, so a link or the Back
 * button reopens exactly what was on screen.
 */
export function VisionTimelinePage() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [areas, setAreas] = useState<VisionArea[]>([]);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [steps, setSteps] = useState<VisionStep[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const visionAreaId = searchParams.get('visionAreaId') ?? '';
  const dreamId = searchParams.get('dreamId') ?? '';
  const priority = searchParams.get('priority') ?? '';
  const status = searchParams.get('status') ?? '';
  const showArchived = searchParams.get('archived') === 'true';
  const zoomParam = searchParams.get('zoom') as TimelineZoom | null;
  const zoom: TimelineZoom = zoomParam && ZOOMS.includes(zoomParam) ? zoomParam : 'month';
  const showCompleted = searchParams.get('done') !== 'hide';
  const at = searchParams.get('at');
  const focus = Number(searchParams.get('focus')) || null;
  const openParam = searchParams.get('open');
  const hiddenMarkers = (searchParams.get('hide') ?? '').split(',')
    .filter((value): value is TimelineMarkerType => MARKER_TYPES.includes(value as TimelineMarkerType));

  /** Changes some URL values in one step, without adding history entries. */
  function setParams(changes: Record<string, string | null>) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      for (const [key, value] of Object.entries(changes)) {
        if (value) next.set(key, value);
        else next.delete(key);
      }
      return next;
    }, { replace: true });
  }

  async function load() {
    if (!token) return;
    setLoading(true);
    try {
      const [areaData, dreamData, goalData, stepData, taskData] = await Promise.all([
        listVisionAreas(token),
        listDreams(token, showArchived),
        listGoals(token, showArchived),
        listSteps(token, showArchived),
        listTasks(token, showArchived),
      ]);
      setAreas(areaData);
      setDreams(dreamData);
      setGoals(goalData);
      setSteps(stepData);
      setTasks(taskData);
      setError('');
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : tr('Unable to load the timeline.'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, showArchived]);

  const shownDreams = useMemo(() => dreams.filter((dream) =>
    (!visionAreaId || String(dream.visionAreaId) === visionAreaId) && (!dreamId || String(dream.id) === dreamId)),
  [dreams, visionAreaId, dreamId]);

  // Expanded rows: from the URL; "all" means every row that has children; with
  // none given, the focused dream (opened from its map) or nothing.
  const allParents = useMemo(() => {
    const { rows } = buildPlanTimeline(areas, shownDreams, goals, steps, tasks);
    return { rows, keys: rows.filter((row) => row.hasChildren).map((row) => row.key) };
  }, [areas, shownDreams, goals, steps, tasks]);
  const expanded = useMemo(() => {
    if (openParam === 'all') return new Set(allParents.keys);
    if (openParam) return new Set(openParam.split(','));
    if (focus) return expandedForDream(allParents.rows, focus);
    return new Set<string>();
  }, [openParam, focus, allParents]);

  function changeExpanded(next: Set<string>) {
    const everything = allParents.keys.length > 0 && allParents.keys.every((key) => next.has(key));
    // A very long list would make an unwieldy link; past that, the view is kept but not linked.
    const value = everything ? 'all' : next.size === 0 ? '' : next.size <= 150 ? [...next].join(',') : null;
    setParams({ open: value === '' ? null : value, focus: null });
  }

  const markers = useTimelineMarkers(token, shownDreams, goals, steps, tasks);
  const reschedule = useTimelineReschedule(token, steps, tasks, load);
  const timelineActions = useTimelineActions({ token, steps, onChanged: load });
  const hasDreams = dreams.length > 0;

  return (
    <PageSection
      title={tr('Timeline')}
      subtitle={tr('Every dream on one calendar, grouped by vision area. Open a dream to see its goals, steps and tasks.')}
      actions={<Button variant="secondary" onClick={() => navigate('/vision-map')}>{tr('Back to the Vision Map')}</Button>}
    >
      {hasDreams && (
        <Card className="vm-timeline-filters" sx={{ mb: 2 }}>
          <CardContent sx={{ display: 'flex', alignItems: 'flex-end', gap: 2, flexWrap: 'wrap' }}>
            <FilterSelect
              label={tr('Vision Area')}
              value={visionAreaId}
              onChange={(value) => setParams({ visionAreaId: value, dreamId: null })}
              options={optionsFromEntities(areas, (area) => area.name)}
              allLabel={tr('All areas')}
            />
            <FilterSelect
              label={tr('Dream')}
              value={dreamId}
              onChange={(value) => setParams({ dreamId: value })}
              options={optionsFromEntities(dreams.filter((dream) => !visionAreaId || String(dream.visionAreaId) === visionAreaId), (dream) => dream.title)}
              allLabel={tr('All dreams')}
            />
            <FilterSelect label={tr('Priority')} value={priority} onChange={(value) => setParams({ priority: value })} options={optionsFromLabels(priorityLabels)} />
            <FilterSelect label={tr('Status')} value={status} onChange={(value) => setParams({ status: value })} options={optionsFromLabels(workStatusLabels)} />
            <ShowArchivedToggle checked={showArchived} onToggle={() => setParams({ archived: showArchived ? null : 'true' })} />
          </CardContent>
        </Card>
      )}

      {loading && <Loading />}
      {error && <ErrorMessage message={error} onRetry={() => void load()} />}

      {!loading && !error && !hasDreams && (
        <EmptyState
          headline={tr('No dreams on the map yet')}
          icon={MoonStar}
          action={<Button type="button" onClick={() => navigate('/dreams?create=dream')}>{tr('Create your first dream')}</Button>}
        >
          {tr('A dream is the meaningful outcome everything else builds toward — create one and the map grows from there.')}
        </EmptyState>
      )}
      {!loading && !error && hasDreams && shownDreams.length === 0 && <EmptyState>{tr('No dreams match these filters.')}</EmptyState>}

      {!loading && !error && shownDreams.length > 0 && (
        <PlanTimeline
          areas={areas}
          dreams={shownDreams}
          goals={goals}
          steps={steps}
          tasks={tasks}
          priorityFilter={priority}
          statusFilter={status}
          expanded={expanded}
          onExpandedChange={changeExpanded}
          zoom={zoom}
          onZoomChange={(next) => setParams({ zoom: next === 'month' ? null : next })}
          showCompleted={showCompleted}
          onShowCompletedChange={(next) => setParams({ done: next ? null : 'hide' })}
          initialCentre={at ? toDay(at) : null}
          onCentreChange={(day) => setParams({ at: fromDay(day) })}
          focusKey={focus ? `dream-${focus}` : null}
          markers={markers}
          hiddenMarkers={hiddenMarkers}
          onHiddenMarkersChange={(next) => setParams({ hide: next.join(',') })}
          printable
          onOpen={(target) => navigate(editLinkFor(target))}
          onReschedule={reschedule}
          onQuickAdd={timelineActions.addChild}
          onArchive={timelineActions.archive}
          onRestore={timelineActions.restore}
          onAddDream={() => navigate(visionAreaId ? `/dreams?create=dream&parent=${visionAreaId}` : '/dreams?create=dream')}
        />
      )}
      {timelineActions.dialogs}
    </PageSection>
  );
}
