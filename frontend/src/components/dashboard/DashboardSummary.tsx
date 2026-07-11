import { Ban, CalendarClock, CalendarDays, CheckCircle2, CheckSquare, Compass, Flag, Sparkles, TrendingUp } from 'lucide-react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { DashboardCard } from './DashboardCard';
import type { DashboardSummary as DashboardSummaryData } from '../../types/vision';

type DashboardSummaryProps = {
  summary?: DashboardSummaryData | null;
};

function TileGroup({ label, columns, children }: { label: string; columns: number; children: React.ReactNode }) {
  return (
    <Box component="section">
      <Typography
        variant="overline"
        component="h3"
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
export function DashboardSummary({ summary }: DashboardSummaryProps) {
  return (
    <Box sx={{ display: 'grid', gap: 2.5 }}>
      <TileGroup label="Needs attention" columns={3}>
        <DashboardCard
          label="Overdue Tasks"
          value={summary?.overdueTasks ?? 0}
          icon={CalendarClock}
          tone={(summary?.overdueTasks ?? 0) > 0 ? 'critical' : 'neutral'}
        />
        <DashboardCard
          label="Blocked Tasks"
          value={summary?.blockedTasks ?? 0}
          icon={Ban}
          tone={(summary?.blockedTasks ?? 0) > 0 ? 'warning' : 'neutral'}
        />
        <DashboardCard
          label="Due This Week"
          value={summary?.tasksDueThisWeek ?? 0}
          icon={CalendarDays}
          tone={(summary?.tasksDueThisWeek ?? 0) > 0 ? 'warning' : 'neutral'}
        />
      </TileGroup>
      <TileGroup label="Portfolio overview" columns={3}>
        <DashboardCard label="Vision Areas" value={summary?.totalVisionAreas ?? 0} icon={Compass} />
        <DashboardCard label="Active Dreams" value={summary?.activeDreams ?? 0} icon={Sparkles} />
        <DashboardCard label="Active Goals" value={summary?.activeGoals ?? 0} icon={Flag} />
        <DashboardCard label="Open Tasks" value={summary?.activeTasks ?? 0} icon={CheckSquare} />
        <DashboardCard
          label="Completed Tasks"
          value={summary?.completedTasks ?? 0}
          icon={CheckCircle2}
          tone={(summary?.completedTasks ?? 0) > 0 ? 'positive' : 'neutral'}
        />
        <DashboardCard
          label="Average Progress"
          value={`${summary?.averageProgress ?? 0}%`}
          icon={TrendingUp}
          tone={(summary?.averageProgress ?? 0) >= 50 ? 'positive' : 'neutral'}
        />
      </TileGroup>
    </Box>
  );
}
