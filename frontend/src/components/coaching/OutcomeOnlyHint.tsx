import { useState } from 'react';
import MuiButton from '@mui/material/Button';
import { findLimitPhrase } from '../../utils/limitPhrases';
import { tr } from '../../i18n/translate';

const DISMISS_PREFIX = 'vms.limitNote.dismissed.';

function readDismissed(recordKey: string | undefined): boolean {
  if (!recordKey) {
    return false;
  }
  try {
    return localStorage.getItem(DISMISS_PREFIX + recordKey) === '1';
  } catch {
    return false;
  }
}

/**
 * V17 BL-FR-11, BL-FR-12, BL-FR-13 (D-17-2): dreams and goals describe the
 * outcome only. Shows the outcome line for the form, and a soft note while the
 * text names a limit ("can't afford", "if I have time"). The note never blocks
 * saving (BL-NFR-03) and, once dismissed, stays hidden for that record. A new
 * record (no key yet) forgets the dismissal when the form closes.
 */
export function OutcomeOnlyHint({ level, texts, recordKey }: {
  level: 'dream' | 'goal';
  texts: readonly (string | null | undefined)[];
  /** e.g. "dream-12"; undefined while creating. */
  recordKey?: string;
}) {
  const [dismissedNow, setDismissedNow] = useState(false);
  const dismissed = dismissedNow || readDismissed(recordKey);
  const phrase = findLimitPhrase(texts);

  function dismiss() {
    setDismissedNow(true);
    if (recordKey) {
      try {
        localStorage.setItem(DISMISS_PREFIX + recordKey, '1');
      } catch {
        // Browser storage is a convenience only; the note still hides for now.
      }
    }
  }

  return (
    <div className="field-full outcome-only-hint">
      <p className="field-hint">
        {level === 'dream'
          ? tr('Describe what success looks like. Leave out what might stop you; you will deal with that in the steps.')
          : tr('Say what result this goal delivers. Gaps and help come in the steps.')}
      </p>
      {phrase && !dismissed && (
        <div className="coaching-panel limit-note" role="status">
          <p>
            {level === 'dream'
              ? tr('This sounds like a blocker. Keep the dream bold. You can add this as a gap on a step after saving.')
              : tr('This sounds like a blocker. Keep the goal about the result. You can add this as a gap on a step after saving.')}
          </p>
          <MuiButton type="button" size="small" variant="text" onClick={dismiss}>{tr('Hide this note')}</MuiButton>
        </div>
      )}
    </div>
  );
}
