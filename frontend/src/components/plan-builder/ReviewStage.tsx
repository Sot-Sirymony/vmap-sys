import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { checkPlanDraft, fixPlanDraft, savePlanCapacity } from '../../api/aiPlanApi';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import type { DraftCheck, DraftFix, DraftIssue, PlanCapacity, PlanDraftContent, PlanStage, VisionStep } from '../../types/vision';
import { Button } from '../common/Button';
import { Loading } from '../common/Loading';
import { SuggestionRating } from '../ai/SuggestionRating';
import { type DraftUpdate, STAGES, applyAiFix, applyIssueFix, countNew, overloadedWeeks, readiness, spreadDates } from './draftModel';

type Props = {
  content: PlanDraftContent;
  onChange: (update: DraftUpdate) => void;
  existingSteps: VisionStep[];
  busy: boolean;
  onGoTo: (stage: PlanStage) => void;
  onAdd: () => void;
  // V11 Phase 6.
  dreamId: number;
  token: string;
  language: string;
  aiReady: boolean;
  saveDraft: () => Promise<void>;
  capacity: PlanCapacity | null;
  onCapacityChange: (capacity: PlanCapacity) => void;
};

function summary(counts: ReturnType<typeof countNew>) {
  if (counts.total === 0) {
    return tr('Nothing new to add yet.');
  }
  if (counts.helpers + counts.risks > 0) {
    return tr('Ready to add {goals} goal(s), {steps} step(s), {tasks} task(s), {helpers} helper(s) and {risks} risk(s).', counts);
  }
  return tr('Ready to add {goals} goal(s), {steps} step(s) and {tasks} task(s).', counts);
}

/** The proposed change in words, or null when Claude had none. */
function describeChange(proposal: DraftFix) {
  if (proposal.newStepTitle) {
    return tr('Add the step "{step}"', { step: proposal.newStepTitle })
      + (proposal.newStepTargetDate ? ` · ${day(proposal.newStepTargetDate)}` : '');
  }
  if (proposal.title) {
    return tr('Rename to "{title}"', { title: proposal.title });
  }
  return proposal.targetDate ? tr('Move to {date}', { date: day(proposal.targetDate) }) : null;
}

const day = (value: string) => formatDate(new Date(`${value}T00:00:00`), { year: true });

/**
 * Stage 6: what will be added and what still needs attention (PB-FR-04).
 * Problems the server would refuse block Add plan; the rest are worth a look.
 * Check my draft runs the plan check on the draft before saving (PB-FR-28),
 * with one-click fixes and, for AI issues, a proposed change to apply or not
 * (PB-FR-29). Hours a week flags weeks with too much work (PB-FR-16).
 */
export function ReviewStage({
  content, onChange, existingSteps, busy, onGoTo, onAdd, dreamId, token, language, aiReady, saveDraft, capacity, onCapacityChange,
}: Props) {
  const counts = countNew(content);
  const items = readiness(content, existingSteps);
  const errors = items.filter(({ item }) => item.level === 'error');
  const warnings = items.filter(({ item }) => item.level === 'warning');
  const stageLabel = (stage: PlanStage) => tr(STAGES.find((item) => item.id === stage)?.label ?? stage);

  return (
    <Stack spacing={2}>
      <Typography variant="body1">{summary(counts)}</Typography>
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

      {counts.total > 0 && (
        <DraftChecker dreamId={dreamId} token={token} language={language} aiReady={aiReady} saveDraft={saveDraft}
          onChange={onChange} onGoTo={onGoTo} />
      )}
      <CapacityCheck content={content} onChange={onChange} capacity={capacity} onCapacityChange={onCapacityChange} token={token} />

      <Box>
        <Button disabled={busy || counts.total === 0 || errors.length > 0} onClick={onAdd}>{tr('Add plan')}</Button>
      </Box>
      <Typography variant="caption" color="text.secondary">
        {tr('Everything is added together, or nothing is. You can undo right after.')}
      </Typography>
    </Stack>
  );
}

/** PB-FR-28, PB-FR-29: the plan check on the draft, and its fixes. */
function DraftChecker({ dreamId, token, language, aiReady, saveDraft, onChange, onGoTo }: {
  dreamId: number;
  token: string;
  language: string;
  aiReady: boolean;
  saveDraft: () => Promise<void>;
  onChange: (update: DraftUpdate) => void;
  onGoTo: (stage: PlanStage) => void;
}) {
  const [check, setCheck] = useState<DraftCheck | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [proposals, setProposals] = useState<Record<string, DraftFix>>({});

  const id = (issue: DraftIssue) => `${issue.kind}:${issue.itemKey}`;
  const done = (issue: DraftIssue) => setCheck((current) => current && {
    ...current, issues: current.issues.filter((item) => id(item) !== id(issue)),
  });

  async function run() {
    setBusy('check');
    setError('');
    setProposals({});
    try {
      await saveDraft();
      setCheck(await checkPlanDraft(token, dreamId, language));
    } catch (checkError) {
      setError(checkError instanceof Error ? tr(checkError.message) : tr('Unable to check your draft.'));
    } finally {
      setBusy(null);
    }
  }

  function fix(issue: DraftIssue) {
    if (issue.fixAction === 'GO_TO_STEPS') {
      onGoTo('STEPS');
    } else if (issue.fixAction === 'GO_TO_TASKS') {
      onGoTo('TASKS');
    } else {
      onChange((current) => applyIssueFix(current, issue));
      done(issue);
    }
  }

  async function askAi(issue: DraftIssue) {
    setBusy(id(issue));
    setError('');
    try {
      await saveDraft();
      const proposal = await fixPlanDraft(token, dreamId, language, { itemKey: issue.itemKey, kind: issue.kind, problem: issue.problem });
      setProposals((current) => ({ ...current, [id(issue)]: proposal }));
    } catch (fixError) {
      setError(fixError instanceof Error ? tr(fixError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(null);
    }
  }

  function apply(issue: DraftIssue, proposal: DraftFix) {
    onChange((current) => applyAiFix(current, proposal));
    done(issue);
  }

  const fixLabel = (issue: DraftIssue) => {
    switch (issue.fixAction) {
      case 'SET_OWNER_ME': return tr('Make it mine');
      case 'SET_DATE': return issue.fixValue ? tr('Use {date}', { date: day(issue.fixValue) }) : null;
      case 'GO_TO_STEPS': return tr('Go to Steps');
      case 'GO_TO_TASKS': return tr('Go to Tasks');
      default: return null;
    }
  };

  return (
    <Box sx={{ display: 'grid', gap: 1, p: 1.5, border: 1, borderColor: 'divider', borderRadius: 2 }}>
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
        <Button variant="secondary" disabled={busy !== null} onClick={() => void run()}>{tr('Check my draft')}</Button>
        <Typography variant="caption" color="text.secondary">
          {aiReady ? tr('Built-in checks, plus an AI review.') : tr('Built-in checks. The AI review is off.')}
        </Typography>
      </Box>
      {busy === 'check' && <Loading />}
      {error && <Alert severity="error">{error}</Alert>}
      {check && !check.aiChecked && aiReady && (
        <Typography variant="caption" color="text.secondary">{tr('Showing the built-in checks only. The AI review was not available.')}</Typography>
      )}
      {check && check.issues.length === 0 && <Alert severity="success">{tr('No problems found. Your draft looks ready.')}</Alert>}
      {check?.issues.map((issue) => {
        const label = fixLabel(issue);
        const proposal = proposals[id(issue)];
        return (
          <Box key={id(issue)} sx={{ borderLeft: 3, borderColor: issue.fromAi ? 'secondary.main' : 'warning.main', pl: 1.5, display: 'grid', gap: 0.5 }}>
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
              {issue.fromAi && <Chip size="small" variant="outlined" label={tr('AI suggestion')} />}
              <Typography variant="body2" sx={{ fontWeight: 600 }}>{issue.itemTitle || tr('Untitled')}</Typography>
            </Box>
            <Typography variant="body2">{tr(issue.problem)}</Typography>
            {issue.fix && <Typography variant="body2" color="text.secondary">{tr(issue.fix)}</Typography>}
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              {label && <Button variant="secondary" onClick={() => fix(issue)}>{label}</Button>}
              {issue.fixAction === 'FIX_WITH_AI' && !proposal && (
                <Button variant="secondary" disabled={busy !== null || !aiReady} onClick={() => void askAi(issue)}>{tr('Fix with AI')}</Button>
              )}
            </Box>
            {busy === id(issue) && <Loading />}
            {proposal && <Proposal proposal={proposal} onApply={() => apply(issue, proposal)} onDismiss={() => done(issue)} />}
          </Box>
        );
      })}
      {check && <SuggestionRating key={check.requestId ?? 'none'} token={token} requestId={check.requestId} />}
    </Box>
  );
}

function Proposal({ proposal, onApply, onDismiss }: { proposal: DraftFix; onApply: () => void; onDismiss: () => void }) {
  const change = describeChange(proposal);
  return (
    <Alert severity="info" icon={false}>
      {change ? (
        <Stack spacing={0.5}>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>{change}</Typography>
          {proposal.note && <Typography variant="body2">{proposal.note}</Typography>}
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button onClick={onApply}>{tr('Apply to my draft')}</Button>
            <Button variant="secondary" onClick={onDismiss}>{tr('Keep mine')}</Button>
          </Box>
        </Stack>
      ) : (
        <Typography variant="body2">{tr('No change to suggest. Edit it yourself.')}</Typography>
      )}
    </Alert>
  );
}

/** PB-FR-16: hours a week, and weeks where the draft adds more work than that. */
function CapacityCheck({ content, onChange, capacity, onCapacityChange, token }: {
  content: PlanDraftContent;
  onChange: (update: DraftUpdate) => void;
  capacity: PlanCapacity | null;
  onCapacityChange: (capacity: PlanCapacity) => void;
  token: string;
}) {
  const [hours, setHours] = useState(capacity?.hoursPerWeek != null ? String(capacity.hoursPerWeek) : '');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const planned = capacity?.planned ?? [];
  const perWeek = capacity?.hoursPerWeek ?? null;
  const weeks = overloadedWeeks(content, planned, perWeek);

  async function save() {
    const value = hours.trim() === '' ? null : Number(hours);
    if (value === (perWeek ?? null) || (value != null && (Number.isNaN(value) || value < 0.5 || value > 100))) {
      return;
    }
    setError('');
    try {
      onCapacityChange(await savePlanCapacity(token, value));
    } catch (saveError) {
      setError(saveError instanceof Error ? tr(saveError.message) : tr('Unable to save your hours.'));
    }
  }

  function spread() {
    if (!perWeek) {
      return;
    }
    const result = spreadDates(content, planned, perWeek);
    onChange(result.content);
    setNotice(result.moved > 0
      ? tr('Moved {count} item(s) a week or more later.', { count: result.moved })
      : tr('Nothing could move without going past its step or goal date. Change the dates or hours yourself.'));
  }

  return (
    <Box sx={{ display: 'grid', gap: 1 }}>
      <TextField size="small" type="number" label={tr('Hours a week you have for your plans')} value={hours} sx={{ maxWidth: 320 }}
        slotProps={{ htmlInput: { min: 0.5, max: 100, step: 0.5 } }} helperText={tr('Optional. Used to flag weeks with too much work.')}
        onChange={(event) => setHours(event.target.value)} onBlur={() => void save()} />
      {error && <Alert severity="error">{error}</Alert>}
      {weeks.length > 0 && (
        <Alert severity="warning" action={<Button variant="secondary" onClick={spread}>{tr('Spread the dates')}</Button>}>
          <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>
            {tr('These weeks have more work than your {hours} hours:', { hours: perWeek ?? 0 })}
          </Typography>
          <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
            {weeks.map((week) => (
              <Typography component="li" variant="body2" key={week.weekStart}>
                {tr('Week of {date}: {total} h ({draft} h new in this draft)', {
                  date: day(week.weekStart), total: Math.round(week.total * 10) / 10, draft: Math.round(week.draft * 10) / 10,
                })}
              </Typography>
            ))}
          </Box>
        </Alert>
      )}
      {notice && <Alert severity="info" onClose={() => setNotice('')}>{notice}</Alert>}
    </Box>
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
