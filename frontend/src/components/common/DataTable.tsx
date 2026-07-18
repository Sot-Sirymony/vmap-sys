import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useStoredState } from '../../hooks/useStoredState';
import Checkbox from '@mui/material/Checkbox';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TableSortLabel from '@mui/material/TableSortLabel';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import type { SxProps, Theme } from '@mui/material/styles';
import { Button } from './Button';
import { EmptyState } from './EmptyState';
import { TablePaginationActions } from './TablePaginationActions';

export type SortDirection = 'asc' | 'desc';

export type SortValue = string | number | boolean | null | undefined;

export type DataTableColumn<T> = {
  /** Stable key, also used as the sort key. */
  key: string;
  label: ReactNode;
  render: (row: T) => ReactNode;
  /** Providing this makes the column sortable. */
  sortValue?: (row: T) => SortValue;
  align?: 'left' | 'center' | 'right';
  className?: string;
  sx?: SxProps<Theme>;
};

export type DataTableSelection<T> = {
  selectedIds: Set<number>;
  onChange: (selectedIds: Set<number>) => void;
  /** Accessible label for a row's checkbox. */
  rowLabel: (row: T) => string;
  /** Extra bulk controls rendered in the selection toolbar. */
  actions?: ReactNode;
};

/**
 * Server-driven paging and sorting. Rows are then rendered as given: the table
 * does not re-sort or re-slice them, because the server already did.
 */
export type DataTableServerPaging = {
  page: number;
  rowsPerPage: number;
  totalRows: number;
  onPageChange: (page: number) => void;
  onRowsPerPageChange: (rowsPerPage: number) => void;
  onSortChange: (key: string, direction: SortDirection) => void;
};

type DataTableProps<T> = {
  rows: T[];
  columns: DataTableColumn<T>[];
  /** Shown instead of the table when there are no rows. */
  emptyMessage: string;
  rowClassName?: (row: T) => string;
  defaultSortKey?: string;
  defaultSortDirection?: SortDirection;
  selection?: DataTableSelection<T>;
  /** Omit to page in the browser over `rows`. */
  serverPaging?: DataTableServerPaging;
  rowsPerPageOptions?: number[];
  defaultRowsPerPage?: number;
  /**
   * Changing this jumps back to the first page. Pass whatever narrows the rows
   * (a search term, the active filters) so results are not hidden behind a page
   * number left over from the previous, longer list.
   */
  pageResetKey?: string | number;
  /** Persist the rows-per-page choice under this key (FR-23.3). */
  storageKey?: string;
};

function compare(left: SortValue, right: SortValue) {
  const leftEmpty = left === null || left === undefined || left === '';
  const rightEmpty = right === null || right === undefined || right === '';
  if (leftEmpty || rightEmpty) {
    // Empty values always sort last, in both directions.
    return leftEmpty && rightEmpty ? 0 : leftEmpty ? 1 : -1;
  }
  if (typeof left === 'number' && typeof right === 'number') {
    return left - right;
  }
  if (typeof left === 'boolean' && typeof right === 'boolean') {
    return Number(left) - Number(right);
  }
  return String(left).localeCompare(String(right), undefined, { sensitivity: 'base', numeric: true });
}

/**
 * The shared table for every list page: sortable headers, optional checkbox
 * selection with a bulk-action toolbar, and pagination with first/last page
 * actions. Columns describe how to read and render each cell, so pages stay
 * declarative instead of hand-rolling markup per entity.
 */
export function DataTable<T extends { id: number }>({
  rows,
  columns,
  emptyMessage,
  rowClassName,
  defaultSortKey,
  defaultSortDirection = 'asc',
  selection,
  serverPaging,
  rowsPerPageOptions = [5, 10, 25, 50],
  defaultRowsPerPage = 10,
  pageResetKey,
  storageKey,
}: DataTableProps<T>) {
  const [sortKey, setSortKey] = useState<string | undefined>(defaultSortKey);
  const [sortDirection, setSortDirection] = useState<SortDirection>(defaultSortDirection);
  const [page, setPage] = useState(0);
  // FR-23.3: with a storageKey, the rows-per-page choice survives reloads.
  const [rowsPerPage, setRowsPerPage] = useStoredState(storageKey ? `vms-rows-${storageKey}` : null, defaultRowsPerPage);

  useEffect(() => {
    setPage(0);
  }, [pageResetKey]);

  const sortedRows = useMemo(() => {
    const column = columns.find((item) => item.key === sortKey);
    if (serverPaging || !column?.sortValue) {
      return rows;
    }
    const sortValue = column.sortValue;
    const direction = sortDirection === 'asc' ? 1 : -1;
    return [...rows].sort((left, right) => compare(sortValue(left), sortValue(right)) * direction);
  }, [rows, columns, sortKey, sortDirection, serverPaging]);

  const currentPage = serverPaging ? serverPaging.page : page;
  const currentRowsPerPage = serverPaging ? serverPaging.rowsPerPage : rowsPerPage;
  const totalRows = serverPaging ? serverPaging.totalRows : sortedRows.length;
  // Rows can shrink under a stale page (a filter change, an archive); fall back
  // to the last page that still holds rows instead of rendering an empty table.
  const safePage = serverPaging
    ? currentPage
    : Math.min(currentPage, Math.max(0, Math.ceil(totalRows / currentRowsPerPage) - 1));
  const visibleRows = serverPaging
    ? sortedRows
    : sortedRows.slice(safePage * currentRowsPerPage, safePage * currentRowsPerPage + currentRowsPerPage);

  function handleSort(key: string) {
    const nextDirection: SortDirection = sortKey === key && sortDirection === 'asc' ? 'desc' : 'asc';
    setSortKey(key);
    setSortDirection(nextDirection);
    setPage(0);
    serverPaging?.onSortChange(key, nextDirection);
  }

  function handlePageChange(nextPage: number) {
    if (serverPaging) {
      serverPaging.onPageChange(nextPage);
    } else {
      setPage(nextPage);
    }
  }

  function handleRowsPerPageChange(nextRowsPerPage: number) {
    if (serverPaging) {
      serverPaging.onRowsPerPageChange(nextRowsPerPage);
    } else {
      setRowsPerPage(nextRowsPerPage);
      setPage(0);
    }
  }

  const selectedCount = selection ? visibleRows.filter((row) => selection.selectedIds.has(row.id)).length : 0;
  const allVisibleSelected = selectedCount > 0 && selectedCount === visibleRows.length;

  function toggleRow(id: number) {
    if (!selection) {
      return;
    }
    const next = new Set(selection.selectedIds);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    selection.onChange(next);
  }

  function toggleAllVisible() {
    if (!selection) {
      return;
    }
    const next = new Set(selection.selectedIds);
    for (const row of visibleRows) {
      if (allVisibleSelected) {
        next.delete(row.id);
      } else {
        next.add(row.id);
      }
    }
    selection.onChange(next);
  }

  if (rows.length === 0) {
    return <EmptyState>{emptyMessage}</EmptyState>;
  }

  const totalSelected = selection?.selectedIds.size ?? 0;

  return (
    <>
      {selection && totalSelected > 0 && (
        <Toolbar
          className="bulk-actions-bar"
          sx={{ gap: 1.5, flexWrap: 'wrap', bgcolor: 'action.selected', borderRadius: 1, mb: 1.5, minHeight: 56 }}
        >
          <Typography className="bulk-count" variant="subtitle2" component="span">
            {totalSelected} selected
          </Typography>
          {selection.actions}
          <Button type="button" variant="secondary" onClick={() => selection.onChange(new Set())}>
            Clear selection
          </Button>
        </Toolbar>
      )}
      <TableContainer>
        <Table className="data-table">
          <TableHead>
            <TableRow>
              {selection && (
                <TableCell padding="checkbox">
                  <Checkbox
                    checked={allVisibleSelected}
                    indeterminate={selectedCount > 0 && !allVisibleSelected}
                    onChange={toggleAllVisible}
                    slotProps={{ input: { 'aria-label': 'Select all rows on this page' } }}
                  />
                </TableCell>
              )}
              {columns.map((column) => (
                <TableCell key={column.key} align={column.align} sortDirection={sortKey === column.key ? sortDirection : false}>
                  {column.sortValue ? (
                    <TableSortLabel
                      active={sortKey === column.key}
                      direction={sortKey === column.key ? sortDirection : 'asc'}
                      onClick={() => handleSort(column.key)}
                    >
                      {column.label}
                    </TableSortLabel>
                  ) : (
                    column.label
                  )}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {visibleRows.map((row) => (
              <TableRow
                key={row.id}
                className={rowClassName?.(row) ?? ''}
                selected={selection?.selectedIds.has(row.id) ?? false}
              >
                {selection && (
                  <TableCell padding="checkbox">
                    <Checkbox
                      checked={selection.selectedIds.has(row.id)}
                      onChange={() => toggleRow(row.id)}
                      slotProps={{ input: { 'aria-label': `Select ${selection.rowLabel(row)}` } }}
                    />
                  </TableCell>
                )}
                {columns.map((column) => (
                  <TableCell key={column.key} align={column.align} className={column.className} sx={column.sx}>
                    {column.render(row)}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <TablePagination
        component="div"
        count={totalRows}
        page={safePage}
        rowsPerPage={currentRowsPerPage}
        rowsPerPageOptions={rowsPerPageOptions}
        onPageChange={(_event, nextPage) => handlePageChange(nextPage)}
        onRowsPerPageChange={(event) => handleRowsPerPageChange(Number(event.target.value))}
        ActionsComponent={TablePaginationActions}
      />
    </>
  );
}
