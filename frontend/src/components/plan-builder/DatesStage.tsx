import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import type { DraftIssue, PlanDraftContent } from '../../types/vision';
import { Button } from '../common/Button';
import { Loading } from '../common/Loading';
import { type DraftUpdate, applyIssueFix, applyParentDateFix } from './draftModel';
import { useRuleCheck } from './useRuleCheck';

const day = (value: string) => formatDate(new Date(`${value}T00:00:00`), { year: true });

type Props = {
  content: PlanDraftContent;
  onChange: (update: DraftUpdate) => void;
  dreamId: number;
  token: string;
  language: string;
  saveDraft: () => Promise<void>;
  /** Saves a later target date on the dream itself (MOVE_DREAM_DATE). */
  onMoveDreamDate: (date: string) => Promise<void>;
  dreamDate?: string | null;
};

/**
 * V31 v1 step 2 (BRD 31 stage 7, DC-FR-41 to DC-FR-45): every date conflict
 * (rule R-04) across the dream, goals, steps and tasks, from the server's
 * rules, run again a second after each change. Each conflict offers "bring it
 * forward" and, when the parent can move (a new goal or step, or a dream whose
 * date follows its goals), "move the parent later". Moving a parent re-runs
 * the rules, so any conflict it makes one level up shows next (DC-FR-43).
 */
export function DatesStage({ content, onChange, dreamId, token, language, saveDraft, onMoveDreamDate, dreamDate }: Props) {
  const { report, issues, current, checking, error } = useRuleCheck({ content, dreamId, token, language, saveDraft, dreamDate });
  const conflicts = issues.filter((issue) => issue.rule === 'R-04' && issue.level === 'MUST');

  async function moveParent(issue: DraftIssue) {
    if (issue.altFixAction === 'MOVE_DREAM_DATE' && issue.altFixValue) {
      await onMoveDreamDate(issue.altFixValue);
    } else {
      onChange((draft) => applyParentDateFix(draft, issue));
    }
  }

  return (
    <Stack spacing={2}>
      <Typography variant="body2">
        {tr('Dates build up from the bottom: no task is due after its step, no step after its goal, no goal after its dream.')}
      </Typography>
      {(checking || !current) && !error && <Box><Loading /><Typography variant="caption" color="text.secondary">{tr('Checking…')}</Typography></Box>}
      {error && <Alert severity="error">{error}</Alert>}
      {report && current && conflicts.length === 0 && <Alert severity="success">{tr('No date conflicts. Every date fits inside its parent.')}</Alert>}
      {conflicts.map((issue, index) => (
        <Box key={`${issue.kind}:${issue.itemKey ?? index}`}
          sx={{ borderLeft: 3, borderColor: 'error.main', pl: 1.5, display: 'grid', gap: 0.5 }}>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>{issue.itemTitle || tr('Untitled')}</Typography>
          <Typography variant="body2">{tr(issue.problem)}</Typography>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            {issue.fixAction === 'SET_DATE' && issue.fixValue && (
              <Button variant="secondary" onClick={() => onChange((draft) => applyIssueFix(draft, issue))}>
                {tr('Bring it forward to {date}', { date: day(issue.fixValue) })}
              </Button>
            )}
            {issue.altFixValue && (issue.altFixAction === 'MOVE_PARENT_DATE' || issue.altFixAction === 'MOVE_DREAM_DATE') && (
              <Button variant="secondary" onClick={() => void moveParent(issue)}>
                {issue.altFixAction === 'MOVE_DREAM_DATE'
                  ? tr('Move the dream to {date}', { date: day(issue.altFixValue) })
                  : tr('Move the parent to {date}', { date: day(issue.altFixValue) })}
              </Button>
            )}
          </Box>
          {!issue.altFixAction && issue.fixAction === 'SET_DATE' && (
            <Typography variant="caption" color="text.secondary">
              {tr('Its parent is already saved or has a fixed deadline, so only this date can move here.')}
            </Typography>
          )}
        </Box>
      ))}
    </Stack>
  );
}
