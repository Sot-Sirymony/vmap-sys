import { useState, type ReactNode } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, Legend, ResponsiveContainer, Tooltip as RechartsTooltip, XAxis, YAxis } from 'recharts';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { ChartTooltipContent } from '../dashboard/ChartTooltipContent';
import { chartPrimary, statusColors } from '../../theme';
import type { ManagementReport, ObstacleType, Priority, WorkStatus } from '../../types/vision';
import { obstacleTypeLabels, priorityLabels, workStatusLabels } from '../../utils/enumLabels';
import { tr } from '../../i18n/translate';

/**
 * V18 MR-FR-08 to MR-FR-10, MR-FR-13: the Summary tab's charts. Each sits in a
 * card with a title, a one-line text summary and a "Show as table" switch
 * with the same numbers, so no figure depends on reading the chart.
 */

type Row = { label: string; values: (string | number)[] };

export function ReportChartCard({ title, summary, headers, rows, height = 240, children }: Readonly<{
  title: string;
  summary: string;
  headers: string[];
  rows: Row[];
  height?: number;
  children: ReactNode;
}>) {
  const [asTable, setAsTable] = useState(false);
  return (
    <Card component="section" aria-label={title}>
      <CardHeader
        title={title}
        titleTypographyProps={{ variant: 'subtitle1', component: 'h3', fontWeight: 600 }}
        action={(
          <Link component="button" type="button" variant="body2" aria-pressed={asTable} onClick={() => setAsTable((current) => !current)}>
            {asTable ? tr('Show as chart') : tr('Show as table')}
          </Link>
        )}
      />
      <CardContent sx={{ pt: 0 }}>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{summary}</Typography>
        {asTable ? (
          <Box component="table" sx={{ width: '100%', borderCollapse: 'collapse', '& th, & td': { textAlign: 'left', py: 0.5, pr: 2 } }}>
            <thead>
              <tr>{headers.map((header) => <th scope="col" key={header}>{header}</th>)}</tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.label}>
                  <td>{row.label}</td>
                  {row.values.map((value, index) => <td key={index}>{value}</td>)}
                </tr>
              ))}
            </tbody>
          </Box>
        ) : (
          <Box sx={{ height }} aria-hidden="true">{children}</Box>
        )}
      </CardContent>
    </Card>
  );
}

const STATUS_ORDER: readonly WorkStatus[] = ['NOT_STARTED', 'IN_PROGRESS', 'WAITING', 'BLOCKED', 'COMPLETED', 'PAUSED'];

/** MR-FR-08: tasks by status, coloured by the theme's status palette, named on the axis. */
export function TasksByStatusChart({ report }: Readonly<{ report: ManagementReport }>) {
  const data = STATUS_ORDER.map((status) => ({ key: status, name: tr(workStatusLabels[status]), tasks: report.tasksByStatus[status] ?? 0 }));
  return (
    <ReportChartCard
      title={tr('Tasks by status')}
      summary={tr('{completed} of {total} tasks are completed.', { completed: report.headline.completedTasks, total: report.headline.totalTasks })}
      headers={[tr('Status'), tr('Tasks')]}
      rows={data.map((row) => ({ label: row.name, values: [row.tasks] }))}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ left: 8, right: 16 }}>
          <CartesianGrid horizontal={false} />
          <XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} />
          <YAxis type="category" dataKey="name" tickLine={false} axisLine={false} width={110} />
          <RechartsTooltip content={<ChartTooltipContent />} />
          <Bar dataKey="tasks" name={tr('Tasks')} radius={4}>
            {data.map((row) => <Cell key={row.key} fill={statusColors[row.key]} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </ReportChartCard>
  );
}

const percentTick = (value: number) => `${value}%`;

/** MR-FR-08: progress (%) per goal, in the goal health table's order. One series, so no legend. */
export function GoalProgressChart({ report }: Readonly<{ report: ManagementReport }>) {
  const data = report.goals.map((goal) => ({ name: goal.goalTitle, progress: Number(goal.progress) }));
  return (
    <ReportChartCard
      title={tr('Progress by goal')}
      summary={tr('Overall progress is {percent}% across {goals} goal(s).', { percent: report.headline.overallProgress, goals: report.goals.length })}
      headers={[tr('Goal'), tr('Progress')]}
      rows={data.map((row, index) => ({ label: `${index + 1}. ${row.name}`, values: [`${row.progress}%`] }))}
      height={Math.max(160, data.length * 32 + 40)}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ left: 8, right: 16 }}>
          <CartesianGrid horizontal={false} />
          <XAxis type="number" domain={[0, 100]} tickFormatter={percentTick} tickLine={false} axisLine={false} />
          <YAxis type="category" dataKey="name" tickLine={false} axisLine={false} width={160} />
          <RechartsTooltip content={<ChartTooltipContent />} />
          <Bar dataKey="progress" name={tr('Progress %')} radius={4} fill={chartPrimary} />
        </BarChart>
      </ResponsiveContainer>
    </ReportChartCard>
  );
}

/** MR-FR-09: open tasks by priority, on track and overdue. Two series, so a legend names them. */
export function PriorityChart({ report }: Readonly<{ report: ManagementReport }>) {
  const data = report.openTasksByPriority.map((row) => ({
    key: row.priority,
    name: tr(priorityLabels[row.priority as Priority]),
    onTrack: row.onTrack,
    overdue: row.overdue,
  }));
  const open = data.reduce((sum, row) => sum + row.onTrack + row.overdue, 0);
  const urgent = data.filter((row) => row.key === 'HIGH' || row.key === 'CRITICAL').reduce((sum, row) => sum + row.onTrack + row.overdue, 0);
  return (
    <ReportChartCard
      title={tr('Open tasks by priority')}
      summary={tr('{open} open task(s), {urgent} of them high or critical.', { open, urgent })}
      headers={[tr('Priority'), tr('On track'), tr('Overdue')]}
      rows={data.map((row) => ({ label: row.name, values: [row.onTrack, row.overdue] }))}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ left: 0, right: 16 }}>
          <CartesianGrid vertical={false} />
          <XAxis dataKey="name" tickLine={false} axisLine={false} />
          <YAxis allowDecimals={false} tickLine={false} axisLine={false} width={32} />
          <RechartsTooltip content={<ChartTooltipContent />} />
          <Legend />
          <Bar dataKey="onTrack" name={tr('On track')} stackId="open" fill={chartPrimary} />
          <Bar dataKey="overdue" name={tr('Overdue')} stackId="open" fill={statusColors.DECLINED} radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </ReportChartCard>
  );
}

/** MR-FR-10: open blockers by kind, plus Blocked tasks. One series. */
export function BlockersChart({ report }: Readonly<{ report: ManagementReport }>) {
  const data = Object.entries(report.blockersByKind).map(([kind, count]) => ({
    name: tr(obstacleTypeLabels[kind as ObstacleType] ?? kind),
    blockers: count ?? 0,
  }));
  return (
    <ReportChartCard
      title={tr('Blocked items')}
      summary={tr('{blocked} blocked task(s) and {blockers} open blocker(s) on steps or tasks.', {
        blocked: report.headline.blockedTasks,
        blockers: report.headline.openBlockers,
      })}
      headers={[tr('Kind'), tr('Open blockers')]}
      rows={data.map((row) => ({ label: row.name, values: [row.blockers] }))}
      height={Math.max(120, data.length * 32 + 40)}
    >
      {data.length === 0 ? (
        <Typography variant="body2" sx={{ pt: 2 }}>{tr('No open blockers right now.')}</Typography>
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} layout="vertical" margin={{ left: 8, right: 16 }}>
            <CartesianGrid horizontal={false} />
            <XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} />
            <YAxis type="category" dataKey="name" tickLine={false} axisLine={false} width={110} />
            <RechartsTooltip content={<ChartTooltipContent />} />
            <Bar dataKey="blockers" name={tr('Open blockers')} radius={4} fill={chartPrimary} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ReportChartCard>
  );
}
