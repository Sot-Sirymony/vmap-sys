import { useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Link } from 'react-router';
import Box from '@mui/material/Box';
import Tooltip from '@mui/material/Tooltip';
import { Flame } from 'lucide-react';
import { StatusChip } from '../common/StatusChip';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import type { ObstacleType, PartnerStatus } from '../../types/vision';
import { obstacleTypeLabels, partnerStatusLabels } from '../../utils/enumLabels';
import { formatNumber } from '../../utils/format';
import { localDateIso } from '../../utils/localDate';
import { RankedBars } from './RankedBars';
import { cardSurface, tabular } from './surface';

/* ------------------------------------------------------------------ */
/* Reviews: heatmap and streak                                         */
/* ------------------------------------------------------------------ */

export const HEATMAP_WEEKS = 12;

export type HeatmapDay = { date: string; count: number; isFuture: boolean };

/** Twelve Sunday-to-Saturday weeks ending with the current one, in the user's own calendar. */
export function buildReviewHeatmapWeeks(countsByDate: Record<string, number>, today: Date = new Date()): HeatmapDay[][] {
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const endOfWeek = new Date(start);
  endOfWeek.setDate(start.getDate() + (6 - start.getDay()));
  const first = new Date(endOfWeek);
  first.setDate(endOfWeek.getDate() - HEATMAP_WEEKS * 7 + 1);
  const todayIso = localDateIso(today);

  const weeks: HeatmapDay[][] = [];
  for (let week = 0; week < HEATMAP_WEEKS; week += 1) {
    const days: HeatmapDay[] = [];
    for (let day = 0; day < 7; day += 1) {
      const date = new Date(first);
      date.setDate(first.getDate() + week * 7 + day);
      const iso = localDateIso(date);
      days.push({ date: iso, count: countsByDate[iso] ?? 0, isFuture: iso > todayIso });
    }
    weeks.push(days);
  }
  return weeks;
}

/**
 * Weeks in a row with at least one review, counting back from this week. A week
 * still in progress with no review yet does not break the streak; it simply
 * does not add to it (BRD 25 §7.2 — the streak makes the habit visible).
 */
export function reviewStreak(weeks: HeatmapDay[][]): number {
  let streak = 0;
  for (let index = weeks.length - 1; index >= 0; index -= 1) {
    const reviewed = weeks[index].some((day) => day.count > 0);
    if (reviewed) {
      streak += 1;
    } else if (index !== weeks.length - 1) {
      break;
    }
  }
  return streak;
}

function heatLevel(count: number): number {
  return Math.min(4, Math.max(0, count));
}

function heatTooltip(day: HeatmapDay): string {
  if (day.isFuture) {
    return '';
  }
  const label = formatDate(new Date(`${day.date}T00:00:00`), { year: true });
  return tr(day.count === 1 ? '{date} — 1 review' : '{date} — {count} reviews', { date: label, count: day.count });
}

function ReviewsPanel({ reviewCadence, weeksWithDiligence }: { reviewCadence: Record<string, number>; weeksWithDiligence: number }) {
  const total = Object.values(reviewCadence).reduce((sum, count) => sum + count, 0);
  if (total === 0) {
    return (
      <Box sx={{ py: 2, color: 'var(--text-secondary)' }}>
        {tr('No daily or weekly reviews logged yet.')}{' '}
        <Box component={Link} to="/reviews" sx={{ color: 'var(--primary)' }}>{tr('Your first one takes five minutes.')}</Box>
      </Box>
    );
  }
  const weeks = buildReviewHeatmapWeeks(reviewCadence);
  const streak = reviewStreak(weeks);
  const dayLabels = [tr('S'), tr('M'), '', tr('W'), '', tr('F'), ''];
  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, mb: 2 }}>
        <Box component="span" sx={{ ...tabular, fontSize: '2rem', lineHeight: 1.25, fontWeight: 600, letterSpacing: '-0.5px' }}>{formatNumber(streak)}</Box>
        <Box component="span" sx={{ display: 'inline-flex', color: 'var(--text-secondary)' }}><Flame size={20} strokeWidth={1.75} aria-hidden="true" /></Box>
        <Box component="span" sx={{ color: 'var(--text-secondary)' }}>{tr('week review streak')}</Box>
      </Box>
      <Box sx={{ overflowX: 'auto' }}>
        <Box
          role="group"
          aria-label={tr('Daily and weekly reviews logged over the last 12 weeks')}
          sx={{ display: 'grid', gridTemplateColumns: `auto repeat(${HEATMAP_WEEKS}, 14px)`, gridAutoFlow: 'column', gridTemplateRows: 'repeat(7, 14px)', gap: '3px', alignItems: 'center' }}
        >
          {dayLabels.map((label, index) => (
            <Box key={`label-${index}`} aria-hidden="true" sx={{ fontSize: '0.6875rem', color: 'var(--text-tertiary)', pr: 0.75, lineHeight: 1 }}>{label}</Box>
          ))}
          {weeks.flat().map((day) => (
            <Tooltip key={day.date} title={heatTooltip(day)} arrow disableHoverListener={day.isFuture}>
              <Box
                role={day.isFuture ? undefined : 'img'}
                aria-label={day.isFuture ? undefined : heatTooltip(day)}
                aria-hidden={day.isFuture ? true : undefined}
                data-level={day.isFuture ? 'future' : heatLevel(day.count)}
                sx={{
                  width: 14,
                  height: 14,
                  borderRadius: '3px',
                  bgcolor: day.isFuture ? 'transparent' : `var(--heat-${heatLevel(day.count)})`,
                  border: day.isFuture ? '1px dashed var(--border)' : 'none',
                }}
              />
            </Tooltip>
          ))}
        </Box>
      </Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '3px', mt: 1.5, fontSize: '0.6875rem', color: 'var(--text-tertiary)' }}>
        <Box component="span" sx={{ mr: 0.5 }}>{tr('Less')}</Box>
        {[0, 1, 2, 3, 4].map((level) => (
          <Box key={level} sx={{ width: 12, height: 12, borderRadius: '3px', bgcolor: `var(--heat-${level})` }} />
        ))}
        <Box component="span" sx={{ ml: 0.5 }}>{tr('More')}</Box>
      </Box>
      <Box sx={{ mt: 1, fontSize: 'var(--font-caption)', color: 'var(--text-secondary)' }}>
        {tr('{count} of the last 12 weeks include a diligence checkup.', { count: weeksWithDiligence })}
      </Box>
    </Box>
  );
}

/* ------------------------------------------------------------------ */
/* Obstacles                                                           */
/* ------------------------------------------------------------------ */

function ObstaclesPanel({ obstaclesByType, blockedTasks, scope }: { obstaclesByType: Record<string, number>; blockedTasks: number; scope: string }) {
  const items = Object.entries(obstaclesByType)
    .filter(([, count]) => count > 0)
    .map(([type, count]) => ({
      id: type,
      label: obstacleTypeLabels[type as ObstacleType] ?? type,
      value: count,
      to: `/obstacles?type=${type}`,
    }));
  return (
    <Box>
      {blockedTasks > 0 && (
        <Box
          component={Link}
          to={`/tasks?status=BLOCKED${scope}`}
          sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', p: '10px 12px', mb: 2, borderRadius: 'var(--radius-md)', bgcolor: 'var(--surface-sunken)', color: 'inherit', textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
        >
          <StatusChip status="BLOCKED" />
          <span>{blockedTasks === 1 ? tr('1 blocked task') : tr('{n} blocked tasks', { n: formatNumber(blockedTasks) })}</span>
        </Box>
      )}
      {items.length === 0 ? (
        <Box sx={{ py: 2, color: 'var(--text-secondary)' }}>{tr('No open obstacles.')}</Box>
      ) : (
        <>
          <Box component="p" sx={{ m: 0, mb: 1.5, fontSize: 'var(--font-caption)', color: 'var(--text-secondary)' }}>{tr('Open obstacles by type, largest first')}</Box>
          <RankedBars items={items} color="var(--st-open)" label={tr('Top obstacles')} />
        </>
      )}
    </Box>
  );
}

/* ------------------------------------------------------------------ */
/* Partners                                                            */
/* ------------------------------------------------------------------ */

const PARTNER_STATUS_ORDER: PartnerStatus[] = ['TO_CONTACT', 'CONTACTED', 'WAITING', 'AGREED', 'ACTIVE', 'COMPLETED', 'DECLINED'];

/** BRD 25 §7.2 — four stages, left to right; Declined sits outside the pipeline. */
const PIPELINE_STAGES: Array<{ label: string; statuses: PartnerStatus[] }> = [
  { label: 'To contact', statuses: ['TO_CONTACT'] },
  { label: 'Contacted or waiting', statuses: ['CONTACTED', 'WAITING'] },
  { label: 'Agreed or active', statuses: ['AGREED', 'ACTIVE'] },
  { label: 'Completed', statuses: ['COMPLETED'] },
];

function PartnersPanel({ partnersByStatus }: { partnersByStatus: Record<string, number> }) {
  const count = (status: PartnerStatus) => partnersByStatus[status] ?? 0;
  const total = PARTNER_STATUS_ORDER.reduce((sum, status) => sum + count(status), 0);
  if (total === 0) {
    return <Box sx={{ py: 2, color: 'var(--text-secondary)' }}>{tr('No partners yet.')}</Box>;
  }
  return (
    <Box>
      <Box component="ol" aria-label={tr('Partner pipeline')} sx={{ listStyle: 'none', m: 0, mb: 2, p: 0, display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: '4px' }}>
        {PIPELINE_STAGES.map((stage, index) => {
          const stageCount = stage.statuses.reduce((sum, status) => sum + count(status), 0);
          const first = index === 0;
          const last = index === PIPELINE_STAGES.length - 1;
          let clipPath = 'polygon(0 0, calc(100% - 8px) 0, 100% 50%, calc(100% - 8px) 100%, 0 100%, 8px 50%)';
          let corners = '0';
          if (first) {
            clipPath = 'polygon(0 0, calc(100% - 8px) 0, 100% 50%, calc(100% - 8px) 100%, 0 100%)';
            corners = 'var(--radius-md) 0 0 var(--radius-md)';
          } else if (last) {
            clipPath = 'polygon(0 0, 100% 0, 100% 100%, 0 100%, 8px 50%)';
            corners = '0 var(--radius-md) var(--radius-md) 0';
          }
          return (
            <Box
              component="li"
              key={stage.label}
              sx={{ bgcolor: 'var(--surface-sunken)', p: '10px 10px 12px', pl: first ? '10px' : '18px', minWidth: 0, clipPath, borderRadius: corners }}
            >
              <Box sx={{ ...tabular, fontSize: '1.5rem', lineHeight: 1.25, fontWeight: 600 }}>{formatNumber(stageCount)}</Box>
              <Box sx={{ fontSize: '0.6875rem', lineHeight: 1.3, color: 'var(--text-secondary)', overflowWrap: 'anywhere' }}>{tr(stage.label)}</Box>
            </Box>
          );
        })}
      </Box>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
        {PARTNER_STATUS_ORDER.filter((status) => count(status) > 0).map((status) => (
          <Box
            key={status}
            component={Link}
            to={`/partners?status=${status}`}
            sx={{ display: 'inline-flex', alignItems: 'center', minHeight: 28, px: 1, borderRadius: 'var(--radius-pill)', color: 'var(--text-secondary)', fontSize: 'var(--font-caption)', textDecoration: 'none', '&:hover': { bgcolor: 'var(--surface-sunken)', textDecoration: 'underline' } }}
          >
            {partnerStatusLabels[status]} ({formatNumber(count(status))})
          </Box>
        ))}
      </Box>
    </Box>
  );
}

/* ------------------------------------------------------------------ */
/* The tabbed card                                                     */
/* ------------------------------------------------------------------ */

type InsightTabsProps = {
  obstaclesByType: Record<string, number>;
  blockedTasks: number;
  partnersByStatus: Record<string, number>;
  reviewCadence: Record<string, number>;
  weeksWithDiligence: number;
  /** `&visionAreaId=…` or ''. */
  scope: string;
};

type TabId = 'obstacles' | 'partners' | 'reviews';

/**
 * BRD 25 §3 — the Insights card: Obstacles, Partners and Reviews behind tabs,
 * so only one secondary chart shows at a time. Arrow keys move between tabs
 * (WAI-ARIA tabs pattern).
 */
export function InsightTabs(props: InsightTabsProps) {
  const baseId = useId();
  const [active, setActive] = useState<TabId>('obstacles');
  const tabRefs = useRef<Record<TabId, HTMLButtonElement | null>>({ obstacles: null, partners: null, reviews: null });
  const tabs: Array<{ id: TabId; label: string; panel: ReactNode }> = [
    { id: 'obstacles', label: tr('Obstacles'), panel: <ObstaclesPanel obstaclesByType={props.obstaclesByType} blockedTasks={props.blockedTasks} scope={props.scope} /> },
    { id: 'partners', label: tr('Partners'), panel: <PartnersPanel partnersByStatus={props.partnersByStatus} /> },
    { id: 'reviews', label: tr('Reviews'), panel: <ReviewsPanel reviewCadence={props.reviewCadence} weeksWithDiligence={props.weeksWithDiligence} /> },
  ];

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    const index = tabs.findIndex((tab) => tab.id === active);
    let next = index;
    if (event.key === 'ArrowRight') {
      next = (index + 1) % tabs.length;
    } else if (event.key === 'ArrowLeft') {
      next = (index - 1 + tabs.length) % tabs.length;
    } else {
      return;
    }
    event.preventDefault();
    setActive(tabs[next].id);
    tabRefs.current[tabs[next].id]?.focus();
  }

  return (
    <Box component="section" aria-labelledby={`${baseId}-title`} sx={cardSurface}>
      <Box component="h3" id={`${baseId}-title`} sx={{ m: 0, mb: 1.5, fontSize: '1rem', lineHeight: 1.5, fontWeight: 600 }}>{tr('Insights')}</Box>
      <Box role="tablist" aria-label={tr('Insights')} sx={{ display: 'flex', gap: 0.5, p: '3px', mb: 2, bgcolor: 'var(--surface-sunken)', borderRadius: 'var(--radius-md)', overflowX: 'auto' }}>
        {tabs.map((tab) => {
          const selected = tab.id === active;
          return (
            <Box
              key={tab.id}
              component="button"
              type="button"
              role="tab"
              id={`${baseId}-tab-${tab.id}`}
              aria-controls={`${baseId}-panel-${tab.id}`}
              aria-selected={selected}
              tabIndex={selected ? 0 : -1}
              ref={(node: HTMLButtonElement | null) => { tabRefs.current[tab.id] = node; }}
              onClick={() => setActive(tab.id)}
              onKeyDown={onKeyDown}
              sx={{
                flex: 1, height: 30, px: 1.25, border: 0, borderRadius: 'var(--radius-sm)', whiteSpace: 'nowrap', cursor: 'pointer',
                font: 'inherit', fontSize: '0.8125rem', fontWeight: 500,
                bgcolor: selected ? 'var(--surface)' : 'transparent',
                color: selected ? 'var(--text-primary)' : 'var(--text-secondary)',
                boxShadow: selected ? '0 0 0 1px var(--border)' : 'none',
                transition: 'background var(--motion-fast) var(--motion-ease), color var(--motion-fast) var(--motion-ease), transform var(--motion-fast) var(--motion-ease)',
                '&:hover': { color: 'var(--text-primary)' },
                '&:active': { transform: 'scale(var(--style-press-scale))' },
              }}
            >
              {tab.label}
            </Box>
          );
        })}
      </Box>
      {tabs.map((tab) => (
        <Box
          key={tab.id}
          role="tabpanel"
          id={`${baseId}-panel-${tab.id}`}
          aria-labelledby={`${baseId}-tab-${tab.id}`}
          hidden={tab.id !== active}
        >
          {tab.id === active && tab.panel}
        </Box>
      ))}
    </Box>
  );
}
