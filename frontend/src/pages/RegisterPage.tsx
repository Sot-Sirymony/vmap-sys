import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { register } from '../api/authApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { AuthLayout } from '../layouts/AuthLayout';

export function RegisterPage() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');
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
    <AuthLayout>
      <h1 className="text-2xl font-semibold">Create account</h1>
      <form className="form-stack" onSubmit={handleSubmit}>
        <label>
          Full name
          <Input value={fullName} onChange={(event) => setFullName(event.target.value)} required />
        </label>
        <label>
          Email
          <Input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
        </label>
        <label>
          Password
          <Input type="password" minLength={8} value={password} onChange={(event) => setPassword(event.target.value)} required />
        </label>
        {error && <ErrorMessage message={error} />}
        <Button type="submit" disabled={loading}>{loading ? 'Creating...' : 'Create account'}</Button>
      </form>
      <p className="auth-link">Already registered? <Link to="/login">Sign in</Link></p>
    </AuthLayout>
  );
}
