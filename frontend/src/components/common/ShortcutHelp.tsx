import { Modal } from './Modal';
import { tr } from '../../i18n/translate';

const SHORTCUTS: { keys: string; action: string }[] = [
  { keys: 'Ctrl/⌘ + K', action: tr('Open the command palette') },
  { keys: 'g then d', action: tr('Go to Dashboard') },
  { keys: 'g then m', action: tr('Go to Vision Map') },
  { keys: 'g then g', action: tr('Go to Goals') },
  { keys: 'g then s', action: tr('Go to Steps') },
  { keys: 'g then t', action: tr('Go to Tasks') },
  { keys: 'g then a', action: tr('Go to Vision Areas') },
  { keys: 'g then p', action: tr('Go to Partners') },
  { keys: 'g then r', action: tr('Go to Reviews') },
  { keys: 'n', action: tr('New record on the current page') },
  { keys: '/', action: tr('Focus the page search') },
  { keys: '?', action: tr('Show this list') },
  { keys: '↑ ↓ → ← · Enter · N', action: tr('Vision Map: move, expand, rename, add child') },
];

/** FR-29 acceptance: every shortcut is discoverable in-app. */
export function ShortcutHelp({ onClose }: { onClose: () => void }) {
  return (
    <Modal title={tr('Keyboard shortcuts')} onClose={onClose}>
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
