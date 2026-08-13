import { Ban, CalendarClock, CalendarDays, Rocket } from 'lucide-react';
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

/**
 * The comp's KPI split: the three counts that demand action (overdue, blocked,
 * due soon) stack on the left as rail tiles, and the six portfolio counts fill
 * a 3×2 grid on the right. On narrow screens both clusters flatten into plain
 * columns.
 */
export function DashboardSummary({ summary, periodLabel, dueInPeriodLink, visionAreaId }: DashboardSummaryProps) {
  const averageProgress = Math.min(100, Math.max(0, summary?.averageProgress ?? 0));

  return (
    <Box sx={{ display: 'grid', gap: 2 }}>
      {/*
        A tile links only where a filter reproduces its number exactly. "Active
        Goals" counts IN_PROGRESS *or* NOT_STARTED and "Open Tasks" counts
        everything not completed — no single-status filter matches either, and a
        tile that lands on a different number than it advertised is worse than
        one that doesn't link at all. Average Progress also stays unlinked — it's
        an aggregate, not a set of rows. Due This Week now links via the tasks
        due-date range (BRD C-6).
      */}
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: 'repeat(12, 1fr)' } }}>
        <Box
          component="section"
          aria-label="Task pressure"
          sx={{
            gridColumn: { lg: 'span 4' },
            display: 'grid',
            gap: 1.5,
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)', lg: '1fr' },
          }}
        >
          <DashboardCard
            label="Overdue Tasks"
            value={summary?.overdueTasks ?? 0}
            icon={CalendarClock}
            variant="rail"
            tone={(summary?.overdueTasks ?? 0) > 0 ? 'critical' : 'neutral'}
            to={scoped('/tasks?overdue=true', visionAreaId)}
          />
          <DashboardCard
            label="Blocked Tasks"
            value={summary?.blockedTasks ?? 0}
            icon={Ban}
            variant="rail"
            tone={(summary?.blockedTasks ?? 0) > 0 ? 'warning' : 'neutral'}
            to={scoped('/tasks?status=BLOCKED', visionAreaId)}
          />
          {/* The comp keeps this tile on the accent rail: due-soon is workload,
              not an alarm, so it stays neutral regardless of the count. */}
          <DashboardCard
            label={`Due ${periodLabel}`}
            value={summary?.tasksDueInPeriod ?? 0}
            icon={CalendarDays}
            variant="rail"
            to={dueInPeriodLink}
          />
        </Box>
        <Box
          component="section"
          aria-label="Portfolio overview"
          sx={{
            gridColumn: { lg: 'span 8' },
            display: 'grid',
            gap: 1.5,
            gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(3, 1fr)' },
          }}
        >
          <DashboardCard label="Vision Areas" value={summary?.totalVisionAreas ?? 0} to="/vision-areas" />
          <DashboardCard
            label="Active Dreams"
            value={summary?.activeDreams ?? 0}
            to={scoped('/dreams?status=ACTIVE', visionAreaId)}
          />
          <DashboardCard label="Active Goals" value={summary?.activeGoals ?? 0} />
          <DashboardCard label="Open Tasks" value={summary?.activeTasks ?? 0} />
          {/*
            Completed is scoped to the period by completion date. It doesn't link:
            the tasks board filters by *due* date, not completion date, so no
            filter reproduces "completed this month" — and a tile that links to a
            different number is worse than one that doesn't link.
          */}
          <DashboardCard
            label={`Completed ${periodLabel}`}
            value={summary?.completedTasksInPeriod ?? 0}
            tone={(summary?.completedTasksInPeriod ?? 0) > 0 ? 'positive' : 'neutral'}
          />
          <DashboardCard
            label="Average Progress"
            value={`${Math.round(summary?.averageProgress ?? 0)}%`}
            tone={(summary?.averageProgress ?? 0) >= 50 ? 'positive' : 'neutral'}
            extra={
              <Box
                aria-hidden
                sx={{
                  flex: 1,
                  maxWidth: 120,
                  height: 6,
                  borderRadius: 'var(--radius-pill)',
                  bgcolor: 'var(--background-subtle)',
                  border: '1px solid var(--border-soft)',
                  overflow: 'hidden',
                }}
              >
                <Box sx={{ width: `${averageProgress}%`, height: '100%', bgcolor: 'primary.main', borderRadius: 'var(--radius-pill)' }} />
              </Box>
            }
          />
        </Box>
      </Box>
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
