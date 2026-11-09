import { useNavigate } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Typography from '@mui/material/Typography';
import { tr } from '../../i18n/translate';
import type { LearningModuleDetail } from '../../types/vision';
import { Button } from '../common/Button';

/**
 * AH-FR-19: for a module whose work is done on another page, exactly what to
 * do and where. Program content, written once in English and Khmer; no AI.
 */
export const MODULE_ELSEWHERE: Record<number, { todo: string; page: string }> = {
  2: { todo: 'Log one fear in your Journal and plan the smallest safe test.', page: 'Journal' },
  4: { todo: 'Add your dreams under their vision areas, then give your most important ones a letter rank (A, B or C).', page: 'Dreams' },
  5: { todo: 'Give your top dream its goals and dated steps until the plan is ready.', page: 'Plan' },
  8: { todo: 'Stretch one goal: write a bolder version of it.', page: 'Goals' },
  9: { todo: 'Add one partner who could supply something your plan is missing.', page: 'Partners' },
  10: { todo: 'Log one attitude check-in on the Today page.', page: 'Today' },
  12: { todo: 'Add one habit that would move your plan, on the Today page.', page: 'Today' },
  13: { todo: 'Give your next step a date, on the Today page.', page: 'Today' },
  14: { todo: 'Write a passion statement for one dream: why it truly matters to you.', page: 'Dreams' },
  15: { todo: 'Sign your commitment on your top dream\'s Vision Map, with a start date.', page: 'Vision Map' },
};

/**
 * V15 (AH-FR-19): a module done elsewhere says what to do and where, whether
 * the participant's data already shows it, and opens that page.
 */
export function ModuleElsewhereCard({ module }: Readonly<{ module: LearningModuleDetail }>) {
  const navigate = useNavigate();
  const content = MODULE_ELSEWHERE[module.number];
  if (module.fields.length > 0 || !content || !module.linkPath) {
    return null;
  }
  const done = module.status === 'COMPLETED' || !module.requirementMessage;
  return (
    <Card component="section" aria-label={tr('Where to do this module')}>
      <CardHeader title={tr('Where to do this module')} />
      <CardContent sx={{ display: 'grid', gap: 1 }}>
        <Typography variant="body2">{tr(content.todo)}</Typography>
        {done
          ? <Alert severity="success">{tr('Your data already shows this. You can mark the module as done.')}</Alert>
          : <Alert severity="info">{tr('Still to do: {what}', { what: tr(module.requirementMessage ?? '') })}</Alert>}
        <Box>
          <Button variant="secondary" onClick={() => navigate(module.linkPath as string)}>
            {tr('Open {page}', { page: tr(content.page) })}
          </Button>
        </Box>
      </CardContent>
    </Card>
  );
}
