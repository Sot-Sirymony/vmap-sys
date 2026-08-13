import { FormEvent, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import MuiAlert from '@mui/material/Alert';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import { Lock, Mail } from 'lucide-react';
import { login } from '../api/authApi';
import { AuthField } from '../components/common/AuthField';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { GoogleSignInButton } from '../components/common/GoogleSignInButton';
import { useAuth } from '../context/AuthContext';
import { AuthLayout } from '../layouts/AuthLayout';

// Only the email is stored. The password is never written to disk by this app —
// the autoComplete attributes below hand that job to the browser's own password
// manager, which keeps it in the OS keychain instead of in plaintext where any
// script on the page could read it.
const REMEMBERED_EMAIL_KEY = 'visionMappingRememberedEmail';

export function LoginPage() {
  const rememberedEmail = localStorage.getItem(REMEMBERED_EMAIL_KEY) ?? '';
  const location = useLocation();
  const state = location.state as {
    from?: { pathname?: string };
    registeredEmail?: string;
    notice?: string;
  } | null;
  // Both are set by the page that sent the user here — RegisterPage after a
  // sign-up, ResetPasswordPage after a recovery. They are the only confirmation
  // either flow gives, so they travel in navigation state rather than as a
  // toast, which would expire while the user is still reading the form it
  // belongs to.
  const registeredEmail = state?.registeredEmail;
  const notice = registeredEmail
    ? `Account created for ${registeredEmail}. Sign in to continue.`
    : state?.notice;
  const [email, setEmail] = useState(registeredEmail ?? rememberedEmail);
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(true);
  const [error, setError] = useState('');
  // Dismissed on the first submit, so a later failure is never shown underneath
  // a stale success notice.
  const [showNotice, setShowNotice] = useState(Boolean(notice));
  const [loading, setLoading] = useState(false);
  const { setSession } = useAuth();
  const navigate = useNavigate();
  const from = state?.from?.pathname ?? '/';

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');
    setShowNotice(false);
    setLoading(true);
    try {
      const response = await login({ email, password });

      if (remember) {
        localStorage.setItem(REMEMBERED_EMAIL_KEY, email);
      } else {
        localStorage.removeItem(REMEMBERED_EMAIL_KEY);
      }

      setSession(response, remember);
      navigate(from, { replace: true });
    } catch (error) {
      setError(error instanceof Error ? error.message : 'Login failed. Check your email and password.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout subtitle="Sign in to continue to your dashboard">
      {showNotice && notice && <MuiAlert severity="success">{notice}</MuiAlert>}
      <GoogleSignInButton onClick={() => navigate('/oauth/google', { state: { from } })} />
      <div className="auth-divider" aria-hidden="true">
        <span>or sign in with email</span>
      </div>
      <form className="auth-form" onSubmit={handleSubmit}>
        <AuthField
          id="login-email"
          label="Email address"
          type="email"
          name="email"
          autoComplete="username"
          placeholder="you@company.com"
          icon={<Mail size={18} />}
          value={email}
          onChange={setEmail}
          required
        />
        <AuthField
          id="login-password"
          label="Password"
          name="password"
          autoComplete="current-password"
          placeholder="••••••••"
          icon={<Lock size={18} />}
          revealable
          value={password}
          onChange={setPassword}
          required
          labelEnd={
            <span className="auth-link auth-forgot">
              <Link to="/forgot-password">Forgot password?</Link>
            </span>
          }
        />
        <FormControlLabel
          control={<Checkbox checked={remember} onChange={(event) => setRemember(event.target.checked)} />}
          label="Remember me on this device"
        />
        {error && <ErrorMessage message={error} />}
        <button className="auth-submit" type="submit" disabled={loading}>
          {loading ? 'Signing in...' : 'Sign in'}
        </button>
      </form>
      <p className="auth-link" style={{ textAlign: 'center' }}>
        No account yet? <Link to="/register">Create one</Link>
      </p>
    </AuthLayout>
  );
}
