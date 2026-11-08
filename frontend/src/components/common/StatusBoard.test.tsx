import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { StatusBoard, type StatusBoardColumn } from './StatusBoard';

type Status = 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED';
type Card = { id: number; title: string; status: Status; archived: boolean };

const columns: StatusBoardColumn<Status>[] = [
  { value: 'NOT_STARTED', label: 'Not Started' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'BLOCKED', label: 'Blocked' },
  { value: 'COMPLETED', label: 'Completed' },
];

const cards: Card[] = [
  { id: 1, title: 'Search PubMed', status: 'NOT_STARTED', archived: false },
  { id: 2, title: 'Save 15 articles', status: 'IN_PROGRESS', archived: false },
  { id: 3, title: 'Old idea', status: 'NOT_STARTED', archived: true },
];

function renderBoard(onMove?: (card: Card, status: Status) => void, items = cards) {
  render(
    <StatusBoard
      items={items}
      columns={columns}
      statusOf={(card) => card.status}
      renderCard={(card) => <h3>{card.title}</h3>}
      cardClassName={(card) => (card.status === 'BLOCKED' ? 'card--blocked' : '')}
      cardActions={(card) => <button type="button">Edit {card.title}</button>}
      onMove={onMove}
      entityLabel="tasks"
    />,
  );
}

const column = (label: string) => screen.getByRole('heading', { level: 2, name: new RegExp(`^${label}`) }).closest('section') as HTMLElement;

/** Drag-and-drop events need a dataTransfer object, which jsdom does not provide. */
function dataTransfer() {
  const data = new Map<string, string>();
  return { effectAllowed: '', dropEffect: '', setData: (key: string, value: string) => data.set(key, value), getData: (key: string) => data.get(key) ?? '' };
}

describe('StatusBoard (CLAUDE.md page 7: Kanban columns)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows each column with its count and cards, and an empty message', () => {
    renderBoard(vi.fn());
    expect(within(column('Not Started')).getByText('2')).toBeInTheDocument();
    expect(within(column('Not Started')).getByText('Search PubMed')).toBeInTheDocument();
    expect(within(column('In Progress')).getByText('Save 15 articles')).toBeInTheDocument();
    expect(within(column('Blocked')).getByText('Drop tasks here')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Edit Search PubMed' })).toBeInTheDocument();
  });

  it('says "No tasks" in an empty column when cards cannot be moved', () => {
    renderBoard(undefined);
    expect(within(column('Blocked')).getByText('No tasks')).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Move to status' })).not.toBeInTheDocument();
  });

  it('moves a card with its status menu and announces the move', async () => {
    const onMove = vi.fn();
    renderBoard(onMove);
    const [firstMenu] = screen.getAllByRole('combobox', { name: 'Move to status' });
    await userEvent.click(firstMenu);
    await userEvent.click(await screen.findByRole('option', { name: 'Blocked' }));
    expect(onMove).toHaveBeenCalledWith(cards[0], 'BLOCKED');
    expect(screen.getByRole('status')).toHaveTextContent('Moved to Blocked.');
  });

  it('moves a card by dragging it to another column', () => {
    const onMove = vi.fn();
    renderBoard(onMove);
    const card = screen.getByText('Search PubMed').closest('article') as HTMLElement;
    expect(card).toHaveAttribute('draggable', 'true');
    const transfer = dataTransfer();
    fireEvent.dragStart(card, { dataTransfer: transfer });
    expect(card).toHaveClass('list-card--dragging');
    fireEvent.dragOver(column('Completed'), { dataTransfer: transfer });
    expect(column('Completed')).toHaveClass('kanban-column--over');
    fireEvent.drop(column('Completed'), { dataTransfer: transfer });
    expect(onMove).toHaveBeenCalledWith(cards[0], 'COMPLETED');
    expect(screen.getByRole('status')).toHaveTextContent('Moved to Completed.');
    expect(column('Completed')).not.toHaveClass('kanban-column--over');
  });

  it('ignores a drop on the same column, and a drag that leaves or ends', () => {
    const onMove = vi.fn();
    renderBoard(onMove);
    const card = screen.getByText('Search PubMed').closest('article') as HTMLElement;
    const transfer = dataTransfer();
    fireEvent.dragStart(card, { dataTransfer: transfer });
    fireEvent.dragOver(column('Blocked'), { dataTransfer: transfer });
    fireEvent.dragLeave(column('Blocked'));
    expect(column('Blocked')).not.toHaveClass('kanban-column--over');
    fireEvent.drop(column('Not Started'), { dataTransfer: transfer });
    expect(onMove).not.toHaveBeenCalled();
    fireEvent.dragStart(card, { dataTransfer: transfer });
    fireEvent.dragEnd(card);
    expect(card).not.toHaveClass('list-card--dragging');
  });

  it('keeps archived cards in place: not draggable, no status menu, shown as archived', () => {
    renderBoard(vi.fn());
    const archived = screen.getByText('Old idea').closest('article') as HTMLElement;
    expect(archived).toHaveAttribute('draggable', 'false');
    expect(archived).toHaveClass('list-card--archived');
    expect(within(archived).queryByRole('combobox', { name: 'Move to status' })).not.toBeInTheDocument();
  });

  it('adds the page’s highlight class, for example for blocked cards', () => {
    renderBoard(vi.fn(), [{ id: 5, title: 'Waiting on mentor', status: 'BLOCKED', archived: false }]);
    expect(screen.getByText('Waiting on mentor').closest('article')).toHaveClass('card--blocked');
  });

  it('shows one column at a time on a phone, picked from status tabs with counts', async () => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.includes('max-width:599.95px'), media: query, onchange: null,
      addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn(), dispatchEvent: vi.fn(),
    }));
    renderBoard(vi.fn());
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(1);
    expect(screen.getByRole('tablist', { name: 'Show column' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'In Progress 1' }));
    expect(screen.getByRole('tab', { name: 'In Progress 1' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('heading', { level: 2, name: /In Progress/ })).toBeInTheDocument();
    expect(screen.getByText('Save 15 articles')).toBeInTheDocument();
  });

  describe('ordering cards', () => {
    const ordered: Card[] = [
      { id: 1, title: 'First', status: 'NOT_STARTED', archived: false },
      { id: 2, title: 'Second', status: 'NOT_STARTED', archived: false },
      { id: 3, title: 'Third', status: 'NOT_STARTED', archived: false },
      { id: 4, title: 'Doing', status: 'IN_PROGRESS', archived: false },
    ];

    function renderOrdered(onMove = vi.fn(), onReorder = vi.fn()) {
      render(
        <StatusBoard
          items={ordered}
          columns={columns}
          statusOf={(card) => card.status}
          renderCard={(card) => <h3>{card.title}</h3>}
          onMove={onMove}
          onReorder={onReorder}
          entityLabel="tasks"
        />,
      );
      return { onMove, onReorder };
    }

    const cardOf = (title: string) => screen.getByText(title).closest('article') as HTMLElement;

    it('drops a card onto another in its column to take its place', () => {
      const { onMove, onReorder } = renderOrdered();
      const transfer = dataTransfer();
      fireEvent.dragStart(cardOf('Third'), { dataTransfer: transfer });
      fireEvent.dragOver(cardOf('First'), { dataTransfer: transfer });
      expect(cardOf('First')).toHaveClass('list-card--drop-target');
      fireEvent.drop(cardOf('First'), { dataTransfer: transfer });
      expect(onReorder).toHaveBeenCalledWith(3, 1);
      expect(onMove).not.toHaveBeenCalled();
      expect(cardOf('First')).not.toHaveClass('list-card--drop-target');
    });

    it('drops a card onto a card in another column: moves it there and into that place', () => {
      const { onMove, onReorder } = renderOrdered();
      const transfer = dataTransfer();
      fireEvent.dragStart(cardOf('Doing'), { dataTransfer: transfer });
      fireEvent.dragOver(cardOf('Second'), { dataTransfer: transfer });
      fireEvent.drop(cardOf('Second'), { dataTransfer: transfer });
      expect(onMove).toHaveBeenCalledWith(ordered[3], 'NOT_STARTED');
      expect(onReorder).toHaveBeenCalledWith(4, 2);
    });

    it('still only changes status when dropped on a column, not a card', () => {
      const { onMove, onReorder } = renderOrdered();
      const transfer = dataTransfer();
      fireEvent.dragStart(cardOf('First'), { dataTransfer: transfer });
      fireEvent.dragOver(column('Blocked'), { dataTransfer: transfer });
      fireEvent.drop(column('Blocked'), { dataTransfer: transfer });
      expect(onMove).toHaveBeenCalledWith(ordered[0], 'BLOCKED');
      expect(onReorder).not.toHaveBeenCalled();
    });

    it('gives each card a drag handle on a phone, which moves it with the arrow keys', async () => {
      vi.stubGlobal('matchMedia', (query: string) => ({
        matches: query.includes('max-width:599.95px'), media: query, onchange: null,
        addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn(), dispatchEvent: vi.fn(),
      }));
      const { onReorder } = renderOrdered();
      expect(cardOf('First')).toHaveAttribute('draggable', 'false');
      within(cardOf('Second')).getByRole('button', { name: /Drag to reorder/ }).focus();
      await userEvent.keyboard('{ArrowUp}');
      expect(onReorder).toHaveBeenCalledWith(2, 1);
    });
  });
});
