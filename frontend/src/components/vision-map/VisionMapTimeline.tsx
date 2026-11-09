import { useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Ban, ChevronDown, ChevronRight } from 'lucide-react';
import { Link as RouterLink } from 'react-router';
import Box from '@mui/material/Box';
import FormControlLabel from '@mui/material/FormControlLabel';
import Link from '@mui/material/Link';
import Switch from '@mui/material/Switch';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { Button } from '../common/Button';
import { tintColors } from '../common/TintedChip';
import { useThemeSettings } from '../../context/ThemeModeContext';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import { statusColor, type StatusToken } from '../../theme';
import type { Dream, Goal, TaskItem, VisionStep } from '../../types/vision';
import { dreamStatusLabels, priorityLabels, workStatusLabels } from '../../utils/enumLabels';
import { progressTone } from '../../utils/progressTone';
import {
  buildDreamTimeline,
  defaultExpanded,
  filterTimelineRows,
  fromDay,
  todayDay,
  visibleRows,
  type TimelineKind,
  type TimelineRow,
  type WorkedOutReason,
} from '../../utils/timeline';

export type TimelineZoom = 'week' | 'month' | 'quarter' | 'year';

/** Pixels per day at each zoom (TL-FR-23). */
const PX_PER_DAY: Record<TimelineZoom, number> = { week: 28, month: 6, quarter: 2.2, year: 0.75 };
/** Empty days kept before the first bar and after the last one. */
const PADDING_DAYS: Record<TimelineZoom, number> = { week: 7, month: 14, quarter: 31, year: 62 };
const ROW_HEIGHT = 36;
const BAR_HEIGHT = 20;

const WORKED_OUT_TEXT: Record<WorkedOutReason, string> = {
  'task-no-start': tr('No start date, so shown on its due date'),
  'from-first-task': tr('Worked out from its first task'),
  'after-previous-step': tr('Worked out: the day after the previous step ends'),
  'from-goal': tr('Worked out from its goal'),
  'from-first-step': tr('Worked out from its first step'),
  'from-dream': tr('Worked out from its dream'),
  'from-first-goal': tr('Worked out from its first goal'),
  'from-created': tr('Worked out: the day the dream was created'),
  'from-last-task': tr('Worked out from its last task'),
  'from-last-step': tr('Worked out from its last step'),
  'from-last-goal': tr('Worked out from its last goal'),
  'clamped-to-end': tr('Worked out: kept on its end date'),
};

const KIND_LABEL: Record<TimelineKind, string> = {
  dream: tr('Dream'),
  goal: tr('Goal'),
  step: tr('Step'),
  task: tr('Task'),
};

type VisionMapTimelineProps = {
  dream: Dream;
  goals: Goal[];
  steps: VisionStep[];
  tasks: TaskItem[];
  priorityFilter?: string;
  statusFilter?: string;
  /** TL-FR-27: open the record's existing edit form. */
  onOpen: (kind: TimelineKind, id: number) => void;
  /** For tests: the day treated as today. */
  today?: number;
};

/** A day number as a local Date, for display only. */
function dayToDate(day: number) {
  const [year, month, date] = fromDay(day).split('-').map(Number);
  return new Date(year, month - 1, date);
}

function startOfMonth(day: number) {
  const date = dayToDate(day);
  return Math.round((Date.UTC(date.getFullYear(), date.getMonth(), 1)) / 86_400_000);
}

type HeaderCell = { key: string; start: number; days: number; label: string };

/** The two header rows for a zoom: a wide unit on top, ticks below. */
function headerCells(rangeStart: number, rangeEnd: number, zoom: TimelineZoom, locale: string) {
  const top: HeaderCell[] = [];
  const bottom: HeaderCell[] = [];
  const format = (day: number, options: Intl.DateTimeFormatOptions) => dayToDate(day).toLocaleDateString(locale, options);
  const pushUnits = (target: HeaderCell[], unit: 'day' | 'week' | 'month' | 'quarter' | 'year', label: (day: number) => string) => {
    let cursor = rangeStart;
    while (cursor <= rangeEnd) {
      const date = dayToDate(cursor);
      let next: number;
      if (unit === 'day') next = cursor + 1;
      else if (unit === 'week') next = cursor + (((8 - date.getDay()) % 7) || 7);
      else if (unit === 'month') next = Math.round(Date.UTC(date.getFullYear(), date.getMonth() + 1, 1) / 86_400_000);
      else if (unit === 'quarter') next = Math.round(Date.UTC(date.getFullYear(), Math.floor(date.getMonth() / 3) * 3 + 3, 1) / 86_400_000);
      else next = Math.round(Date.UTC(date.getFullYear() + 1, 0, 1) / 86_400_000);
      const end = Math.min(next, rangeEnd + 1);
      target.push({ key: `${unit}-${cursor}`, start: cursor, days: end - cursor, label: label(cursor) });
      cursor = next;
    }
  };
  if (zoom === 'week') {
    pushUnits(top, 'month', (day) => format(day, { month: 'long', year: 'numeric' }));
    pushUnits(bottom, 'day', (day) => format(day, { day: 'numeric' }));
  } else if (zoom === 'month') {
    pushUnits(top, 'month', (day) => format(day, { month: 'long', year: 'numeric' }));
    pushUnits(bottom, 'week', (day) => format(day, { day: 'numeric' }));
  } else if (zoom === 'quarter') {
    pushUnits(top, 'year', (day) => format(day, { year: 'numeric' }));
    pushUnits(bottom, 'month', (day) => format(day, { month: 'short' }));
  } else {
    pushUnits(top, 'year', (day) => format(day, { year: 'numeric' }));
    pushUnits(bottom, 'quarter', (day) => `Q${Math.floor(dayToDate(day).getMonth() / 3) + 1}`);
  }
  return { top, bottom };
}

/**
 * V16 (BRD 16.0.0, phase 1): one dream's goals, steps and tasks on a
 * calendar. It reads the same records as the tree and opens the tree's own
 * edit forms, so dates are still changed in one place.
 */
export function VisionMapTimeline({
  dream, goals, steps, tasks, priorityFilter = '', statusFilter = '', onOpen, today: todayOverride,
}: VisionMapTimelineProps) {
  const { t, language } = useI18n();
  const { settings: themeSettings, resolvedMode } = useThemeSettings();
  const today = todayOverride ?? todayDay();
  const [zoom, setZoom] = useState<TimelineZoom>('month');
  const [showCompleted, setShowCompleted] = useState(true);
  const timeline = useMemo(() => buildDreamTimeline(dream, goals, steps, tasks, today), [dream, goals, steps, tasks, today]);
  const filtered = useMemo(
    () => filterTimelineRows(timeline.rows, { priority: priorityFilter, status: statusFilter, showCompleted }),
    [timeline.rows, priorityFilter, statusFilter, showCompleted],
  );
  const [expanded, setExpanded] = useState<Set<string>>(() => defaultExpanded(timeline.rows));
  const rows = visibleRows(filtered, expanded);

  const scrollRef = useRef<HTMLDivElement>(null);
  const labelWidth = 280;
  const pxPerDay = PX_PER_DAY[zoom];

  // The calendar always covers every bar and today, from the first of a month.
  const planStart = Math.min(...timeline.rows.map((row) => row.start), today);
  const planEnd = Math.max(...timeline.rows.map((row) => row.end), today);
  const rangeStart = startOfMonth(planStart - PADDING_DAYS[zoom]);
  const rangeEnd = planEnd + PADDING_DAYS[zoom];
  const trackWidth = Math.ceil((rangeEnd - rangeStart + 1) * pxPerDay);
  const header = useMemo(() => headerCells(rangeStart, rangeEnd, zoom, language === 'km' ? 'km-KH' : 'en-GB'),
    [rangeStart, rangeEnd, zoom, language]);

  // TL-FR-24 and TL-FR-23: open on today (or the plan's start when today is
  // outside the plan) and keep the same day centred when the zoom changes.
  const firstBar = timeline.rows.length ? Math.min(...timeline.rows.map((row) => row.start)) : today;
  const lastBar = timeline.rows.length ? Math.max(...timeline.rows.map((row) => row.end)) : today;
  const centreDay = useRef<number>(today >= firstBar && today <= lastBar ? today : firstBar);
  useLayoutEffect(() => {
    scrollToDay(centreDay.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [zoom]);

  function scrollToDay(day: number) {
    const element = scrollRef.current;
    if (!element) return;
    const visibleTrack = Math.max(0, element.clientWidth - labelWidth);
    element.scrollLeft = Math.max(0, (day - rangeStart) * pxPerDay - visibleTrack / 2);
  }

  function currentCentre() {
    const element = scrollRef.current;
    if (!element) return centreDay.current;
    const visibleTrack = Math.max(0, element.clientWidth - labelWidth);
    return Math.round(rangeStart + (element.scrollLeft + visibleTrack / 2) / pxPerDay);
  }

  function changeZoom(next: TimelineZoom | null) {
    if (!next || next === zoom) return;
    centreDay.current = currentCentre();
    setZoom(next);
  }

  function toggle(key: string) {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  function statusHue(status: string) {
    return statusColor(status as StatusToken, resolvedMode, themeSettings.highContrast);
  }

  function statusLabel(row: TimelineRow) {
    const labels: Record<string, string> = row.kind === 'dream' ? dreamStatusLabels : workStatusLabels;
    return t(labels[row.status] ?? row.status);
  }

  function formatDay(day: number) {
    return dayToDate(day).toLocaleDateString(language === 'km' ? 'km-KH' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  }

  function tooltip(row: TimelineRow): ReactNode {
    const line = (label: string, value: ReactNode) => (
      <Typography variant="caption" component="div">{label}: {value}</Typography>
    );
    return (
      <Box sx={{ display: 'grid', gap: 0.25, py: 0.5 }}>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>{row.code} · {row.title}</Typography>
        {line(t('Start'), <>{formatDay(row.start)}{row.startWorkedOut ? ` (${t(WORKED_OUT_TEXT[row.startWorkedOut])})` : ''}</>)}
        {line(t('End'), <>{formatDay(row.end)}{row.endWorkedOut ? ` (${t(WORKED_OUT_TEXT[row.endWorkedOut])})` : ''}</>)}
        {line(t('Status'), statusLabel(row))}
        {line(t('Progress'), `${row.progress}%`)}
        {row.priority && line(t('Priority'), t(priorityLabels[row.priority as keyof typeof priorityLabels] ?? row.priority))}
        {row.owner && line(t('Owner'), row.owner)}
        {row.overdueDays > 0 && line(t('Overdue'), t('{days} day(s)', { days: row.overdueDays }))}
        {row.blockerReason && line(t('Blocked because'), row.blockerReason)}
        {row.overrunFrom !== null && (
          <Typography variant="caption" component="div">{t('Runs past the end of what it belongs to (hatched part).')}</Typography>
        )}
        {row.overrunDetail && <Typography variant="caption" component="div">{row.overrunDetail}</Typography>}
      </Box>
    );
  }

  /** Read aloud for the bar button, so the timeline is usable without seeing it. */
  function ariaLabel(row: TimelineRow) {
    const parts = [
      `${t(KIND_LABEL[row.kind])} ${row.code} ${row.title}`,
      `${formatDay(row.start)} – ${formatDay(row.end)}`,
      statusLabel(row),
      `${row.progress}%`,
    ];
    if (row.startWorkedOut || row.endWorkedOut) parts.push(t('some dates worked out'));
    if (row.overdueDays > 0) parts.push(t('Overdue by {days} day(s)', { days: row.overdueDays }));
    if (row.blocked) parts.push(t('Blocked'));
    if (row.overrunFrom !== null) parts.push(t('runs past its parent'));
    return parts.join(', ');
  }

  function onBarKey(event: KeyboardEvent<HTMLButtonElement>, row: TimelineRow) {
    if (event.key === 'ArrowRight' && row.hasChildren && !expanded.has(row.key)) {
      event.preventDefault();
      toggle(row.key);
    } else if (event.key === 'ArrowLeft' && row.hasChildren && expanded.has(row.key)) {
      event.preventDefault();
      toggle(row.key);
    }
  }

  const todayLeft = (today - rangeStart) * pxPerDay;
  const headerHeight = 48;

  return (
    <Box className="vm-timeline" sx={{ display: 'grid', gap: 1.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
        <ToggleButtonGroup
          size="small"
          exclusive
          value={zoom}
          onChange={(_, next: TimelineZoom | null) => changeZoom(next)}
          aria-label={t('Zoom')}
        >
          <ToggleButton value="week">{t('Week')}</ToggleButton>
          <ToggleButton value="month">{t('Month')}</ToggleButton>
          <ToggleButton value="quarter">{t('Quarter')}</ToggleButton>
          <ToggleButton value="year">{t('Year')}</ToggleButton>
        </ToggleButtonGroup>
        <Button variant="secondary" onClick={() => scrollToDay(today)}>{t('Today')}</Button>
        <Button variant="secondary" onClick={() => setExpanded(new Set(timeline.rows.filter((row) => row.hasChildren).map((row) => row.key)))}>
          {t('Expand all')}
        </Button>
        <Button variant="secondary" onClick={() => setExpanded(new Set([`dream-${dream.id}`]))}>{t('Collapse all')}</Button>
        <FormControlLabel
          control={<Switch size="small" checked={showCompleted} onChange={(event) => setShowCompleted(event.target.checked)} />}
          label={t('Show completed')}
        />
      </Box>

      {timeline.rows.length > 0 && (
        <Box
          ref={scrollRef}
          role="region"
          aria-label={t('Timeline of {title}', { title: dream.title })}
          sx={{ overflow: 'auto', maxHeight: '70vh', border: 1, borderColor: 'divider', borderRadius: 1, bgcolor: 'background.paper', position: 'relative' }}
        >
          <Box sx={{ width: labelWidth + trackWidth, position: 'relative' }}>
            {/* Header: sticky to the top, with a sticky corner on the left. */}
            <Box sx={{ display: 'flex', position: 'sticky', top: 0, zIndex: 3, bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider' }}>
              <Box sx={{ width: labelWidth, flex: 'none', position: 'sticky', left: 0, zIndex: 4, bgcolor: 'background.paper', borderRight: 1, borderColor: 'divider', display: 'flex', alignItems: 'flex-end', px: 1.5, pb: 0.5, height: headerHeight }}>
                <Typography variant="caption" color="text.secondary">{t('Goals, steps and tasks')}</Typography>
              </Box>
              <Box sx={{ position: 'relative', width: trackWidth, height: headerHeight, flex: 'none' }}>
                {[header.top, header.bottom].map((cells, level) => cells.map((cell) => (
                  <Box
                    key={cell.key}
                    sx={{
                      position: 'absolute', top: level * 24, height: 24, left: (cell.start - rangeStart) * pxPerDay, width: cell.days * pxPerDay,
                      borderLeft: 1, borderColor: 'divider', px: 0.5, overflow: 'hidden', whiteSpace: 'nowrap',
                      fontSize: level === 0 ? 12 : 11, fontWeight: level === 0 ? 600 : 400, color: level === 0 ? 'text.primary' : 'text.secondary', lineHeight: '24px',
                    }}
                  >
                    {cell.days * pxPerDay >= 18 ? cell.label : ''}
                  </Box>
                )))}
                {today >= rangeStart && today <= rangeEnd && (
                  <Box sx={{ position: 'absolute', top: 24, left: todayLeft + 3, zIndex: 1, fontSize: 11, fontWeight: 700, color: 'error.main', lineHeight: '24px', whiteSpace: 'nowrap', bgcolor: 'background.paper', px: 0.5, borderLeft: 2, borderColor: 'error.main' }}>
                    {t('Today')}
                  </Box>
                )}
              </Box>
            </Box>

            {/* Today line across every row (TL-FR-12). */}
            <Box
              aria-hidden="true"
              sx={{ position: 'absolute', top: headerHeight, bottom: 0, left: labelWidth + todayLeft, width: 2, bgcolor: 'error.main', opacity: 0.6, zIndex: 1, pointerEvents: 'none' }}
            />

            {rows.map((row) => {
              const tint = tintColors(statusHue(row.status));
              const left = (row.start - rangeStart) * pxPerDay;
              const width = Math.max(10, (row.end - row.start + 1) * pxPerDay);
              const isDream = row.kind === 'dream';
              const isExpanded = expanded.has(row.key);
              return (
                <Box
                  key={row.key}
                  data-row-key={row.key}
                  sx={{ display: 'flex', height: ROW_HEIGHT, borderBottom: 1, borderColor: 'divider', opacity: row.archived ? 0.55 : 1 }}
                >
                  <Box sx={{
                    width: labelWidth, flex: 'none', position: 'sticky', left: 0, zIndex: 2, bgcolor: 'background.paper',
                    borderRight: 1, borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 0.5, pl: 0.5 + row.depth * 2, pr: 1, minWidth: 0,
                  }}>
                    {row.hasChildren ? (
                      <button
                        type="button"
                        className="vm-timeline__toggle"
                        aria-expanded={isExpanded}
                        aria-label={isExpanded ? t('Collapse {title}', { title: row.title }) : t('Expand {title}', { title: row.title })}
                        onClick={() => toggle(row.key)}
                      >
                        {isExpanded ? <ChevronDown size={16} aria-hidden="true" /> : <ChevronRight size={16} aria-hidden="true" />}
                      </button>
                    ) : (
                      <Box sx={{ width: 24, flex: 'none' }} />
                    )}
                    <Typography variant="caption" color="text.secondary" sx={{ flex: 'none' }}>{row.code}</Typography>
                    <Link
                      component="button"
                      type="button"
                      underline="hover"
                      color="inherit"
                      onClick={() => onOpen(row.kind, row.id)}
                      sx={{ fontSize: 14, fontWeight: isDream ? 700 : row.kind === 'goal' ? 600 : 400, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', textAlign: 'left', minWidth: 0 }}
                    >
                      {row.title}{row.archived ? ` ${t('(archived)')}` : ''}
                    </Link>
                    {row.blocked && <Ban size={14} aria-label={t('Blocked')} style={{ flex: 'none', color: statusHue('BLOCKED') }} />}
                    {row.overdueDays > 0 && (
                      <Typography variant="caption" sx={{ color: 'error.main', fontWeight: 600, flex: 'none' }}>{t('Overdue')}</Typography>
                    )}
                  </Box>
                  <Box sx={{ position: 'relative', width: trackWidth, flex: 'none' }}>
                    <Tooltip title={tooltip(row)} placement="top" arrow describeChild>
                      <button
                        type="button"
                        className="vm-timeline__bar"
                        aria-label={ariaLabel(row)}
                        onClick={() => onOpen(row.kind, row.id)}
                        onKeyDown={(event) => onBarKey(event, row)}
                        style={{
                          left,
                          width,
                          top: (ROW_HEIGHT - (isDream ? BAR_HEIGHT + 4 : BAR_HEIGHT)) / 2,
                          height: isDream ? BAR_HEIGHT + 4 : BAR_HEIGHT,
                          background: tint.background,
                          borderColor: row.blocked ? statusHue('BLOCKED') : tint.border,
                          borderWidth: row.blocked || isDream ? 2 : 1,
                          borderLeftStyle: row.startWorkedOut ? 'dashed' : 'solid',
                          borderRightStyle: row.endWorkedOut ? 'dashed' : 'solid',
                        }}
                      >
                        <span
                          className="vm-timeline__fill"
                          style={{ width: `${Math.min(100, Math.max(0, row.progress))}%`, background: `var(--progress-${progressTone(row.progress)})` }}
                        />
                        {row.overrunFrom !== null && (
                          <span
                            className="vm-timeline__overrun"
                            aria-hidden="true"
                            style={{ left: Math.max(0, (row.overrunFrom - row.start) * pxPerDay), right: 0 }}
                          />
                        )}
                        {row.overdueDays > 0 && <span className="vm-timeline__overdue" aria-hidden="true" />}
                        {width >= 44 && <span className="vm-timeline__label">{row.progress}%</span>}
                      </button>
                    </Tooltip>
                  </Box>
                </Box>
              );
            })}
          </Box>
        </Box>
      )}

      <Typography variant="caption" color="text.secondary">
        {t('A dashed bar end means the date was worked out from related records, not entered. Hatching shows the part that runs past what it belongs to. Select a bar to edit it.')}
      </Typography>

      {timeline.undated.length > 0 && (
        <Box component="section" aria-label={t('Undated')} sx={{ border: 1, borderColor: 'divider', borderRadius: 1, p: 1.5 }}>
          <Typography variant="subtitle2">{t('Undated')}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {t('These have no date yet, so they are not on the timeline.')}{' '}
            <Link component={RouterLink} to="/steps/undated">{t('Add dates to steps')}</Link>
          </Typography>
          <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
            {timeline.undated.map((item) => (
              <li key={item.key}>
                <Link component="button" type="button" onClick={() => onOpen(item.kind, item.id)}>
                  {t(KIND_LABEL[item.kind])} {item.code} · {item.title}
                </Link>
              </li>
            ))}
          </Box>
        </Box>
      )}
    </Box>
  );
}
