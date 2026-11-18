import { useEffect, useMemo, useState } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { CircleCheck, CircleX, Clock, TriangleAlert } from 'lucide-react';
import { Link } from 'react-router';
import type { ReportRow } from '../../types/vision';
import { priorityLabels } from '../../utils/enumLabels';
import { useI18n } from '../../i18n/I18nContext';
import { DataTable, type DataTableColumn } from '../common/DataTable';
import { ErrorMessage } from '../common/ErrorMessage';
import { Input } from '../common/Input';
import { Loading } from '../common/Loading';
import { tr } from '../../i18n/translate';

type Quick = 'OVERDUE' | 'BLOCKED' | 'DUE_SOON';
type Row = ReportRow & { id: number };

const QUICK_LABELS: Record<Quick, string> = { OVERDUE: 'Overdue', BLOCKED: 'Blocked', DUE_SOON: 'Due soon' };

/** RM-FR-14: a row's state as an icon and words, never colour alone. */
function RowState({ row }: Readonly<{ row: ReportRow }>) {
  const states = [
    row.completed && { icon: CircleCheck, label: tr('Completed') },
    row.blocked && { icon: CircleX, label: tr('Blocked') },
    row.overdue && { icon: TriangleAlert, label: tr('Overdue') },
    !row.overdue && row.dueSoon && { icon: Clock, label: tr('Due soon') },
  ].filter(Boolean) as { icon: typeof CircleCheck; label: string }[];
  return (
    <Stack spacing={0.25}>
      {states.map(({ icon: Icon, label }) => (
        <Box component="span" key={label} sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, whiteSpace: 'nowrap' }}>
          <Icon size={14} aria-hidden="true" />
          <span>{label}</span>
        </Box>
      ))}
    </Stack>
  );
}

/**
 * V19 RM-FR-13 to RM-FR-15: the Excel Report sheet's rows on screen, with
 * search, sorting, quick filters and 50 rows a page. The rows come from the
 * same server code as the Excel file, in the participant's language.
 */
export function ReportDetailsTab({ loadRows, showDream, reloadKey }: Readonly<{
  loadRows: (language: string) => Promise<ReportRow[]>;
  /** False for a one-dream report, where the column would repeat one name. */
  showDream: boolean;
  /** Changes when the report's scope, period or filters change. */
  reloadKey: string;
}>) {
  const { language } = useI18n();
  const [rows, setRows] = useState<ReportRow[] | null>(null);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [quick, setQuick] = useState<Quick | null>(null);

  useEffect(() => {
    let active = true;
    setError('');
    loadRows(language)
      .then((data) => active && setRows(data))
      .catch((loadError) => active && setError(loadError instanceof Error ? loadError.message : tr('Unable to load the report.')));
    return () => {
      active = false;
    };
    // loadRows is rebuilt each render; reloadKey says when its answer changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reloadKey, language]);

  const visible: Row[] = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return (rows ?? [])
      .map((row, index) => ({ ...row, id: index + 1 }))
      .filter((row) => !needle || [row.dream, row.goal, row.step, row.task, row.nextAction, ...row.issues]
        .some((text) => text.toLowerCase().includes(needle)))
      .filter((row) => quick === null || (quick === 'OVERDUE' && row.overdue) || (quick === 'BLOCKED' && row.blocked)
        || (quick === 'DUE_SOON' && row.dueSoon));
  }, [rows, search, quick]);

  if (error) return <ErrorMessage message={error} />;
  if (rows === null) return <Loading />;

  const columns: DataTableColumn<Row>[] = [
    ...(showDream ? [{ key: 'dream', label: tr('Dream'), sortValue: (row: Row) => row.dream, render: (row: Row) => row.dream }] : []),
    { key: 'goal', label: tr('Goals'), sortValue: (row) => row.goal, render: (row) => row.goal },
    { key: 'step', label: tr('Steps'), sortValue: (row) => row.step, render: (row) => row.step || '—' },
    {
      key: 'task',
      label: tr('Tasks'),
      sortValue: (row) => row.task,
      render: (row) => (row.task && row.stepId ? <Link to={`/tasks?stepId=${row.stepId}`}>{row.task}</Link> : row.task || '—'),
    },
    { key: 'state', label: tr('State'), render: (row) => <RowState row={row} /> },
    {
      key: 'issues',
      label: tr('Blockers / Management Issues'),
      render: (row) => (row.issues.length ? row.issues.map((issue) => <div key={issue}>{issue}</div>) : '—'),
    },
    { key: 'progress', label: tr('Progress (%)'), align: 'right', sortValue: (row) => Number(row.progress),
      render: (row) => `${Math.round(Number(row.progress))}%` },
    { key: 'priority', label: tr('Priority'), sortValue: (row) => ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].indexOf(row.priority ?? ''),
      render: (row) => (row.priority ? tr(priorityLabels[row.priority]) : '—') },
    { key: 'due', label: tr('Due Date'), sortValue: (row) => row.due ?? '9999', render: (row) => row.due ?? '—' },
    { key: 'next', label: tr('Next Action'), render: (row) => row.nextAction || '—' },
  ];

  return (
    <Card component="section" aria-label={tr('Details')}>
      <CardContent sx={{ display: 'grid', gap: 1.5 }}>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
          <Box sx={{ minWidth: 240, flex: '1 1 240px', maxWidth: 360 }}>
            <Input type="search" aria-label={tr('Search the rows')} placeholder={tr('Search the rows')} value={search}
              onChange={(event) => setSearch(event.target.value)} />
          </Box>
          {(Object.keys(QUICK_LABELS) as Quick[]).map((key) => (
            <Chip key={key} label={tr(QUICK_LABELS[key])} clickable aria-pressed={quick === key}
              color={quick === key ? 'primary' : 'default'} variant={quick === key ? 'filled' : 'outlined'}
              onClick={() => setQuick((current) => (current === key ? null : key))} />
          ))}
          <Typography variant="body2" color="text.secondary" role="status" sx={{ ml: 'auto' }}>
            {tr('{shown} of {total} row(s)', { shown: visible.length, total: rows.length })}
          </Typography>
        </Box>
        <DataTable rows={visible} columns={columns} emptyMessage={tr('No rows match.')}
          defaultRowsPerPage={50} rowsPerPageOptions={[50, 100]} pageResetKey={`${search}|${quick}|${reloadKey}`} />
      </CardContent>
    </Card>
  );
}
