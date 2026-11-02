import { FormEvent, useState } from 'react';
import { Link as LinkIcon } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router';
import { linkGoogleAccount } from '../api/oauthApi';
import { AuthField } from '../components/common/AuthField';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { useAuth } from '../context/AuthContext';
import { AuthLayout } from '../layouts/AuthLayout';
import { tr } from '../i18n/translate';

/**
 * The account-linking screen: reached from the Google callback when the
 * Google email already has a password account. The link token proves which
 * Google identity asked; the password proves the person linking owns the
 * existing account. Linking opens a session, since both proofs together are
 * exactly a sign-in.
 */
export function AccountLinkingPage() {
  const navigate = useNavigate();
  const state = useLocation().state as { linkToken?: string } | null;
  const linkToken = state?.linkToken ?? '';
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { setSession } = useAuth();

  // Reached directly — no Google callback put a link token in navigation
  // state. Saying so beats a password form that can only fail on submit.
  if (!linkToken) {
    return (
      <AuthLayout brand={false}>
        <div className="auth-status">
          <span className="auth-status-icon auth-status-icon--link">
            <LinkIcon size={28} />
          </span>
          <h1 className="auth-status-title">{tr('Link your accounts')}</h1>
          <p className="auth-status-text">
            {tr('This page is only reachable from a Google sign-in. Start again from the sign-in screen.')}
          </p>
        </div>
        <div className="auth-actions">
          <Link className="auth-submit" to="/login">
            {tr('Go to sign in')}
          </Link>
        </div>
      </AuthLayout>
    );
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      const response = await linkGoogleAccount({ linkToken, password });
      setSession(response, true);
      navigate('/', { replace: true });
    } catch (error) {
      setError(error instanceof Error ? error.message : tr('Could not link the accounts.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout brand={false}>
      <div className="auth-status">
        <span className="auth-status-icon auth-status-icon--link">
          <LinkIcon size={28} />
        </span>
        <h1 className="auth-status-title">{tr('Link your accounts')}</h1>
        <p className="auth-status-text">
          {tr('An account with this email already exists. To enable Google sign-in, please verify your password once to link them.')}
        </p>
      </div>
      <form className="auth-form" onSubmit={handleSubmit}>
        <AuthField
          id="link-password"
          label={tr('Password')}
          name="password"
          autoComplete="current-password"
          placeholder={tr('Enter your password')}
          revealable
          value={password}
          onChange={setPassword}
          required
        />
        {error && <ErrorMessage message={error} />}
        <div className="auth-actions">
          <button className="auth-submit" type="submit" disabled={loading}>
            {loading ? tr('Linking...') : tr('Link accounts')}
          </button>
          <Link className="auth-submit auth-submit--ghost" to="/login">
            {tr('Cancel')}
          </Link>
        </div>
      </form>
    </AuthLayout>
  );
}
