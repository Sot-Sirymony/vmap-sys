import { Modal } from './Modal';

const SHORTCUTS: { keys: string; action: string }[] = [
  { keys: 'Ctrl/⌘ + K', action: 'Open the command palette' },
  { keys: 'g then d', action: 'Go to Dashboard' },
  { keys: 'g then m', action: 'Go to Vision Map' },
  { keys: 'g then g', action: 'Go to Goals' },
  { keys: 'g then s', action: 'Go to Steps' },
  { keys: 'g then t', action: 'Go to Tasks' },
  { keys: 'g then a', action: 'Go to Vision Areas' },
  { keys: 'g then p', action: 'Go to Partners' },
  { keys: 'g then r', action: 'Go to Reviews' },
  { keys: 'n', action: 'New record on the current page' },
  { keys: '/', action: 'Focus the page search' },
  { keys: '?', action: 'Show this list' },
  { keys: '↑ ↓ → ← · Enter · N', action: 'Vision Map: move, expand, rename, add child' },
];

/** FR-29 acceptance: every shortcut is discoverable in-app. */
export function ShortcutHelp({ onClose }: { onClose: () => void }) {
  return (
    <Modal title="Keyboard shortcuts" onClose={onClose}>
      <table className="shortcut-table">
        <tbody>
          {SHORTCUTS.map((shortcut) => (
            <tr key={shortcut.keys}>
              <td><kbd>{shortcut.keys}</kbd></td>
              <td>{shortcut.action}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Modal>
  );
}
