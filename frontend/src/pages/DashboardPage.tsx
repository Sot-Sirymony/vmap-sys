import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Compass } from 'lucide-react';
import { Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip as RechartsTooltip, XAxis, YAxis } from 'recharts';
import { getDashboardSummary } from '../api/dashboardApi';
import { CategoryBreakdownChart } from '../components/dashboard/CategoryBreakdownChart';
import { ChartTooltipContent } from '../components/dashboard/ChartTooltipContent';
import { listVisionAreas } from '../api/visionAreaApi';
import { FilterSelect, optionsFromEntities } from '../components/common/FilterSelect';
import { AttentionPanel } from '../components/dashboard/AttentionPanel';
import { DashboardSummary } from '../components/dashboard/DashboardSummary';
import { EnergyBudgetCard } from '../components/dashboard/EnergyBudgetCard';
import { GettingStarted } from '../components/dashboard/GettingStarted';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { ProgressBar } from '../components/common/ProgressBar';
import { StatusBadge } from '../components/common/StatusBadge';
import Box from '@mui/material/Box';
import MuiButton from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useAuth } from '../context/AuthContext';
import { useUrlFilter } from '../hooks/useUrlFilter';
import type { DashboardSummary as DashboardSummaryData, PartnerStatus, Priority, VisionArea, WorkStatus } from '../types/vision';
import { categoricalPieColor, chartPrimary, heatmapLevelColors, priorityColor, statusColor } from '../theme';
import { obstacleTypeLabels, partnerStatusLabels } from '../utils/enumLabels';
import { useThemeSettings } from '../context/ThemeModeContext';
import { PageSection } from './PageSection';

const PRIORITY_ORDER: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const TOP_OBSTACLE_TYPE_COUNT = 4;
const OTHER_OBSTACLE_TYPES_KEY = 'OTHER_TYPES';
const PARTNER_STATUS_ORDER: PartnerStatus[] = ['TO_CONTACT', 'CONTACTED', 'ACTIVE', 'WAITING', 'DECLINED', 'COMPLETED'];
const HEATMAP_WEEKS = 12;
// Chart palettes live in theme.ts (BR-15). Pipeline stages keep per-hue
// meaning (real status information); obstacle types use the blue depth ramp
// (arbitrary categories); the heatmap ramp encodes intensity. The original
// rationale for each choice is documented next to the values in theme.ts.
// FR-39.3 / BR-34: the status and priority hues are now resolved per render
// rather than frozen at module load, because high contrast changes them (and
// changes them per mode). A chart fill IS the colour — unlike a badge, whose
// label text blends with --foreground and so corrects itself — so leaving these
// static would have made the charts the one place the toggle didn't reach.
// The partner pipeline still draws each stage in exactly its status colour
// (FR-25.3, audit V-02), which is why it goes through the same resolver.
const HEATMAP_LEVEL_COLORS = heatmapLevelColors;

type DashboardPeriod = 'month' | 'quarter' | 'year';

const PERIOD_OPTIONS = [
  { value: 'month', label: 'This month' },
  { value: 'quarter', label: 'This quarter' },
  { value: 'year', label: 'This year' },
];

function isPeriod(value: string): value is DashboardPeriod {
  return value === 'month' || value === 'quarter' || value === 'year';
}

function isoDay(year: number, month1: number, day: number): string {
  return `${year}-${String(month1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

// Calendar-aligned window for the two time-based tiles. All bounds are inclusive
// and computed from the browser's local date.
function periodRange(period: DashboardPeriod): { from: string; to: string } {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth(); // 0-11
  if (period === 'year') {
    return { from: isoDay(year, 1, 1), to: isoDay(year, 12, 31) };
  }
  if (period === 'quarter') {
    const startMonth = Math.floor(month / 3) * 3; // 0, 3, 6, 9
    const lastDay = new Date(year, startMonth + 3, 0).getDate();
    return { from: isoDay(year, startMonth + 1, 1), to: isoDay(year, startMonth + 3, lastDay) };
  }
  const lastDay = new Date(year, month + 1, 0).getDate();
  return { from: isoDay(year, month + 1, 1), to: isoDay(year, month + 1, lastDay) };
}

export function DashboardPage() {
  const { token } = useAuth();
  const navigate = useNavigate();
  // FR-39.3: chart fills follow the appearance settings, so high contrast
  // reaches the charts and not only the badges.
  const { settings: appearance, resolvedMode } = useThemeSettings();
  const workStatusFill = (status: WorkStatus) => statusColor(status, resolvedMode, appearance.highContrast);
  const partnerStatusFill = (status: PartnerStatus) => statusColor(status, resolvedMode, appearance.highContrast);
  const priorityFill = (priority: Priority) => priorityColor(priority, resolvedMode, appearance.highContrast);
  // FR-41: the categorical pie palette, indexed by position. Follows mode and
  // high contrast like every other chart fill.
  const pieFill = (index: number) => categoricalPieColor(index, resolvedMode, appearance.highContrast);
  const [summary, setSummary] = useState<DashboardSummaryData | null>(null);
  const [visionAreas, setVisionAreas] = useState<VisionArea[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  // In the URL, so a scoped dashboard can be bookmarked and shared.
  const [filterVisionAreaId, setFilterVisionAreaId] = useUrlFilter('visionAreaId');
  const [periodValue, setPeriodValue] = useUrlFilter('period');
  const period = isPeriod(periodValue) ? periodValue : 'month';
  const { from, to } = periodRange(period);
  // Drill-down links carry the dashboard's vision-area scope, so the target
  // page shows the same rows the widget counted. When no area is selected the
  // links carry only their own value (status, priority, dates). The period
  // window is not forwarded: it only maps onto task due dates, and the "Due"
  // tile already carries that range explicitly.
  const scopeSuffix = filterVisionAreaId ? `&visionAreaId=${filterVisionAreaId}` : '';

  useEffect(() => {
    if (!token) {
      return;
    }

    setLoading(true);
    getDashboardSummary(token, filterVisionAreaId, from, to)
      .then((summaryData) => {
        setSummary(summaryData);
        setError('');
      })
      .catch((loadError) => setError(loadError instanceof Error ? loadError.message : 'Unable to load dashboard.'))
      .finally(() => setLoading(false));
  }, [token, filterVisionAreaId, from, to]);

  // The dropdown always offers every area, even while the dashboard is scoped to
  // one — otherwise picking an area would leave you unable to pick a different
  // one, since the scoped summary only knows about the area you're already in.
  useEffect(() => {
    if (!token) {
      return;
    }
    void listVisionAreas(token).then(setVisionAreas).catch(() => setVisionAreas([]));
  }, [token]);

  // Everything below reshapes the single /api/dashboard payload for its
  // widget — the per-entity list fetches this page used to make (8 requests
  // per load) now happen server-side.
  const priorityTasks = summary?.priorityTasks ?? [];
  const tasksByStatus = summary?.tasksByStatus ?? {};

  const tasksByPriority = PRIORITY_ORDER.reduce<Record<string, number>>((counts, priority) => {
    counts[priority] = summary?.tasksByPriority?.[priority] ?? 0;
    return counts;
  }, {});

  const sortedObstacleTypes = Object.entries(summary?.activeObstaclesByType ?? {}).sort(([, first], [, second]) => second - first);
  const topObstaclesByType = Object.fromEntries(sortedObstacleTypes.slice(0, TOP_OBSTACLE_TYPE_COUNT));
  const remainingObstacleCount = sortedObstacleTypes
    .slice(TOP_OBSTACLE_TYPE_COUNT)
    .reduce((sum, [, count]) => sum + count, 0);
  if (remainingObstacleCount > 0) {
    topObstaclesByType[OTHER_OBSTACLE_TYPES_KEY] = remainingObstacleCount;
  }

  const partnerCounts = PARTNER_STATUS_ORDER.reduce<Record<string, number>>((counts, status) => {
    counts[status] = summary?.partnersByStatus?.[status] ?? 0;
    return counts;
  }, {});
  const totalPartners = Object.values(partnerCounts).reduce((sum, count) => sum + count, 0);

  const partnerPipelineData = [{ name: 'Partners', ...partnerCounts }];

  const reviewCadence = summary?.reviewCadence ?? {};
  const reviewHeatmapWeeks = buildReviewHeatmapWeeks(reviewCadence);
  const cadenceReviewCount = Object.values(reviewCadence).reduce((sum, count) => sum + count, 0);

  const progressTrend = (summary?.progressTrend ?? []).map((point) => ({
    label: new Date(`${point.weekEnd}T00:00:00`).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
    progress: Number(point.progress),
  }));

  const visionAreaProgress = (summary?.visionAreaProgress ?? []).map((area) => ({
    name: area.name,
    progress: Number(area.progress),
  }));

  // The whole hierarchy hangs off Vision Areas, so zero areas means a brand
  // new account — show the getting-started checklist instead of nine "0"
  // tiles and six empty chart shells. Once areas exist the checklist stays,
  // as a banner above the stats, until the first dream has goals (FR-21.1 —
  // the old single-step card dead-ended after step one, audit H-01). Gated
  // on !loading so it can't flash during fetch.
  const hasArea = (summary?.totalVisionAreas ?? 0) > 0;
  const hasDream = (summary?.activeDreams ?? 0) > 0;
  const hasGoal = (summary?.activeGoals ?? 0) > 0;
  const isFirstRun = !loading && !error && summary !== null && !hasArea;
  const showOnboardingBanner = !loading && !error && summary !== null && hasArea && (!hasDream || !hasGoal);

  return (
    <PageSection title="Dashboard" subtitle="Track progress across dreams, goals, steps, and tasks.">
      {loading && <Loading variant="cards" rows={6} />}
      {error && <ErrorMessage message={error} />}
      {isFirstRun ? (
        <GettingStarted hasArea={hasArea} hasDream={hasDream} hasGoal={hasGoal} />
      ) : (
      <>
      {showOnboardingBanner && <GettingStarted hasArea={hasArea} hasDream={hasDream} hasGoal={hasGoal} />}
      <Card className="filter-bar flex-row">
        <FilterSelect
          label="Vision Area"
          value={filterVisionAreaId}
          onChange={setFilterVisionAreaId}
          options={optionsFromEntities(visionAreas, (area) => area.name)}
          allLabel="All areas"
        />
        <label>
          Period
          <FormControl fullWidth size="small">
            <Select SelectDisplayProps={{ 'aria-label': "Period" }} value={period} onChange={(event) => setPeriodValue(event.target.value)}>
              {PERIOD_OPTIONS.map((option) => (
                <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>
              ))}
            </Select>
          </FormControl>
        </label>
      </Card>
      {/* FR-25.3: what to DO comes before what IS — the feed leads the page. */}
      <AttentionPanel attention={summary?.attention} overdueCount={summary?.overdueTasks ?? 0} visionAreaId={filterVisionAreaId} />
      <DashboardSummary
        summary={summary}
        periodLabel={PERIOD_OPTIONS.find((option) => option.value === period)?.label.toLowerCase() ?? 'this month'}
        dueInPeriodLink={`/tasks?dueFrom=${from}&dueTo=${to}${scopeSuffix}`}
        visionAreaId={filterVisionAreaId}
      />
      <EnergyBudgetCard budget={summary?.energyBudget} />
      <Card>
        <CardHeader title="Priority tasks" subheader="The five highest-priority tasks that are not yet completed" />
        <CardContent>
          {priorityTasks.length === 0 ? (
            <EmptyState>No open priority tasks.</EmptyState>
          ) : (
            <TableContainer>
            <Table className="data-table">
              <TableHead>
                <TableRow>
                  <TableCell>Task</TableCell>
                  <TableCell>Due</TableCell>
                  <TableCell>Priority</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Progress</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {priorityTasks.map((task) => (
                  <TableRow key={task.id}>
                    <TableCell sx={{ fontWeight: 500 }}>{task.title}</TableCell>
                    <TableCell>{task.dueDate}</TableCell>
                    <TableCell><PriorityBadge priority={task.priority} /></TableCell>
                    <TableCell><StatusBadge status={task.status} /></TableCell>
                    <TableCell sx={{ width: 160 }}><ProgressBar value={Number(task.progressPercent)} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>
      <Card>
        <CardHeader title="Progress trend" subheader="Portfolio-wide average task progress over the last 12 weeks" />
        <CardContent>
          {progressTrend.length === 0 ? (
            <EmptyState>No progress logged yet — update a task's progress to start the trend.</EmptyState>
          ) : (
            <Box sx={{ width: '100%', height: 220 }}>
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={progressTrend} margin={{ left: 8, right: 16 }}>
                  <CartesianGrid vertical={false} />
                  <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} />
                  <YAxis type="number" domain={[0, 100]} tickFormatter={(value) => `${value}%`} tickLine={false} axisLine={false} width={40} />
                  <RechartsTooltip content={<ChartTooltipContent />} />
                  <Area
                    dataKey="progress"
                    name="Average progress %"
                    type="monotone"
                    fill={chartPrimary}
                    fillOpacity={0.15}
                    stroke={chartPrimary}
                    strokeWidth={2}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </Box>
          )}
        </CardContent>
      </Card>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: 'repeat(2, 1fr)' } }}>
        <CategoryBreakdownChart
          title="Goals by status"
          description="Current distribution across all active goals — click a slice to open those goals"
          data={summary?.goalsByStatus ?? {}}
          formatLabel={formatLabel}
          variant="donut"
          colorForKey={(key) => workStatusFill(key as WorkStatus)}
          linkForKey={(key) => `/goals?status=${key}${scopeSuffix}`}
        />
        <CategoryBreakdownChart
          title="Dreams by vision area"
          description="Where active dreams are concentrated — click a slice to open those dreams"
          data={summary?.dreamsByVisionArea ?? {}}
          variant="pie"
          // FR-41: Vision Areas are categories with no inherent meaning, so the
          // four-hue pie palette is safe here — a green slice means "this area",
          // not "Completed". Status and priority charts stay on their own
          // palettes for exactly that reason (BR-14).
          colorForKey={(name) => pieFill(Object.keys(summary?.dreamsByVisionArea ?? {}).indexOf(name))}
          // The payload keys this chart by area *name*, so the drill-down maps
          // it back to the area's id before linking.
          linkForKey={(name) => {
            const area = visionAreas.find((candidate) => candidate.name === name);
            return area ? `/dreams?visionAreaId=${area.id}` : '/dreams';
          }}
        />
        <CategoryBreakdownChart
          title="Tasks by status"
          description="Current distribution across all tasks — click a slice to open that column"
          data={tasksByStatus}
          formatLabel={formatLabel}
          variant="donut"
          colorForKey={(key) => workStatusFill(key as WorkStatus)}
          linkForKey={(key) => `/tasks?status=${key}${scopeSuffix}`}
        />
        <CategoryBreakdownChart
          title="Tasks by priority"
          description="Workload skew from Low to Critical — click a bar to open those tasks"
          data={tasksByPriority}
          formatLabel={formatLabel}
          variant="bar"
          colorForKey={(key) => priorityFill(key as Priority)}
          linkForKey={(key) => `/tasks?priority=${key}${scopeSuffix}`}
        />
        <CategoryBreakdownChart
          title="Top obstacles"
          description="What's actually blocking active work right now — click a slice to open those obstacles"
          data={topObstaclesByType}
          formatLabel={(key) => (key === OTHER_OBSTACLE_TYPES_KEY ? 'Other types' : obstacleTypeLabels[key as keyof typeof obstacleTypeLabels])}
          variant="pie"
          // Obstacle types are likewise non-semantic categories (FR-41).
          colorForKey={(key) => pieFill(Object.keys(topObstaclesByType).indexOf(key))}
          // The rollup bucket spans several types, so it links to the plain list.
          linkForKey={(key) => (key === OTHER_OBSTACLE_TYPES_KEY ? '/obstacles' : `/obstacles?type=${key}`)}
        />
        <Card>
          <CardHeader title="Vision Area progress" subheader="Average goal progress per area — lowest first is what needs attention" />
          <CardContent>
            {visionAreaProgress.length === 0 ? (
              <EmptyState>No vision areas yet.</EmptyState>
            ) : (
              <Box sx={{ width: '100%', height: Math.max(220, visionAreaProgress.length * 44) }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={visionAreaProgress} layout="vertical" margin={{ left: 8, right: 16 }}>
                    <CartesianGrid horizontal={false} />
                    <XAxis type="number" domain={[0, 100]} tickFormatter={(value) => `${value}%`} tickLine={false} axisLine={false} />
                    <YAxis type="category" dataKey="name" tickLine={false} axisLine={false} width={120} />
                    <RechartsTooltip content={<ChartTooltipContent />} />
                    <Bar dataKey="progress" name="Progress %" radius={4} fill={chartPrimary} />
                  </BarChart>
                </ResponsiveContainer>
              </Box>
            )}
          </CardContent>
        </Card>
      </Box>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: 'repeat(2, 1fr)' } }}>
      <Card>
        <CardHeader title="Partner engagement" subheader="Where partners sit in the pipeline, from first contact to done — click a stage to open those partners" />
        <CardContent>
          {totalPartners === 0 ? (
            <EmptyState>No partners yet.</EmptyState>
          ) : (
            <>
              <Box sx={{ width: '100%', height: 72 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={partnerPipelineData} layout="vertical" margin={{ left: 0, right: 0, top: 0, bottom: 0 }}>
                    <XAxis type="number" hide />
                    <YAxis type="category" dataKey="name" hide />
                    <RechartsTooltip content={<ChartTooltipContent />} />
                    {PARTNER_STATUS_ORDER.map((status) => (
                      <Bar
                        key={status}
                        dataKey={status}
                        stackId="pipeline"
                        name={partnerStatusLabels[status]}
                        fill={partnerStatusFill(status)}
                        cursor="pointer"
                        onClick={() => navigate(`/partners?status=${status}`)}
                      />
                    ))}
                  </BarChart>
                </ResponsiveContainer>
              </Box>
              <Stack direction="row" spacing={1.5} sx={{ flexWrap: 'wrap', justifyContent: 'center', pt: 1.5 }}>
                {PARTNER_STATUS_ORDER.map((status) => (
                  <Stack
                    key={status}
                    component={Link}
                    to={`/partners?status=${status}`}
                    direction="row"
                    spacing={0.75}
                    sx={{ alignItems: 'center', minHeight: 24, px: 0.5, textDecoration: 'none', '&:hover .MuiTypography-root': { textDecoration: 'underline' } }}
                  >
                    <Box sx={{ width: 8, height: 8, borderRadius: '2px', bgcolor: partnerStatusFill(status) }} />
                    <Typography variant="caption" color="text.secondary">
                      {partnerStatusLabels[status]} ({partnerCounts[status]})
                    </Typography>
                  </Stack>
                ))}
              </Stack>
            </>
          )}
        </CardContent>
      </Card>
      <Card>
        <CardHeader title="Review cadence" subheader="Daily and weekly reviews logged over the last 12 weeks" />
        <CardContent>
          {cadenceReviewCount === 0 ? (
            <EmptyState>No daily or weekly reviews logged yet.</EmptyState>
          ) : (
            <Box sx={{ overflowX: 'auto' }}>
              <Box sx={{ display: 'inline-flex', gap: '3px' }}>
                {reviewHeatmapWeeks.map((week, weekIndex) => (
                  <Box key={week[0]?.date ?? weekIndex} sx={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
                    {week.map((day) => (
                      <Tooltip key={day.date} title={formatHeatmapTooltip(day)} arrow>
                        <Box
                          sx={{
                            width: 13,
                            height: 13,
                            borderRadius: '3px',
                            bgcolor: day.isFuture ? 'transparent' : HEATMAP_LEVEL_COLORS[heatmapLevel(day.count)],
                            border: day.isFuture ? '1px dashed' : 'none',
                            borderColor: 'divider',
                          }}
                        />
                      </Tooltip>
                    ))}
                  </Box>
                ))}
              </Box>
              <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', pt: 1.5 }}>
                <Typography variant="caption" color="text.secondary">Less</Typography>
                {HEATMAP_LEVEL_COLORS.map((color) => (
                  <Box key={color} sx={{ width: 13, height: 13, borderRadius: '3px', bgcolor: color }} />
                ))}
                <Typography variant="caption" color="text.secondary">More</Typography>
              </Stack>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', pt: 1 }}>
                {summary?.weeksWithDiligence ?? 0} of the last 12 weeks include a diligence checkup.
              </Typography>
            </Box>
          )}
        </CardContent>
      </Card>
      </Box>
      </>
      )}
    </PageSection>
  );
}

function formatLabel(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

type HeatmapDay = { date: string; count: number; isFuture: boolean };

function buildReviewHeatmapWeeks(countsByDate: Record<string, number>): HeatmapDay[][] {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const endOfWeek = new Date(today);
  endOfWeek.setDate(today.getDate() + (6 - today.getDay()));
  const totalDays = HEATMAP_WEEKS * 7;
  const start = new Date(endOfWeek);
  start.setDate(endOfWeek.getDate() - totalDays + 1);

  const days: HeatmapDay[] = [];
  for (let i = 0; i < totalDays; i += 1) {
    const date = new Date(start);
    date.setDate(start.getDate() + i);
    const iso = date.toISOString().slice(0, 10);
    days.push({ date: iso, count: countsByDate[iso] ?? 0, isFuture: date > today });
  }

  const weeks: HeatmapDay[][] = [];
  for (let i = 0; i < days.length; i += 7) {
    weeks.push(days.slice(i, i + 7));
  }
  return weeks;
}

function heatmapLevel(count: number) {
  if (count <= 0) {
    return 0;
  }
  if (count === 1) {
    return 1;
  }
  if (count === 2) {
    return 2;
  }
  if (count === 3) {
    return 3;
  }
  return 4;
}

function formatHeatmapTooltip(day: HeatmapDay) {
  if (day.isFuture) {
    return '';
  }
  const label = new Date(`${day.date}T00:00:00`).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
  const reviewWord = day.count === 1 ? 'review' : 'reviews';
  return `${label} — ${day.count} ${reviewWord}`;
}
