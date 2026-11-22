import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Typography from '@mui/material/Typography';
import { TWO_FACTOR_SETUP_REQUIRED } from '../../api/apiClient';
import { getTwoFactorStatus, skipTwoFactorSetup } from '../../api/twoFactorApi';
import { useAuth } from '../../context/AuthContext';
import { tr } from '../../i18n/translate';
import { Button } from '../common/Button';

const SECURITY_PAGE = '/settings/security';
const SKIPPED_KEY = 'vms.twoFactorSetupSkipped';

/** Whole days until the set-up-by date, never below zero. */
export function daysLeft(dueAt: string, now = Date.now()) {
  return Math.max(0, Math.ceil((new Date(dueAt).getTime() - now) / 86_400_000));
}

function skippedThisSession() {
  try {
    return sessionStorage.getItem(SKIPPED_KEY) === '1';
  } catch {
    return false;
  }
}

/**
 * V21 (D-21-2, TF-FR-33, TF-FR-34): for admins and coaches without two-step
 * sign-in. Before their date: "Set up now" or "Skip for now (N days left)",
 * once per browser session. From their date, or whenever the server answers
 * TWO_FACTOR_SETUP_REQUIRED, the Security page opens; the server refuses the
 * rest anyway.
 */
export function TwoFactorRequirementGate() {
  const { token, user } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [dueAt, setDueAt] = useState<string | null>(null);
  const isStaff = user?.role === 'COACH' || user?.role === 'ADMIN';

  useEffect(() => {
    const open = () => navigate(SECURITY_PAGE, { state: { twoFactorRequired: true } });
    window.addEventListener(TWO_FACTOR_SETUP_REQUIRED, open);
    return () => window.removeEventListener(TWO_FACTOR_SETUP_REQUIRED, open);
  }, [navigate]);

  useEffect(() => {
    if (!token || !isStaff) return undefined;
    let active = true;
    getTwoFactorStatus(token)
      .then((status) => {
        if (!active || status.enabled || !status.dueAt) return;
        if (daysLeft(status.dueAt) === 0) {
          navigate(SECURITY_PAGE, { replace: true, state: { twoFactorRequired: true } });
        } else if (!skippedThisSession()) {
          setDueAt(status.dueAt);
        }
      })
      .catch(() => undefined);
    return () => { active = false; };
  }, [token, isStaff, navigate]);

  if (!dueAt || pathname === SECURITY_PAGE) return null;

  const skip = () => {
    try {
      sessionStorage.setItem(SKIPPED_KEY, '1');
    } catch {
      // Without storage the prompt simply comes back next time.
    }
    void skipTwoFactorSetup(token).catch(() => undefined);
    setDueAt(null);
  };

  return (
    <Dialog open onClose={skip} aria-labelledby="two-factor-required-title">
      <DialogTitle id="two-factor-required-title">{tr('Set up two-step sign-in')}</DialogTitle>
      <DialogContent>
        <Typography variant="body2">
          {tr('Coach and admin accounts can see other people\'s plans, so they need two-step sign-in with an authenticator app.')}
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button variant="secondary" onClick={skip}>
          {tr('Skip for now ({days} days left)', { days: daysLeft(dueAt) })}
        </Button>
        <Button onClick={() => { setDueAt(null); navigate(SECURITY_PAGE); }}>{tr('Set up now')}</Button>
      </DialogActions>
    </Dialog>
  );
}
