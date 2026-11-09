import { useEffect, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react';
import { Ban, ChevronDown, ChevronRight, CircleCheck, Plus, ClipboardCheck, Diamond, Flag, Mail, Printer, type LucideIcon } from 'lucide-react';
import { Link as RouterLink } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';
import FormControlLabel from '@mui/material/FormControlLabel';
import Link from '@mui/material/Link';
import Switch from '@mui/material/Switch';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { TimelineAgenda, TimelineTable } from './TimelineAlternateViews';
import { Button } from '../common/Button';
import { tintColors } from '../common/TintedChip';
import { useThemeSettings } from '../../context/ThemeModeContext';
import type { DateChange } from '../../hooks/useTimelineReschedule';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import { statusColor, type StatusToken } from '../../theme';
import type { Dream, Goal, TaskItem, VisionArea, VisionStep } from '../../types/vision';
import { dreamStatusLabels, priorityLabels, reviewTypeLabels, workStatusLabels } from '../../utils/enumLabels';
import { progressTone, progressToneLegend } from '../../utils/progressTone';
import {
  MARKER_TYPES,
  buildDreamTimeline,
  buildPlanTimeline,
  busyCounts,
  defaultExpanded,
  filterTimelineRows,
  fromDay,
  todayDay,
  visibleRows,
  type DreamTimeline,
  type TimelineKind,
  type TimelineMarker,
  type TimelineMarkerType,
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
  'from-today': tr('Worked out: no start date yet, so shown from today'),
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

const ADD_CHILD_LABEL: Record<Exclude<TimelineKind, 'task'>, string> = {
  dream: tr('Add a goal to {title}'),
  goal: tr('Add a step to {title}'),
  step: tr('Add a task to {title}'),
};

const MARKER_INFO: Record<TimelineMarkerType, { icon: LucideIcon; label: string; colour: string }> = {
  commitment: { icon: Flag, label: tr('Commitment start'), colour: 'var(--progress-done)' },
  milestone: { icon: Diamond, label: tr('Milestones'), colour: 'var(--moonshot-fg, #6b4fbb)' },
  review: { icon: ClipboardCheck, label: tr('Reviews'), colour: 'var(--progress-high)' },
  'follow-up': { icon: Mail, label: tr('Follow-ups'), colour: 'var(--progress-mid-text)' },
};

/** What a bar or an undated item points at; enough to open its edit form. */
export type TimelineTarget = { kind: TimelineKind; id: number; dreamId: number };

/** Rows above this many are drawn only when on screen (TL-FR-37). */
const WINDOW_FROM = 300;

type TimelineViewProps = {
  timeline: DreamTimeline;
  /** The region's accessible name. */
  label: string;
  priorityFilter?: string;
  statusFilter?: string;
  /** Rows expanded on first open; ignored when `expanded` is controlled. */
  initialExpanded: Set<string>;
  expanded?: Set<string>;
  onExpandedChange?: (next: Set<string>) => void;
  zoom?: TimelineZoom;
  onZoomChange?: (next: TimelineZoom) => void;
  showCompleted?: boolean;
  onShowCompletedChange?: (next: boolean) => void;
  /** The day to centre on first open; defaults to today, or the plan's start. */
  initialCentre?: number | null;
  /** Told the centred day after scrolling stops (TL-FR-36). */
  onCentreChange?: (day: number) => void;
  /** A row to bring into view on first open (TL-FR-34). */
  focusKey?: string | null;
  /** TL-FR-27: open the record's edit form. */
  onOpen: (target: TimelineTarget) => void;
  /** TL-FR-13: commitment, milestone, review and follow-up marks on dream rows. */
  markers?: TimelineMarker[];
  hiddenMarkers?: TimelineMarkerType[];
  onHiddenMarkersChange?: (next: TimelineMarkerType[]) => void;
  /** TL-FR-38: offer a Print button (the full timeline page). */
  printable?: boolean;
  /** TL-FR-39 to TL-FR-43: when given, an Edit dates switch lets task and step bars be dragged. */
  onReschedule?: (change: DateChange) => Promise<void>;
  /** TL-FR-31: when given, dream, goal and step rows offer to add a child. */
  onQuickAdd?: (target: TimelineTarget) => void;
  /** TL-FR-48: a view-only timeline (the shared plan page): nothing opens or changes. */
  readOnly?: boolean;
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

type ListItem = { type: 'group'; key: string; label: string } | { type: 'row'; key: string; row: TimelineRow };

/**
 * V16 (BRD 16.0.0): goals, steps and tasks on a calendar. Shared by the
 * single-dream timeline, the overview and the full timeline page. It opens the
 * existing edit forms, so dates are still changed in one place.
 */
export function TimelineView({
  timeline, label, priorityFilter = '', statusFilter = '', initialExpanded, expanded: expandedProp, onExpandedChange,
  zoom: zoomProp, onZoomChange, showCompleted: showCompletedProp, onShowCompletedChange, initialCentre, onCentreChange,
  focusKey, onOpen, markers = [], hiddenMarkers: hiddenMarkersProp, onHiddenMarkersChange, printable = false, onReschedule, onQuickAdd, readOnly = false, today: todayOverride,
}: TimelineViewProps) {
  const { t, language } = useI18n();
  const { settings: themeSettings, resolvedMode } = useThemeSettings();
  const today = todayOverride ?? todayDay();
  const [ownZoom, setOwnZoom] = useState<TimelineZoom>(zoomProp ?? 'month');
  const zoom = zoomProp ?? ownZoom;
  const [ownShowCompleted, setOwnShowCompleted] = useState(true);
  const showCompleted = showCompletedProp ?? ownShowCompleted;
  const [ownExpanded, setOwnExpanded] = useState<Set<string>>(initialExpanded);
  const [ownHidden, setOwnHidden] = useState<TimelineMarkerType[]>([]);
  const hiddenMarkers = hiddenMarkersProp ?? ownHidden;
  const [legendOpen, setLegendOpen] = useState(false);
  // TL-FR-44: the same breakpoint the Vision Map uses for its phone layout.
  const isPhone = useMediaQuery('(max-width:600px)');
  // TL-FR-47: the rows as a plain table instead of the calendar.
  const [asTable, setAsTable] = useState(false);
  // TL-NFR-06: no animation when the participant or the system asks for less motion.
  const systemReducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)');
  const reduceMotion = themeSettings.reduceMotion || systemReducedMotion;
  const [pendingFocus, setPendingFocus] = useState<string | null>(null);
  // TL-FR-42: dragging is off until asked for, and never offered on touch screens.
  const coarsePointer = useMediaQuery('(pointer: coarse)');
  const canEditDates = Boolean(onReschedule) && !readOnly && !coarsePointer;
  const [editDates, setEditDates] = useState(false);
  type Shift = { key: string; mode: 'move' | 'start' | 'end'; delta: number; moved: boolean };
  const [shift, setShift] = useState<Shift | null>(null);
  const dragOrigin = useRef(0);
  const suppressClick = useRef(false);
  const [savingKey, setSavingKey] = useState<string | null>(null);
  const [rescheduleError, setRescheduleError] = useState('');
  // While printing, every row is drawn: windowing would leave the page half empty.
  const [printing, setPrinting] = useState(false);
  useEffect(() => {
    const before = () => setPrinting(true);
    const after = () => setPrinting(false);
    window.addEventListener('beforeprint', before);
    window.addEventListener('afterprint', after);
    return () => {
      window.removeEventListener('beforeprint', before);
      window.removeEventListener('afterprint', after);
    };
  }, []);
  const expanded = expandedProp ?? ownExpanded;
  const filtered = useMemo(
    () => filterTimelineRows(timeline.rows, { priority: priorityFilter, status: statusFilter, showCompleted }),
    [timeline.rows, priorityFilter, statusFilter, showCompleted],
  );
  const rows = useMemo(() => visibleRows(filtered, expanded), [filtered, expanded]);
  const dreamCount = timeline.rows.filter((row) => row.kind === 'dream').length;

  function setExpanded(next: Set<string>) {
    setOwnExpanded(next);
    onExpandedChange?.(next);
  }

  function setZoom(next: TimelineZoom) {
    setOwnZoom(next);
    onZoomChange?.(next);
  }

  function toggleMarker(type: TimelineMarkerType) {
    const next = hiddenMarkers.includes(type) ? hiddenMarkers.filter((item) => item !== type) : [...hiddenMarkers, type];
    setOwnHidden(next);
    onHiddenMarkersChange?.(next);
  }

  const markerTypesPresent = MARKER_TYPES.filter((type) => markers.some((marker) => marker.type === type));
  const markersByDream = new Map<number, TimelineMarker[]>();
  for (const marker of markers) {
    if (hiddenMarkers.includes(marker.type)) continue;
    markersByDream.set(marker.dreamId, [...(markersByDream.get(marker.dreamId) ?? []), marker]);
  }

  function setShowCompleted(next: boolean) {
    setOwnShowCompleted(next);
    onShowCompletedChange?.(next);
  }

  // Group headers go in front of the first row of each vision area (TL-FR-33).
  const items = useMemo(() => {
    const list: ListItem[] = [];
    let lastGroup: string | undefined;
    for (const row of rows) {
      if (row.groupKey && row.groupKey !== lastGroup) {
        list.push({ type: 'group', key: row.groupKey, label: row.groupLabel ?? '' });
      }
      lastGroup = row.groupKey;
      list.push({ type: 'row', key: row.key, row });
    }
    return list;
  }, [rows]);

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
  const centreDay = useRef<number>(initialCentre ?? (today >= firstBar && today <= lastBar ? today : firstBar));
  useLayoutEffect(() => {
    scrollToDay(centreDay.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [zoom]);

  // TL-FR-34: bring the focused dream into view once, on first open.
  useLayoutEffect(() => {
    const element = scrollRef.current;
    if (!element || !focusKey) return;
    const index = items.findIndex((item) => item.key === focusKey);
    if (index > 0) element.scrollTop = Math.max(0, (index - 1) * ROW_HEIGHT);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // After a keyboard move, focus the target bar once it is drawn.
  useEffect(() => {
    if (!pendingFocus) return;
    const bar = scrollRef.current?.querySelector<HTMLButtonElement>(`[data-row-key="${pendingFocus}"] .vm-timeline__bar`);
    if (bar) {
      bar.focus();
      setPendingFocus(null);
    }
  });

  // TL-FR-37: with many rows, draw only those on screen. The scroll position
  // is tracked for that, and reported (debounced) as the centred day.
  const [scrollTop, setScrollTop] = useState(0);
  const [viewportHeight, setViewportHeight] = useState(800);
  const centreTimer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(centreTimer.current), []);

  function onScroll() {
    const element = scrollRef.current;
    if (!element) return;
    if (items.length > WINDOW_FROM) {
      // The window keeps 10 rows above and 20 below the screen, so it only
      // needs redrawing every few rows; React skips the render when unchanged.
      const step = 5 * ROW_HEIGHT;
      setScrollTop(Math.floor(element.scrollTop / step) * step);
      setViewportHeight(element.clientHeight);
    }
    if (onCentreChange) {
      window.clearTimeout(centreTimer.current);
      centreTimer.current = window.setTimeout(() => onCentreChange(currentCentre()), 400);
    }
  }

  const windowed = items.length > WINDOW_FROM && !printing;
  const firstIndex = windowed ? Math.max(0, Math.floor(scrollTop / ROW_HEIGHT) - 10) : 0;
  const lastIndex = windowed ? Math.min(items.length, firstIndex + Math.ceil(viewportHeight / ROW_HEIGHT) + 20) : items.length;
  const shownItems = items.slice(firstIndex, lastIndex);

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
    // Only the zoom is reported here: two URL writes in one go would race,
    // and the centred day is reported by the scroll that follows.
    setZoom(next);
  }

  function toggle(key: string) {
    const next = new Set(expanded);
    if (next.has(key)) next.delete(key);
    else next.add(key);
    setExpanded(next);
  }

  function statusHue(status: string) {
    return statusColor(status as StatusToken, resolvedMode, themeSettings.highContrast);
  }

  function statusLabel(row: TimelineRow) {
    const labels: Record<string, string> = row.kind === 'dream' ? dreamStatusLabels : workStatusLabels;
    return t(labels[row.status] ?? row.status);
  }

  const locale = language === 'km' ? 'km-KH' : 'en-GB';

  function formatDay(day: number) {
    return dayToDate(day).toLocaleDateString(locale, { day: 'numeric', month: 'short', year: 'numeric' });
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

  function movable(row: TimelineRow) {
    return editDates && canEditDates && !row.archived && (row.kind === 'task' || row.kind === 'step');
  }

  /** A row's start and end with a drag or keyboard move applied (TL-FR-39). */
  function shifted(row: TimelineRow): { start: number; end: number } {
    if (!shift || shift.key !== row.key) return { start: row.start, end: row.end };
    if (shift.mode === 'move') return { start: row.start + shift.delta, end: row.end + shift.delta };
    if (shift.mode === 'start') return { start: Math.min(row.start + shift.delta, row.end), end: row.end };
    return { start: row.start, end: Math.max(row.end + shift.delta, row.start) };
  }

  async function commitShift(row: TimelineRow) {
    const current = shift;
    if (!current || current.key !== row.key || current.delta === 0 || !onReschedule) {
      setShift(null);
      return;
    }
    const { start, end } = shifted(row);
    // A task without its own start keeps none, unless its start is what was dragged.
    const keepsNoStart = row.startWorkedOut !== null && current.mode !== 'start';
    const change: DateChange = row.kind === 'task'
      ? { kind: 'task', id: row.id, startDate: keepsNoStart ? null : fromDay(start), dueDate: fromDay(end) }
      : { kind: 'step', id: row.id, targetDate: fromDay(end) };
    setSavingKey(row.key);
    setRescheduleError('');
    try {
      await onReschedule(change);
    } catch (error) {
      // TL-FR-40: refused by the server; the bar goes back and says why.
      setRescheduleError(error instanceof Error ? error.message : t('That date could not be saved.'));
    } finally {
      setSavingKey(null);
      setShift(null);
    }
  }

  function onBarPointerDown(event: ReactPointerEvent<HTMLElement>, row: TimelineRow) {
    if (!movable(row) || event.button !== 0) return;
    const handle = (event.target as HTMLElement).dataset.handle as 'start' | 'end' | undefined;
    const mode = handle ?? (row.kind === 'task' ? 'move' : null);
    if (!mode) return;
    event.currentTarget.setPointerCapture?.(event.pointerId);
    dragOrigin.current = event.clientX;
    setShift({ key: row.key, mode, delta: 0, moved: false });
  }

  function onBarPointerMove(event: ReactPointerEvent<HTMLElement>, row: TimelineRow) {
    if (!shift || shift.key !== row.key) return;
    const dx = event.clientX - dragOrigin.current;
    // TL-FR-42: a few pixels of wobble is still a click, not a drag.
    if (!shift.moved && Math.abs(dx) < 4) return;
    setShift({ ...shift, moved: true, delta: Math.round(dx / pxPerDay) });
  }

  function onBarPointerUp(row: TimelineRow) {
    if (!shift || shift.key !== row.key) return;
    if (shift.moved) {
      suppressClick.current = true;
      void commitShift(row);
    } else {
      setShift(null);
    }
  }

  function onBarClick(row: TimelineRow) {
    if (suppressClick.current) {
      suppressClick.current = false;
      return;
    }
    if (!readOnly) onOpen(row);
  }

  /** Marks closer than one icon apart alternate between an upper and a lower lane, so none hides another. */
  function staggered(list: TimelineMarker[]) {
    let lastX = Number.NEGATIVE_INFINITY;
    let lastLane = 1;
    return list.map((marker) => {
      const x = (marker.day - rangeStart) * pxPerDay;
      const lane = x - lastX < 16 ? (lastLane === 0 ? 1 : 0) : 0;
      lastX = x;
      lastLane = lane;
      return { marker, lane };
    });
  }

  function markerText(marker: TimelineMarker) {
    const date = formatDay(marker.day);
    if (marker.type === 'commitment') return t('Commitment start: {date}', { date });
    if (marker.type === 'milestone') return t('Milestone on {date}: {title}', { date, title: marker.label });
    if (marker.type === 'review') {
      return t('{type} review on {date}', { type: t(reviewTypeLabels[marker.label as keyof typeof reviewTypeLabels] ?? marker.label), date });
    }
    return marker.label ? t('Follow up on {date}: {subject}', { date, subject: marker.label }) : t('Follow up on {date}', { date });
  }

  function completedText(row: TimelineRow) {
    const done = row.completedOn ?? row.end;
    const difference = done - row.end;
    const date = formatDay(done);
    return difference < 0
      ? t('Completed on {date}, {days} day(s) early', { date, days: -difference })
      : t('Completed on {date}, {days} day(s) late', { date, days: difference });
  }

  /** Days one Page Up / Page Down moves at each zoom. */
  const PAGE_DAYS: Record<TimelineZoom, number> = { week: 7, month: 30, quarter: 91, year: 365 };

  /** Moves keyboard focus to another row's bar, scrolling it into the drawn window first. */
  function focusRowAt(index: number) {
    const rowItems = items.filter((item): item is Extract<ListItem, { type: 'row' }> => item.type === 'row');
    const target = rowItems[Math.max(0, Math.min(rowItems.length - 1, index))];
    const element = scrollRef.current;
    if (!target || !element) return;
    const position = items.findIndex((item) => item.key === target.key) * ROW_HEIGHT;
    const visible = element.clientHeight - headerHeight;
    if (position < element.scrollTop || position + ROW_HEIGHT > element.scrollTop + visible) {
      element.scrollTop = Math.max(0, position - visible / 2);
      setScrollTop(element.scrollTop);
      setViewportHeight(element.clientHeight);
    }
    setPendingFocus(target.key);
  }

  // TL-FR-29: up and down move between rows, left and right fold and unfold,
  // Home and End jump to the ends, Page Up and Page Down move the calendar.
  function onBarKey(event: KeyboardEvent<HTMLElement>, row: TimelineRow) {
    // TL-FR-43: with Edit dates on, Shift + arrows move a task a day (or a
    // step's end), Enter saves the move and Escape drops it.
    if (movable(row) && event.shiftKey && (event.key === 'ArrowLeft' || event.key === 'ArrowRight')) {
      event.preventDefault();
      const step = event.key === 'ArrowRight' ? 1 : -1;
      const base = shift && shift.key === row.key ? shift.delta : 0;
      setShift({ key: row.key, mode: row.kind === 'task' ? 'move' : 'end', delta: base + step, moved: true });
      return;
    }
    if (shift && shift.key === row.key && (event.key === 'Enter' || event.key === 'Escape')) {
      event.preventDefault();
      if (event.key === 'Enter') void commitShift(row);
      else setShift(null);
      return;
    }
    const rowKeys = items.filter((item) => item.type === 'row').map((item) => item.key);
    const index = rowKeys.indexOf(row.key);
    const element = scrollRef.current;
    if (event.key === 'ArrowRight' && row.hasChildren && !expanded.has(row.key)) {
      toggle(row.key);
    } else if (event.key === 'ArrowLeft' && row.hasChildren && expanded.has(row.key)) {
      toggle(row.key);
    } else if (event.key === 'ArrowLeft' && row.parentKey && rowKeys.includes(row.parentKey)) {
      focusRowAt(rowKeys.indexOf(row.parentKey));
    } else if (event.key === 'ArrowDown') {
      focusRowAt(index + 1);
    } else if (event.key === 'ArrowUp') {
      focusRowAt(index - 1);
    } else if (event.key === 'Home') {
      focusRowAt(0);
    } else if (event.key === 'End') {
      focusRowAt(rowKeys.length - 1);
    } else if ((event.key === 'PageDown' || event.key === 'PageUp') && element) {
      element.scrollLeft += (event.key === 'PageDown' ? 1 : -1) * PAGE_DAYS[zoom] * pxPerDay;
    } else {
      return;
    }
    event.preventDefault();
  }

  const todayLeft = (today - rangeStart) * pxPerDay;
  // A shared, view-only timeline draws bars as images, not buttons that do nothing.
  const BarElement = readOnly ? 'span' : 'button';
  // TL-FR-35: in the long zooms, a band under the dates counts unfinished work per period.
  const showBusy = zoom === 'quarter' || zoom === 'year';
  const busy = useMemo(() => (showBusy ? busyCounts(filtered, header.bottom) : []), [showBusy, filtered, header.bottom]);
  const busiest = Math.max(1, ...busy);

  // TL-NFR-01: the date header can hold hundreds of cells (every day at Week
  // zoom). They are plain elements with inline positions, built once per zoom,
  // so scrolling never restyles or rebuilds them.
  const dateCells = useMemo(() => [header.top, header.bottom].flatMap((cells, level) => cells.map((cell) => (
    <div
      key={cell.key}
      className={`vm-timeline__date vm-timeline__date--${level === 0 ? 'top' : 'bottom'}`}
      style={{ top: level * 24, left: (cell.start - rangeStart) * pxPerDay, width: cell.days * pxPerDay }}
    >
      {cell.days * pxPerDay >= 18 ? cell.label : ''}
    </div>
  ))), [header, rangeStart, pxPerDay]);

  const busyCells = useMemo(() => (showBusy ? header.bottom.map((cell, index) => (
    <div
      key={`busy-${cell.key}`}
      className="vm-timeline__busy"
      title={t('{count} unfinished in this period', { count: busy[index] })}
      style={{
        left: (cell.start - rangeStart) * pxPerDay,
        width: cell.days * pxPerDay,
        background: busy[index] ? `color-mix(in srgb, var(--progress-mid) ${Math.round(12 + (busy[index] / busiest) * 58)}%, transparent)` : 'transparent',
      }}
    >
      {busy[index] && cell.days * pxPerDay >= 18 ? busy[index] : ''}
    </div>
  )) : null), [showBusy, header.bottom, busy, busiest, rangeStart, pxPerDay, t]);
  const headerHeight = showBusy ? 70 : 48;

  return (
    <Box className="vm-timeline" sx={{ display: 'grid', gap: 1.5 }}>
      <Typography className="vm-timeline__print-only" variant="caption">
        {t('Printed on {date}', { date: formatDay(todayDay()) })}
      </Typography>
      <Box className="vm-timeline__toolbar" sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
        {!isPhone && !asTable && (
        <>
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
        <Button
          variant="secondary"
          onClick={() => setExpanded(new Set(dreamCount === 1 ? timeline.rows.filter((row) => row.kind === 'dream').map((row) => row.key) : []))}
        >
          {t('Collapse all')}
        </Button>
        </>
        )}
        <FormControlLabel
          control={<Switch size="small" checked={showCompleted} onChange={(event) => setShowCompleted(event.target.checked)} />}
          label={t('Show completed')}
        />
        {canEditDates && !isPhone && !asTable && (
          <FormControlLabel
            control={<Switch size="small" checked={editDates} onChange={(event) => { setEditDates(event.target.checked); setShift(null); }} />}
            label={t('Edit dates')}
          />
        )}
        <Button variant="secondary" aria-expanded={legendOpen} onClick={() => setLegendOpen((open) => !open)}>
          {legendOpen ? t('Hide legend') : t('Legend')}
        </Button>
        {!isPhone && (
          <Button variant="secondary" aria-pressed={asTable} onClick={() => setAsTable((value) => !value)}>
            {asTable ? t('Show as calendar') : t('Show as table')}
          </Button>
        )}
        {printable && (
          <Button variant="secondary" onClick={() => window.print()}>
            <Printer size={16} aria-hidden="true" style={{ marginRight: 6 }} />
            {t('Print')}
          </Button>
        )}
      </Box>

      {markerTypesPresent.length > 0 && (
        <Box className="vm-timeline__toolbar" role="group" aria-label={t('Marks to show')} sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
          <Typography variant="caption" color="text.secondary">{t('Show on the calendar:')}</Typography>
          {markerTypesPresent.map((type) => {
            const info = MARKER_INFO[type];
            const Icon = info.icon;
            const on = !hiddenMarkers.includes(type);
            return (
              <Chip
                key={type}
                size="small"
                icon={<Icon size={14} aria-hidden="true" style={{ color: info.colour }} />}
                label={t(info.label)}
                variant={on ? 'filled' : 'outlined'}
                aria-pressed={on}
                onClick={() => toggleMarker(type)}
              />
            );
          })}
        </Box>
      )}

      <Collapse in={legendOpen || printing} unmountOnExit timeout={reduceMotion ? 0 : 'auto'}>
        <Box className="vm-timeline__legend" sx={{ display: 'grid', gap: 1, gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', border: 1, borderColor: 'divider', borderRadius: 1, p: 1.5 }}>
          {progressToneLegend().map((tone) => (
            <LegendItem key={tone.tone} swatch={<span className="vm-legend__swatch" style={{ background: `var(--progress-${tone.tone})` }} />}>
              {t('Filled part: {label} ({range})', { label: t(tone.label), range: tone.range })}
            </LegendItem>
          ))}
          <LegendItem swatch={<span className="vm-legend__swatch vm-legend__swatch--dashed" />}>{t('Dashed end: date worked out, not entered')}</LegendItem>
          <LegendItem swatch={<span className="vm-legend__swatch vm-legend__swatch--hatched" />}>{t('Hatched: runs past what it belongs to')}</LegendItem>
          <LegendItem swatch={<span className="vm-legend__swatch vm-legend__swatch--overdue" />}>{t('Red end: overdue')}</LegendItem>
          <LegendItem swatch={<Ban size={14} aria-hidden="true" style={{ color: statusHue('BLOCKED') }} />}>{t('Blocked')}</LegendItem>
          <LegendItem swatch={<span className="vm-legend__swatch vm-legend__swatch--today" />}>{t('Red line: today')}</LegendItem>
          <LegendItem swatch={<CircleCheck size={14} aria-hidden="true" style={{ color: 'var(--progress-done)' }} />}>{t('Check: the day a task was really completed')}</LegendItem>
          {MARKER_TYPES.map((type) => {
            const Icon = MARKER_INFO[type].icon;
            return (
              <LegendItem key={type} swatch={<Icon size={14} aria-hidden="true" style={{ color: MARKER_INFO[type].colour }} />}>
                {t(MARKER_INFO[type].label)}
              </LegendItem>
            );
          })}
          {showBusy && <LegendItem swatch={<span className="vm-legend__swatch vm-legend__swatch--busy" />}>{t('Busy band: unfinished goals, steps and tasks in each period')}</LegendItem>}
        </Box>
      </Collapse>

      {rescheduleError && (
        <Alert severity="error" onClose={() => setRescheduleError('')}>{rescheduleError}</Alert>
      )}
      {editDates && canEditDates && !isPhone && !asTable && (
        <Typography variant="caption" color="text.secondary">
          {t('Drag a task to move it, or drag the end of a task or step to change its date. On a focused bar, Shift + ← → moves it a day, Enter saves and Esc cancels. Every change is checked like a form save, and can be undone.')}
        </Typography>
      )}
      {isPhone && (
        <TimelineAgenda
          rows={filtered}
          markers={markers.filter((marker) => !hiddenMarkers.includes(marker.type))}
          markerText={markerText}
          today={today}
          kindLabel={(kind) => t(KIND_LABEL[kind])}
          statusLabel={statusLabel}
          formatDay={formatDay}
          onOpen={onOpen}
          locale={locale}
        />
      )}
      {!isPhone && asTable && (
        <TimelineTable
          rows={filtered}
          caption={label}
          kindLabel={(kind) => t(KIND_LABEL[kind])}
          statusLabel={statusLabel}
          formatDay={formatDay}
          onOpen={onOpen}
        />
      )}
      {timeline.rows.length > 0 && !isPhone && !asTable && (
        <Box
          ref={scrollRef}
          role="region"
          aria-label={label}
          onScroll={onScroll}
          className="vm-timeline__scroll"
          sx={{ overflow: 'auto', maxHeight: '70vh', border: 1, borderColor: 'divider', borderRadius: 1, bgcolor: 'background.paper', position: 'relative' }}
        >
          <Box sx={{ width: labelWidth + trackWidth, position: 'relative' }}>
            {/* Header: sticky to the top, with a sticky corner on the left. */}
            <Box sx={{ display: 'flex', position: 'sticky', top: 0, zIndex: 3, bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider' }}>
              <Box sx={{ width: labelWidth, flex: 'none', position: 'sticky', left: 0, zIndex: 4, bgcolor: 'background.paper', borderRight: 1, borderColor: 'divider', height: headerHeight, display: 'grid', gridTemplateRows: showBusy ? '48px 22px' : '48px' }}>
                <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'end', px: 1.5, pb: 0.5 }}>
                  {dreamCount > 1 ? t('Dreams, goals, steps and tasks') : t('Goals, steps and tasks')}
                </Typography>
                {showBusy && (
                  <Typography variant="caption" color="text.secondary" sx={{ px: 1.5, lineHeight: '22px', borderTop: 1, borderColor: 'divider' }}>
                    {t('Busy (unfinished work)')}
                  </Typography>
                )}
              </Box>
              <Box sx={{ position: 'relative', width: trackWidth, height: headerHeight, flex: 'none' }}>
                {dateCells}
                {busyCells}
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

            {windowed && <Box sx={{ height: firstIndex * ROW_HEIGHT }} />}
            {shownItems.map((item) => {
              if (item.type === 'group') {
                return (
                  <Box
                    key={item.key}
                    role="heading"
                    aria-level={3}
                    sx={{ display: 'flex', height: ROW_HEIGHT, alignItems: 'center', borderBottom: 1, borderColor: 'divider', bgcolor: 'action.hover' }}
                  >
                    <Typography
                      variant="overline"
                      sx={{ position: 'sticky', left: 0, px: 1.5, color: 'text.secondary', letterSpacing: '0.05em', lineHeight: `${ROW_HEIGHT}px` }}
                    >
                      {item.label}
                    </Typography>
                  </Box>
                );
              }
              const { row } = item;
              const tint = tintColors(statusHue(row.status));
              const span = shifted(row);
              const left = (span.start - rangeStart) * pxPerDay;
              const width = Math.max(10, (span.end - span.start + 1) * pxPerDay);
              const canMove = movable(row);
              const isShifted = shift?.key === row.key && shift.delta !== 0;
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
                    {readOnly ? (
                      <Typography sx={{ fontSize: 14, fontWeight: isDream ? 700 : row.kind === 'goal' ? 600 : 400, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0 }}>
                        {row.title}
                      </Typography>
                    ) : (
                    <Link
                      component="button"
                      type="button"
                      underline="hover"
                      color="inherit"
                      onClick={() => onOpen(row)}
                      sx={{ fontSize: 14, fontWeight: isDream ? 700 : row.kind === 'goal' ? 600 : 400, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', textAlign: 'left', minWidth: 0 }}
                    >
                      {row.title}{row.archived ? ` ${t('(archived)')}` : ''}
                    </Link>
                    )}
                    {onQuickAdd && !readOnly && row.kind !== 'task' && !row.archived && (
                      <button
                        type="button"
                        className="vm-timeline__toggle vm-timeline__add"
                        aria-label={t(ADD_CHILD_LABEL[row.kind], { title: row.title })}
                        title={t(ADD_CHILD_LABEL[row.kind], { title: row.title })}
                        onClick={() => onQuickAdd(row)}
                      >
                        <Plus size={14} aria-hidden="true" />
                      </button>
                    )}
                    {row.blocked && <Ban size={14} aria-label={t('Blocked')} style={{ flex: 'none', color: statusHue('BLOCKED') }} />}
                    {row.overdueDays > 0 && (
                      <Typography variant="caption" sx={{ color: 'error.main', fontWeight: 600, flex: 'none' }}>{t('Overdue')}</Typography>
                    )}
                  </Box>
                  <Box sx={{ position: 'relative', width: trackWidth, flex: 'none' }}>
                    <Tooltip title={tooltip(row)} placement="top" arrow describeChild>
                      <BarElement
                        {...(readOnly ? { role: 'img', tabIndex: 0 } : { type: 'button' as const })}
                        className={`vm-timeline__bar${canMove ? ' vm-timeline__bar--movable' : ''}${isShifted ? ' vm-timeline__bar--shifted' : ''}`}
                        aria-label={isShifted ? `${ariaLabel(row)}, ${t('moving to {start} – {end}, Enter saves', { start: formatDay(span.start), end: formatDay(span.end) })}` : ariaLabel(row)}
                        aria-busy={savingKey === row.key || undefined}
                        onClick={() => onBarClick(row)}
                        onKeyDown={(event: KeyboardEvent<HTMLElement>) => onBarKey(event, row)}
                        onPointerDown={(event: ReactPointerEvent<HTMLElement>) => onBarPointerDown(event, row)}
                        onPointerMove={(event: ReactPointerEvent<HTMLElement>) => onBarPointerMove(event, row)}
                        onPointerUp={() => onBarPointerUp(row)}
                        onPointerCancel={() => setShift(null)}
                        style={{
                          opacity: savingKey === row.key ? 0.6 : undefined,
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
                        {canMove && row.kind === 'task' && <span className="vm-timeline__handle vm-timeline__handle--start" data-handle="start" aria-hidden="true" />}
                        {canMove && <span className="vm-timeline__handle vm-timeline__handle--end" data-handle="end" aria-hidden="true" />}
                      </BarElement>
                    </Tooltip>
                    {isDream && staggered(markersByDream.get(row.dreamId) ?? []).map(({ marker, lane }) => {
                      const info = MARKER_INFO[marker.type];
                      const Icon = info.icon;
                      const text = markerText(marker);
                      return (
                        <Tooltip key={marker.key} title={text} placement="top" arrow>
                          <button
                            type="button"
                            className="vm-timeline__marker"
                            aria-label={text}
                            style={{ left: (marker.day - rangeStart) * pxPerDay + pxPerDay / 2 - 8, top: lane === 0 ? 2 : 18, color: info.colour }}
                          >
                            <Icon size={16} aria-hidden="true" />
                          </button>
                        </Tooltip>
                      );
                    })}
                    {row.completedOn !== null && row.completedOn !== row.end && (
                      <Tooltip title={completedText(row)} placement="top" arrow>
                        <button
                          type="button"
                          className="vm-timeline__marker"
                          aria-label={completedText(row)}
                          style={{ left: (row.completedOn - rangeStart) * pxPerDay + pxPerDay / 2 - 8, color: 'var(--progress-done)' }}
                        >
                          <CircleCheck size={16} aria-hidden="true" />
                        </button>
                      </Tooltip>
                    )}
                  </Box>
                </Box>
              );
            })}
            {windowed && <Box sx={{ height: (items.length - lastIndex) * ROW_HEIGHT }} />}
          </Box>
        </Box>
      )}

      <Typography className="vm-timeline__toolbar" variant="caption" color="text.secondary">
        {readOnly
          ? t('Open the legend to see what the colours and marks mean.')
          : t('Select a bar to edit it. Open the legend to see what the colours and marks mean.')}
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
                {readOnly ? `${t(KIND_LABEL[item.kind])} · ${item.title}` : (
                  <Link component="button" type="button" onClick={() => onOpen(item)}>
                    {t(KIND_LABEL[item.kind])} {item.code} · {item.title}
                  </Link>
                )}
              </li>
            ))}
          </Box>
        </Box>
      )}
    </Box>
  );
}

function LegendItem({ swatch, children }: { swatch: ReactNode; children: ReactNode }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <Box sx={{ width: 28, display: 'flex', justifyContent: 'center', flex: 'none' }}>{swatch}</Box>
      <Typography variant="caption">{children}</Typography>
    </Box>
  );
}

type VisionMapTimelineProps = {
  dream: Dream;
  goals: Goal[];
  steps: VisionStep[];
  tasks: TaskItem[];
  markers?: TimelineMarker[];
  priorityFilter?: string;
  statusFilter?: string;
  onOpen: (kind: TimelineKind, id: number) => void;
  onReschedule?: (change: DateChange) => Promise<void>;
  onQuickAdd?: (target: TimelineTarget) => void;
  today?: number;
};

/** TL-FR-01: one dream's goals, steps and tasks (phase 1). */
export function VisionMapTimeline({
  dream, goals, steps, tasks, markers, priorityFilter, statusFilter, onOpen, onReschedule, onQuickAdd, today,
}: VisionMapTimelineProps) {
  const { t } = useI18n();
  const timeline = useMemo(() => buildDreamTimeline(dream, goals, steps, tasks, today), [dream, goals, steps, tasks, today]);
  const initial = useMemo(() => defaultExpanded(timeline.rows), [timeline.rows]);
  return (
    <TimelineView
      timeline={timeline}
      label={t('Timeline of {title}', { title: dream.title })}
      priorityFilter={priorityFilter}
      statusFilter={statusFilter}
      initialExpanded={initial}
      markers={markers}
      onOpen={(target) => onOpen(target.kind, target.id)}
      onReschedule={onReschedule}
      onQuickAdd={onQuickAdd}
      today={today}
    />
  );
}

type PlanTimelineProps = Omit<TimelineViewProps, 'timeline' | 'label' | 'initialExpanded'> & {
  areas: VisionArea[];
  dreams: Dream[];
  goals: Goal[];
  steps: VisionStep[];
  tasks: TaskItem[];
  /** Rows to open with when `expanded` is not controlled; every dream folded by default. */
  initialExpanded?: Set<string>;
};

/** TL-FR-02 and TL-FR-32: every dream on one timeline, grouped by vision area. */
export function PlanTimeline({ areas, dreams, goals, steps, tasks, initialExpanded, today, ...view }: PlanTimelineProps) {
  const { t } = useI18n();
  const timeline = useMemo(
    () => buildPlanTimeline(areas, dreams, goals, steps, tasks, today, t('Unassigned')),
    [areas, dreams, goals, steps, tasks, today, t],
  );
  return (
    <TimelineView
      {...view}
      timeline={timeline}
      label={t('Timeline of all dreams')}
      initialExpanded={initialExpanded ?? new Set()}
      today={today}
    />
  );
}
