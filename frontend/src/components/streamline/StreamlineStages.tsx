import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Button } from '../common/Button';
import { tr } from '../../i18n/translate';
import { surface } from '../../design/surface';
import type {
  AutomationChoice,
  RepeatCadence,
  RequirementSource,
  SpeedLever,
  StreamlineItem,
  StreamlineReview,
  StreamlineSimplify,
  StreamlineVerdict,
} from '../../types/streamline';
import {
  AUTOMATION_LABELS,
  CADENCE_LABELS,
  days,
  hours,
  LEVER_HINTS,
  LEVER_LABELS,
  SOURCE_LABELS,
  VERDICT_LABELS,
} from './streamlineLabels';

/** The actions a stage panel can take; the page turns each into an API call. */
export type StageActions = {
  updateItem: (itemId: number, update: Parameters<typeof import('../../api/streamlineApi').updateStreamlineItem>[3]) => void;
  bulkQuestion: (itemIds: number[], answers: { requirementSource?: RequirementSource; requirementOwner?: string; verdict?: StreamlineVerdict }) => void;
  deleteItem: (item: StreamlineItem) => void;
  restoreItem: (item: StreamlineItem) => void;
  mergeItem: (item: StreamlineItem, targetItemId: number) => void;
  simplify: (item: StreamlineItem, body: StreamlineSimplify) => void;
  setTarget: (days: number) => void;
  repeat: (item: StreamlineItem, cadence: RepeatCadence) => void;
};

type StageProps = { review: StreamlineReview; readOnly: boolean; actions: StageActions; partners: { id: number; name: string }[] };

const rowSx = {
  display: 'grid',
  gap: 1,
  py: 1.25,
  borderTop: '1px solid var(--divider)',
  '&:first-of-type': { borderTop: 'none' },
} as const;

/** Items still in play: not cut, or cut and brought back. */
export function activeItems(review: StreamlineReview): StreamlineItem[] {
  return review.items.filter((item) => !item.archived);
}

function ItemTitle({ item }: { item: StreamlineItem }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, flexWrap: 'wrap', minWidth: 0 }}>
      <Chip size="small" label={item.kind === 'STEP' ? tr('Step') : tr('Task')} variant="outlined" />
      <Typography component="span" variant="body2" sx={{ color: 'var(--text-secondary)' }}>{item.code}</Typography>
      <Typography component="span" variant="body1" sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{item.title}</Typography>
    </Box>
  );
}

// ---------- Stage 1: question ----------

export function QuestionStage({ review, readOnly, actions }: StageProps) {
  const items = activeItems(review);
  const [selected, setSelected] = useState<number[]>([]);
  const [bulkSource, setBulkSource] = useState<RequirementSource | ''>('');
  const [bulkOwner, setBulkOwner] = useState('');
  const [bulkVerdict, setBulkVerdict] = useState<StreamlineVerdict | ''>('');
  const sources = SOURCE_LABELS();
  const verdicts = VERDICT_LABELS();
  const toggle = (id: number) => setSelected((current) => (current.includes(id) ? current.filter((value) => value !== id) : [...current, id]));
  const selectTasksOf = (stepId: number) =>
    setSelected((current) => Array.from(new Set([...current, ...items.filter((item) => item.parentStepId === stepId).map((item) => item.id)])));

  return (
    <Box sx={{ display: 'grid', gap: 2 }}>
      {!readOnly && (
        <Box sx={{ ...surface('inset'), p: 1.5, display: 'grid', gap: 1 }} role="group" aria-label={tr('Answer several at once')}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
            <FormControlLabel
              control={
                <Checkbox
                  checked={selected.length > 0 && selected.length === items.length}
                  indeterminate={selected.length > 0 && selected.length < items.length}
                  onChange={(event) => setSelected(event.target.checked ? items.map((item) => item.id) : [])}
                />
              }
              label={tr('Select all ({count})', { count: items.length })}
            />
            <Typography variant="body2" sx={{ color: 'var(--text-secondary)' }}>
              {tr('{count} selected', { count: selected.length })}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
            <TextField select size="small" label={tr('Who asked for it')} value={bulkSource} sx={{ minWidth: 170 }}
              onChange={(event) => setBulkSource(event.target.value as RequirementSource)}>
              {(Object.keys(sources) as RequirementSource[]).map((key) => <MenuItem key={key} value={key}>{sources[key]}</MenuItem>)}
            </TextField>
            <TextField size="small" label={tr('Name')} value={bulkOwner} onChange={(event) => setBulkOwner(event.target.value)} sx={{ minWidth: 140 }} />
            <TextField select size="small" label={tr('Verdict')} value={bulkVerdict} sx={{ minWidth: 150 }}
              onChange={(event) => setBulkVerdict(event.target.value as StreamlineVerdict)}>
              {(Object.keys(verdicts) as StreamlineVerdict[]).map((key) => <MenuItem key={key} value={key}>{verdicts[key]}</MenuItem>)}
            </TextField>
            <Button
              variant="secondary"
              disabled={selected.length === 0 || (!bulkSource && !bulkOwner && !bulkVerdict)}
              onClick={() => {
                actions.bulkQuestion(selected, {
                  requirementSource: bulkSource || undefined,
                  requirementOwner: bulkOwner || undefined,
                  verdict: bulkVerdict || undefined,
                });
                setSelected([]);
              }}
            >
              {tr('Apply to selected')}
            </Button>
          </Box>
        </Box>
      )}
      <Box>
        {items.map((item) => (
          <Box key={item.id} sx={rowSx} data-testid={`question-${item.code}`}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, justifyContent: 'space-between', flexWrap: 'wrap' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, minWidth: 0 }}>
                {!readOnly && (
                  <Checkbox checked={selected.includes(item.id)} onChange={() => toggle(item.id)} slotProps={{ input: { 'aria-label': tr('Select {code}', { code: item.code }) } }} />
                )}
                <ItemTitle item={item} />
              </Box>
              {!readOnly && item.kind === 'STEP' && items.some((other) => other.parentStepId === item.stepId) && (
                <Button variant="secondary" onClick={() => selectTasksOf(item.stepId as number)}>{tr('Select its tasks')}</Button>
              )}
            </Box>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '180px 160px 1fr auto' }, gap: 1, alignItems: 'center' }}>
              <TextField select size="small" label={tr('Who asked for it')} value={item.requirementSource ?? ''} disabled={readOnly}
                onChange={(event) => actions.updateItem(item.id, { requirementSource: event.target.value as RequirementSource })}>
                {(Object.keys(sources) as RequirementSource[]).map((key) => <MenuItem key={key} value={key}>{sources[key]}</MenuItem>)}
              </TextField>
              <TextField size="small" label={tr('Name')} defaultValue={item.requirementOwner ?? ''} disabled={readOnly}
                helperText={item.requirementSource === 'NOT_SURE' ? tr('Not needed when you are not sure') : undefined}
                onBlur={(event) => event.target.value !== (item.requirementOwner ?? '') && actions.updateItem(item.id, { requirementOwner: event.target.value })} />
              <TextField size="small" label={tr('Why is it needed?')} defaultValue={item.whyNeeded ?? ''} disabled={readOnly}
                onBlur={(event) => event.target.value !== (item.whyNeeded ?? '') && actions.updateItem(item.id, { whyNeeded: event.target.value })} />
              <ToggleButtonGroup exclusive size="small" value={item.verdict} disabled={readOnly} aria-label={tr('Verdict for {code}', { code: item.code })}
                onChange={(_, value: StreamlineVerdict | null) => value && actions.updateItem(item.id, { verdict: value })}>
                {(Object.keys(verdicts) as StreamlineVerdict[]).map((key) => <ToggleButton key={key} value={key}>{verdicts[key]}</ToggleButton>)}
              </ToggleButtonGroup>
            </Box>
          </Box>
        ))}
      </Box>
    </Box>
  );
}

// ---------- Stage 2: delete ----------

export function DeleteStage({ review, readOnly, actions }: StageProps) {
  const order: Record<string, number> = { NOT_NEEDED: 0, NOT_SURE: 1, NEEDED: 2 };
  const items = [...activeItems(review)].sort((a, b) => (order[a.verdict ?? 'NOT_SURE'] ?? 1) - (order[b.verdict ?? 'NOT_SURE'] ?? 1));
  const cut = review.items.filter((item) => item.archivedAt && !item.archivedWithParent && item.decision !== 'KEEP');
  const verdicts = VERDICT_LABELS();
  return (
    <Box sx={{ display: 'grid', gap: 2 }}>
      <Box>
        {items.map((item) => (
          <DeleteRow key={item.id} item={item} review={review} readOnly={readOnly} actions={actions} verdictLabel={item.verdict ? verdicts[item.verdict] : tr('No verdict')} />
        ))}
      </Box>
      {cut.length > 0 && (
        <Box sx={{ ...surface('inset'), p: 1.5 }} aria-label={tr('Cut in this review')} role="region">
          <Typography variant="subtitle2" sx={{ mb: 1 }}>{tr('Cut in this review ({count})', { count: cut.length })}</Typography>
          {cut.map((item) => (
            <Box key={item.id} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, py: 0.5, flexWrap: 'wrap' }}>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'baseline', flexWrap: 'wrap' }}>
                <ItemTitle item={item} />
                <Chip size="small" label={item.decision === 'MERGE' ? tr('Merged') : tr('Deleted')} />
                {item.restoredAt && <Chip size="small" color="success" label={tr('Brought back')} />}
              </Box>
              {!readOnly && item.archived && review.status !== 'ABANDONED' && (
                <Button variant="secondary" onClick={() => actions.restoreItem(item)}>{tr('Restore')}</Button>
              )}
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );
}

function DeleteRow({ item, review, readOnly, actions, verdictLabel }: { item: StreamlineItem; review: StreamlineReview; readOnly: boolean; actions: StageActions; verdictLabel: string }) {
  const [mergeTarget, setMergeTarget] = useState('');
  const targets = activeItems(review).filter((other) => other.kind === item.kind && other.id !== item.id);
  const needsReason = item.decision === 'KEEP' && item.verdict === 'NOT_NEEDED';
  return (
    <Box sx={rowSx} data-testid={`delete-${item.code}`}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', justifyContent: 'space-between' }}>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'baseline', flexWrap: 'wrap' }}>
          <ItemTitle item={item} />
          <Chip size="small" label={verdictLabel} variant={item.verdict === 'NEEDED' ? 'outlined' : 'filled'} />
          {item.decision === 'KEEP' && <Chip size="small" label={tr('Kept')} />}
        </Box>
        {!readOnly && (
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
            <Button variant="danger" onClick={() => actions.deleteItem(item)}>{tr('Delete')}</Button>
            <Button variant="secondary" onClick={() => actions.updateItem(item.id, { keep: true })}>{tr('Keep')}</Button>
            {targets.length > 0 && (
              <>
                <TextField select size="small" label={tr('Merge into')} value={mergeTarget} sx={{ minWidth: 180 }}
                  onChange={(event) => setMergeTarget(event.target.value)}>
                  {targets.map((target) => <MenuItem key={target.id} value={String(target.id)}>{target.code} · {target.title}</MenuItem>)}
                </TextField>
                <Button variant="secondary" disabled={!mergeTarget} onClick={() => actions.mergeItem(item, Number(mergeTarget))}>{tr('Merge')}</Button>
              </>
            )}
          </Box>
        )}
      </Box>
      {needsReason && (
        <TextField size="small" label={tr('Why keep it, if it is not needed?')} defaultValue={item.keepReason ?? ''} disabled={readOnly} required
          onBlur={(event) => event.target.value !== (item.keepReason ?? '') && actions.updateItem(item.id, { keepReason: event.target.value })} />
      )}
    </Box>
  );
}

// ---------- Stage 3: simplify ----------

export function SimplifyStage({ review, readOnly, actions }: StageProps) {
  return (
    <Box sx={{ display: 'grid', gap: 2 }}>
      <BeforeAfter review={review} />
      <Box>
        {activeItems(review).map((item) => (
          <SimplifyRow key={item.id} item={item} readOnly={readOnly} actions={actions} />
        ))}
      </Box>
    </Box>
  );
}

function SimplifyRow({ item, readOnly, actions }: { item: StreamlineItem; readOnly: boolean; actions: StageActions }) {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState(item.title);
  const [estimate, setEstimate] = useState(item.estimatedHours === null ? '' : String(item.estimatedHours));
  const [owner, setOwner] = useState('');
  const [markSimple, setMarkSimple] = useState(false);
  const [note, setNote] = useState(item.simplifyNote ?? '');
  return (
    <Box sx={rowSx} data-testid={`simplify-${item.code}`}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'baseline', flexWrap: 'wrap' }}>
          <ItemTitle item={item} />
          <Typography component="span" variant="body2" sx={{ color: 'var(--text-secondary)' }}>{hours(item.estimatedHours)}</Typography>
          {item.simplified && <Chip size="small" color="success" label={tr('Simplified')} />}
        </Box>
        {!readOnly && <Button variant="secondary" onClick={() => setOpen((value) => !value)} aria-expanded={open}>{tr('Simplify')}</Button>}
      </Box>
      {item.simplifyNote && !open && <Typography variant="body2" sx={{ color: 'var(--text-secondary)' }}>{item.simplifyNote}</Typography>}
      {open && !readOnly && (
        <Box component="form" sx={{ display: 'grid', gap: 1, gridTemplateColumns: { xs: '1fr', md: '2fr 1fr 1fr' } }}
          onSubmit={(event) => {
            event.preventDefault();
            actions.simplify(item, {
              title: title !== item.title ? title : undefined,
              estimatedHours: estimate === '' ? undefined : Number(estimate),
              owner: owner || undefined,
              markSimple: markSimple || undefined,
              note,
            });
            setOpen(false);
          }}>
          <TextField size="small" label={tr('Simpler title or scope')} value={title} onChange={(event) => setTitle(event.target.value)} />
          <TextField size="small" label={tr('Estimate (hours)')} type="number" value={estimate} onChange={(event) => setEstimate(event.target.value)}
            slotProps={{ htmlInput: { min: 0, step: 0.5 } }} />
          <TextField size="small" label={tr('One owner, no hand-off')} value={owner} onChange={(event) => setOwner(event.target.value)} />
          <TextField size="small" label={tr('What you simplified')} value={note} onChange={(event) => setNote(event.target.value)} sx={{ gridColumn: { md: '1 / 3' } }} />
          {item.kind === 'STEP' && (
            <FormControlLabel control={<Checkbox checked={markSimple} onChange={(event) => setMarkSimple(event.target.checked)} />} label={tr('Now a simple step')} />
          )}
          <Box sx={{ gridColumn: '1 / -1' }}><Button type="submit">{tr('Save')}</Button></Box>
        </Box>
      )}
    </Box>
  );
}

export function BeforeAfter({ review }: { review: StreamlineReview }) {
  const { summary } = review;
  const cells: [string, string, string][] = [
    [tr('Steps'), String(summary.stepsBefore), String(summary.stepsAfter)],
    [tr('Tasks'), String(summary.tasksBefore), String(summary.tasksAfter)],
    [tr('Estimated hours'), hours(summary.hoursBefore), hours(summary.hoursAfter)],
  ];
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 1 }} role="group" aria-label={tr('Before and now')}>
      {cells.map(([label, before, after]) => (
        <Box key={label} sx={{ ...surface('inset'), p: 1.25 }}>
          <Typography variant="caption" sx={{ color: 'var(--text-secondary)', display: 'block' }}>{label}</Typography>
          <Typography variant="body1" sx={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>{before} → {after}</Typography>
        </Box>
      ))}
    </Box>
  );
}

// ---------- Stage 4: speed up ----------

export function SpeedStage({ review, readOnly, actions }: StageProps) {
  const [target, setTarget] = useState(review.targetCycleDays === null ? '' : String(review.targetCycleDays));
  const levers = LEVER_LABELS();
  const hints = LEVER_HINTS();
  const items = [...activeItems(review)].sort((a, b) =>
    (Number(b.cycleTimeDays ?? 0) + Number(b.waitingDays ?? 0) + Number(b.blockedDays ?? 0))
    - (Number(a.cycleTimeDays ?? 0) + Number(a.waitingDays ?? 0) + Number(a.blockedDays ?? 0)));
  const stats: [string, string][] = [
    [tr('Median days from start to done'), review.currentCycleDays === null ? tr('Not enough finished tasks yet') : days(review.currentCycleDays)],
    [tr('Waiting, in total'), days(review.waitingDays)],
    [tr('Blocked, in total'), days(review.blockedDays)],
  ];
  return (
    <Box sx={{ display: 'grid', gap: 2 }}>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, minmax(0, 1fr))' }, gap: 1 }}>
        {stats.map(([label, value]) => (
          <Box key={label} sx={{ ...surface('inset'), p: 1.25 }}>
            <Typography variant="caption" sx={{ color: 'var(--text-secondary)', display: 'block' }}>{label}</Typography>
            <Typography variant="body1" sx={{ fontWeight: 600 }}>{value}</Typography>
          </Box>
        ))}
      </Box>
      <Box component="form" sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}
        onSubmit={(event) => { event.preventDefault(); if (target) actions.setTarget(Number(target)); }}>
        <TextField size="small" type="number" label={tr('Target days from start to done')} value={target} disabled={readOnly}
          onChange={(event) => setTarget(event.target.value)} slotProps={{ htmlInput: { min: 0.1, step: 0.5 } }} />
        {!readOnly && <Button type="submit" variant="secondary" disabled={!target}>{tr('Set target')}</Button>}
      </Box>
      <Box>
        {items.map((item) => (
          <Box key={item.id} sx={rowSx} data-testid={`speed-${item.code}`}>
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'baseline', flexWrap: 'wrap' }}>
              <ItemTitle item={item} />
              {item.kind === 'TASK' && (
                <Typography component="span" variant="body2" sx={{ color: 'var(--text-secondary)' }}>
                  {tr('Took {took} · waiting {waiting} · blocked {blocked}', { took: days(item.cycleTimeDays), waiting: days(item.waitingDays), blocked: days(item.blockedDays) })}
                </Typography>
              )}
            </Box>
            <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap' }} role="group" aria-label={tr('Ways to speed up {code}', { code: item.code })}>
              {(Object.keys(levers) as SpeedLever[]).map((lever) => {
                const on = item.speedLevers.includes(lever);
                return (
                  <Chip key={lever} label={levers[lever]} title={hints[lever]} color={on ? 'primary' : 'default'} variant={on ? 'filled' : 'outlined'}
                    aria-pressed={on} disabled={readOnly}
                    onClick={readOnly ? undefined : () => actions.updateItem(item.id, {
                      speedLevers: on ? item.speedLevers.filter((value) => value !== lever) : [...item.speedLevers, lever],
                    })} />
                );
              })}
            </Box>
          </Box>
        ))}
      </Box>
    </Box>
  );
}

// ---------- Stage 5: automate ----------

export function AutomateStage({ review, readOnly, actions, partners }: StageProps) {
  const labels = AUTOMATION_LABELS();
  const cadences = CADENCE_LABELS();
  const items = [...activeItems(review)].sort((a, b) => Number(b.repeatCandidate) - Number(a.repeatCandidate));
  return (
    <Box sx={{ display: 'grid', gap: 0 }}>
      <Typography variant="body2" sx={{ color: 'var(--text-secondary)', mb: 1 }}>
        {tr('"Remind me" uses your email reminders (Account settings). A repeating task is created by the app once the current one is due.')}
      </Typography>
      {items.map((item) => (
        <AutomateRow key={item.id} item={item} readOnly={readOnly} actions={actions} labels={labels} cadences={cadences} partners={partners} />
      ))}
    </Box>
  );
}

function AutomateRow({ item, readOnly, actions, labels, cadences, partners }: {
  item: StreamlineItem; readOnly: boolean; actions: StageActions; labels: Record<AutomationChoice, string>;
  cadences: Record<RepeatCadence, string>; partners: { id: number; name: string }[];
}) {
  const [cadence, setCadence] = useState<RepeatCadence>('WEEKLY');
  return (
    <Box sx={rowSx} data-testid={`automate-${item.code}`}>
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'baseline', flexWrap: 'wrap' }}>
        <ItemTitle item={item} />
        {item.repeatCandidate && <Chip size="small" color="primary" variant="outlined" label={tr('Done before: a repeat candidate')} />}
        {item.automation && <Chip size="small" label={labels[item.automation]} />}
        {item.repeatRuleId && <Chip size="small" color="success" label={tr('Repeats')} />}
      </Box>
      {!readOnly && (
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
          {item.kind === 'TASK' && (
            <>
              <TextField select size="small" label={tr('How often')} value={cadence} onChange={(event) => setCadence(event.target.value as RepeatCadence)} sx={{ minWidth: 150 }}>
                {(Object.keys(cadences) as RepeatCadence[]).map((key) => <MenuItem key={key} value={key}>{cadences[key]}</MenuItem>)}
              </TextField>
              <Button variant="secondary" onClick={() => actions.repeat(item, cadence)}>{tr('Make it repeat')}</Button>
            </>
          )}
          <Button variant="secondary" onClick={() => actions.updateItem(item.id, { automation: 'REMIND' })}>{labels.REMIND}</Button>
          <TextField select size="small" label={labels.DELEGATE} value={item.delegatePartnerId ? String(item.delegatePartnerId) : ''} sx={{ minWidth: 220 }}
            onChange={(event) => actions.updateItem(item.id, event.target.value === 'none'
              ? { automation: 'DELEGATE' }
              : { automation: 'DELEGATE', delegatePartnerId: Number(event.target.value) })}>
            <MenuItem value="none">{tr('No partner')}</MenuItem>
            {partners.map((partner) => <MenuItem key={partner.id} value={String(partner.id)}>{partner.name}</MenuItem>)}
          </TextField>
          <Button variant="secondary" onClick={() => actions.updateItem(item.id, { automation: 'MANUAL' })}>{labels.MANUAL}</Button>
        </Box>
      )}
    </Box>
  );
}
