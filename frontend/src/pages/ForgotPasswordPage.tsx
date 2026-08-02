import { FormEvent, useState } from 'react';
import { Link } from 'react-router';
import MuiAlert from '@mui/material/Alert';
import { forgotPassword } from '../api/authApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { AuthLayout } from '../layouts/AuthLayout';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      await forgotPassword({ email });
      setSent(true);
    } catch (error) {
      // Only a transport or server failure reaches here: the endpoint answers
      // 204 for an unknown address exactly as it does for a known one.
      setError(error instanceof Error ? error.message : 'Could not send the reset link.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout>
      <h1 className="text-2xl font-semibold">Reset your password</h1>

      {sent ? (
        <>
          {/*
            Phrased so it says the same thing whether or not the address has an
            account, because the backend deliberately does not tell us. "We sent
            you an email" would be a claim we cannot make, and one that would
            turn this form into a way to discover which addresses are registered.
          */}
          <MuiAlert severity="success">
            If <strong>{email}</strong> has an account, a reset link is on its way. The link
            works once and expires in 30 minutes.
          </MuiAlert>
          <p className="auth-link">
            Didn't get it? Check the spam folder, or <Link to="/forgot-password" onClick={() => setSent(false)}>try another address</Link>.
          </p>
        </>
      ) : (
        <form className="form-stack" onSubmit={handleSubmit}>
          <p className="text-sm text-muted-foreground">
            Enter the email address for your account and we'll send you a link to choose a
            new password.
          </p>
          <label>
            Email
            <Input
              type="email"
              name="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </label>
          {error && <ErrorMessage message={error} />}
          <Button type="submit" disabled={loading}>
            {loading ? 'Sending...' : 'Send reset link'}
          </Button>
        </form>
      )}

      <p className="auth-link">Remembered it? <Link to="/login">Sign in</Link></p>
    </AuthLayout>
  );
}
