import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { CircleCheck, CircleX, ListChecks, TriangleAlert, type LucideIcon } from 'lucide-react';
import { Link } from 'react-router';
import type { ReportFilter } from '../../api/reportApi';
import { statusColors } from '../../theme';
import type { ManagementReport, ReportHealth } from '../../types/vision';
import {
  ATTENTION_LABELS, HEALTH_LABELS, attentionLink, blockedTasksLink, healthParts, overdueTasksLink, unplacedLink,
} from '../../utils/managementReport';
import { DashboardCard } from '../dashboard/DashboardCard';
import { DataTable, type DataTableColumn } from '../common/DataTable';
import { ProgressBar } from '../common/ProgressBar';
import { BlockersChart, GoalProgressChart, PriorityChart, TasksByStatusChart } from './ReportCharts';
import { tr } from '../../i18n/translate';

const HEALTH_ICONS: Record<ReportHealth, { icon: LucideIcon; color: string }> = {
  RED: { icon: CircleX, color: statusColors.DECLINED },
  AMBER: { icon: TriangleAlert, color: statusColors.BLOCKED },
  GREEN: { icon: CircleCheck, color: statusColors.COMPLETED },
  DONE: { icon: ListChecks, color: statusColors.PAUSED },
};

const HEALTH_RANK: Record<ReportHealth, number> = { RED: 0, AMBER: 1, GREEN: 2, DONE: 3 };

/** MR-FR-11, MR-NFR-07: health as an icon and a text label, never colour alone. */
export function HealthBadge({ health }: Readonly<{ health: ReportHealth }>) {
  const { icon: Icon, color } = HEALTH_ICONS[health];
  return (
    <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, whiteSpace: 'nowrap' }}>
      <Icon size={16} color={color} aria-hidden="true" />
      <span>{tr(HEALTH_LABELS[health])}</span>
    </Box>
  );
}

type GoalRow = ManagementReport['goals'][number] & { id: number };

/** V18 MR-FR-07 to MR-FR-13: the Summary tab. */
export function ReportSummary({ report, filter }: Readonly<{ report: ManagementReport; filter: ReportFilter }>) {
  const { headline } = report;
  const goalRows: GoalRow[] = report.goals.map((goal) => ({ ...goal, id: goal.goalId }));
  const columns: DataTableColumn<GoalRow>[] = [
    ...(report.scope.kind === 'DREAM' ? [] : [{
      key: 'dream', label: tr('Dream'), sortValue: (row: GoalRow) => row.dreamTitle, render: (row: GoalRow) => row.dreamTitle,
    }]),
    { key: 'goal', label: tr('Goal'), sortValue: (row) => row.goalTitle, render: (row) => row.goalTitle },
    { key: 'health', label: tr('Health'), sortValue: (row) => HEALTH_RANK[row.health], render: (row) => <HealthBadge health={row.health} /> },
    {
      key: 'progress',
      label: tr('Progress'),
      sortValue: (row) => Number(row.progress),
      render: (row) => (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 120 }}>
          <Box sx={{ flex: 1 }}><ProgressBar value={Number(row.progress)} /></Box>
          <span>{Math.round(Number(row.progress))}%</span>
        </Box>
      ),
    },
    { key: 'target', label: tr('Target date'), sortValue: (row) => row.targetDate ?? '9999', render: (row) => row.targetDate ?? '—' },
    { key: 'overdue', label: tr('Overdue tasks'), sortValue: (row) => row.overdueTasks, render: (row) => row.overdueTasks },
    { key: 'blocked', label: tr('Blocked items'), sortValue: (row) => row.blockedItems, render: (row) => row.blockedItems },
    { key: 'next', label: tr('Next action'), render: (row) => row.nextAction ?? '—' },
  ];

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(3, 1fr)', lg: 'repeat(6, 1fr)' } }}>
        <DashboardCard label={tr('Overall progress')} value={`${headline.overallProgress}%`} />
        <DashboardCard
          label={tr('Goals by health')}
          value={report.goals.length}
          extra={(
            <Typography variant="caption" color="text.secondary">
              {healthParts(report).map((part) => `${part.count} ${tr(HEALTH_LABELS[part.health]).toLowerCase()}`).join(' · ')}
            </Typography>
          )}
        />
        <DashboardCard label={tr('Overdue tasks')} value={headline.overdueTasks} tone={headline.overdueTasks > 0 ? 'critical' : 'neutral'}
          to={headline.overdueTasks > 0 ? overdueTasksLink(filter) : undefined} />
        <DashboardCard label={tr('Blocked tasks')} value={headline.blockedTasks} tone={headline.blockedTasks > 0 ? 'warning' : 'neutral'}
          to={headline.blockedTasks > 0 ? blockedTasksLink(filter) : undefined} />
        <DashboardCard label={tr('Open blockers')} value={headline.openBlockers} tone={headline.openBlockers > 0 ? 'warning' : 'neutral'} />
        <DashboardCard label={tr('Due in 14 days')} value={headline.dueSoonTasks} />
      </Box>

      {report.unplacedObstacles > 0 && (
        <Alert severity="warning" action={<Link to={unplacedLink(filter)}>{tr('Show them')}</Link>}>
          {tr('{count} blocker(s) are not on a step yet, so they are not counted on any goal.', { count: report.unplacedObstacles })}
        </Alert>
      )}

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
        <GoalProgressChart report={report} />
        <TasksByStatusChart report={report} />
        <PriorityChart report={report} />
        <BlockersChart report={report} />
      </Box>

      <Card component="section" aria-label={tr('Goal health')}>
        <CardHeader title={tr('Goal health')} titleTypographyProps={{ variant: 'subtitle1', component: 'h3', fontWeight: 600 }}
          subheader={tr('Off track: overdue, or a blocked task or critical blocker. At risk: overdue work, an open blocker, or more than 20 points behind schedule.')} />
        <CardContent sx={{ pt: 0 }}>
          <DataTable rows={goalRows} columns={columns} emptyMessage={tr('No goals in this scope.')} defaultSortKey="health" />
        </CardContent>
      </Card>

      <Card component="section" aria-label={tr('Needs attention')}>
        <CardHeader title={tr('Needs attention')} titleTypographyProps={{ variant: 'subtitle1', component: 'h3', fontWeight: 600 }} />
        <CardContent sx={{ pt: 0 }}>
          {report.attention.length === 0 ? (
            <Typography variant="body2">{tr('Nothing needs attention right now.')}</Typography>
          ) : (
            <Box component="ol" sx={{ m: 0, pl: 3, display: 'grid', gap: 1 }}>
              {report.attention.map((item) => (
                <li key={`${item.itemType}-${item.itemId}-${item.kind}`}>
                  <Typography variant="body2" component="span" sx={{ fontWeight: 600 }}>{tr(ATTENTION_LABELS[item.kind])}: </Typography>
                  <Link to={attentionLink(item)}>{item.title}</Link>
                  <Typography variant="body2" component="span" color="text.secondary"> · {item.context}</Typography>
                </li>
              ))}
            </Box>
          )}
        </CardContent>
      </Card>
    </Stack>
  );
}
