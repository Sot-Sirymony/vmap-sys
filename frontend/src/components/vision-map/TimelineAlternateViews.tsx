import { useMemo, useState } from 'react';
import { Ban } from 'lucide-react';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { Button } from '../common/Button';
import { useI18n } from '../../i18n/I18nContext';
import { fromDay, type TimelineKind, type TimelineMarker, type TimelineRow } from '../../utils/timeline';

/** What both views need from the timeline that hosts them. */
type SharedProps = {
  rows: TimelineRow[];
  kindLabel: (kind: TimelineKind) => string;
  statusLabel: (row: TimelineRow) => string;
  formatDay: (day: number) => string;
  onOpen: (row: TimelineRow) => void;
};

/**
 * TL-FR-47: the same rows as a plain table, for screen readers and for
 * copying. Every row is listed whether or not its branch is folded.
 */
export function TimelineTable({ rows, kindLabel, statusLabel, formatDay, onOpen, caption }: SharedProps & { caption: string }) {
  const { t } = useI18n();
  const workedOut = (why: unknown) => (why ? ` (${t('worked out')})` : '');
  return (
    <TableContainer sx={{ border: 1, borderColor: 'divider', borderRadius: 1, maxHeight: '70vh' }}>
      <Table size="small" stickyHeader>
        <caption style={{ captionSide: 'top', textAlign: 'left', padding: '8px 12px' }}>{caption}</caption>
        <TableHead>
          <TableRow>
            <TableCell>{t('Level')}</TableCell>
            <TableCell>{t('Code')}</TableCell>
            <TableCell>{t('Title')}</TableCell>
            <TableCell>{t('Start')}</TableCell>
            <TableCell>{t('End')}</TableCell>
            <TableCell>{t('Status')}</TableCell>
            <TableCell align="right">{t('Progress')}</TableCell>
            <TableCell>{t('Owner')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.key} hover>
              <TableCell>{kindLabel(row.kind)}</TableCell>
              <TableCell sx={{ whiteSpace: 'nowrap' }}>{row.code}</TableCell>
              <TableCell sx={{ pl: 2 + row.depth * 2 }}>
                <Link component="button" type="button" onClick={() => onOpen(row)} sx={{ textAlign: 'left' }}>{row.title}</Link>
                {row.archived ? ` ${t('(archived)')}` : ''}
              </TableCell>
              <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDay(row.start)}{workedOut(row.startWorkedOut)}</TableCell>
              <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDay(row.end)}{workedOut(row.endWorkedOut)}</TableCell>
              <TableCell>
                {statusLabel(row)}
                {row.overdueDays > 0 ? ` · ${t('Overdue by {days} day(s)', { days: row.overdueDays })}` : ''}
                {row.blockerReason ? ` · ${t('Blocked because')}: ${row.blockerReason}` : ''}
              </TableCell>
              <TableCell align="right">{row.progress}%</TableCell>
              <TableCell>{row.owner ?? ''}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

type AgendaEntry = { key: string; day: number; what: string; row?: TimelineRow; marker?: TimelineMarker };

function monthKey(day: number) {
  return fromDay(day).slice(0, 7);
}

/**
 * TL-FR-44 to TL-FR-46: on a phone, the timeline as a list by month. Each
 * month lists what starts, ends or is due in it, plus the marks that fall in
 * it. Past months are folded behind one button so the list opens on now.
 */
export function TimelineAgenda({
  rows, markers, markerText, today, kindLabel, statusLabel, formatDay, onOpen, locale,
}: SharedProps & { markers: TimelineMarker[]; markerText: (marker: TimelineMarker) => string; today: number; locale: string }) {
  const { t } = useI18n();
  const [showPast, setShowPast] = useState(false);

  const months = useMemo(() => {
    const entries: AgendaEntry[] = [];
    for (const row of rows) {
      const hasOwnStart = !row.startWorkedOut && row.start !== row.end;
      if (hasOwnStart) entries.push({ key: `${row.key}-start`, day: row.start, what: t('Starts'), row });
      if (!row.endWorkedOut) entries.push({ key: `${row.key}-end`, day: row.end, what: row.kind === 'task' ? t('Due') : t('Ends'), row });
    }
    for (const marker of markers) {
      entries.push({ key: marker.key, day: marker.day, what: '', marker });
    }
    entries.sort((a, b) => a.day - b.day || a.key.localeCompare(b.key));
    const grouped = new Map<string, AgendaEntry[]>();
    for (const entry of entries) {
      const key = monthKey(entry.day);
      grouped.set(key, [...(grouped.get(key) ?? []), entry]);
    }
    return [...grouped.entries()];
  }, [rows, markers, t]);

  const current = monthKey(today);
  const past = months.filter(([key]) => key < current);
  const shown = showPast ? months : months.filter(([key]) => key >= current);
  const monthTitle = (key: string) => {
    const [year, month] = key.split('-').map(Number);
    return new Date(year, month - 1, 1).toLocaleDateString(locale, { month: 'long', year: 'numeric' });
  };

  if (months.length === 0) {
    return <Typography variant="body2" color="text.secondary">{t('Nothing with a date yet.')}</Typography>;
  }

  return (
    <Box sx={{ display: 'grid', gap: 2 }}>
      {past.length > 0 && (
        <Button variant="secondary" onClick={() => setShowPast((value) => !value)}>
          {showPast ? t('Hide earlier months') : t('Show earlier months ({count})', { count: past.length })}
        </Button>
      )}
      {shown.length === 0 && <Typography variant="body2" color="text.secondary">{t('Nothing planned from this month on.')}</Typography>}
      {shown.map(([key, entries]) => (
        <Box component="section" key={key} aria-label={monthTitle(key)} sx={{ border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
          <Typography component="h3" variant="subtitle2" sx={{ px: 1.5, py: 1, bgcolor: 'action.hover' }}>
            {monthTitle(key)}{key === current ? ` · ${t('This month')}` : ''}
          </Typography>
          <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
            {entries.map((entry) => (
              <Box component="li" key={entry.key} sx={{ display: 'flex', gap: 1.5, px: 1.5, py: 1, borderTop: 1, borderColor: 'divider', minWidth: 0 }}>
                <Typography variant="caption" sx={{ width: 64, flex: 'none', fontWeight: 600, color: entry.day < today ? 'text.secondary' : 'text.primary' }}>
                  {formatDay(entry.day).split(' ').slice(0, 2).join(' ')}
                </Typography>
                {entry.row ? (
                  <Box sx={{ minWidth: 0, display: 'grid', gap: 0.25 }}>
                    <Link component="button" type="button" onClick={() => onOpen(entry.row!)} sx={{ textAlign: 'left', fontSize: 14, overflowWrap: 'anywhere' }}>
                      {entry.what}: {kindLabel(entry.row.kind)} {entry.row.code} · {entry.row.title}
                    </Link>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
                      <span>{statusLabel(entry.row)} · {entry.row.progress}%</span>
                      {entry.row.overdueDays > 0 && entry.what !== t('Starts') && (
                        <Box component="span" sx={{ color: 'error.main', fontWeight: 600 }}>{t('Overdue by {days} day(s)', { days: entry.row.overdueDays })}</Box>
                      )}
                      {entry.row.blocked && (
                        <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
                          <Ban size={12} aria-hidden="true" />{t('Blocked')}
                        </Box>
                      )}
                    </Typography>
                  </Box>
                ) : (
                  <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{entry.marker ? markerText(entry.marker) : ''}</Typography>
                )}
              </Box>
            ))}
          </Box>
        </Box>
      ))}
    </Box>
  );
}
