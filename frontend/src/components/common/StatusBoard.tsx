import { CSSProperties, DragEvent, ReactNode, useState } from 'react';
import useMediaQuery from '@mui/material/useMediaQuery';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import { EmptyState } from './EmptyState';
import { tr } from '../../i18n/translate';

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
  entityLabel,
}: StatusBoardProps<T, S>) {
  const [draggedId, setDraggedId] = useState<number | null>(null);
  const [dragOverColumn, setDragOverColumn] = useState<S | null>(null);
  // FR-26.2: moves are announced for screen readers, whether they came from
  // drag-and-drop or the per-card dropdown.
  const [announcement, setAnnouncement] = useState('');
  // FR-28.2: one column at a time on small screens, chosen by a switcher —
  // moves keep working through each card's dropdown (drag needs no pointer
  // estate we don't have).
  const isNarrow = useMediaQuery('(max-width:599.95px)');
  const [activeColumn, setActiveColumn] = useState<S>(columns[0]?.value);

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

  return (
    <div className={`kanban${isNarrow ? ' kanban--mobile' : ''}`} style={{ '--kanban-cols': shownColumns.length } as CSSProperties}>
      <span className="sr-only" role="status">{announcement}</span>
      {isNarrow && (
        <FormControl size="small" fullWidth className="kanban-switcher">
          <Select
            SelectDisplayProps={{ 'aria-label': 'Show column' }}
            value={activeColumn}
            onChange={(event) => setActiveColumn(event.target.value as S)}
          >
            {columns.map((column) => (
              <MenuItem value={column.value} key={column.value}>
                {column.label} ({items.filter((item) => statusOf(item) === column.value).length})
              </MenuItem>
            ))}
          </Select>
        </FormControl>
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
              <div className="stack-list">
                {columnItems.map((item) => (
                  <article
                    className={cardClasses(item)}
                    key={item.id}
                    draggable={Boolean(onMove) && !item.archived}
                    onDragStart={(event) => handleDragStart(event, item.id)}
                    onDragEnd={handleDragEnd}
                  >
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
                ))}
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}
