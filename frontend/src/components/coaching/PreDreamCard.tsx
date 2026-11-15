import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { getChainAssessments } from '../../api/chainAssessmentApi';
import { Button } from '../common/Button';
import { tr } from '../../i18n/translate';

const SKIP_KEY = 'vms.preDreamCard.skippedAt';
const SKIP_DAYS = 30;
const DAY_MS = 86_400_000;

/** True while a "Skip for now" from the last 30 days is remembered. */
export function preDreamCardSkipped(now: number = Date.now()): boolean {
  try {
    const skippedAt = Number(localStorage.getItem(SKIP_KEY));
    return Number.isFinite(skippedAt) && skippedAt > 0 && now - skippedAt < SKIP_DAYS * DAY_MS;
  } catch {
    return false;
  }
}

function rememberSkip() {
  try {
    localStorage.setItem(SKIP_KEY, String(Date.now()));
  } catch {
    // Without storage the card simply shows again next time.
  }
}

/**
 * V17 BL-FR-33: before a participant's first dream, a short invitation to the
 * two-minute chain check, so inner blockers are seen before planning. Shown
 * only with no dreams and no chain check yet. Never required: "Skip for now"
 * hides it for 30 days, and nothing waits on it (BL-NFR-03).
 */
export function PreDreamCard({ token, hasDreams }: Readonly<{ token: string; hasDreams: boolean }>) {
  const navigate = useNavigate();
  const [hasCheck, setHasCheck] = useState<boolean | null>(null);
  const [skipped, setSkipped] = useState(preDreamCardSkipped);

  useEffect(() => {
    if (hasDreams || skipped || !token) {
      return;
    }
    let active = true;
    getChainAssessments(token)
      .then((summary) => active && setHasCheck(summary.assessments.length > 0))
      // If the check cannot be read, say nothing rather than nag.
      .catch(() => active && setHasCheck(true));
    return () => {
      active = false;
    };
  }, [token, hasDreams, skipped]);

  if (hasDreams || skipped || hasCheck !== false) {
    return null;
  }

  return (
    <section className="coaching-panel pre-dream-card" aria-label={tr('Before your first dream')}>
      <strong>{tr('Before your first dream')}</strong>
      <p>{tr('Before you map a dream, take two minutes to see what might hold you back inside.')}</p>
      <div className="row-actions">
        <Button type="button" onClick={() => navigate('/assessment')}>{tr('Take the 2-minute check')}</Button>
        <Button type="button" variant="secondary" onClick={() => { rememberSkip(); setSkipped(true); }}>{tr('Skip for now')}</Button>
      </div>
    </section>
  );
}
