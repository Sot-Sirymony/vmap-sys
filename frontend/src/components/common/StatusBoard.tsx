import { CSSProperties, DragEvent, ReactNode, useState } from 'react';
import useMediaQuery from '@mui/material/useMediaQuery';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import { EmptyState } from './EmptyState';
import { tr } from '../../i18n/translate';
import { usePointerReorder } from './usePointerReorder';

export type StatusBoardColumn<S extends string> = { value: S; label: string };

type StatusBoardProps<T extends { id: number; archived: boolean }, S extends string> = {
  items: T[];
  /** Columns to show, in order. */
  columns: StatusBoardColumn<S>[];
  /** Statuses offered by each card's move dropdown; defaults to `columns`. */
  moveOptions?: StatusBoardColumn<S>[];
  statusOf: (item: T) => S;
  /** Card body: title, meta, badges. The board supplies the card shell, drag handling, and the actions row. */
  renderCard: (item: T) => ReactNode;
  /** Extra class for a card, e.g. "list-card--overdue". Archived styling is applied automatically. */
  cardClassName?: (item: T) => string;
  /** Row actions (edit/archive menu), rendered next to the move dropdown. */
  cardActions?: (item: T) => ReactNode;
  /** Enables drag-and-drop between columns and the per-card status dropdown. */
  onMove?: (item: T, status: S) => void;
  /**
   * Enables ordering: drop a card onto another and it takes that card's
   * place (`items` must already be in that order). Dropped onto a card in
   * another column, the card also moves there. On a phone each card gets a
   * drag handle for this, since native drag needs a mouse.
   */
  onReorder?: (draggedId: number, targetId: number) => void;
  /** Plural noun for empty columns, e.g. "tasks". */
  entityLabel: string;
};

export function StatusBoard<T extends { id: number; archived: boolean }, S extends string>({
  items,
  columns,
  moveOptions = columns,
  statusOf,
  renderCard,
  cardClassName,
  cardActions,
  onMove,
  onReorder,
  entityLabel,
}: StatusBoardProps<T, S>) {
  const [draggedId, setDraggedId] = useState<number | null>(null);
  const [dragOverColumn, setDragOverColumn] = useState<S | null>(null);
  const [dragOverCard, setDragOverCard] = useState<number | null>(null);
  // FR-26.2: moves are announced for screen readers, whether they came from
  // drag-and-drop or the per-card dropdown.
  const [announcement, setAnnouncement] = useState('');
  // FR-28.2: one column at a time on small screens, chosen by a switcher —
  // moves keep working through each card's dropdown (drag needs no pointer
  // estate we don't have).
  const isNarrow = useMediaQuery('(max-width:599.95px)');
  const [chosenColumn, setActiveColumn] = useState<S>(columns[0]?.value);
  // A page filter can take the chosen column away (tapping "blocked" leaves
  // only that column), so fall back to the first one still shown.
  const activeColumn = columns.some((column) => column.value === chosenColumn) ? chosenColumn : columns[0]?.value;

  function announceMove(status: S) {
    const label = columns.find((column) => column.value === status)?.label ?? status;
    setAnnouncement(tr('Moved to {status}.', { status: label }));
  }

  function handleDragStart(event: DragEvent<HTMLElement>, id: number) {
    setDraggedId(id);
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', String(id));
  }

  function handleDragEnd() {
    setDraggedId(null);
    setDragOverColumn(null);
    setDragOverCard(null);
  }

  // Ordering on a wide screen: hovering a card marks it as the drop spot.
  function handleCardDragOver(event: DragEvent<HTMLElement>, target: T) {
    if (!onReorder || draggedId === null || draggedId === target.id || target.archived) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    event.dataTransfer.dropEffect = 'move';
    if (dragOverCard !== target.id) {
      setDragOverCard(target.id);
    }
    const column = statusOf(target);
    if (dragOverColumn !== column) {
      setDragOverColumn(column);
    }
  }

  // Dropped onto a card: take its place, and its column if that differs.
  function handleCardDrop(event: DragEvent<HTMLElement>, target: T) {
    if (!onReorder) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    const id = draggedId ?? Number(event.dataTransfer.getData('text/plain'));
    handleDragEnd();
    const item = items.find((candidate) => candidate.id === id);
    if (!item || item.id === target.id || target.archived) {
      return;
    }
    const targetStatus = statusOf(target);
    if (onMove && statusOf(item) !== targetStatus) {
      onMove(item, targetStatus);
      announceMove(targetStatus);
    }
    onReorder(item.id, target.id);
  }

  function handleColumnDragOver(event: DragEvent<HTMLElement>, column: S) {
    if (!onMove) {
      return;
    }
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
    if (dragOverColumn !== column) {
      setDragOverColumn(column);
    }
  }

  function handleColumnDrop(event: DragEvent<HTMLElement>, column: S) {
    event.preventDefault();
    const id = draggedId ?? Number(event.dataTransfer.getData('text/plain'));
    setDraggedId(null);
    setDragOverColumn(null);
    setDragOverCard(null);
    const item = items.find((candidate) => candidate.id === id);
    if (!item || statusOf(item) === column || !onMove) {
      return;
    }
    onMove(item, column);
    announceMove(column);
  }

  function cardClasses(item: T) {
    const classes = ['list-card'];
    if (draggedId === item.id) {
      classes.push('list-card--dragging');
    }
    if (item.archived) {
      classes.push('list-card--archived');
    } else {
      const extra = cardClassName?.(item);
      if (extra) {
        classes.push(extra);
      }
    }
    return classes.join(' ');
  }

  const shownColumns = isNarrow
    ? columns.filter((column) => column.value === activeColumn)
    : columns;

  // Phone: reorder within the one column on screen, by a handle.
  const shownItems = items.filter((item) => statusOf(item) === activeColumn);
  const pointerReorder = usePointerReorder({
    enabled: Boolean(onReorder) && isNarrow,
    orderedIds: shownItems.filter((item) => !item.archived).map((item) => item.id),
    onReorder: (draggedItemId, targetId) => onReorder?.(draggedItemId, targetId),
    canDrag: (id) => !items.find((item) => item.id === id)?.archived,
    canDrop: (_draggedItemId, targetId) => !items.find((item) => item.id === targetId)?.archived,
  });

  return (
    <div className={`kanban${isNarrow ? ' kanban--mobile' : ''}`} style={{ '--kanban-cols': shownColumns.length } as CSSProperties}>
      <span className="sr-only" role="status">{announcement}</span>
      {isNarrow && (
        // One tap per column: a sideways-scrolling row of status tabs, each
        // with its count, instead of a dropdown you have to open first.
        <div className="kanban-tabs" role="tablist" aria-label={tr('Show column')}>
          {columns.map((column) => {
            const active = column.value === activeColumn;
            return (
              <button
                type="button"
                role="tab"
                key={column.value}
                aria-selected={active}
                className={`kanban-tab${active ? ' kanban-tab--active' : ''}`}
                onClick={() => setActiveColumn(column.value)}
              >
                {column.label}
                <span className="kanban-tab-count">{items.filter((item) => statusOf(item) === column.value).length}</span>
              </button>
            );
          })}
        </div>
      )}
      {shownColumns.map((column) => {
        const columnItems = items.filter((item) => statusOf(item) === column.value);
        return (
          <section
            className={`kanban-column${dragOverColumn === column.value ? ' kanban-column--over' : ''}`}
            key={column.value}
            onDragOver={(event) => handleColumnDragOver(event, column.value)}
            onDragLeave={() => setDragOverColumn((current) => (current === column.value ? null : current))}
            onDrop={(event) => handleColumnDrop(event, column.value)}
          >
            <h2 className="text-sm font-semibold flex items-center gap-1.5">
              {column.label}
              <span className="text-muted-foreground font-normal">{columnItems.length}</span>
            </h2>
            {columnItems.length === 0 ? (
              <EmptyState>{onMove ? `Drop ${entityLabel} here` : `No ${entityLabel}`}</EmptyState>
            ) : (
              <div className={`stack-list${pointerReorder.dragging ? ' stack-list--dragging' : ''}`} ref={isNarrow ? pointerReorder.listRef : undefined}>
                {columnItems.map((item) => {
                  const handle = isNarrow ? pointerReorder.handle(item.id) : null;
                  return (
                  <article
                    className={`${cardClasses(item)}${dragOverCard === item.id ? ' list-card--drop-target' : ''}${handle ? ' list-card--with-handle' : ''}${pointerReorder.cardClassName(item.id, 'board-card')}`}
                    key={item.id}
                    data-row-id={item.id}
                    style={pointerReorder.cardStyle(item.id)}
                    draggable={!isNarrow && Boolean(onMove || onReorder) && !item.archived}
                    onDragStart={(event) => handleDragStart(event, item.id)}
                    onDragEnd={handleDragEnd}
                    onDragOver={(event) => handleCardDragOver(event, item)}
                    onDragLeave={() => setDragOverCard((current) => (current === item.id ? null : current))}
                    onDrop={(event) => handleCardDrop(event, item)}
                  >
                    {handle && <div className="list-card-handle">{handle}</div>}
                    {renderCard(item)}
                    <div className="row-actions">
                      {onMove && !item.archived && (
                        <FormControl size="small">
                          <Select
                            SelectDisplayProps={{ 'aria-label': 'Move to status' }}
                            value={statusOf(item)}
                            onChange={(event) => {
                              onMove(item, event.target.value as S);
                              announceMove(event.target.value as S);
                            }}
                          >
                            {moveOptions.map((option) => (
                              <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                      )}
                      {cardActions?.(item)}
                    </div>
                  </article>
                  );
                })}
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}
