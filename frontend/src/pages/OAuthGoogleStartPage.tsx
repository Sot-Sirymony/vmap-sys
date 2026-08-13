import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { getGoogleAuthorizeUrl } from '../api/oauthApi';
import { GoogleGlyph } from '../components/common/GoogleSignInButton';
import { AuthLayout } from '../layouts/AuthLayout';

/**
 * The OAuth loading screen. On arrival it asks the backend for Google's
 * authorization URL and sends the browser there; while that is in flight the
 * user sees the Stitch spinner-with-G treatment. If the backend cannot start
 * the flow (it does not implement OAuth yet, or the request fails), the flow
 * lands on the designed error screen rather than a dead page.
 */
export function OAuthGoogleStartPage() {
  const navigate = useNavigate();

  useEffect(() => {
    let cancelled = false;

    getGoogleAuthorizeUrl()
      .then(({ url }) => {
        if (!cancelled) {
          window.location.assign(url);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          navigate('/oauth/google/error', {
            replace: true,
            state: { message: error instanceof Error ? error.message : undefined },
          });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  return (
    <AuthLayout>
      <div className="auth-status" role="status">
        <div className="auth-spinner">
          <svg className="auth-spinner-ring" viewBox="25 25 50 50" aria-hidden="true">
            <circle cx="50" cy="50" r="20" />
          </svg>
          <span className="auth-spinner-badge">
            <GoogleGlyph />
          </span>
        </div>
        <p className="auth-status-text">Signing you in with Google…</p>
        <p className="auth-status-note">Please wait while we securely authenticate your account.</p>
      </div>
    </AuthLayout>
  );
}
