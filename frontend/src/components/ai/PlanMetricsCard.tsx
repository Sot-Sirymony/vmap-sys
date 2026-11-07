import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { getPlanMetrics } from '../../api/aiPlanApi';
import { tr } from '../../i18n/translate';
import type { PlanMetrics } from '../../types/vision';
import { Loading } from '../common/Loading';

const FEATURE_LABELS: Record<string, string> = {
  AREA_BUILDER: 'Vision areas',
  DREAM_BUILDER: 'Dreams',
  STRETCH_DREAM: 'Moonshot dream',
  GOAL_BUILDER: 'Goals',
  STRETCH_GOAL: 'Stretch goal',
  PLAN_BUILDER: 'Steps',
  PLAN_CHECK: 'Plan check',
  CLARIFY: 'Clarify',
  TASK_BUILDER: 'Tasks',
  HELPERS_RISKS: 'Helpers and risks',
  DRAFT_CHECK: 'Check my draft',
  PLAN_FIX: 'Fix with AI',
  PLAN_ASSISTANT: 'Plan assistant',
  PLAN_ASSISTANT_CHAT: 'Plan assistant chat',
};

const percent = (value?: number | null) => (value === null || value === undefined ? '–' : `${Math.round(value * 100)}%`);
const dollars = (value: number) => `$${Number(value).toFixed(4)}`;

/**
 * V11 (PB-FR-36): how useful plan builder suggestions are, per feature.
 * Accepted = added as suggested or after editing, out of all suggested
 * (BO-08 target: 50%). Counts only; no suggestion text is stored.
 */
export function PlanMetricsCard({ token }: { token: string }) {
  const [days, setDays] = useState(30);
  const [metrics, setMetrics] = useState<PlanMetrics | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    setMetrics(null);
    setError('');
    getPlanMetrics(token, days)
      .then(setMetrics)
      .catch((loadError) => setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load plan builder metrics.')));
  }, [token, days]);

  return (
    <Card>
      <CardHeader
        title={tr('Plan builder quality')}
        subheader={tr('How many AI suggestions people keep, how they rate them, and what they cost.')}
        action={
          <Select size="small" value={days} onChange={(event) => setDays(Number(event.target.value))}
            SelectDisplayProps={{ 'aria-label': tr('Period') }}>
            <MenuItem value={7}>{tr('Last 7 days')}</MenuItem>
            <MenuItem value={30}>{tr('Last 30 days')}</MenuItem>
            <MenuItem value={90}>{tr('Last 90 days')}</MenuItem>
          </Select>
        }
      />
      <CardContent>
        {error && <Alert severity="error">{error}</Alert>}
        {!metrics && !error && <Loading />}
        {metrics && (
          <Typography variant="body2" sx={{ mb: 1.5 }}>
            {(metrics.finishedPlans ?? 0) === 0
              ? tr('No plans finished with Build my plan in this period.')
              : tr('{count} plan(s) finished with Build my plan: {requests} AI requests and {cost} each on average.', {
                count: metrics.finishedPlans ?? 0,
                requests: metrics.requestsPerFinishedPlan ?? 0,
                cost: dollars(metrics.costPerFinishedPlan ?? 0),
              })}
          </Typography>
        )}
        {metrics && metrics.features.length === 0 && (
          <Typography variant="body2" color="text.secondary">{tr('No plan builder suggestions in this period.')}</Typography>
        )}
        {metrics && metrics.features.length > 0 && (
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{tr('Feature')}</TableCell>
                  <TableCell align="right">{tr('Requests')}</TableCell>
                  <TableCell align="right">{tr('Suggested')}</TableCell>
                  <TableCell align="right">{tr('Accepted')}</TableCell>
                  <TableCell align="right">{tr('Edited first')}</TableCell>
                  <TableCell align="right">{tr('Helpful')}</TableCell>
                  <TableCell align="right">{tr('Cost')}</TableCell>
                  <TableCell align="right">{tr('Per request')}</TableCell>
                  <TableCell align="right">{tr('Requests per finished plan')}</TableCell>
                  <TableCell align="right">{tr('Cost per finished plan')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {metrics.features.map((row) => (
                  <TableRow key={row.feature}>
                    <TableCell>{tr(FEATURE_LABELS[row.feature] ?? row.feature)}</TableCell>
                    <TableCell align="right">{row.requests}</TableCell>
                    <TableCell align="right">{row.feature === 'PLAN_CHECK' ? '–' : row.suggestedItems}</TableCell>
                    <TableCell align="right">{percent(row.acceptanceRate)}</TableCell>
                    <TableCell align="right">{percent(row.editRate)}</TableCell>
                    <TableCell align="right">
                      {row.ratings === 0 ? '–' : tr('{rate} of {count}', { rate: percent(row.thumbsUpRate), count: row.ratings })}
                    </TableCell>
                    <TableCell align="right">{dollars(row.costUsd)}</TableCell>
                    <TableCell align="right">{dollars(row.averageCostUsd)}</TableCell>
                    <TableCell align="right">{row.requestsPerFinishedPlan ?? '–'}</TableCell>
                    <TableCell align="right">{row.costPerFinishedPlan == null ? '–' : dollars(row.costPerFinishedPlan)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
