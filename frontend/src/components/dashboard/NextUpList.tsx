import { Link } from 'react-router';
import Box from '@mui/material/Box';
import { Check, ChevronRight, Flag } from 'lucide-react';
import { StatusChip } from '../common/StatusChip';
import { currentLanguage, tr } from '../../i18n/translate';
import type { Priority } from '../../types/vision';
import { priorityLabels } from '../../utils/enumLabels';
import { localDateIso } from '../../utils/localDate';
import { tabular } from './surface';

export type NextUpTask = {
  id: string | number;
  title: string;
  /** Goal · area, shown under the title. */
  context?: string;
  priority: Priority;
  /** YYYY-MM-DD. */
  dueDate?: string | null;
  status: string;
  blockerReason?: string | null;
  to?: string;
};

const PRIORITY_RANK: Record<Priority, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };

/** Whole days from `today` to `date`, both YYYY-MM-DD, ignoring time zones. */
export function daysUntil(date: string, today: string): number {
  const toUtc = (iso: string) => {
    const [year, month, day] = iso.split('-').map(Number);
    return Date.UTC(year, month - 1, day);
  };
  return Math.round((toUtc(date) - toUtc(today)) / 86_400_000);
}

/**
 * DA-FR-02 — the order of "Next up": overdue first, then due today, then by
 * priority, then by due date. Completed tasks never appear.
 */
export function sortNextUp(tasks: NextUpTask[], today: string): NextUpTask[] {
  const bucket = (task: NextUpTask) => {
    if (!task.dueDate) {
      return 2;
    }
    const days = daysUntil(task.dueDate, today);
    if (days < 0) {
      return 0;
    }
    return days === 0 ? 1 : 2;
  };
  return tasks
    .filter((task) => task.status !== 'COMPLETED')
    .sort((a, b) =>
      bucket(a) - bucket(b)
      || PRIORITY_RANK[a.priority] - PRIORITY_RANK[b.priority]
      || (a.dueDate ?? '9999-12-31').localeCompare(b.dueDate ?? '9999-12-31')
      || a.title.localeCompare(b.title),
    );
}

export type DueTone = 'late' | 'today' | 'soon' | 'later' | 'none';

/** BRD 25 §4 — relative within a week ("Today", "Tomorrow", "Fri"), a short date after. */
export function dueLabel(dueDate: string | null | undefined, today: string): { text: string; tone: DueTone } {
  if (!dueDate) {
    return { text: '', tone: 'none' };
  }
  const days = daysUntil(dueDate, today);
  if (days < 0) {
    return { text: days === -1 ? tr('1 day late') : tr('{n} days late', { n: -days }), tone: 'late' };
  }
  if (days === 0) {
    return { text: tr('Today'), tone: 'today' };
  }
  if (days === 1) {
    return { text: tr('Tomorrow'), tone: 'soon' };
  }
  const [year, month, day] = dueDate.split('-').map(Number);
  const date = new Date(year, month - 1, day);
  const locale = currentLanguage() === 'km' ? 'km-KH' : 'en-GB';
  if (days < 7) {
    return { text: new Intl.DateTimeFormat(locale, { weekday: 'short' }).format(date), tone: 'soon' };
  }
  return { text: new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short' }).format(date), tone: 'later' };
}

const DUE_STYLE: Record<DueTone, { color: string; fontWeight: number }> = {
  late: { color: 'var(--st-declined-fg)', fontWeight: 600 },
  today: { color: 'var(--text-primary)', fontWeight: 600 },
  soon: { color: 'var(--text-secondary)', fontWeight: 400 },
  later: { color: 'var(--text-secondary)', fontWeight: 400 },
  none: { color: 'var(--text-secondary)', fontWeight: 400 },
};

type NextUpListProps = {
  tasks: NextUpTask[];
  /** YYYY-MM-DD; defaults to the user's own calendar day. */
  today?: string;
  limit?: number;
  /** Quick complete. Without it (or with `readOnly`) no checkbox shows. */
  onComplete?: (task: NextUpTask) => void;
  /** The coach view (D-25-10): no action controls. */
  readOnly?: boolean;
  emptyMessage?: string;
};

/**
 * BRD 25 §3 — "Next up": up to five open tasks in DA-FR-02 order, each with its
 * priority flag, due label and, when blocked or waiting, the status and reason.
 */
export function NextUpList({ tasks, today = localDateIso(), limit = 5, onComplete, readOnly = false, emptyMessage }: NextUpListProps) {
  const rows = sortNextUp(tasks, today).slice(0, limit);
  const canComplete = Boolean(onComplete) && !readOnly;

  if (rows.length === 0) {
    return <Box sx={{ py: 2, color: 'var(--text-secondary)' }}>{emptyMessage ?? tr('Nothing to show yet.')}</Box>;
  }

  return (
    <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
      {rows.map((task, index) => {
        const due = dueLabel(task.dueDate, today);
        const urgent = task.priority === 'CRITICAL' || task.priority === 'HIGH';
        const showStatus = task.status === 'BLOCKED' || task.status === 'WAITING';
        return (
          <Box
            component="li"
            key={task.id}
            sx={{
              display: 'grid',
              gridTemplateColumns: canComplete ? '24px minmax(0, 1fr) auto 16px' : 'minmax(0, 1fr) auto 16px',
              alignItems: 'center',
              columnGap: 1.5,
              rowGap: 0.5,
              py: 1.25,
              px: 1,
              mx: -1,
              borderTop: index === 0 ? 'none' : '1px solid var(--divider)',
              borderRadius: 'var(--radius-md)',
              transition: 'background var(--motion-fast) var(--motion-ease)',
              '&:hover': { bgcolor: 'var(--surface-sunken)' },
              '@media (max-width: 599px)': {
                gridTemplateColumns: canComplete ? '24px minmax(0, 1fr) 16px' : 'minmax(0, 1fr) 16px',
                '& .next-meta': { gridColumn: canComplete ? 2 : 1, gridRow: 2, justifySelf: 'start' },
                '& .next-chev': { gridColumn: canComplete ? 3 : 2, gridRow: 1 },
              },
            }}
          >
            {canComplete && (
              <Box
                component="button"
                type="button"
                role="checkbox"
                aria-checked={false}
                aria-label={tr('Mark {title} as done', { title: task.title })}
                onClick={() => onComplete?.(task)}
                sx={{
                  width: 20, height: 20, p: 0, borderRadius: '50%', border: '1.5px solid var(--border-strong)', bgcolor: 'transparent',
                  display: 'grid', placeItems: 'center', color: 'transparent', cursor: 'pointer',
                  transition: 'border-color var(--motion-fast) var(--motion-ease), transform var(--motion-fast) var(--motion-ease)',
                  '&:hover': { borderColor: 'var(--st-done)', color: 'var(--st-done)' },
                  '&:active': { transform: 'scale(0.9)' },
                }}
              >
                <Check size={12} strokeWidth={2.5} aria-hidden="true" />
              </Box>
            )}
            <Box sx={{ minWidth: 0 }}>
              {task.to ? (
                <Box component={Link} to={task.to} sx={{ color: 'inherit', fontWeight: 500, textDecoration: 'none', overflowWrap: 'anywhere', '&:hover': { textDecoration: 'underline' } }}>
                  {task.title}
                </Box>
              ) : (
                <Box sx={{ fontWeight: 500, overflowWrap: 'anywhere' }}>{task.title}</Box>
              )}
              {task.context && <Box sx={{ fontSize: 'var(--font-caption)', color: 'var(--text-secondary)' }}>{task.context}</Box>}
            </Box>
            <Box className="next-meta" sx={{ display: 'flex', alignItems: 'center', gap: 1.5, justifySelf: 'end' }}>
              <Box
                component="span"
                sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, fontSize: 'var(--font-caption)', whiteSpace: 'nowrap', color: urgent ? 'var(--text-primary)' : 'var(--text-secondary)', fontWeight: urgent ? 600 : 400 }}
              >
                <Flag size={14} strokeWidth={1.75} aria-hidden="true" />
                {priorityLabels[task.priority]}
              </Box>
              {due.text && (
                <Box component="span" sx={{ ...tabular, ...DUE_STYLE[due.tone], fontSize: 'var(--font-caption)', whiteSpace: 'nowrap', minWidth: { sm: 64 }, textAlign: { sm: 'right' } }}>
                  {due.text}
                </Box>
              )}
            </Box>
            <Box className="next-chev" sx={{ display: 'inline-flex', color: 'var(--text-tertiary)' }}>
              {task.to && <ChevronRight size={16} aria-hidden="true" />}
            </Box>
            {showStatus && (
              <Box sx={{ gridColumn: canComplete ? '2 / 4' : '1 / 3', display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                <StatusChip status={task.status} />
                {task.blockerReason && <Box component="span" sx={{ fontSize: 'var(--font-caption)', color: 'var(--text-secondary)' }}>{task.blockerReason}</Box>}
              </Box>
            )}
          </Box>
        );
      })}
    </Box>
  );
}
