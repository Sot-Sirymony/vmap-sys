import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { tr } from '../../i18n/translate';
import type { PlanDraftContent, PlanStage, VisionStep } from '../../types/vision';
import { Button } from '../common/Button';
import { STAGES, countNew, readiness } from './draftModel';

type Props = {
  content: PlanDraftContent;
  existingSteps: VisionStep[];
  busy: boolean;
  onGoTo: (stage: PlanStage) => void;
  onAdd: () => void;
};

/**
 * Stage 6: what will be added and what still needs attention (PB-FR-04).
 * Problems the server would refuse block Add plan; the rest are worth a look.
 * The AI plan check on the draft comes with PB-FR-28.
 */
export function ReviewStage({ content, existingSteps, busy, onGoTo, onAdd }: Props) {
  const counts = countNew(content);
  const items = readiness(content, existingSteps);
  const errors = items.filter(({ item }) => item.level === 'error');
  const warnings = items.filter(({ item }) => item.level === 'warning');
  const stageLabel = (stage: PlanStage) => tr(STAGES.find((item) => item.id === stage)?.label ?? stage);

  return (
    <Stack spacing={2}>
      <Typography variant="body1">
        {counts.total === 0
          ? tr('Nothing new to add yet.')
          : counts.helpers + counts.risks > 0
            ? tr('Ready to add {goals} goal(s), {steps} step(s), {tasks} task(s), {helpers} helper(s) and {risks} risk(s).', counts)
            : tr('Ready to add {goals} goal(s), {steps} step(s) and {tasks} task(s).', counts)}
      </Typography>
      {errors.length > 0 && (
        <Alert severity="error">
          <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>{tr('Fix these before adding:')}</Typography>
          <Issues list={errors} stageLabel={stageLabel} onGoTo={onGoTo} />
        </Alert>
      )}
      {warnings.length > 0 && (
        <Alert severity="warning">
          <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>{tr('Worth a look:')}</Typography>
          <Issues list={warnings} stageLabel={stageLabel} onGoTo={onGoTo} />
        </Alert>
      )}
      {counts.total > 0 && errors.length === 0 && warnings.length === 0 && (
        <Alert severity="success">{tr('Everything new has an owner and a date.')}</Alert>
      )}
      <Box>
        <Button disabled={busy || counts.total === 0 || errors.length > 0} onClick={onAdd}>{tr('Add plan')}</Button>
      </Box>
      <Typography variant="caption" color="text.secondary">
        {tr('Everything is added together, or nothing is. You can undo right after.')}
      </Typography>
    </Stack>
  );
}

function Issues({ list, stageLabel, onGoTo }: {
  list: ReturnType<typeof readiness>;
  stageLabel: (stage: PlanStage) => string;
  onGoTo: (stage: PlanStage) => void;
}) {
  return (
    <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
      {list.map(({ item, values }, index) => (
        <Typography component="li" variant="body2" key={index}>
          {tr(item.message, values)}{' '}
          <Link component="button" type="button" onClick={() => onGoTo(item.stage)}>{tr('Go to {stage}', { stage: stageLabel(item.stage) })}</Link>
        </Typography>
      ))}
    </Box>
  );
}
