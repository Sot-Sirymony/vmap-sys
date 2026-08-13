import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { register } from '../api/authApi';
import { AuthField } from '../components/common/AuthField';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { GoogleSignInButton } from '../components/common/GoogleSignInButton';
import { AuthLayout } from '../layouts/AuthLayout';

export function RegisterPage() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');

    if (password !== confirmPassword) {
      setError('The passwords do not match.');
      return;
    }

    setLoading(true);
    try {
      await register({ fullName, email, password });
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
      setError(error instanceof Error ? error.message : 'Registration failed.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout subtitle="Create your account">
      <GoogleSignInButton onClick={() => navigate('/oauth/google')} />
      <div className="auth-divider" aria-hidden="true">
        <span>or</span>
      </div>
      <form className="auth-form" onSubmit={handleSubmit}>
        <AuthField
          id="register-name"
          label="Full name"
          name="fullName"
          autoComplete="name"
          placeholder="John Doe"
          value={fullName}
          onChange={setFullName}
          required
        />
        <AuthField
          id="register-email"
          label="Email"
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
          label="Password"
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
          label="Confirm password"
          type="password"
          name="confirmPassword"
          autoComplete="new-password"
          placeholder="••••••••"
          minLength={8}
          value={confirmPassword}
          onChange={setConfirmPassword}
          required
        />
        {error && <ErrorMessage message={error} />}
        <button className="auth-submit" type="submit" disabled={loading}>
          {loading ? 'Creating...' : 'Create account'}
        </button>
      </form>
      <p className="auth-link" style={{ textAlign: 'center' }}>
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </AuthLayout>
  );
}
