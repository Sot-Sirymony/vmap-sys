import { useEffect, useMemo, useState } from 'react';
import { MoonStar, Rocket } from 'lucide-react';
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
import Typography from '@mui/material/Typography';
import { Breadcrumbs } from '../components/common/Breadcrumbs';
import { Button } from '../components/common/Button';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { FilterSelect, optionsFromEntities, optionsFromLabels } from '../components/common/FilterSelect';
import { Loading } from '../components/common/Loading';
import { ProgressBar } from '../components/common/ProgressBar';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { StatusBadge } from '../components/common/StatusBadge';
import { VisionMapTree } from '../components/vision-map/VisionMapTree';
import { useAuth } from '../context/AuthContext';
import { useUrlFilter } from '../hooks/useUrlFilter';
import type { Dream, Goal, TaskItem, VisionArea, VisionStep } from '../types/vision';
import { priorityLabels, workStatusLabels } from '../utils/enumLabels';
import { PageSection } from './PageSection';

/** Per-dream rollup for the overview cards. */
type DreamStats = { goals: number; steps: number; tasks: number; progress: number };

export function DreamDetailPage() {
  const { token } = useAuth();
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
      setError(loadError instanceof Error ? loadError.message : 'Unable to load vision map.');
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

  const selectedArea = visionAreas.find((area) => area.id === selectedDream?.visionAreaId);
  const areaHasNoDreams = Boolean(filterVisionAreaId) && areaDreams.length === 0 && !loading;

  return (
    <PageSection
      title="Vision Map"
      subtitle={overviewMode ? 'Every dream at a glance — open one to work its tree.' : 'View one dream from area to executable tasks.'}
    >
      {dreams.length > 0 && (
        <Card>
          <CardContent sx={{ display: 'flex', alignItems: 'flex-end', gap: 2, flexWrap: 'wrap' }}>
            <FilterSelect
              label="Vision Area"
              value={filterVisionAreaId}
              onChange={setFilterVisionAreaId}
              options={optionsFromEntities(visionAreas, (area) => area.name)}
              allLabel="All areas"
            />
            {!overviewMode && (
              <>
                <label>
                  Dream
                  <FormControl fullWidth size="small">
                    <Select
                      SelectDisplayProps={{ 'aria-label': 'Dream' }}
                      value={areaDreams.some((dream) => String(dream.id) === String(selectedDream?.id)) ? String(selectedDream?.id ?? '') : ''}
                      displayEmpty
                      onChange={(event) => event.target.value && navigate(`/dreams/${event.target.value}${location.search}`)}
                    >
                      {areaDreams.length === 0 && <MenuItem value="" disabled><em>No dreams in this area</em></MenuItem>}
                      {areaDreams.map((dream) => <MenuItem value={String(dream.id)} key={dream.id}>{dream.title}{dream.archived ? ' (archived)' : ''}</MenuItem>)}
                    </Select>
                  </FormControl>
                </label>
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
                  options={optionsFromLabels(workStatusLabels)}
                />
              </>
            )}
            <ShowArchivedToggle checked={showArchived} onToggle={() => setShowArchived((current) => !current)} />
          </CardContent>
        </Card>
      )}
      {loading && <Loading variant={overviewMode ? 'cards' : 'tree'} />}
      {error && <ErrorMessage message={error} onRetry={() => void load()} />}

      {overviewMode && !loading && !error && dreams.length === 0 && (
        <EmptyState
          headline="No dreams on the map yet"
          icon={MoonStar}
          action={<Button type="button" onClick={() => navigate('/dreams?create=dream')}>Create your first dream</Button>}
        >
          A dream is the meaningful outcome everything else builds toward — create one and the map grows from there.
        </EmptyState>
      )}
      {overviewMode && areaHasNoDreams && <EmptyState>No dreams in this vision area yet.</EmptyState>}
      {overviewMode && !loading && areaDreams.length > 0 && (
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 2 }}>
          {areaDreams.map((dream) => {
            const stats = dreamStats.get(dream.id) ?? { goals: 0, steps: 0, tasks: 0, progress: 0 };
            const areaName = visionAreas.find((area) => area.id === dream.visionAreaId)?.name ?? 'Unassigned';
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
                      {dream.moonshot && <Rocket size={14} aria-label="Moonshot" style={{ color: 'var(--moonshot-fg)' }} />}
                      {dream.targetDate && (
                        <Typography variant="caption" color="text.secondary">target {dream.targetDate}</Typography>
                      )}
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <ProgressBar value={stats.progress} />
                      <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0 }}>{stats.progress}%</Typography>
                    </Box>
                    <Typography variant="caption" color="text.secondary">
                      {stats.goals === 0
                        ? 'No goals yet — open to add the first'
                        : `${stats.goals} ${stats.goals === 1 ? 'goal' : 'goals'} · ${stats.steps} ${stats.steps === 1 ? 'step' : 'steps'} · ${stats.tasks} ${stats.tasks === 1 ? 'task' : 'tasks'}`}
                    </Typography>
                  </CardContent>
                </CardActionArea>
              </Card>
            );
          })}
        </Box>
      )}

      {!overviewMode && !areaHasNoDreams && !loading && !selectedDream && <EmptyState>No dream selected.</EmptyState>}
      {!overviewMode && !areaHasNoDreams && selectedDream && (
        <>
        <Breadcrumbs
          crumbs={[
            { label: 'All dreams', to: `/vision-map${location.search}` },
            selectedArea
              ? { label: selectedArea.name, to: `/dreams?visionAreaId=${selectedArea.id}` }
              : { label: 'Unassigned' },
            { label: selectedDream.title },
          ]}
        />
        <VisionMapTree
          dream={selectedDream}
          visionAreaName={selectedArea?.name ?? 'Unassigned'}
          goals={goals}
          steps={steps}
          tasks={tasks}
          token={token ?? ''}
          onDataChange={load}
          onDreamPermanentlyDeleted={() => navigate('/dreams')}
          priorityFilter={filterPriority}
          statusFilter={filterStatus}
        />
        </>
      )}
    </PageSection>
  );
}
