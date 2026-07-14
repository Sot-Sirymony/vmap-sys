import { useState } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { DataTable, type DataTableColumn } from './DataTable';

type Row = { id: number; name: string; score: number };

const rows: Row[] = [
  { id: 1, name: 'Carol', score: 30 },
  { id: 2, name: 'alice', score: 10 },
  { id: 3, name: 'Bob', score: 20 },
];

const columns: DataTableColumn<Row>[] = [
  { key: 'name', label: 'Name', sortValue: (row) => row.name, render: (row) => row.name },
  { key: 'score', label: 'Score', sortValue: (row) => row.score, render: (row) => row.score },
  { key: 'actions', label: 'Action', render: () => 'Edit' },
];

function bodyNames() {
  const body = screen.getAllByRole('rowgroup')[1];
  return within(body).getAllByRole('row').map((row) => within(row).getAllByRole('cell')[0].textContent);
}

function SelectableTable({ rowsPerPage = 10 }: { rowsPerPage?: number }) {
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  return (
    <DataTable
      rows={rows}
      columns={columns}
      emptyMessage="No rows."
      defaultRowsPerPage={rowsPerPage}
      rowsPerPageOptions={[2, 5, 10]}
      selection={{ selectedIds, onChange: setSelectedIds, rowLabel: (row) => row.name }}
    />
  );
}

describe('DataTable', () => {
  it('shows the empty message when there are no rows', () => {
    render(<DataTable rows={[]} columns={columns} emptyMessage="No rows." />);
    expect(screen.getByText('No rows.')).toBeInTheDocument();
  });

  it('sorts by a column, case-insensitively, and reverses on a second click', async () => {
    const user = userEvent.setup();
    render(<DataTable rows={rows} columns={columns} emptyMessage="No rows." />);
    expect(bodyNames()).toEqual(['Carol', 'alice', 'Bob']);

    await user.click(screen.getByRole('button', { name: 'Name' }));
    expect(bodyNames()).toEqual(['alice', 'Bob', 'Carol']);

    await user.click(screen.getByRole('button', { name: 'Name' }));
    expect(bodyNames()).toEqual(['Carol', 'Bob', 'alice']);
  });

  it('sorts numeric columns by value, not by text', async () => {
    const user = userEvent.setup();
    render(<DataTable rows={rows} columns={columns} emptyMessage="No rows." />);
    await user.click(screen.getByRole('button', { name: 'Score' }));
    expect(bodyNames()).toEqual(['alice', 'Bob', 'Carol']);
  });

  it('does not offer sorting for columns without a sort value', () => {
    render(<DataTable rows={rows} columns={columns} emptyMessage="No rows." />);
    expect(screen.queryByRole('button', { name: 'Action' })).not.toBeInTheDocument();
  });

  it('selects a row and shows the selection count', async () => {
    const user = userEvent.setup();
    render(<SelectableTable />);
    await user.click(screen.getByRole('checkbox', { name: 'Select Bob' }));
    expect(screen.getByText('1 selected')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Clear selection' }));
    expect(screen.queryByText('1 selected')).not.toBeInTheDocument();
  });

  it('select-all only covers the current page', async () => {
    const user = userEvent.setup();
    render(<SelectableTable rowsPerPage={2} />);
    await user.click(screen.getByRole('checkbox', { name: 'Select all rows on this page' }));
    expect(screen.getByText('2 selected')).toBeInTheDocument();
  });

  it('pages rows and moves between first, next, and last page', async () => {
    const user = userEvent.setup();
    render(<DataTable rows={rows} columns={columns} emptyMessage="No rows." defaultRowsPerPage={2} rowsPerPageOptions={[2, 5, 10]} />);
    expect(bodyNames()).toEqual(['Carol', 'alice']);

    await user.click(screen.getByRole('button', { name: 'Next page' }));
    expect(bodyNames()).toEqual(['Bob']);

    await user.click(screen.getByRole('button', { name: 'First page' }));
    expect(bodyNames()).toEqual(['Carol', 'alice']);

    await user.click(screen.getByRole('button', { name: 'Last page' }));
    expect(bodyNames()).toEqual(['Bob']);
  });

  it('disables first/previous on page one and next/last on the final page', async () => {
    const user = userEvent.setup();
    render(<DataTable rows={rows} columns={columns} emptyMessage="No rows." defaultRowsPerPage={2} rowsPerPageOptions={[2, 5, 10]} />);
    expect(screen.getByRole('button', { name: 'First page' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Last page' }));
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Last page' })).toBeDisabled();
  });
});
