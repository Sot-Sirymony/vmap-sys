import { Ban, CalendarClock, CalendarDays, CheckCircle2, CheckSquare, Compass, Flag, Rocket, Sparkles, TrendingUp } from 'lucide-react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

import { DashboardCard } from './DashboardCard';
import type { DashboardSummary as DashboardSummaryData } from '../../types/vision';

type DashboardSummaryProps = {
  summary?: DashboardSummaryData | null;
  // Lowercase phrase for the two windowed tiles, e.g. "this month".
  periodLabel: string;
  // Link for the "Due" tile — the tasks board filtered to the same window.
  dueInPeriodLink: string;
  // The dashboard's vision-area scope; when set, tile links carry it so the
  // target page reproduces the scoped counts.
  visionAreaId?: string;
};

// Appends the vision-area scope to a drill-down link when the dashboard is
// filtered; otherwise the link keeps only its own filter.
function scoped(base: string, visionAreaId?: string) {
  if (!visionAreaId) {
    return base;
  }
  return `${base}${base.includes('?') ? '&' : '?'}visionAreaId=${visionAreaId}`;
}

function TileGroup({ label, columns, children }: { label: string; columns: number; children: React.ReactNode }) {
  return (
    <Box component="section">
      <Typography
        variant="overline"
        component="h2"
        sx={{ color: 'text.secondary', fontWeight: 600, letterSpacing: '0.05em', lineHeight: 1, display: 'block', mb: 1.5 }}
      >
        {label}
      </Typography>
      <Box
        sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: `repeat(${columns}, 1fr)` },
        }}
      >
        {children}
      </Box>
    </Box>
  );
}

/**
 * KPI tiles split into two clusters: the three counts that demand action
 * (overdue, blocked, due soon) lead the page, followed by the six
 * portfolio-level counts. 3- and 3x2-column grids keep both rows exact,
 * with no orphaned tile (the old flat 9-tile grid always left one
 * hanging alone in a half-empty last row).
 */
export function DashboardSummary({ summary, periodLabel, dueInPeriodLink, visionAreaId }: DashboardSummaryProps) {
  return (
    <Box sx={{ display: 'grid', gap: 2.5 }}>
      {/*
        A tile links only where a filter reproduces its number exactly. "Active
        Goals" counts IN_PROGRESS *or* NOT_STARTED and "Open Tasks" counts
        everything not completed — no single-status filter matches either, and a
        tile that lands on a different number than it advertised is worse than
        one that doesn't link at all. Average Progress also stays unlinked — it's
        an aggregate, not a set of rows. Due This Week now links via the tasks
        due-date range (BRD C-6).
      */}
      <TileGroup label="Needs attention" columns={3}>
        <DashboardCard
          label="Overdue Tasks"
          value={summary?.overdueTasks ?? 0}
          icon={CalendarClock}
          tone={(summary?.overdueTasks ?? 0) > 0 ? 'critical' : 'neutral'}
          to={scoped('/tasks?overdue=true', visionAreaId)}
        />
        <DashboardCard
          label="Blocked Tasks"
          value={summary?.blockedTasks ?? 0}
          icon={Ban}
          tone={(summary?.blockedTasks ?? 0) > 0 ? 'warning' : 'neutral'}
          to={scoped('/tasks?status=BLOCKED', visionAreaId)}
        />
        <DashboardCard
          label={`Due ${periodLabel}`}
          value={summary?.tasksDueInPeriod ?? 0}
          icon={CalendarDays}
          tone={(summary?.tasksDueInPeriod ?? 0) > 0 ? 'warning' : 'neutral'}
          to={dueInPeriodLink}
        />
      </TileGroup>
      <TileGroup label="Portfolio overview" columns={3}>
        <DashboardCard label="Vision Areas" value={summary?.totalVisionAreas ?? 0} icon={Compass} to="/vision-areas" />
        <DashboardCard
          label="Active Dreams"
          value={summary?.activeDreams ?? 0}
          icon={Sparkles}
          to={scoped('/dreams?status=ACTIVE', visionAreaId)}
        />
        <DashboardCard label="Active Goals" value={summary?.activeGoals ?? 0} icon={Flag} />
        <DashboardCard label="Open Tasks" value={summary?.activeTasks ?? 0} icon={CheckSquare} />
        {/*
          Completed is scoped to the period by completion date. It doesn't link:
          the tasks board filters by *due* date, not completion date, so no
          filter reproduces "completed this month" — and a tile that links to a
          different number is worse than one that doesn't link.
        */}
        <DashboardCard
          label={`Completed ${periodLabel}`}
          value={summary?.completedTasksInPeriod ?? 0}
          icon={CheckCircle2}
          tone={(summary?.completedTasksInPeriod ?? 0) > 0 ? 'positive' : 'neutral'}
        />
        <DashboardCard
          label="Average Progress"
          value={`${summary?.averageProgress ?? 0}%`}
          icon={TrendingUp}
          tone={(summary?.averageProgress ?? 0) >= 50 ? 'positive' : 'neutral'}
        />
      </TileGroup>
      {(summary?.moonshotGoals ?? 0) > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, color: 'var(--moonshot-fg)', mt: -0.5 }}>
          <Rocket size={14} />
          <Typography variant="caption" sx={{ fontWeight: 600 }}>
            {summary?.moonshotGoals} of your goals {summary?.moonshotGoals === 1 ? 'is a moonshot' : 'are moonshots'}.
          </Typography>
        </Box>
      )}
      {(summary?.moonshotDreams ?? 0) > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, color: 'var(--moonshot-fg)', mt: -1 }}>
          <Rocket size={14} />
          <Typography variant="caption" sx={{ fontWeight: 600 }}>
            {summary?.moonshotDreams} of your dreams {summary?.moonshotDreams === 1 ? 'is a moonshot' : 'are moonshots'}.
          </Typography>
        </Box>
      )}
    </Box>
  );
}
