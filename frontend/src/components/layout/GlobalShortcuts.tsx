import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { CommandPalette } from '../common/CommandPalette';
import { ShortcutHelp } from '../common/ShortcutHelp';
import { useCommandPalette } from './command-palette-context';

const GO_TARGETS: Record<string, string> = {
  d: '/',
  m: '/vision-map',
  g: '/goals',
  s: '/steps',
  t: '/tasks',
  a: '/vision-areas',
  p: '/partners',
  r: '/reviews',
};

// 'n' opens the current page's create flow via its ?create param.
const CREATE_TARGETS: Record<string, string> = {
  '/vision-areas': '/vision-areas?create=area',
  '/dreams': '/dreams?create=dream',
  '/goals': '/goals?create=goal',
  '/steps': '/steps?create=step',
  '/tasks': '/tasks?create=task',
  '/partners': '/partners?create=partner',
  '/obstacles': '/obstacles?create=obstacle',
};

function isEditable(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  return Boolean(target.closest('input, textarea, select, [contenteditable="true"], [role="combobox"]'));
}

/**
 * FR-29.1/29.2 global keys: Ctrl/⌘+K palette anywhere; single-letter keys
 * (g-sequences, n, /, ?) only when focus is not in a field, no dialog is
 * open, and nothing else claimed the event (the Vision Map's own keys call
 * preventDefault, so the tree always wins). Everything here is listed in
 * the ? help dialog.
 */
export function GlobalShortcuts() {
  const navigate = useNavigate();
  const location = useLocation();
  // FR-48.3: the palette's open state is shared, because the Modern sidebar's
  // search button opens the same palette this shortcut does.
  const { open: paletteOpen, closePalette, toggle: togglePalette } = useCommandPalette();
  const [helpOpen, setHelpOpen] = useState(false);
  const pendingGo = useRef<number | null>(null);
  const goArmed = useRef(false);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      // Palette: works everywhere, including inside fields.
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        togglePalette();
        return;
      }
      if (event.defaultPrevented || event.metaKey || event.ctrlKey || event.altKey) {
        return;
      }
      if (isEditable(event.target) || document.querySelector('.MuiDialog-root')) {
        return;
      }

      if (goArmed.current) {
        goArmed.current = false;
        if (pendingGo.current !== null) {
          window.clearTimeout(pendingGo.current);
        }
        const target = GO_TARGETS[event.key.toLowerCase()];
        if (target) {
          event.preventDefault();
          navigate(target);
        }
        return;
      }

      if (event.key === 'g') {
        event.preventDefault();
        goArmed.current = true;
        pendingGo.current = window.setTimeout(() => {
          goArmed.current = false;
        }, 800);
      } else if (event.key === 'n') {
        const base = Object.keys(CREATE_TARGETS).find(
          (path) => location.pathname === path || (path !== '/' && location.pathname === path),
        );
        if (base) {
          event.preventDefault();
          navigate(CREATE_TARGETS[base]);
        }
      } else if (event.key === '/') {
        const search = document.querySelector<HTMLInputElement>('.search-bar input, input[placeholder="Search Here"]');
        if (search) {
          event.preventDefault();
          search.focus();
        }
      } else if (event.key === '?') {
        event.preventDefault();
        setHelpOpen(true);
      }
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [navigate, location.pathname, togglePalette]);

  return (
    <>
      <CommandPalette open={paletteOpen} onClose={closePalette} onShowShortcuts={() => setHelpOpen(true)} />
      {helpOpen && <ShortcutHelp onClose={() => setHelpOpen(false)} />}
    </>
  );
}
