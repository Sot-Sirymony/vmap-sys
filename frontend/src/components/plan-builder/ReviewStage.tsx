import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { checkPlanDraft, fixPlanDraft } from '../../api/aiPlanApi';
import { useRuleCheck } from './useRuleCheck';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import type {
  DraftCheck, DraftFix, DraftIssue, Dream, Goal, PlanDraftContent, PlanRule, PlanStage, ReviewRhythmChoice, VisionStep, Weekday,
} from '../../types/vision';
import { obstacleTypeLabels, workStatusLabels } from '../../utils/enumLabels';
import { Button } from '../common/Button';
import { Loading } from '../common/Loading';
import { SuggestionRating } from '../ai/SuggestionRating';
import { InnerBlockersNote } from '../inner-blockers/InnerBlockersNote';
import { type DraftUpdate, STAGES, applyAiFix, applyIssueFix, countNew, readiness } from './draftModel';

type Props = {
  content: PlanDraftContent;
  onChange: (update: DraftUpdate) => void;
  existingSteps: VisionStep[];
  busy: boolean;
  onGoTo: (stage: PlanStage) => void;
  // V31 (DC-FR-53, DC-FR-56): the SHOULD rules the participant accepted go with the save.
  onAdd: (acceptedShould: PlanRule[]) => void;
  // V11 Phase 6.
  dreamId: number;
  token: string;
  language: string;
  aiReady: boolean;
  saveDraft: () => Promise<void>;
  // V31 v1 step 3 (DC-FR-51): the dream and its saved goals, for the plan tree.
  dream?: Dream;
  existingGoals?: Goal[];
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

/** V31 (section 5.1): what each rule checks, in plain words. */
export const RULE_NAMES: Record<PlanRule, string> = {
  'R-01': 'Every item is in the right place',
  'R-02': 'The dream and goals are clear and measurable',
  'R-03': 'Steps and tasks are complete',
  'R-04': 'Dates fit together',
  'R-05': 'Blockers are in the right place',
  'R-06': 'Gaps point to the right kind of helper',
  'R-07': 'Every suggested detail is confirmed by you',
  'R-08': 'The check is up to date',
};
const RULES = Object.keys(RULE_NAMES) as PlanRule[];

/** Where a fix that opens another stage goes. */
export const GO_TO: Partial<Record<NonNullable<DraftIssue['fixAction']>, PlanStage>> = {
  GO_TO_CLARIFY: 'CLARIFY', GO_TO_INNER: 'INNER', GO_TO_GOALS: 'GOALS', GO_TO_STEPS: 'STEPS', GO_TO_TASKS: 'TASKS',
  // V31 v1 step 2: helpers and risks are part of the Steps stage.
  GO_TO_HELPERS: 'STEPS',
};

export function fixLabel(issue: DraftIssue) {
  switch (issue.fixAction) {
    case 'SET_OWNER_ME': return tr('Make it mine');
    case 'SET_DATE': return issue.fixValue ? tr('Use {date}', { date: day(issue.fixValue) }) : null;
    case 'SET_PRIORITY': return tr('Use Medium');
    case 'APPROVE': return tr('Approve');
    case 'GO_TO_CLARIFY': return tr('Go to Clarify');
    case 'GO_TO_INNER': return tr('Go to Inner blockers');
    case 'GO_TO_GOALS': return tr('Go to Goals');
    case 'GO_TO_STEPS': return tr('Go to Steps');
    case 'GO_TO_TASKS': return tr('Go to Tasks');
    case 'GO_TO_HELPERS': return tr('Go to Steps');
    default: return null;
  }
}

/**
 * Stage 6, Check and add. V31 (DC-FR-51 to DC-FR-58): the rules R-01 to R-08
 * re-run on the server, without AI, a second after each change, and show as a
 * pass/fail list. MUST fails block Save my plan; each rule with SHOULD fails
 * needs "I understand"; the save waits until the list matches the draft. The
 * server checks again when saving. Check my draft adds the AI review
 * (PB-FR-28, PB-FR-29). V31 v1 step 3: the plan tree (DC-FR-51) sits on top;
 * hours a week (PB-FR-16) moved to the Schedule stage.
 */
export function ReviewStage({
  content, onChange, existingSteps, busy, onGoTo, onAdd, dreamId, token, language, aiReady, saveDraft, dream, existingGoals = [],
}: Props) {
  const counts = countNew(content);
  // Helpers and risks are not in the server rules yet; their field checks stay here.
  const helperErrors = readiness(content, existingSteps).filter(({ item }) => item.level === 'error' && item.stage === 'HELPERS');
  const { report, issues, current, checking, error: checkError } = useRuleCheck({ content, dreamId, token, language, saveDraft });
  const [accepted, setAccepted] = useState<PlanRule[]>([]);
  const stageLabel = (stage: PlanStage) => tr(STAGES.find((item) => item.id === stage)?.label ?? stage);

  const must = issues.filter((issue) => issue.level === 'MUST');
  const shouldRules = RULES.filter((rule) => issues.some((issue) => issue.rule === rule && issue.level === 'SHOULD'));
  const unaccepted = shouldRules.filter((rule) => !accepted.includes(rule));
  const advice = issues.filter((issue) => issue.level === 'INFO');
  const canSave = !busy && counts.total > 0 && current && must.length === 0 && unaccepted.length === 0 && helperErrors.length === 0;

  function fix(issue: DraftIssue) {
    const stage = issue.fixAction ? GO_TO[issue.fixAction] : undefined;
    if (stage) {
      onGoTo(stage);
    } else {
      onChange((draft) => applyIssueFix(draft, issue));
    }
  }

  return (
    <Stack spacing={2}>
      <Typography variant="body1">{summary(counts)}</Typography>
      <InnerBlockersNote token={token} dreamId={dreamId} />
      {counts.total > 0 && <PlanTree content={content} dream={dream} existingGoals={existingGoals} existingSteps={existingSteps} />}
      {helperErrors.length > 0 && (
        <Alert severity="error">
          <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>{tr('Fix these before adding:')}</Typography>
          <Issues list={helperErrors} stageLabel={stageLabel} onGoTo={onGoTo} />
        </Alert>
      )}

      {counts.total > 0 && (
        <Box component="section" aria-label={tr('Plan check')}
          sx={{ display: 'grid', gap: 1, p: 1.5, border: 1, borderColor: 'divider', borderRadius: 'var(--style-radius-control)' }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="subtitle2">{tr('Plan check')}</Typography>
            {(checking || !current) && !checkError && <Typography variant="caption" color="text.secondary">{tr('Checking…')}</Typography>}
          </Box>
          {checkError && <Alert severity="error">{checkError}</Alert>}
          {report && (
            <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 1 }}>
              {RULES.map((rule) => (
                <RuleRow key={rule} rule={rule} issues={issues.filter((issue) => issue.rule === rule && issue.level !== 'INFO')}
                  stale={rule === 'R-08' && !current} accepted={accepted.includes(rule)}
                  onAccept={(value) => setAccepted((list) => (value ? [...list, rule] : list.filter((item) => item !== rule)))}
                  onFix={fix} />
              ))}
            </Box>
          )}
          {advice.length > 0 && (
            <Box>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>{tr('Worth a look:')}</Typography>
              <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
                {advice.map((issue) => (
                  <Typography component="li" variant="body2" key={`${issue.kind}:${issue.itemKey}`}>
                    {issue.itemTitle ? `${issue.itemTitle}: ` : ''}{tr(issue.problem)}
                  </Typography>
                ))}
              </Box>
            </Box>
          )}
        </Box>
      )}

      {counts.total > 0 && (
        <DraftChecker dreamId={dreamId} token={token} language={language} aiReady={aiReady} saveDraft={saveDraft}
          onChange={onChange} />
      )}

      <Box>
        <Button disabled={!canSave} onClick={() => onAdd(accepted.filter((rule) => shouldRules.includes(rule)))}>{tr('Save my plan')}</Button>
      </Box>
      <Typography variant="caption" color="text.secondary">
        {must.length > 0 || unaccepted.length > 0
          ? tr('Fix every red line, and tick "I understand" on each orange one, to save.')
          : tr('Everything is added together, or nothing is. You can undo right after.')}
      </Typography>
    </Stack>
  );
}

const WEEKDAY_NAMES: Record<Weekday, string> = {
  MON: 'Monday', TUE: 'Tuesday', WED: 'Wednesday', THU: 'Thursday', FRI: 'Friday', SAT: 'Saturday', SUN: 'Sunday',
};

/** V31 v1 step 3: the review rhythm in words, for the summary. */
export function rhythmWords(rhythm: ReviewRhythmChoice | null | undefined) {
  if (!rhythm) {
    return tr('No review rhythm chosen yet.');
  }
  const parts = [
    rhythm.daily ? tr('daily check-in') : null,
    rhythm.weeklyDay ? tr('weekly on {day}', { day: tr(WEEKDAY_NAMES[rhythm.weeklyDay]) }) : null,
    rhythm.monthlyDay ? tr('monthly on day {day}', { day: rhythm.monthlyDay }) : null,
    rhythm.quarterly ? tr('quarterly') : null,
  ].filter((part): part is string => part !== null);
  return parts.length ? tr('Reviews: {list}.', { list: parts.join(', ') }) : tr('No reviews chosen.');
}

/**
 * V31 v1 step 3 (DC-FR-51): the whole plan as a tree, dream to goals, steps
 * and tasks, with gaps, helpers and risks; items already saved are marked.
 */
function PlanTree({ content, dream, existingGoals, existingSteps }: {
  content: PlanDraftContent;
  dream?: Dream;
  existingGoals: Goal[];
  existingSteps: VisionStep[];
}) {
  const helpersFor = (key: string) => (content.helpers ?? []).filter((helper) => helper.linkKey === key);
  const risksFor = (key: string) => (content.risks ?? []).filter((risk) => risk.linkKey === key);
  const saved = <Chip size="small" variant="outlined" label={tr('Already in your plan')} sx={{ ml: 0.5 }} />;
  const date = (value?: string | null) => (value ? ` · ${day(value)}` : '');
  return (
    <Box component="section" aria-label={tr('Your plan')}
      sx={{ p: 1.5, border: 1, borderColor: 'divider', borderRadius: 'var(--style-radius-control)' }}>
      <Typography variant="subtitle2">{dream ? `${tr('Dream')}: ${dream.title}${date(dream.targetDate)}` : tr('Your plan')}</Typography>
      <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
        {content.goals.map((goal) => {
          const savedGoal = goal.existingId != null ? existingGoals.find((item) => item.id === goal.existingId) : undefined;
          return (
            <Typography component="li" variant="body2" key={goal.key} sx={{ mt: 0.5 }}>
              <strong>{goal.title || savedGoal?.title || tr('Untitled')}</strong>{date(goal.targetDate ?? savedGoal?.targetDate)}
              {goal.existingId != null && saved}
              <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
                {goal.steps.map((step) => {
                  const savedStep = step.existingId != null ? existingSteps.find((item) => item.id === step.existingId) : undefined;
                  return (
                    <Typography component="li" variant="body2" key={step.key}>
                      {step.title || savedStep?.title || tr('Untitled')}{date(step.targetDate ?? savedStep?.targetDate)}
                      {step.existingId != null && saved}
                      {step.gapKind && ` · ${tr('Gap')}: ${obstacleTypeLabels[step.gapKind]}`}
                      {helpersFor(step.key).map((helper) => ` · ${tr('Helper')}: ${helper.partnerName || helper.name || helper.role}`).join('')}
                      {risksFor(step.key).map((risk) => ` · ${tr('Risk')}: ${risk.title}`).join('')}
                      {step.tasks.length > 0 && (
                        <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
                          {step.tasks.map((task) => (
                            <Typography component="li" variant="body2" key={task.key} color="text.secondary">
                              {task.title || tr('Untitled')}{date(task.dueDate)} · {task.owner || tr('No owner')}
                              {task.status && task.status !== 'NOT_STARTED' ? ` · ${workStatusLabels[task.status]}` : ''}
                            </Typography>
                          ))}
                        </Box>
                      )}
                    </Typography>
                  );
                })}
              </Box>
            </Typography>
          );
        })}
      </Box>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{rhythmWords(content.reviewRhythm)}</Typography>
    </Box>
  );
}

/** One rule of the pass/fail list (DC-FR-52, DC-FR-53). */
function RuleRow({ rule, issues, stale, accepted, onAccept, onFix }: {
  rule: PlanRule;
  issues: DraftIssue[];
  stale: boolean;
  accepted: boolean;
  onAccept: (value: boolean) => void;
  onFix: (issue: DraftIssue) => void;
}) {
  const must = issues.filter((issue) => issue.level === 'MUST');
  const should = issues.filter((issue) => issue.level === 'SHOULD');
  const failing = stale || must.length > 0;
  const status = stale ? tr('Checking again')
    : must.length > 0 ? tr('{count} to fix', { count: must.length })
      : should.length > 0 ? tr('{count} to accept', { count: should.length }) : tr('Pass');
  const color = failing ? 'error.main' : should.length > 0 ? 'warning.main' : 'success.main';
  return (
    <Box component="li" sx={{ borderLeft: 3, borderColor: color, pl: 1.5, display: 'grid', gap: 0.5 }}>
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'baseline', flexWrap: 'wrap' }}>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          <span aria-hidden="true">{failing ? '✗' : should.length > 0 ? '!' : '✓'} </span>{rule} · {tr(RULE_NAMES[rule])}
        </Typography>
        <Typography variant="body2" sx={{ color }}>{status}</Typography>
      </Box>
      {[...must, ...should].map((issue, index) => {
        const label = fixLabel(issue);
        return (
          <Box key={`${issue.kind}:${issue.itemKey ?? index}`} sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Chip size="small" variant="outlined" color={issue.level === 'MUST' ? 'error' : 'warning'}
              label={issue.level === 'MUST' ? tr('Must fix') : tr('Can accept')} />
            <Typography variant="body2">{issue.itemTitle ? `${issue.itemTitle}: ` : ''}{tr(issue.problem)}</Typography>
            {label && <Button variant="secondary" onClick={() => onFix(issue)}>{label}</Button>}
          </Box>
        );
      })}
      {should.length > 0 && (
        <FormControlLabel control={<Checkbox checked={accepted} onChange={(event) => onAccept(event.target.checked)} />}
          label={tr('I understand, save anyway')} />
      )}
    </Box>
  );
}

/** PB-FR-28, PB-FR-29: the plan check on the draft, and its fixes. */
function DraftChecker({ dreamId, token, language, aiReady, saveDraft, onChange }: {
  dreamId: number;
  token: string;
  language: string;
  aiReady: boolean;
  saveDraft: () => Promise<void>;
  onChange: (update: DraftUpdate) => void;
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
      const result = await checkPlanDraft(token, dreamId, language);
      // V31: the rules show in the plan check list; this panel shows what the AI review found.
      setCheck({ ...result, issues: result.issues.filter((issue) => issue.fromAi) });
    } catch (checkError) {
      setError(checkError instanceof Error ? tr(checkError.message) : tr('Unable to check your draft.'));
    } finally {
      setBusy(null);
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

  return (
    <Box sx={{ display: 'grid', gap: 1, p: 1.5, border: 1, borderColor: 'divider', borderRadius: 'var(--style-radius-control)' }}>
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
        <Button variant="secondary" disabled={busy !== null} onClick={() => void run()}>{tr('Check my draft')}</Button>
        <Typography variant="caption" color="text.secondary">
          {aiReady ? tr('An AI review of vague steps, timing and gaps.') : tr('The AI review is off.')}
        </Typography>
      </Box>
      {busy === 'check' && <Loading />}
      {error && <Alert severity="error">{error}</Alert>}
      {check && !check.aiChecked && aiReady && (
        <Typography variant="caption" color="text.secondary">{tr('The AI review was not available. The plan check above still applies.')}</Typography>
      )}
      {check?.aiChecked && check.issues.length === 0 && <Alert severity="success">{tr('The AI review found nothing to change.')}</Alert>}
      {check?.issues.map((issue) => {
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
