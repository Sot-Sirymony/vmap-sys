import { useCallback, useEffect, useRef, useState } from 'react';
import Alert from '@mui/material/Alert';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Typography from '@mui/material/Typography';
import { ApiError } from '../../api/apiClient';
import { requestStepUpCode, verifyStepUpCode } from '../../api/stepUpApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { StepUpAction, StepUpChallenge } from '../../types/vision';
import { Button } from '../common/Button';
import { Input } from '../common/Input';

const CODE_LENGTH = 6;

/** Digits only, as the server reads them (spaces and dashes are ignored). */
function digitsOf(value: string) {
  return value.replace(/[\s-]/g, '');
}

function secondsUntil(iso?: string | null) {
  return iso ? Math.max(0, Math.ceil((new Date(iso).getTime() - Date.now()) / 1000)) : 0;
}

/**
 * V20 (BRD 20.0.0, SV-FR-20 to SV-FR-26): "Verify it's you". It asks the
 * server to email a code for the action, then checks what the person types.
 * onVerified gets the verified challenge id, or undefined when no code could
 * be sent because the server has no mail (D-20-4: the action keeps its password
 * checks). Nothing the person typed on the page is lost: the page only waits.
 */
export function StepUpDialog({ token, action, target, onVerified, onCancel }: Readonly<{
  token: string;
  action: StepUpAction;
  /** The new reminder address, for CONFIRM_REMINDER_EMAIL. */
  target?: string;
  onVerified: (challengeId: number | undefined) => void;
  onCancel: () => void;
}>) {
  const { language } = useI18n();
  const [challenge, setChallenge] = useState<StepUpChallenge | null>(null);
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [ended, setEnded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [wait, setWait] = useState(0);
  const sent = useRef(false);

  const send = useCallback(async () => {
    setBusy(true);
    setError('');
    setEnded(false);
    setCode('');
    try {
      const next = await requestStepUpCode(token, action, language, target);
      setChallenge(next);
      setWait(secondsUntil(next.resendAfter));
    } catch (sendError) {
      setError(sendError instanceof Error ? tr(sendError.message) : tr('Unable to send a code.'));
    } finally {
      setBusy(false);
    }
  }, [token, action, language, target]);

  useEffect(() => {
    // Once per dialog, even under React's double-run of effects in development.
    if (!sent.current) {
      sent.current = true;
      void send();
    }
  }, [send]);

  useEffect(() => {
    if (wait <= 0) return undefined;
    const timer = window.setTimeout(() => setWait(secondsUntil(challenge?.resendAfter)), 1000);
    return () => window.clearTimeout(timer);
  }, [wait, challenge]);

  async function verify(typed: string) {
    if (!challenge?.challengeId || digitsOf(typed).length !== CODE_LENGTH) return;
    setBusy(true);
    setError('');
    try {
      const result = await verifyStepUpCode(token, challenge.challengeId, digitsOf(typed));
      onVerified(result.challengeId);
    } catch (verifyError) {
      setError(verifyError instanceof Error ? tr(verifyError.message) : tr('Unable to check the code.'));
      if (verifyError instanceof ApiError && (verifyError.code === 'STEP_UP_CODE_ENDED' || verifyError.triesLeft === 0)) {
        setEnded(true);
      }
      setCode('');
    } finally {
      setBusy(false);
    }
  }

  function change(value: string) {
    setCode(value);
    // SV-FR-21: it submits on its own once six digits are in.
    if (digitsOf(value).length === CODE_LENGTH && /^\d+$/.test(digitsOf(value))) {
      void verify(value);
    }
  }

  const noMail = challenge?.delivery === 'NONE';
  const minutes = Math.max(1, Math.round(secondsUntil(challenge?.expiresAt) / 60));

  return (
    <Dialog open onClose={onCancel} aria-labelledby="step-up-title" aria-describedby="step-up-description" fullWidth maxWidth="xs">
      <DialogTitle id="step-up-title">{tr("Verify it's you")}</DialogTitle>
      <DialogContent sx={{ display: 'grid', gap: 1.5 }}>
        {!challenge && !error && <Typography variant="body2" role="status">{tr('Sending a code…')}</Typography>}
        {challenge && noMail && (
          <Alert severity="info" id="step-up-description">
            {tr("Email is not set up on this server, so we can't send a code. You can go on; your password is still checked.")}
          </Alert>
        )}
        {challenge && !noMail && (
          <>
            <Typography variant="body2" id="step-up-description">
              {tr('We sent a 6-digit code to {address}. It works for {minutes} minutes.', { address: challenge.sentTo, minutes })}
            </Typography>
            {challenge.delivery === 'LOG' && (
              <Typography variant="caption" color="text.secondary">
                {tr('Development server: the code is in the server log.')}
              </Typography>
            )}
            <label>
              {tr('Code')}
              <Input aria-label={tr('Code')} value={code} autoFocus inputMode="numeric" autoComplete="one-time-code"
                maxLength={9} disabled={busy || ended} onChange={(event) => change(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') void verify(code);
                }} />
            </label>
          </>
        )}
        {error && <Alert severity="error" role="alert">{error}</Alert>}
      </DialogContent>
      <DialogActions sx={{ flexWrap: 'wrap', gap: 1 }}>
        <Button variant="secondary" onClick={onCancel}>{tr('Cancel')}</Button>
        {noMail ? (
          <Button onClick={() => onVerified(undefined)}>{tr('Continue')}</Button>
        ) : (
          <>
            <Button variant="secondary" disabled={busy || wait > 0} onClick={() => void send()}>
              {wait > 0 ? tr('Send a new code in {seconds} s', { seconds: wait }) : tr('Send a new code')}
            </Button>
            <Button disabled={busy || ended || digitsOf(code).length !== CODE_LENGTH} onClick={() => void verify(code)}>
              {busy ? tr('Checking…') : tr('Verify')}
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
}
