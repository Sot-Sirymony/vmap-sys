import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { listStreamlineCandidates } from '../../api/streamlineApi';
import { tr } from '../../i18n/translate';
import { StreamlineCandidates } from '../../pages/StreamlineListPage';
import type { StreamlineCandidate, StreamlineDashboard } from '../../types/streamline';
import { days } from '../streamline/streamlineLabels';
import { ChartCard } from './ChartCard';
import { tabular } from './surface';

/**
 * V27 SR-FR-13: the dashboard's Streamline card — open reviews, what was cut and
 * brought back this quarter (counts, Q-27-7), the median days from start to
 * done with an eight-week trend (and View as table), and one suggestion (§6.2).
 */
export function StreamlineCard({ token, streamline }: { token: string | null; streamline: StreamlineDashboard | null | undefined }) {
  const navigate = useNavigate();
  const [candidates, setCandidates] = useState<StreamlineCandidate[]>([]);

  useEffect(() => {
    if (!token) return;
    listStreamlineCandidates(token).then(setCandidates).catch(() => setCandidates([]));
  }, [token]);

  if (!streamline) return null;
  const trend = streamline.cycleTrend;
  const max = Math.max(1, ...trend.map((point) => Number(point.medianDays ?? 0)));
  const figures: [string, string][] = [
    [tr('Open reviews'), String(streamline.openReviews)],
    [tr('Cut this quarter'), String(streamline.cutThisQuarter)],
    [tr('Brought back'), String(streamline.addedBackThisQuarter)],
    [tr('Median days, start to done'), days(streamline.medianCycleDays)],
  ];

  return (
    <ChartCard
      title={tr('Streamline')}
      caption={tr('Trim plans before you speed them up or automate them')}
      action={<Box component={Link} to="/streamline" sx={{ color: 'var(--primary)', fontSize: '0.8125rem', fontWeight: 500, textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}>{tr('All reviews')}</Box>}
      table={
        <Box component="table" sx={{ width: '100%', borderCollapse: 'collapse', ...tabular }}>
          <caption style={{ textAlign: 'left' }}>{tr('Median days from start to done, by week')}</caption>
          <thead><tr><th scope="col">{tr('Week ending')}</th><th scope="col">{tr('Median')}</th><th scope="col">{tr('Tasks finished')}</th></tr></thead>
          <tbody>
            {trend.map((point) => (
              <tr key={point.weekEnd}><td>{new Date(point.weekEnd).toLocaleDateString()}</td><td>{days(point.medianDays)}</td><td>{point.tasks}</td></tr>
            ))}
          </tbody>
        </Box>
      }
    >
      <Box sx={{ display: 'grid', gap: 1.5 }}>
        <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 1 }}>
          {figures.map(([label, value]) => (
            <Box key={label}>
              <Typography component="dt" variant="caption" sx={{ color: 'var(--text-secondary)' }}>{label}</Typography>
              <Typography component="dd" variant="body1" sx={{ m: 0, fontWeight: 600, ...tabular }}>{value}</Typography>
            </Box>
          ))}
        </Box>
        <Box aria-hidden="true" sx={{ display: 'flex', alignItems: 'flex-end', gap: '6px', height: 48 }}>
          {trend.map((point) => (
            <Box key={point.weekEnd} sx={{
              flex: 1,
              maxWidth: 16,
              height: point.medianDays === null ? 2 : `${Math.max(6, (Number(point.medianDays) / max) * 100)}%`,
              bgcolor: point.medianDays === null ? 'var(--divider)' : 'var(--chart-1)',
              borderRadius: 'var(--style-chart-bar-radius) var(--style-chart-bar-radius) 0 0',
            }} />
          ))}
        </Box>
        <StreamlineCandidates token={token} limit={1}
          candidates={streamline.nudge ? [streamline.nudge] : candidates}
          title={streamline.nudge ? tr("This week's suggestion") : undefined}
          onStart={(candidate) => navigate(candidate.anchorType === 'GOAL' ? `/streamline/new?goalId=${candidate.anchorId}` : `/streamline/new?stepId=${candidate.anchorId}`)} />
      </Box>
    </ChartCard>
  );
}
