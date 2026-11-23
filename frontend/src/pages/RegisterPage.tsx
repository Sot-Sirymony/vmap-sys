import { FormEvent, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { register } from '../api/authApi';
import { AuthField } from '../components/common/AuthField';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { GoogleSignInSection } from '../components/common/GoogleSignInButton';
import { useOfferedSignInMethods } from '../hooks/useOfferedSignInMethods';
import { AuthLayout } from '../layouts/AuthLayout';
import { tr } from '../i18n/translate';

export function RegisterPage() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  // V9 (D-7): a link like /register?code=ABCD2345 fills the cohort join code in.
  const [searchParams] = useSearchParams();
  const [joinCode, setJoinCode] = useState(searchParams.get('code') ?? '');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  // V24 (SA-FR-11): with password sign-in off, new accounts come through Google only.
  const methods = useOfferedSignInMethods();
  const navigate = useNavigate();

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');

    if (password !== confirmPassword) {
      setError(tr('The passwords do not match.'));
      return;
    }

    setLoading(true);
    try {
      await register({ fullName, email, password, joinCode: joinCode.trim() || undefined });
      // Registering no longer signs the user straight in. It used to call
      // setSession() with the token the endpoint returns and jump to the
      // dashboard, which meant a successful sign-up produced no confirmation at
      // all — the form simply vanished and the app appeared. Handing over to the
      // sign-in page gives the success somewhere to be stated, and makes the
      // account's credentials something the user has actually used once.
      //
      // The token in the response is deliberately dropped. Registering proves
      // the account exists, not that whoever filled the form can sign in to it.
      navigate('/login', { replace: true, state: { registeredEmail: email } });
    } catch (error) {
      setError(error instanceof Error ? tr(error.message) : tr('Registration failed.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout subtitle={tr('Create your account')}>
      {/* V14 (GA-FR-22): a new account made with Google keeps its session, like signing up does. */}
      <GoogleSignInSection
        onClick={() => navigate('/oauth/google', { state: { from: '/', remember: true } })}
        divider={methods.password ? tr('or') : undefined}
      />
      {!methods.password && (
        <p className="auth-status-note" style={{ maxWidth: 'none' }}>
          {tr('Sign-up with a password is turned off. Sign up with Google.')}
        </p>
      )}
      {methods.password && (
      <form className="auth-form" onSubmit={handleSubmit}>
        <AuthField
          id="register-name"
          label={tr('Full name')}
          name="fullName"
          autoComplete="name"
          placeholder="John Doe"
          value={fullName}
          onChange={setFullName}
          required
        />
        <AuthField
          id="register-email"
          label={tr('Email')}
          type="email"
          name="email"
          autoComplete="username"
          placeholder="john@example.com"
          value={email}
          onChange={setEmail}
          required
        />
        <AuthField
          id="register-password"
          label={tr('Password')}
          type="password"
          name="password"
          autoComplete="new-password"
          placeholder="••••••••"
          minLength={8}
          value={password}
          onChange={setPassword}
          required
        />
        <AuthField
          id="register-confirm-password"
          label={tr('Confirm password')}
          type="password"
          name="confirmPassword"
          autoComplete="new-password"
          placeholder="••••••••"
          minLength={8}
          value={confirmPassword}
          onChange={setConfirmPassword}
          required
        />
        <AuthField
          id="register-join-code"
          label={tr('Cohort join code (optional)')}
          name="joinCode"
          autoComplete="off"
          placeholder="ABCD2345"
          value={joinCode}
          onChange={setJoinCode}
        />
        {error && <ErrorMessage message={error} />}
        <button className="auth-submit" type="submit" disabled={loading}>
          {loading ? tr('Creating...') : tr('Create account')}
        </button>
      </form>
      )}
      <p className="auth-link" style={{ textAlign: 'center' }}>
        {tr('Already have an account?')} <Link to="/login">{tr('Sign in')}</Link>
      </p>
    </AuthLayout>
  );
}
