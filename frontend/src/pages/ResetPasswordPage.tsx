import { FormEvent, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { resetPassword } from '../api/authApi';
import { AuthField } from '../components/common/AuthField';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { AuthLayout } from '../layouts/AuthLayout';
import { tr } from '../i18n/translate';

/** Matches the backend's @Size(min = 8) on the new password. */
const MIN_PASSWORD_LENGTH = 8;

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  // The emailed link carries it. Never rendered back to the page — it is a
  // credential, and putting it on screen invites it into a screenshot.
  const token = searchParams.get('token') ?? '';
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');

    if (newPassword !== confirmPassword) {
      setError(tr('The new passwords do not match.'));
      return;
    }

    setLoading(true);
    try {
      await resetPassword({ token, newPassword });
      // Straight to sign-in rather than opening a session off the back of the
      // link: redeeming it proves access to the inbox, and the new password is
      // the credential that should get them in.
      navigate('/login', { replace: true, state: { notice: 'Password updated. Sign in with your new password.' } });
    } catch (error) {
      setError(error instanceof Error ? error.message : tr('Could not reset the password.'));
    } finally {
      setLoading(false);
    }
  }

  // Reached without a token at all — someone typed the URL, or a mail client
  // mangled the link. Saying so beats a form that can only fail on submit.
  if (!token) {
    return (
      <AuthLayout subtitle={tr('Reset your password')}>
        <ErrorMessage message={tr('This reset link is incomplete. Request a new one.')} />
        <p className="auth-link" style={{ textAlign: 'center' }}>
          <Link to="/forgot-password">{tr('Send a new link')}</Link>
        </p>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout subtitle={tr('Choose a new password')}>
      <form className="auth-form" onSubmit={handleSubmit}>
        <AuthField
          id="reset-new-password"
          label={tr('New password')}
          type="password"
          name="newPassword"
          autoComplete="new-password"
          minLength={MIN_PASSWORD_LENGTH}
          value={newPassword}
          onChange={setNewPassword}
          required
        />
        <AuthField
          id="reset-confirm-password"
          label={tr('Confirm new password')}
          type="password"
          name="confirmPassword"
          autoComplete="new-password"
          minLength={MIN_PASSWORD_LENGTH}
          value={confirmPassword}
          onChange={setConfirmPassword}
          required
        />
        <p className="auth-status-note" style={{ margin: 0, maxWidth: 'none' }}>
          {tr('Use at least {count} characters.', { count: MIN_PASSWORD_LENGTH })}
        </p>
        {error && <ErrorMessage message={error} />}
        <button className="auth-submit" type="submit" disabled={loading}>
          {loading ? tr('Saving...') : tr('Set new password')}
        </button>
      </form>
      <p className="auth-link" style={{ textAlign: 'center' }}>
        Link expired? <Link to="/forgot-password">{tr('Request a new one')}</Link>
      </p>
    </AuthLayout>
  );
}
