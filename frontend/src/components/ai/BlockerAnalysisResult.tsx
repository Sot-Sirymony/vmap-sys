import { useState } from 'react';
import { Link as RouterLink } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { tr } from '../../i18n/translate';
import type { BlockerAnalysis, BlockerItem, BlockerKind, TaskItem } from '../../types/vision';
import { obstacleTypeLabels, priorityLabels } from '../../utils/enumLabels';
import { suggestPartnerFor } from '../../utils/partnerSuggestion';
import { Button } from '../common/Button';
import { BreakDownDialog } from '../coaching/BreakDownDialog';

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
 * Every action opens a normal page or dialog; nothing is saved from here.
 */
export function BlockerAnalysisResult({ analysis, dreamId, tasks, token, onDataChange }: {
  analysis: BlockerAnalysis;
  dreamId: number;
  tasks: TaskItem[];
  token: string;
  onDataChange: () => Promise<void>;
}) {
  const [breakingDown, setBreakingDown] = useState<TaskItem | null>(null);

  if (analysis.blockers.length === 0) {
    return <Alert severity="success">{tr('Nothing is blocked or waiting in this dream. Keep going.')}</Alert>;
  }

  return (
    <Stack spacing={1.5}>
      {!analysis.aiChecked && (
        <Typography variant="caption" color="text.secondary">
          {tr('Showing the built-in analysis only. Turn on AI for root causes and first moves.')}
        </Typography>
      )}
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
    </Stack>
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
