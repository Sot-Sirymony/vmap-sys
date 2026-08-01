import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { listDreams } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import { listSteps } from '../api/stepApi';
import { listTasks } from '../api/taskApi';
import { listVisionAreas } from '../api/visionAreaApi';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import { Breadcrumbs } from '../components/common/Breadcrumbs';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { FilterSelect, optionsFromEntities, optionsFromLabels } from '../components/common/FilterSelect';
import { Loading } from '../components/common/Loading';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { VisionMapTree } from '../components/vision-map/VisionMapTree';
import { useAuth } from '../context/AuthContext';
import { useUrlFilter } from '../hooks/useUrlFilter';
import type { Dream, Goal, TaskItem, VisionArea, VisionStep } from '../types/vision';
import { priorityLabels, workStatusLabels } from '../utils/enumLabels';
import { PageSection } from './PageSection';

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

  const selectedDream = useMemo(() => {
    if (dreamId) {
      return dreams.find((dream) => dream.id === Number(dreamId));
    }
    return areaDreams[0] ?? dreams[0];
  }, [dreamId, dreams, areaDreams]);

  // Keep the shown dream inside the active area scope. If the routed dream
  // falls outside it (area picked from the filter, or a deep link), jump to
  // that area's first dream — replace, so it doesn't stack in history. Other
  // query params (priority, status) are preserved. When the area has no
  // dreams there's nothing to jump to; the empty state below handles it.
  useEffect(() => {
    if (!filterVisionAreaId || dreams.length === 0) {
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
    <PageSection title="Vision Map" subtitle="View one dream from area to executable tasks.">
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
            <ShowArchivedToggle checked={showArchived} onToggle={() => setShowArchived((current) => !current)} />
          </CardContent>
        </Card>
      )}
      {loading && <Loading variant="tree" />}
      {error && <ErrorMessage message={error} onRetry={() => void load()} />}
      {areaHasNoDreams && <EmptyState>No dreams in this vision area yet.</EmptyState>}
      {!areaHasNoDreams && !selectedDream && <EmptyState>No dream selected.</EmptyState>}
      {!areaHasNoDreams && selectedDream && (
        <>
        <Breadcrumbs
          crumbs={[
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
