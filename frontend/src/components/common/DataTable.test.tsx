import { useState } from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
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

  it('sorts empty values last in both directions, and yes/no values', async () => {
    type Task = { id: number; name: string; due: string | null; done: boolean };
    const tasks: Task[] = [
      { id: 1, name: 'A', due: null, done: true },
      { id: 2, name: 'B', due: '2027-02-01', done: false },
      { id: 3, name: 'C', due: '2027-01-01', done: true },
    ];
    render(
      <DataTable
        rows={tasks}
        emptyMessage="None"
        columns={[
          { key: 'name', label: 'Name', render: (row) => row.name },
          { key: 'due', label: 'Due', sortValue: (row) => row.due, render: (row) => row.due ?? '—' },
          { key: 'done', label: 'Done', sortValue: (row) => row.done, render: (row) => (row.done ? 'Yes' : 'No') },
        ]}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Due' }));
    expect(bodyNames()).toEqual(['C', 'B', 'A']);
    await userEvent.click(screen.getByRole('button', { name: 'Due' }));
    expect(bodyNames()).toEqual(['B', 'C', 'A']);
    await userEvent.click(screen.getByRole('button', { name: 'Done' }));
    expect(bodyNames()[0]).toBe('B');
  });

  it('clears a row, and select-all again clears the page', async () => {
    render(<SelectableTable />);
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Carol' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Carol' }));
    expect(screen.getByRole('checkbox', { name: 'Select Carol' })).not.toBeChecked();
    const selectAll = screen.getByRole('checkbox', { name: 'Select all rows on this page' });
    await userEvent.click(selectAll);
    expect(screen.getByRole('checkbox', { name: 'Select Bob' })).toBeChecked();
    await userEvent.click(selectAll);
    expect(screen.getByRole('checkbox', { name: 'Select Bob' })).not.toBeChecked();
  });

  it('changes how many rows a page shows, and goes back to the first page', async () => {
    render(<SelectableTable rowsPerPage={2} />);
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    await userEvent.click(screen.getByRole('combobox', { name: /Rows per page/ }));
    await userEvent.click(await screen.findByRole('option', { name: '5' }));
    expect(bodyNames()).toHaveLength(3);
  });

  it('leaves paging and sorting to the server when it pages', async () => {
    const serverPaging = { page: 0, rowsPerPage: 2, totalRows: 6, onPageChange: vi.fn(), onRowsPerPageChange: vi.fn(), onSortChange: vi.fn() };
    render(<DataTable rows={rows} columns={columns} emptyMessage="None" rowsPerPageOptions={[2, 5]} serverPaging={serverPaging} />);
    expect(bodyNames()).toEqual(['Carol', 'alice', 'Bob']);
    await userEvent.click(screen.getByRole('button', { name: 'Score' }));
    expect(serverPaging.onSortChange).toHaveBeenCalledWith('score', 'asc');
    expect(bodyNames()).toEqual(['Carol', 'alice', 'Bob']);
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    expect(serverPaging.onPageChange).toHaveBeenCalledWith(1);
    await userEvent.click(screen.getByRole('combobox', { name: /Rows per page/ }));
    await userEvent.click(await screen.findByRole('option', { name: '5' }));
    expect(serverPaging.onRowsPerPageChange).toHaveBeenCalledWith(5);
  });

  describe('drag to reorder', () => {
    const ordered = [{ id: 1, name: 'First', score: 1 }, { id: 2, name: 'Second', score: 2 }, { id: 3, name: 'Third', score: 3 }];
    const bodyRow = (name: string) => screen.getByText(name).closest('tr') as HTMLElement;

    it('explains how to turn reordering on when sorted by another column', () => {
      render(<DataTable rows={ordered} columns={columns} emptyMessage="None" defaultSortKey="name" reorder={{ columnKey: 'score', onReorder: vi.fn() }} />);
      expect(screen.getByText('Sort by "Score" ascending (click its header) to drag rows into a new order.')).toBeInTheDocument();
      expect(bodyRow('First')).not.toHaveAttribute('draggable');
    });

    it('still drags when the rows run over several pages', async () => {
      const onReorder = vi.fn();
      render(<DataTable rows={ordered} columns={columns} emptyMessage="None" defaultSortKey="score" defaultRowsPerPage={2} rowsPerPageOptions={[2, 5]} reorder={{ columnKey: 'score', onReorder }} />);
      expect(screen.getByText('Drag a row to move it to a new position.')).toBeInTheDocument();
      await userEvent.click(screen.getByRole('button', { name: /next page/i }));
      fireEvent.dragStart(bodyRow('Third'));
      fireEvent.dragOver(bodyRow('Third'));
      fireEvent.drop(bodyRow('Third'));
      expect(onReorder).not.toHaveBeenCalled();
      await userEvent.click(screen.getByRole('button', { name: /previous page/i }));
      fireEvent.dragStart(bodyRow('Second'));
      fireEvent.dragOver(bodyRow('First'));
      fireEvent.drop(bodyRow('First'));
      expect(onReorder).toHaveBeenCalledWith(2, 1);
    });

    it('moves a row onto another when sorted by position', () => {
      const onReorder = vi.fn();
      render(<DataTable rows={ordered} columns={columns} emptyMessage="None" defaultSortKey="score" reorder={{ columnKey: 'score', onReorder }} />);
      expect(screen.getByText('Drag a row to move it to a new position.')).toBeInTheDocument();
      fireEvent.dragStart(bodyRow('First'));
      expect(bodyRow('First')).toHaveClass('row--dragging');
      fireEvent.dragOver(bodyRow('Third'));
      expect(bodyRow('Third')).toHaveClass('row--drag-over');
      fireEvent.dragLeave(bodyRow('Third'));
      expect(bodyRow('Third')).not.toHaveClass('row--drag-over');
      fireEvent.dragOver(bodyRow('First'));
      fireEvent.drop(bodyRow('Third'));
      expect(onReorder).toHaveBeenCalledWith(1, 3);
      fireEvent.dragStart(bodyRow('Second'));
      fireEvent.dragEnd(bodyRow('Second'));
      expect(bodyRow('Second')).not.toHaveClass('row--dragging');
    });
  });

  describe('on a phone', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('shows each row as a card titled by its name, with the other columns as labelled details', () => {
      vi.stubGlobal('matchMedia', (query: string) => ({
        matches: true, media: query, onchange: null, addEventListener: vi.fn(), removeEventListener: vi.fn(),
        addListener: vi.fn(), removeListener: vi.fn(), dispatchEvent: vi.fn(),
      }));
      render(<DataTable rows={rows} columns={columns} emptyMessage="None" reorder={{ columnKey: 'score', onReorder: vi.fn() }} />);
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
      // Not sorted by its position column, so no drag handles yet — and it says why.
      expect(screen.getByText(/Sort by "Score" ascending/)).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Drag to reorder/ })).not.toBeInTheDocument();
      const card = screen.getByText('Carol').closest('article') as HTMLElement;
      expect(within(card).getByText('Score')).toBeInTheDocument();
      expect(within(card).getByText('30')).toBeInTheDocument();
      expect(within(card).getByText('Edit')).toBeInTheDocument();
    });

    // jsdom has no PointerEvent, so fired pointer events would lose clientY.
    class TestPointerEvent extends MouseEvent {
      pointerType: string;
      pointerId: number;
      constructor(type: string, init: PointerEventInit = {}) {
        super(type, init);
        this.pointerType = init.pointerType ?? 'mouse';
        this.pointerId = init.pointerId ?? 1;
      }
    }

    function stubPhone() {
      vi.stubGlobal('PointerEvent', TestPointerEvent);
      vi.stubGlobal('matchMedia', (query: string) => ({
        matches: true, media: query, onchange: null, addEventListener: vi.fn(), removeEventListener: vi.fn(),
        addListener: vi.fn(), removeListener: vi.fn(), dispatchEvent: vi.fn(),
      }));
    }

    function renderReorderable(onReorder = vi.fn(), canDrop?: (draggedId: number, targetId: number) => boolean) {
      render(
        <DataTable
          rows={rows}
          columns={columns}
          emptyMessage="None"
          defaultSortKey="score"
          reorder={{ columnKey: 'score', onReorder, canDrop }}
        />,
      );
      return onReorder;
    }

    // Cards in score order: alice (10), Bob (20), Carol (30), each 100px tall.
    function cardOf(name: string) {
      return screen.getByText(name).closest('article') as HTMLElement;
    }

    function layOutCards() {
      ['alice', 'Bob', 'Carol'].forEach((name, index) => {
        vi.spyOn(cardOf(name), 'getBoundingClientRect').mockReturnValue({
          top: index * 100, bottom: index * 100 + 90, left: 0, right: 300, width: 300, height: 90, x: 0, y: index * 100, toJSON: () => ({}),
        } as DOMRect);
      });
    }

    it('drags a card by its handle with a finger and drops it onto another card', () => {
      stubPhone();
      const onReorder = renderReorderable();
      layOutCards();
      expect(screen.getByText(/Hold ⠿ on a card/)).toBeInTheDocument();
      const handle = within(cardOf('alice')).getByRole('button', { name: /Drag to reorder/ });
      fireEvent.pointerDown(handle, { clientY: 40, pointerType: 'touch' });
      expect(cardOf('alice')).toHaveClass('table-card--dragging');
      fireEvent.pointerMove(handle, { clientY: 240, pointerType: 'touch' });
      expect(cardOf('Carol')).toHaveClass('table-card--drag-over');
      fireEvent.pointerUp(handle, { clientY: 240, pointerType: 'touch' });
      expect(onReorder).toHaveBeenCalledWith(2, 1);
      expect(cardOf('alice')).not.toHaveClass('table-card--dragging');
    });

    it('fades cards that cannot take the drop and ignores a drop on them', () => {
      stubPhone();
      const onReorder = renderReorderable(vi.fn(), (_dragged, target) => target !== 1);
      layOutCards();
      const handle = within(cardOf('alice')).getByRole('button', { name: /Drag to reorder/ });
      fireEvent.pointerDown(handle, { clientY: 40, pointerType: 'touch' });
      expect(cardOf('Carol')).toHaveClass('table-card--no-drop');
      fireEvent.pointerMove(handle, { clientY: 240, pointerType: 'touch' });
      fireEvent.pointerUp(handle, { clientY: 240, pointerType: 'touch' });
      expect(onReorder).not.toHaveBeenCalled();
    });

    it('moves a card one place with the arrow keys on its handle', async () => {
      stubPhone();
      const onReorder = renderReorderable();
      within(cardOf('Bob')).getByRole('button', { name: /Drag to reorder/ }).focus();
      await userEvent.keyboard('{ArrowUp}');
      expect(onReorder).toHaveBeenCalledWith(3, 2);
      await userEvent.keyboard('{ArrowDown}');
      expect(onReorder).toHaveBeenLastCalledWith(3, 1);
    });
  });
});
