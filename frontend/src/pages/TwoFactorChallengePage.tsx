import { FormEvent, useEffect, useState } from 'react';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import { ShieldCheck } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router';
import { ApiError } from '../api/apiClient';
import { resendSignInCode, verifySignIn } from '../api/twoFactorApi';
import { AuthField } from '../components/common/AuthField';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { useAuth } from '../context/AuthContext';
import { AuthLayout } from '../layouts/AuthLayout';
import { tr } from '../i18n/translate';
import type { TwoFactorChallenge } from '../types/auth';
import { saveDeviceToken } from '../utils/deviceToken';

const RESEND_AFTER_SECONDS = 60;

type ChallengeState = { challenge?: TwoFactorChallenge; remember?: boolean; from?: string } | null;

/**
 * V21 (TF-FR-12 to TF-FR-17, TF-FR-21, TF-FR-27): the second step of signing
 * in. The challenge comes in navigation state from the Sign In page, the Google
 * callback or the linking screen; reached directly, it sends the person back.
 */
export function TwoFactorChallengePage() {
  const navigate = useNavigate();
  const state = useLocation().state as ChallengeState;
  const { setSession } = useAuth();
  const [challenge, setChallenge] = useState<TwoFactorChallenge | undefined>(state?.challenge);
  const [useRecovery, setUseRecovery] = useState(Boolean(state?.challenge?.emailUnavailable));
  const [code, setCode] = useState('');
  const [rememberDevice, setRememberDevice] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(false);
  const [showLostHelp, setShowLostHelp] = useState(false);
  const [resendIn, setResendIn] = useState(RESEND_AFTER_SECONDS);
  const isEmail = challenge?.method === 'EMAIL';

  useEffect(() => {
    if (!isEmail || resendIn <= 0) return undefined;
    const timer = window.setTimeout(() => setResendIn((seconds) => seconds - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [isEmail, resendIn]);

  if (!challenge) {
    return (
      <AuthLayout brand={false}>
        <div className="auth-status">
          <h1 className="auth-status-title">{tr('Two-step sign-in')}</h1>
          <p className="auth-status-text">{tr('This page opens after you sign in. Start again from the sign-in screen.')}</p>
        </div>
        <div className="auth-actions">
          <Link className="auth-submit" to="/login">{tr('Go to sign in')}</Link>
        </div>
      </AuthLayout>
    );
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!challenge) return;
    setError('');
    setNotice('');
    setLoading(true);
    try {
      const response = await verifySignIn({
        challengeToken: challenge.challengeToken,
        code: useRecovery ? undefined : code,
        recoveryCode: useRecovery ? code : undefined,
        rememberDevice,
      });
      saveDeviceToken(response.deviceToken);
      setSession(response, state?.remember ?? true);
      navigate(state?.from ?? '/', { replace: true });
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 410) {
        navigate('/login', { replace: true, state: { notice: tr('That sign-in step ended. Please sign in again.') } });
        return;
      }
      setError(caught instanceof Error ? tr(caught.message) : tr('Could not check the code.'));
      setCode('');
    } finally {
      setLoading(false);
    }
  }

  async function handleResend() {
    if (!challenge) return;
    setError('');
    try {
      const fresh = await resendSignInCode(challenge.challengeToken);
      setChallenge(fresh);
      setUseRecovery(fresh.emailUnavailable);
      setNotice(fresh.emailUnavailable ? '' : tr('We sent a new code.'));
      setResendIn(RESEND_AFTER_SECONDS);
    } catch (caught) {
      setError(caught instanceof Error ? tr(caught.message) : tr('Could not send a new code.'));
    }
  }

  const prompt = useRecovery
    ? tr('Type one of the recovery codes you saved when you set up two-step sign-in.')
    : isEmail
      ? tr('We sent a 6-digit code to {email}. It works for 10 minutes.', { email: challenge.sentTo ?? '' })
      : tr('Open your authenticator app and type the 6-digit code for Vision Mapping.');

  return (
    <AuthLayout brand={false}>
      <div className="auth-status">
        <span className="auth-status-icon auth-status-icon--link">
          <ShieldCheck size={28} />
        </span>
        <h1 className="auth-status-title">{tr('Two-step sign-in')}</h1>
        <p className="auth-status-text">{prompt}</p>
        {challenge.emailUnavailable && (
          <p className="auth-status-note">{tr('We could not email a code right now. Use a recovery code, or contact your administrator.')}</p>
        )}
      </div>
      <form className="auth-form" onSubmit={handleSubmit}>
        <AuthField
          id="two-factor-code"
          label={useRecovery ? tr('Recovery code') : tr('6-digit code')}
          name={useRecovery ? 'recovery-code' : 'one-time-code'}
          autoComplete={useRecovery ? 'off' : 'one-time-code'}
          inputMode={useRecovery ? 'text' : 'numeric'}
          maxLength={useRecovery ? 20 : 7}
          placeholder={useRecovery ? 'ABCDE-FGHJK' : '123456'}
          value={code}
          onChange={setCode}
          required
        />
        <FormControlLabel
          control={<Checkbox checked={rememberDevice} onChange={(event) => setRememberDevice(event.target.checked)} />}
          label={tr('Remember this device for 30 days')}
        />
        {notice && <p className="auth-status-note" role="status">{notice}</p>}
        {error && <ErrorMessage message={error} />}
        <div className="auth-actions">
          <button className="auth-submit" type="submit" disabled={loading || !code.trim()}>
            {loading ? tr('Checking...') : tr('Continue')}
          </button>
          <button className="auth-submit auth-submit--text" type="button"
            onClick={() => { setUseRecovery(!useRecovery); setCode(''); setError(''); }}>
            {useRecovery ? tr('Use a code instead') : tr('Use a recovery code')}
          </button>
          {isEmail && !useRecovery && (
            <button className="auth-submit auth-submit--text" type="button" disabled={resendIn > 0} onClick={() => void handleResend()}>
              {resendIn > 0 ? tr('Send a new code in {seconds}s', { seconds: resendIn }) : tr('Send a new code')}
            </button>
          )}
          <button className="auth-submit auth-submit--text" type="button" aria-expanded={showLostHelp}
            onClick={() => setShowLostHelp(!showLostHelp)}>
            {tr('Lost your device?')}
          </button>
        </div>
        {showLostHelp && (
          <ol className="auth-status-note">
            <li>{tr('Use one of the recovery codes you saved.')}</li>
            <li>{tr('If you chose email codes, check your email.')}</li>
            <li>{tr('Otherwise, contact your administrator. After checking it is you, they can reset two-step sign-in.')}</li>
          </ol>
        )}
        <Link className="auth-submit auth-submit--ghost" to="/login">{tr('Cancel')}</Link>
      </form>
    </AuthLayout>
  );
}
