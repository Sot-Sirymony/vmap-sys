import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Dialog from '@mui/material/Dialog';
import { CornerDownLeft } from 'lucide-react';
import { listDreams } from '../../api/dreamApi';
import { useAuth } from '../../context/AuthContext';
import { navItems } from '../layout/nav-items';
import type { Dream } from '../../types/vision';
import { Input } from './Input';

type Command = {
  key: string;
  group: 'Go to' | 'Create' | 'Dreams' | 'Help';
  label: string;
  run: () => void;
};

type CommandPaletteProps = {
  open: boolean;
  onClose: () => void;
  onShowShortcuts: () => void;
};

/**
 * FR-29.1 command palette (Ctrl/Cmd+K): jump to any page or dream, or start
 * a create flow, from anywhere. Filtering is plain substring match; ↑/↓
 * choose, Enter runs, Esc closes.
 */
export function CommandPalette({ open, onClose, onShowShortcuts }: CommandPaletteProps) {
  const navigate = useNavigate();
  const { token } = useAuth();
  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const loadedRef = useRef(false);

  // Dreams load once, on first open — the palette must feel instant after.
  useEffect(() => {
    if (!open || loadedRef.current || !token) {
      return;
    }
    loadedRef.current = true;
    void listDreams(token).then((data) => setDreams(data.filter((dream) => !dream.archived)));
  }, [open, token]);

  useEffect(() => {
    if (open) {
      setQuery('');
      setActiveIndex(0);
    }
  }, [open]);

  const commands = useMemo<Command[]>(() => {
    const go = (to: string) => () => {
      onClose();
      navigate(to);
    };
    return [
      ...navItems.map((item) => ({ key: `go-${item.to}`, group: 'Go to' as const, label: item.label, run: go(item.to) })),
      { key: 'new-area', group: 'Create', label: 'New vision area', run: go('/vision-areas?create=area') },
      { key: 'new-dream', group: 'Create', label: 'New dream', run: go('/dreams?create=dream') },
      { key: 'new-goal', group: 'Create', label: 'New goal', run: go('/goals?create=goal') },
      { key: 'new-step', group: 'Create', label: 'New step', run: go('/steps?create=step') },
      { key: 'new-task', group: 'Create', label: 'New task', run: go('/tasks?create=task') },
      { key: 'new-partner', group: 'Create', label: 'New partner', run: go('/partners?create=partner') },
      { key: 'new-obstacle', group: 'Create', label: 'New obstacle', run: go('/obstacles?create=obstacle') },
      ...dreams.map((dream) => ({
        key: `dream-${dream.id}`,
        group: 'Dreams' as const,
        label: `${dream.title} — open map`,
        run: go(`/dreams/${dream.id}`),
      })),
      { key: 'help', group: 'Help', label: 'Keyboard shortcuts', run: () => { onClose(); onShowShortcuts(); } },
    ];
  }, [dreams, navigate, onClose, onShowShortcuts]);

  const matches = useMemo(() => {
    const needle = query.trim().toLowerCase();
    const filtered = needle
      ? commands.filter((command) => command.label.toLowerCase().includes(needle))
      : commands;
    return filtered.slice(0, 12);
  }, [commands, query]);

  const active = matches[Math.min(activeIndex, matches.length - 1)];

  function handleKeyDown(event: React.KeyboardEvent) {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActiveIndex((index) => Math.min(index + 1, matches.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActiveIndex((index) => Math.max(index - 1, 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      active?.run();
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth className="palette-dialog">
      <div className="palette" onKeyDown={handleKeyDown}>
        <Input
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setActiveIndex(0);
          }}
          placeholder="Type a page, action, or dream…"
          aria-label="Command palette search"
          autoFocus
        />
        <ul className="palette-list" role="listbox" aria-label="Commands">
          {matches.length === 0 && <li className="palette-empty">No matches.</li>}
          {matches.map((command, index) => (
            <li key={command.key} role="option" aria-selected={command === active}>
              <button
                type="button"
                className={`palette-item${command === active ? ' palette-item--active' : ''}`}
                onClick={() => command.run()}
                onMouseEnter={() => setActiveIndex(index)}
              >
                <span className="palette-group">{command.group}</span>
                <span className="palette-label">{command.label}</span>
                {command === active && <CornerDownLeft size={14} aria-hidden="true" />}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </Dialog>
  );
}
