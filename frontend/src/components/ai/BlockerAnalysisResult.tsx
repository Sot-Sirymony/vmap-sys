import { useState } from 'react';
import { Link as RouterLink } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { tr } from '../../i18n/translate';
import type { BlockerAnalysis, BlockerCandidate, BlockerItem, BlockerKind, BlockerSignal, TaskItem } from '../../types/vision';
import { obstacleTypeLabels, priorityLabels } from '../../utils/enumLabels';
import { suggestPartnerFor } from '../../utils/partnerSuggestion';
import { Button } from '../common/Button';
import { BreakDownDialog } from '../coaching/BreakDownDialog';
import { MarkBlockedDialog, type BlockTarget } from './MarkBlockedDialog';

const KIND_LABELS: Record<BlockerKind, string> = {
  BLOCKED: 'Blocked',
  WAITING: 'Waiting',
  GAP: 'Needs a helper',
};

const KIND_COLORS: Record<BlockerKind, 'error' | 'warning' | 'info'> = {
  BLOCKED: 'error',
  WAITING: 'warning',
  GAP: 'info',
};

/**
 * The plan assistant's blocker analysis on the Vision Map: what holds the
 * dream back, biggest impact first, with what each one holds up and what is
 * missing. With AI, each also gets a root cause, a first move and a helper.
 * Below it, V28 (D-28-10) lists work that looks stuck but is not marked, each
 * with Mark as blocked or Not blocked. The analysis itself saves nothing;
 * marking goes through MarkBlockedDialog.
 */
export function BlockerAnalysisResult({ analysis, dreamId, tasks, token, onDataChange, onMarked }: {
  analysis: BlockerAnalysis;
  dreamId: number;
  tasks: TaskItem[];
  token: string;
  onDataChange: () => Promise<void>;
  /** Called after Mark as blocked saved, so the caller can reload and re-run the analysis. */
  onMarked?: () => void;
}) {
  const [breakingDown, setBreakingDown] = useState<TaskItem | null>(null);
  const [marking, setMarking] = useState<BlockTarget | null>(null);
  // D-28-11: Not blocked hides a card for this view only; it is not stored.
  const [dismissed, setDismissed] = useState<string[]>([]);
  const candidates = (analysis.candidates ?? []).filter((item) => !dismissed.includes(`${item.itemType}-${item.itemId}`));
  const hasBlockers = analysis.blockers.length > 0;

  return (
    <Stack spacing={1.5}>
      {!analysis.aiChecked && (hasBlockers || candidates.length > 0) && (
        <Typography variant="caption" color="text.secondary">
          {tr('Showing the built-in analysis only. Turn on AI for root causes and first moves.')}
        </Typography>
      )}
      {!hasBlockers && candidates.length === 0 && (
        <Alert severity="success">{tr('Nothing is blocked or waiting in this dream. Keep going.')}</Alert>
      )}
      {!hasBlockers && candidates.length > 0 && (
        <Alert severity="info">{tr('Nothing is marked as blocked yet, but some work looks stuck.')}</Alert>
      )}
      {hasBlockers && (
        <>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }} aria-label={tr('Blocker summary')}>
            <Chip size="small" color="error" variant="outlined" label={tr('{count} blocked', { count: analysis.blockedCount })} />
            <Chip size="small" color="warning" variant="outlined" label={tr('{count} waiting', { count: analysis.waitingCount })} />
            <Chip size="small" color="info" variant="outlined" label={tr('{count} need a helper', { count: analysis.gapCount })} />
            <Chip size="small" variant="outlined" label={tr('{count} later step(s) held up', { count: analysis.heldUpSteps })} />
          </Box>
          {analysis.pattern && (
            <Alert severity="info" icon={false}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>{tr('What these blockers have in common')}</Typography>
              <Typography variant="body2">{analysis.pattern}</Typography>
            </Alert>
          )}
          <Typography variant="caption" color="text.secondary">{tr('Biggest impact first. Clear the top one to free the most work.')}</Typography>
          {analysis.blockers.map((item, index) => (
            <BlockerCard
              key={`${item.itemType}-${item.itemId}`}
              item={item}
              rank={index + 1}
              dreamId={dreamId}
              task={item.itemType === 'TASK' ? tasks.find((task) => task.id === item.itemId) ?? null : null}
              onBreakDown={setBreakingDown}
            />
          ))}
        </>
      )}
      {candidates.length > 0 && (
        <Stack spacing={1} component="section" aria-label={tr('Possibly blocked')}>
          <Typography variant="subtitle2" sx={{ mt: hasBlockers ? 1 : 0 }}>{tr('Possibly blocked')}</Typography>
          <Typography variant="caption" color="text.secondary">
            {tr('These look stuck but are not marked. Mark the real blocks so you can get help for them.')}
          </Typography>
          {candidates.map((item) => (
            <CandidateCard
              key={`${item.itemType}-${item.itemId}`}
              item={item}
              onMark={() => setMarking({
                itemType: item.itemType, itemId: item.itemId, title: item.title,
                obstacleType: item.suggestedType ?? null,
                reason: item.suggestedReason ?? null,
              })}
              onDismiss={() => setDismissed((current) => [...current, `${item.itemType}-${item.itemId}`])}
            />
          ))}
        </Stack>
      )}
      {breakingDown && (
        <BreakDownDialog
          token={token}
          task={breakingDown}
          onClose={() => setBreakingDown(null)}
          onSaved={() => {
            setBreakingDown(null);
            void onDataChange();
          }}
        />
      )}
      {marking && (
        <MarkBlockedDialog
          token={token}
          target={marking}
          onClose={() => setMarking(null)}
          onSaved={() => {
            setMarking(null);
            if (onMarked) {
              onMarked();
            } else {
              void onDataChange();
            }
          }}
        />
      )}
    </Stack>
  );
}

const SIGNAL_LABELS: Record<BlockerSignal, string> = {
  OVERDUE_NO_CHANGE: 'Overdue, no change',
  STALLED: 'Stalled',
  SOMETHING_MISSING: 'Something missing',
  KEEPS_SLIPPING: 'Keeps slipping',
  AI: 'AI found',
};

/** The evidence for one signal, in words. */
function evidence(item: BlockerCandidate): string[] {
  const lines: string[] = [];
  if (item.daysOverdue != null && item.daysOverdue > 0) lines.push(tr('Overdue {count} day(s)', { count: item.daysOverdue }));
  if (item.signal === 'OVERDUE_NO_CHANGE' && item.daysSinceChange != null) {
    lines.push(tr('No change for {count} day(s)', { count: item.daysSinceChange }));
  }
  if (item.signal === 'STALLED' && item.daysInProgress != null) {
    lines.push(tr('In progress {count} day(s) at {percent}%', { count: item.daysInProgress, percent: Math.round(item.progressPercent ?? 0) }));
  }
  if (item.signal === 'KEEPS_SLIPPING' && item.reschedules > 0) lines.push(tr('Moved {count} time(s)', { count: item.reschedules }));
  return lines;
}

function CandidateCard({ item, onMark, onDismiss }: { item: BlockerCandidate; onMark: () => void; onDismiss: () => void }) {
  return (
    <Box
      sx={{ display: 'grid', gap: 0.75, p: 1.5, border: 1, borderStyle: 'dashed', borderColor: 'divider',
        borderRadius: 'var(--style-radius-control)' }}
      aria-label={tr('Possibly blocked: {title}', { title: item.title })}
    >
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
        <Chip size="small" variant="outlined" color="warning" label={tr(SIGNAL_LABELS[item.signal])} />
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          {item.itemType === 'TASK' ? tr('Task') : tr('Step')}: {item.code ? `${item.code} ` : ''}{item.title}
        </Typography>
      </Box>
      <Typography variant="caption" color="text.secondary">
        {item.goalTitle}{item.itemType === 'TASK' ? ` › ${item.stepTitle}` : ''}
      </Typography>
      {evidence(item).length > 0 && (
        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
          {evidence(item).map((line) => <Chip key={line} size="small" variant="outlined" label={line} />)}
        </Box>
      )}
      {item.signal === 'SOMETHING_MISSING' && item.resources && (
        <Typography variant="body2"><strong>{tr('Resources say')}:</strong> {item.resources}</Typography>
      )}
      {(item.suggestedReason || item.suggestedType) && (
        <Box sx={{ borderLeft: 3, borderColor: 'secondary.main', pl: 1.5, display: 'grid', gap: 0.5 }}>
          <Chip size="small" variant="outlined" label={tr('AI suggestion')} sx={{ justifySelf: 'start' }} />
          {item.suggestedType && <Typography variant="body2"><strong>{tr('Missing')}:</strong> {tr(obstacleTypeLabels[item.suggestedType])}</Typography>}
          {item.suggestedReason && <Typography variant="body2">{item.suggestedReason}</Typography>}
        </Box>
      )}
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 0.5 }}>
        <Button onClick={onMark}>{tr('Mark as blocked')}</Button>
        <Button variant="secondary" onClick={onDismiss}>{tr('Not blocked')}</Button>
      </Box>
    </Box>
  );
}

function BlockerCard({ item, rank, dreamId, task, onBreakDown }: {
  item: BlockerItem;
  rank: number;
  dreamId: number;
  task: TaskItem | null;
  onBreakDown: (task: TaskItem) => void;
}) {
  const suggestion = item.obstacleType ? suggestPartnerFor(item.obstacleType) : null;
  const days = item.daysStuck != null ? Math.round(item.daysStuck) : 0;
  const facts = [
    days > 0 ? tr('Stuck {count} day(s)', { count: days }) : null,
    item.timesBlocked > 1 ? tr('Blocked {count} times', { count: item.timesBlocked }) : null,
    item.heldUpSteps > 0 ? tr('Holds up {count} later step(s)', { count: item.heldUpSteps }) : null,
    item.goalAtRisk ? tr('Goal date at risk') : null,
    item.overdue ? tr('Overdue') : null,
  ].filter(Boolean) as string[];

  const obstacleLink = item.obstacleId
    ? `/obstacles?goalId=${item.goalId}`
    : `/obstacles?create=obstacle&${new URLSearchParams({
      ...(item.itemType === 'TASK' ? { taskId: String(item.itemId) } : {}),
      stepId: String(item.stepId),
      title: item.reason ?? item.title,
    }).toString()}`;
  const partnerLink = `/partners?${new URLSearchParams({
    create: 'partner',
    relatedGoalId: String(item.goalId),
    ...(suggestion ? { supportType: suggestion.supportType } : {}),
  }).toString()}`;
  const editLink = `/dreams/${dreamId}?edit=${item.itemType === 'TASK' ? 'task' : 'step'}-${item.itemId}`;

  return (
    <Box
      sx={{ display: 'grid', gap: 0.75, p: 1.5, border: 1, borderColor: 'divider', borderLeft: 4,
        borderLeftColor: `${KIND_COLORS[item.kind]}.main`, borderRadius: 'var(--style-radius-control)' }}
      aria-label={tr('Blocker {rank}: {title}', { rank, title: item.title })}
    >
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
        <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 700 }}>#{rank}</Typography>
        <Chip size="small" color={KIND_COLORS[item.kind]} label={tr(KIND_LABELS[item.kind])} />
        {item.priority && (item.priority === 'HIGH' || item.priority === 'CRITICAL') && (
          <Chip size="small" variant="outlined" label={tr(priorityLabels[item.priority])} />
        )}
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          {item.itemType === 'TASK' ? tr('Task') : tr('Step')}: {item.code ? `${item.code} ` : ''}{item.title}
        </Typography>
      </Box>
      <Typography variant="caption" color="text.secondary">
        {item.goalTitle}{item.itemType === 'TASK' ? ` › ${item.stepTitle}` : ''}
      </Typography>
      {facts.length > 0 && (
        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
          {facts.map((fact) => <Chip key={fact} size="small" variant="outlined" label={fact} />)}
        </Box>
      )}
      {item.reason
        ? <Typography variant="body2"><strong>{tr('What blocks it')}:</strong> {item.reason}</Typography>
        : item.kind === 'BLOCKED' && item.itemType === 'STEP' && (
          <Typography variant="body2" color="text.secondary">{tr('No reason written yet. Say what is missing so you can find the right help.')}</Typography>
        )}
      {item.obstacleType && (
        <Typography variant="body2">
          <strong>{tr('Missing')}:</strong> {tr(obstacleTypeLabels[item.obstacleType])}
          {item.obstacleTypeGuessed && <Typography component="span" variant="caption" color="text.secondary"> ({tr('AI guess')})</Typography>}
          {suggestion && <> · <strong>{tr('Who can help')}:</strong> {tr(suggestion.label)}</>}
        </Typography>
      )}
      {(item.rootCause || item.firstMove || item.helper) && (
        <Box sx={{ borderLeft: 3, borderColor: 'secondary.main', pl: 1.5, display: 'grid', gap: 0.5 }}>
          <Chip size="small" variant="outlined" label={tr('AI suggestion')} sx={{ justifySelf: 'start' }} />
          {item.rootCause && <Typography variant="body2"><strong>{tr('Likely root cause')}:</strong> {item.rootCause}</Typography>}
          {item.firstMove && <Typography variant="body2"><strong>{tr('First move')}:</strong> {item.firstMove}</Typography>}
          {item.helper && <Typography variant="body2"><strong>{tr('Ask')}:</strong> {item.helper}</Typography>}
        </Box>
      )}
      <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center', mt: 0.5 }}>
        {task && (
          <Button variant="secondary" onClick={() => onBreakDown(task)}>{tr('Break into smaller tasks')}</Button>
        )}
        <Link component={RouterLink} to={obstacleLink} variant="body2">
          {item.obstacleId ? tr('Open the obstacle') : tr('Record as an obstacle')}
        </Link>
        {!item.hasHelper && (
          <Link component={RouterLink} to={partnerLink} variant="body2">{tr('Find a helper')}</Link>
        )}
        <Link component={RouterLink} to={editLink} variant="body2">{tr('Edit')}</Link>
      </Box>
    </Box>
  );
}
