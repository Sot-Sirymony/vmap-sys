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
import { getPlanAccuracy } from '../../api/aiPlanApi';
import { tr } from '../../i18n/translate';
import type { PlanAccuracyMetrics } from '../../types/vision';
import { Loading } from '../common/Loading';
import { RULE_NAMES } from '../plan-builder/ReviewStage';

const percent = (value?: number | null) => (value === null || value === undefined ? '–' : `${Math.round(value * 100)}%`);

/**
 * V31 (BRD 31 section 7, DC-FR-65): how accurate Build my plan drafts are.
 * First check = the first time a draft was checked, before any fixing. Counts
 * only; no plan text is stored.
 */
export function PlanAccuracyCard({ token }: { token: string }) {
  const [days, setDays] = useState(30);
  const [metrics, setMetrics] = useState<PlanAccuracyMetrics | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    setMetrics(null);
    setError('');
    getPlanAccuracy(token, days)
      .then(setMetrics)
      .catch((loadError) => setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load plan accuracy.')));
  }, [token, days]);

  const figures = metrics ? [
    { label: tr('Drafts passing the first check'), value: percent(metrics.firstCheckPassRate), target: '60%',
      detail: tr('{count} draft(s) checked', { count: metrics.checkedSessions }) },
    { label: tr('Saved plans passing every rule'), value: percent(metrics.savedValidRate), target: '100%',
      detail: tr('{count} plan(s) saved', { count: metrics.savedPlans }) },
    { label: tr('AI dates that broke a date rule'), value: percent(metrics.aiDateConflictRate), target: '≤ 2%',
      detail: tr('{count} AI item(s) checked', { count: metrics.aiItemsChecked }) },
    { label: tr('AI owners removed as invented'), value: percent(metrics.inventedOwnerRate), target: '≤ 1%',
      detail: tr('{count} AI owner(s) seen', { count: metrics.aiOwners }) },
  ] : [];

  return (
    <Card>
      <CardHeader
        title={tr('Plan accuracy')}
        subheader={tr('How often Build my plan drafts meet the method rules, and how often the AI invents details.')}
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
          <>
            <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' }, mb: 2 }}>
              {figures.map((figure) => (
                <Box key={figure.label} sx={{ p: 1.5, border: 1, borderColor: 'divider', borderRadius: 'var(--style-radius-control)' }}>
                  <Typography variant="body2" color="text.secondary">{figure.label}</Typography>
                  <Typography variant="h5" component="p">{figure.value}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {tr('Target {target}', { target: figure.target })} · {figure.detail}
                  </Typography>
                </Box>
              ))}
            </Box>
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small" aria-label={tr('Rule results')}>
                <TableHead>
                  <TableRow>
                    <TableCell>{tr('Rule')}</TableCell>
                    <TableCell align="right">{tr('First checks')}</TableCell>
                    <TableCell align="right">{tr('Passed first time')}</TableCell>
                    <TableCell align="right">{tr('Warnings accepted at save')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {metrics.rules.map((rule) => (
                    <TableRow key={rule.rule}>
                      <TableCell>{rule.rule} · {tr(RULE_NAMES[rule.rule])}</TableCell>
                      <TableCell align="right">{rule.firstRuns}</TableCell>
                      <TableCell align="right">{percent(rule.firstPassRate)}</TableCell>
                      <TableCell align="right">{rule.acceptedShould}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          </>
        )}
      </CardContent>
    </Card>
  );
}
