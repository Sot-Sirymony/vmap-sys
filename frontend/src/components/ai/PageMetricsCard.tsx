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
import { getPageMetrics } from '../../api/aiPageApi';
import { tr } from '../../i18n/translate';
import type { PageMetrics } from '../../types/vision';
import { Loading } from '../common/Loading';

/** AH-FR-55: plain names for the V15 features. */
const FEATURE_LABELS: Record<string, string> = {
  PAGE_ASSISTANT_CHAT: 'Page assistant chat',
  PAGE_ASSISTANT: 'Page assistant suggestions',
  MODULE_NEXT: 'Which module next',
  MODULE_HELP: 'Module helper',
  ASSESSMENT_EXPLAIN: 'Explain my result',
  ASSESSMENT_PRACTICE: 'Practise this chain',
  OBSTACLE_HELP: 'Obstacle helpers',
  MESSAGE_FIELD_HELP: 'Message field help',
  MESSAGE_CHECK: 'Check my message',
  MESSAGE_ADAPT: 'Adapt to this partner',
  MESSAGE_OBJECTIONS: 'Prepare for objections',
  MESSAGE_BODY: 'Rebuild the message',
  MEETING_PREP: 'Prepare for the meeting',
  THANK_YOU_DRAFT: 'Thank and report back',
  FOLLOW_UP_DRAFT: 'Draft a follow-up',
  REPLY_HELP: 'Read a reply',
  WORK_STYLE_EXPLAIN: 'What my result means',
  WORK_STYLE_TIPS: 'Put my style to work',
  WORK_STYLE_PARTNER: 'Working with a partner',
};

const percent = (value?: number | null) => (value === null || value === undefined ? '–' : `${Math.round(value * 100)}%`);
const dollars = (value: number) => `$${Number(value).toFixed(4)}`;

/**
 * V15 (AH-FR-56): the page assistant and helpers on the Modules, Assessment,
 * Obstacles, Communication and Work Style pages: requests, cost, rating and
 * how many cards were applied, edited or dismissed, per feature. Below, the
 * Phase 1 baselines for AHBO-01, AHBO-03 and AHBO-04. Counts only.
 */
export function PageMetricsCard({ token }: Readonly<{ token: string }>) {
  const [days, setDays] = useState(30);
  const [metrics, setMetrics] = useState<PageMetrics | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    setMetrics(null);
    setError('');
    getPageMetrics(token, days)
      .then(setMetrics)
      .catch((loadError) => setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load page assistant metrics.')));
  }, [token, days]);

  const baselines = metrics?.baselines;

  return (
    <Card>
      <CardHeader
        title={tr('Page assistant quality')}
        subheader={tr('AI help on the Modules, Assessment, Obstacles, Communication and Work Style pages.')}
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
        {metrics && metrics.features.length === 0 && (
          <Typography variant="body2" color="text.secondary">{tr('No page assistant requests in this period.')}</Typography>
        )}
        {metrics && metrics.features.length > 0 && (
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{tr('Feature')}</TableCell>
                  <TableCell align="right">{tr('Requests')}</TableCell>
                  <TableCell align="right">{tr('Suggested')}</TableCell>
                  <TableCell align="right">{tr('Applied')}</TableCell>
                  <TableCell align="right">{tr('Edited first')}</TableCell>
                  <TableCell align="right">{tr('Dismissed')}</TableCell>
                  <TableCell align="right">{tr('Helpful')}</TableCell>
                  <TableCell align="right">{tr('Cost')}</TableCell>
                  <TableCell align="right">{tr('Per request')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {metrics.features.map((row) => (
                  <TableRow key={row.feature}>
                    <TableCell>{tr(FEATURE_LABELS[row.feature] ?? row.feature)}</TableCell>
                    <TableCell align="right">{row.requests}</TableCell>
                    <TableCell align="right">{row.suggestedItems}</TableCell>
                    <TableCell align="right">{percent(row.appliedRate)}</TableCell>
                    <TableCell align="right">{row.editedItems}</TableCell>
                    <TableCell align="right">{row.dismissedItems}</TableCell>
                    <TableCell align="right">
                      {row.ratings === 0 ? '–' : tr('{rate} of {count}', { rate: percent(row.thumbsUpRate), count: row.ratings })}
                    </TableCell>
                    <TableCell align="right">{dollars(row.costUsd)}</TableCell>
                    <TableCell align="right">{dollars(row.averageCostUsd)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        )}
        {baselines && (
          <Box component="section" aria-label={tr('Baselines')} sx={{ mt: 2 }}>
            <Typography variant="subtitle2" component="h3">{tr('Baselines')}</Typography>
            <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
              <li>
                <Typography variant="body2">
                  {tr('Next module within 7 days: {done} of {total} completions ({rate})', {
                    done: baselines.nextModuleWithinSevenDays, total: baselines.moduleCompletions, rate: percent(baselines.nextModuleRate),
                  })}
                </Typography>
              </li>
              <li>
                <Typography variant="body2">
                  {tr('Open obstacles with a root cause, solution or task: {done} of {total} ({rate})', {
                    done: baselines.openObstaclesWithPlan, total: baselines.openObstacles, rate: percent(baselines.openObstaclesWithPlanRate),
                  })}
                </Typography>
              </li>
              <li>
                <Typography variant="body2">
                  {tr('Messages: {drafts} drafts, {sent} sent, {overdue} past their follow-up date', {
                    drafts: baselines.draftMessages, sent: baselines.sentMessages, overdue: baselines.followUpsOverdue,
                  })}
                </Typography>
              </li>
            </Box>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
