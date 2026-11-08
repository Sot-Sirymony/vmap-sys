import { useRef, useState, type CSSProperties, type KeyboardEvent, type PointerEvent, type ReactNode } from 'react';
import { GripVertical } from 'lucide-react';
import { tr } from '../../i18n/translate';

type PointerReorderOptions = {
  /** Off while reordering makes no sense (another sort order, a server-paged list). */
  enabled: boolean;
  /** Ids in the order they are shown, for the arrow-key moves. */
  orderedIds: number[];
  onReorder: (draggedId: number, targetId: number) => void;
  /** Whether a card may be dropped onto another. Defaults to any other card. */
  canDrop?: (draggedId: number, targetId: number) => boolean;
  /** Whether a card can be picked up at all. Defaults to yes. */
  canDrag?: (id: number) => boolean;
};

type DragState = { id: number; startY: number; startScroll: number; offset: number };

/**
 * Drag-to-reorder for a vertical list of cards, by a handle, with a finger or
 * a mouse. Native HTML5 drag needs a mouse, so phones use pointer events
 * instead: the held card follows the finger, the card under it is the drop
 * target, and the page scrolls near the screen edges. The handle also moves a
 * card one place with the up and down arrow keys.
 *
 * Put `listRef` on the element holding the cards and `data-row-id` on each card.
 */
export function usePointerReorder({ enabled, orderedIds, onReorder, canDrop, canDrag }: PointerReorderOptions) {
  const [drag, setDrag] = useState<DragState | null>(null);
  const [overId, setOverId] = useState<number | null>(null);
  const listRef = useRef<HTMLDivElement | null>(null);

  function canDropOn(sourceId: number, targetId: number): boolean {
    return sourceId !== targetId && (canDrop?.(sourceId, targetId) ?? true);
  }

  function canPickUp(id: number): boolean {
    return enabled && (canDrag?.(id) ?? true);
  }

  /** The card under the finger, other than the one being dragged. */
  function cardAt(clientY: number, sourceId: number): number | null {
    const cards = listRef.current?.querySelectorAll<HTMLElement>('[data-row-id]') ?? [];
    for (const card of cards) {
      const id = Number(card.dataset.rowId);
      if (id === sourceId) {
        continue;
      }
      const box = card.getBoundingClientRect();
      if (clientY >= box.top && clientY <= box.bottom) {
        return id;
      }
    }
    return null;
  }

  function end() {
    setDrag(null);
    setOverId(null);
  }

  function handlePointerDown(event: PointerEvent<HTMLButtonElement>, id: number) {
    if (!canPickUp(id) || (event.pointerType === 'mouse' && event.button !== 0)) {
      return;
    }
    event.preventDefault();
    event.currentTarget.setPointerCapture?.(event.pointerId);
    setDrag({ id, startY: event.clientY, startScroll: window.scrollY, offset: 0 });
  }

  function handlePointerMove(event: PointerEvent<HTMLButtonElement>) {
    if (!drag) {
      return;
    }
    // Near the top or bottom of the screen the page scrolls, so a card can
    // travel further than one screen. The bottom zone clears the phone nav.
    if (event.clientY < 90) {
      window.scrollBy(0, -12);
    } else if (event.clientY > window.innerHeight - 150) {
      window.scrollBy(0, 12);
    }
    const offset = event.clientY - drag.startY + (window.scrollY - drag.startScroll);
    setDrag({ ...drag, offset });
    const over = cardAt(event.clientY, drag.id);
    setOverId(over !== null && canDropOn(drag.id, over) ? over : null);
  }

  function handlePointerUp() {
    if (!drag) {
      return;
    }
    const sourceId = drag.id;
    const targetId = overId;
    end();
    if (targetId !== null && canDropOn(sourceId, targetId)) {
      onReorder(sourceId, targetId);
    }
  }

  // Arrow keys move the card one place, skipping cards it can't be dropped
  // next to.
  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, id: number) {
    if (!enabled || (event.key !== 'ArrowUp' && event.key !== 'ArrowDown')) {
      return;
    }
    event.preventDefault();
    const index = orderedIds.indexOf(id);
    const step = event.key === 'ArrowUp' ? -1 : 1;
    for (let next = index + step; next >= 0 && next < orderedIds.length; next += step) {
      if (canDropOn(id, orderedIds[next])) {
        onReorder(id, orderedIds[next]);
        return;
      }
    }
  }

  /** Classes for a card: being held, the drop target, or unable to take the drop. */
  function cardClassName(id: number, prefix: string): string {
    if (!enabled || !drag) {
      return '';
    }
    if (drag.id === id) {
      return ` ${prefix}--dragging`;
    }
    if (overId === id) {
      return ` ${prefix}--drag-over`;
    }
    if (!canDropOn(drag.id, id)) {
      return ` ${prefix}--no-drop`;
    }
    return '';
  }

  /** Moves the held card with the finger. */
  function cardStyle(id: number): CSSProperties | undefined {
    return drag?.id === id ? { transform: `translateY(${drag.offset}px)` } : undefined;
  }

  function handle(id: number): ReactNode {
    if (!canPickUp(id)) {
      return null;
    }
    return (
      <button
        type="button"
        className="reorder-handle"
        aria-label={tr('Drag to reorder, or press the up and down arrow keys')}
        onPointerDown={(event) => handlePointerDown(event, id)}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={end}
        // Chrome still starts a text selection from mousedown even when
        // pointerdown is cancelled, so cancel that too.
        onMouseDown={(event) => event.preventDefault()}
        // A handle inside a natively draggable card must not start that drag.
        draggable={false}
        onDragStart={(event) => event.preventDefault()}
        onKeyDown={(event) => handleKeyDown(event, id)}
      >
        <GripVertical size={18} aria-hidden="true" />
      </button>
    );
  }

  return { listRef, dragging: drag !== null, handle, cardClassName, cardStyle };
}
