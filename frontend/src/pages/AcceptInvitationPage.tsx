import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { ApiError } from '../api/apiClient';
import { acceptInvitation, checkInvitation } from '../api/authApi';
import { AuthField } from '../components/common/AuthField';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { useAuth } from '../context/AuthContext';
import { tr } from '../i18n/translate';
import { AuthLayout } from '../layouts/AuthLayout';
import type { InvitationInfo } from '../types/admin';
import { continueSignIn } from '../utils/continueSignIn';

/** Matches the backend's @Size(min = 8) on the password. */
const MIN_PASSWORD_LENGTH = 8;

/**
 * V23 (BRD 23.0.0, UM-FR-24, UM-FR-26): a person a program admin added opens the
 * emailed link, chooses their own password, and is signed in. The token is a
 * credential, so it is never shown on the page.
 */
export function AcceptInvitationPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const navigate = useNavigate();
  const { setSession } = useAuth();
  const [invitation, setInvitation] = useState<InvitationInfo | null>(null);
  const [checking, setChecking] = useState(Boolean(token));
  const [gone, setGone] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    checkInvitation(token)
      .then((info) => {
        if (!cancelled) setInvitation(info);
      })
      .catch((caught: unknown) => {
        if (!cancelled) setGone(caught instanceof Error ? caught.message : tr('This invitation link does not work.'));
      })
      .finally(() => {
        if (!cancelled) setChecking(false);
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');
    if (password !== confirm) {
      setError(tr('The passwords do not match.'));
      return;
    }
    setSaving(true);
    try {
      const answer = await acceptInvitation(token, password, true);
      continueSignIn(answer, { remember: true, from: '/', navigate, setSession });
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 410) {
        setGone(caught.message);
      } else {
        setError(caught instanceof Error ? caught.message : tr('Could not save your password.'));
      }
    } finally {
      setSaving(false);
    }
  }

  if (!token) {
    return (
      <AuthLayout subtitle={tr('Accept your invitation')}>
        <ErrorMessage message={tr('This invitation link is incomplete. Open the link from your email again.')} />
      </AuthLayout>
    );
  }

  if (checking) {
    return (
      <AuthLayout subtitle={tr('Accept your invitation')}>
        <Loading />
      </AuthLayout>
    );
  }

  if (gone || !invitation) {
    return (
      <AuthLayout subtitle={tr('Accept your invitation')}>
        <ErrorMessage message={tr(gone || 'This invitation link does not work.')} />
        <p className="auth-link" style={{ textAlign: 'center' }}>
          {tr('Already chose a password?')} <Link to="/login">{tr('Sign in')}</Link>
        </p>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout subtitle={tr('Welcome, {name}', { name: invitation.fullName })}>
      <p className="auth-status-note" style={{ marginTop: 0, maxWidth: 'none' }}>
        {tr('Choose a password for {email}. Only you will know it.', { email: invitation.email })}
      </p>
      <form className="auth-form" onSubmit={handleSubmit}>
        <AuthField
          id="invitation-password"
          label={tr('Password')}
          type="password"
          name="password"
          autoComplete="new-password"
          minLength={MIN_PASSWORD_LENGTH}
          value={password}
          onChange={setPassword}
          required
        />
        <AuthField
          id="invitation-confirm"
          label={tr('Confirm password')}
          type="password"
          name="confirmPassword"
          autoComplete="new-password"
          minLength={MIN_PASSWORD_LENGTH}
          value={confirm}
          onChange={setConfirm}
          required
        />
        <p className="auth-status-note" style={{ margin: 0, maxWidth: 'none' }}>
          {tr('Use at least {count} characters.', { count: MIN_PASSWORD_LENGTH })}
        </p>
        {error && <ErrorMessage message={error} />}
        <button className="auth-submit" type="submit" disabled={saving}>
          {saving ? tr('Saving...') : tr('Save password and sign in')}
        </button>
      </form>
      <p className="auth-link" style={{ textAlign: 'center' }}>
        {tr('Your email is a Google account?')} <Link to="/oauth/google">{tr('Sign in with Google instead')}</Link>
      </p>
    </AuthLayout>
  );
}
