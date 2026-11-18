import type { ReactNode } from 'react';
import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import type { ReportFilter } from '../../api/reportApi';
import type { Dream, Priority, ReportHealth, VisionArea } from '../../types/vision';
import { priorityLabels } from '../../utils/enumLabels';
import { HEALTH_LABELS, HEALTH_ORDER } from '../../utils/managementReport';
import { ReportPeriodPicker, type PeriodChoice } from './ReportPeriodPicker';
import { tr } from '../../i18n/translate';

type ScopeKind = 'ALL' | 'AREA' | 'DREAM';

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

/** True when no filter below the scope and period is set (RM-FR-12). */
export function hasFilters(filter: ReportFilter) {
  return Boolean(filter.priorities?.length || filter.owner || filter.healths?.length || filter.includeCompleted === false
    || filter.areaId || filter.dreamId || (filter.period && filter.period !== 'THIS_MONTH'));
}

/**
 * V18 MR-FR-03, V19 RM-FR-06, RM-FR-10, RM-FR-12: what a report covers. The
 * scope, the period, then the optional filters: priority, owner, goal health
 * and whether completed goals count. Used by the live report and by drafts.
 */
export function ReportControls({ value, onChange, areas, dreams, owners, children }: Readonly<{
  value: ReportFilter;
  onChange: (next: ReportFilter) => void;
  areas: VisionArea[];
  dreams: Dream[];
  owners: string[];
  /** Shown at the end of the bar, e.g. the as-of line. */
  children?: ReactNode;
}>) {
  const scopeKind: ScopeKind = value.dreamId ? 'DREAM' : value.areaId ? 'AREA' : 'ALL';
  const period: PeriodChoice = { period: value.period ?? 'THIS_MONTH', from: value.from, to: value.to };

  function set(changes: Partial<ReportFilter>) {
    onChange({ ...value, ...changes });
  }

  function changeScopeKind(kind: ScopeKind) {
    if (kind === 'ALL') set({ areaId: undefined, dreamId: undefined });
    if (kind === 'AREA') set({ dreamId: undefined, areaId: areas[0]?.id });
    if (kind === 'DREAM') set({ areaId: undefined, dreamId: dreams[0]?.id });
  }

  return (
    <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: 2, flexWrap: 'wrap' }}>
      <label>
        {tr('Report on')}
        <FormControl size="small" sx={{ minWidth: 180, display: 'flex' }}>
          <Select SelectDisplayProps={{ 'aria-label': tr('Report on') }} value={scopeKind}
            onChange={(event) => changeScopeKind(event.target.value as ScopeKind)}>
            <MenuItem value="ALL">{tr('All dreams')}</MenuItem>
            <MenuItem value="AREA" disabled={areas.length === 0}>{tr('One vision area')}</MenuItem>
            <MenuItem value="DREAM" disabled={dreams.length === 0}>{tr('One dream')}</MenuItem>
          </Select>
        </FormControl>
      </label>
      {scopeKind === 'AREA' && (
        <label>
          {tr('Vision Area')}
          <FormControl size="small" sx={{ minWidth: 220, display: 'flex' }}>
            <Select SelectDisplayProps={{ 'aria-label': tr('Vision Area') }} displayEmpty
              value={areas.some((area) => area.id === value.areaId) ? String(value.areaId) : ''}
              onChange={(event) => set({ areaId: Number(event.target.value) || undefined })}>
              {areas.map((area) => <MenuItem value={String(area.id)} key={area.id}>{area.name}</MenuItem>)}
            </Select>
          </FormControl>
        </label>
      )}
      {scopeKind === 'DREAM' && (
        <label>
          {tr('Dream')}
          <FormControl size="small" sx={{ minWidth: 220, display: 'flex' }}>
            <Select SelectDisplayProps={{ 'aria-label': tr('Dream') }} displayEmpty
              value={dreams.some((dream) => dream.id === value.dreamId) ? String(value.dreamId) : ''}
              onChange={(event) => set({ dreamId: Number(event.target.value) || undefined })}>
              {dreams.map((dream) => <MenuItem value={String(dream.id)} key={dream.id}>{dream.title}</MenuItem>)}
            </Select>
          </FormControl>
        </label>
      )}
      <ReportPeriodPicker value={period} onChange={(choice) => set({ period: choice.period, from: choice.from, to: choice.to })} />
      <label>
        {tr('Priority')}
        <FormControl size="small" sx={{ minWidth: 150, display: 'flex' }}>
          <Select<Priority[]> multiple displayEmpty SelectDisplayProps={{ 'aria-label': tr('Priority') }} value={value.priorities ?? []}
            renderValue={(selected) => (selected.length ? selected.map((priority) => tr(priorityLabels[priority])).join(', ') : tr('Any'))}
            onChange={(event) => set({ priorities: (event.target.value as Priority[]).filter(Boolean) })}>
            {PRIORITIES.map((priority) => <MenuItem value={priority} key={priority}>{tr(priorityLabels[priority])}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        {tr('Owner')}
        <FormControl size="small" sx={{ minWidth: 150, display: 'flex' }}>
          <Select SelectDisplayProps={{ 'aria-label': tr('Owner') }} displayEmpty value={value.owner ?? ''}
            renderValue={(selected) => selected || tr('Any')}
            onChange={(event) => set({ owner: event.target.value || undefined })}>
            <MenuItem value="">{tr('Any')}</MenuItem>
            {owners.map((owner) => <MenuItem value={owner} key={owner}>{owner}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        {tr('Goal health')}
        <FormControl size="small" sx={{ minWidth: 150, display: 'flex' }}>
          <Select<ReportHealth[]> multiple displayEmpty SelectDisplayProps={{ 'aria-label': tr('Goal health') }} value={value.healths ?? []}
            renderValue={(selected) => (selected.length ? selected.map((health) => tr(HEALTH_LABELS[health])).join(', ') : tr('Any'))}
            onChange={(event) => set({ healths: (event.target.value as ReportHealth[]).filter(Boolean) })}>
            {HEALTH_ORDER.map((health) => <MenuItem value={health} key={health}>{tr(HEALTH_LABELS[health])}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label className="checkbox-field">
        <Checkbox checked={value.includeCompleted !== false}
          onChange={(event) => set({ includeCompleted: event.target.checked ? undefined : false })} />
        {tr('Include completed goals')}
      </label>
      {hasFilters(value) && (
        <Link component="button" type="button" variant="body2" sx={{ mb: 1 }} onClick={() => onChange({})}>
          {tr('Reset filters')}
        </Link>
      )}
      {children}
    </Box>
  );
}
