import { CircleAlert } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router';
import { AuthLayout } from '../layouts/AuthLayout';

/**
 * The OAuth error screen: what the user sees when Google sign-in could not be
 * completed. The generic explanation is always shown; the specific failure
 * (carried in navigation state by whichever step failed) appears beneath it,
 * so a support conversation has something concrete to point at.
 */
export function OAuthGoogleErrorPage() {
  const navigate = useNavigate();
  const state = useLocation().state as { message?: string } | null;

  return (
    <AuthLayout brand={false}>
      <div className="auth-status">
        <span className="auth-status-icon auth-status-icon--error">
          <CircleAlert size={32} />
        </span>
        <h1 className="auth-status-title">We couldn't sign you in with Google</h1>
        <p className="auth-status-text">
          There was a problem authenticating your account. Please try again or use your password.
        </p>
        {state?.message && <p className="auth-status-note">{state.message}</p>}
      </div>
      <div className="auth-actions">
        <button className="auth-submit" type="button" onClick={() => navigate('/oauth/google')}>
          Try again
        </button>
        <Link className="auth-submit auth-submit--text" to="/login">
          Sign in with email instead
        </Link>
      </div>
    </AuthLayout>
  );
}
