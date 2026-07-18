import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
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
import { Loading } from '../components/common/Loading';
import { VisionMapTree } from '../components/vision-map/VisionMapTree';
import { useAuth } from '../context/AuthContext';
import type { Dream, Goal, TaskItem, VisionArea, VisionStep } from '../types/vision';
import { PageSection } from './PageSection';

export function DreamDetailPage() {
  const { token } = useAuth();
  const { dreamId } = useParams();
  const navigate = useNavigate();
  const [visionAreas, setVisionAreas] = useState<VisionArea[]>([]);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [steps, setSteps] = useState<VisionStep[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function load() {
    if (!token) {
      return;
    }
    setLoading(true);
    try {
      const [areaData, dreamData, goalData, stepData, taskData] = await Promise.all([
        listVisionAreas(token),
        listDreams(token),
        listGoals(token),
        listSteps(token),
        listTasks(token),
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
  }, [token]);

  const selectedDream = useMemo(() => {
    if (dreamId) {
      return dreams.find((dream) => dream.id === Number(dreamId));
    }
    return dreams[0];
  }, [dreamId, dreams]);

  const selectedArea = visionAreas.find((area) => area.id === selectedDream?.visionAreaId);

  return (
    <PageSection title="Vision Map" subtitle="View one dream from area to executable tasks.">
      {dreams.length > 0 && (
        <Card>
          <CardContent>
            <label>
              Dream
              <FormControl fullWidth size="small">
                <Select
                  value={String(selectedDream?.id ?? '')}
                  onChange={(event) => event.target.value && navigate(`/dreams/${event.target.value}`)}
                >
                  {dreams.map((dream) => <MenuItem value={String(dream.id)} key={dream.id}>{dream.title}</MenuItem>)}
                </Select>
              </FormControl>
            </label>
          </CardContent>
        </Card>
      )}
      {loading && <Loading variant="tree" />}
      {error && <ErrorMessage message={error} onRetry={() => void load()} />}
      {!selectedDream ? (
        <EmptyState>No dream selected.</EmptyState>
      ) : (
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
        />
        </>
      )}
    </PageSection>
  );
}
