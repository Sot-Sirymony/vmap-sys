import { useCallback, useEffect, useState } from 'react';
import { CheckCircle2, Circle, Lock } from 'lucide-react';
import { useNavigate } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardContent from '@mui/material/CardContent';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { getChainAssessments } from '../api/chainAssessmentApi';
import { listModules } from '../api/moduleApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { ProgressBar } from '../components/common/ProgressBar';
import { useAuth } from '../context/AuthContext';
import type { LearningModuleSummary, ModuleStage, ModuleStatus } from '../types/vision';
import { useI18n } from '../i18n/I18nContext';
import { PageSection } from './PageSection';
import { HelpWithThis, PageAssistantProvider, PageHelpButton } from '../components/ai/page-assistant/PageAssistant';
import { JourneyCard, ModuleNextCard } from '../components/modules/ModulesListHelpers';

const STAGE_LABELS: Record<ModuleStage, string> = {
  BREAK_CHAINS: 'Break barriers',
  BOOST: 'Build momentum',
  LAUNCH: 'Launch',
};

const STATUS_ICONS: Record<ModuleStatus, typeof Lock> = {
  LOCKED: Lock,
  AVAILABLE: Circle,
  COMPLETED: CheckCircle2,
};

/** FR-72: the fifteen modules in order. Finishing one unlocks the next. */
export function ModulesPage() {
  const { t, language } = useI18n();
  const { token, user } = useAuth();
  const navigate = useNavigate();
  const [modules, setModules] = useState<LearningModuleSummary[] | null>(null);
  const [hasBaseline, setHasBaseline] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!token) {
      return;
    }
    setError('');
    try {
      const [nextModules, assessments] = await Promise.all([listModules(token, language), getChainAssessments(token)]);
      setModules(nextModules);
      setHasBaseline(assessments.assessments.length > 0);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : t('Unable to load the modules.'));
    }
  }, [token, language]);

  useEffect(() => {
    void load();
  }, [load]);

  const completed = modules?.filter((module) => module.status === 'COMPLETED').length ?? 0;
  const total = modules?.length ?? 0;

  return (
    // V15 (AH-FR-01): the page assistant on the Modules list.
    <PageAssistantProvider page="MODULES">
    <PageSection
      title={t('Modules')}
      subtitle={t('Fifteen short lessons, each with an exercise that moves your plan forward')}
      actions={(
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          <PageHelpButton />
          {user?.role === 'ADMIN' && <Button variant="secondary" onClick={() => navigate('/settings/module-content')}>{t('Edit content')}</Button>}
        </Box>
      )}
    >
      {error && <ErrorMessage message={error} onRetry={() => void load()} />}
      {!modules && !error && <Loading />}
      {modules && (
        <Stack spacing={2}>
          {!hasBaseline && (
            <Alert severity="info" action={<Button variant="secondary" onClick={() => navigate('/assessment')}>{t('Take it')}</Button>}>
              {t('Take the assessment first, so you can measure how far you come.')}
            </Alert>
          )}
          {token && (
            // V15 Phase 4 (AH-FR-13 to AH-FR-15): which module next, and the journey so far.
            <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'flex-start' }}>
              <Box sx={{ flex: '1 1 280px' }}><ModuleNextCard token={token} /></Box>
              <Box sx={{ flex: '1 1 280px' }}><JourneyCard token={token} /></Box>
            </Box>
          )}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
            <HelpWithThis area="list" label={t('the Modules list')} />
            <ProgressBar value={total === 0 ? 0 : Math.round((completed / total) * 100)} />
            <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0 }}>{t('{done} of {total} done', { done: completed, total })}</Typography>
          </Box>
          {modules.map((module) => {
            const Icon = STATUS_ICONS[module.status];
            const body = (
              <CardContent sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
                <Box sx={{ mt: 0.25, flexShrink: 0 }}><Icon size={18} aria-hidden /></Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    {module.position}. {t(STAGE_LABELS[module.stage])}{module.status === 'LOCKED' ? ` · ${t('Locked')}` : ''}{module.status === 'COMPLETED' ? ` · ${t('Done')}` : ''}
                  </Typography>
                  <Typography sx={{ fontWeight: 600 }}>{module.title}</Typography>
                  <Typography variant="body2" color="text.secondary">{module.summary}</Typography>
                </Box>
              </CardContent>
            );
            return (
              <Card key={module.number} sx={module.status === 'LOCKED' ? { opacity: 0.65 } : undefined}>
                {module.status === 'LOCKED' ? body : (
                  <CardActionArea onClick={() => navigate(`/modules/${module.number}`)}>{body}</CardActionArea>
                )}
              </Card>
            );
          })}
        </Stack>
      )}
    </PageSection>
    </PageAssistantProvider>
  );
}
