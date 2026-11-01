import { useEffect, useState } from 'react';
import { CheckCircle2, Circle } from 'lucide-react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Typography from '@mui/material/Typography';
import { getProgressMetrics } from '../../api/accountApi';
import type { ProgressMetric } from '../../types/vision';
import { localDateIso } from '../../utils/localDate';
import { useI18n } from '../../i18n/I18nContext';

/** FR-75: the six objectives, measured for one person against real data. */
export function ProgressMetricsCard({ token }: { token: string | null }) {
  const { t } = useI18n();
  const [metrics, setMetrics] = useState<ProgressMetric[] | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token) {
      return;
    }
    getProgressMetrics(token, localDateIso())
      .then((response) => setMetrics(response.metrics))
      .catch((loadError: unknown) => setError(loadError instanceof Error ? loadError.message : t('Unable to load your progress.')));
  }, [token]);

  return (
    <Card>
      <CardHeader title={t('Your objectives')} subheader={t('How far you have come on the six results this method aims for')} />
      <CardContent>
        {error && <Typography variant="body2" color="error">{error}</Typography>}
        <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' } }}>
          {metrics?.map((metric) => (
            <Box key={metric.id} sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}>
              <Box sx={{ mt: 0.25, flexShrink: 0 }}>
                {metric.met ? <CheckCircle2 size={16} aria-label={t('Reached')} /> : <Circle size={16} aria-label={t('Not reached yet')} />}
              </Box>
              <Box>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>{t(metric.label)}: {t(metric.value)}</Typography>
                <Typography variant="caption" color="text.secondary">{t('Target: {text}', { text: t(metric.target) })}</Typography>
              </Box>
            </Box>
          ))}
        </Box>
      </CardContent>
    </Card>
  );
}
