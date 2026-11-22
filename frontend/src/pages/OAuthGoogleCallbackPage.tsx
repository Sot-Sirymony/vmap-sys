import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router';
import { exchangeGoogleCode } from '../api/oauthApi';
import { GoogleGlyph } from '../components/common/GoogleSignInButton';
import { useAuth } from '../context/AuthContext';
import { AuthLayout } from '../layouts/AuthLayout';
import { tr } from '../i18n/translate';
import { continueSignIn } from '../utils/continueSignIn';
import { readDeviceToken } from '../utils/deviceToken';

/** What each failure reason from the server means to the person (GA-FR-21). */
export const GOOGLE_ERROR_MESSAGES: Record<string, string> = {
  cancelled: 'You cancelled the Google sign-in.',
  expired: 'The sign-in took too long or was already used. Please try again.',
  verification: 'We could not confirm your Google account. Please try again.',
  google: 'Google did not answer in time. Please try again.',
  unavailable: 'Google sign-in is not available for this account. Please use your email and password.',
  'too-many': 'Too many Google sign-in attempts from this network. Try again in a few minutes.',
};

type Fragment = { result: string; code: string; reason: string; remember: boolean; from: string };

/**
 * GA-FR-21: reads what the server put after "#" (never sent to any server)
 * and clears it from the address bar at once, so the one-time code is not left
 * in history or a bookmark. Only in-app paths are accepted for "from".
 */
export function readGoogleFragment(hash: string): Fragment {
  const values = new URLSearchParams(hash.startsWith('#') ? hash.slice(1) : hash);
  const from = values.get('from') ?? '/';
  return {
    result: values.get('result') ?? '',
    code: values.get('code') ?? '',
    reason: values.get('reason') ?? '',
    remember: values.get('remember') !== 'false',
    from: from.startsWith('/') && !from.startsWith('//') && !from.startsWith('/\\') ? from : '/',
  };
}

/**
 * V14 (GA-FR-21): where the server sends the browser after Google answers.
 * A sign-in code becomes a session stored exactly as password sign-in stores
 * it; a link code opens the linking screen; anything else opens the error
 * screen with the reason.
 */
export function OAuthGoogleCallbackPage() {
  const navigate = useNavigate();
  const { setSession } = useAuth();
  // The code works once, so the page must not use it twice (React runs effects twice in development).
  const started = useRef(false);

  useEffect(() => {
    if (started.current) {
      return;
    }
    started.current = true;
    const fragment = readGoogleFragment(window.location.hash);
    window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search);

    const showError = (message?: string) =>
      navigate('/oauth/google/error', { replace: true, state: { message } });

    if (fragment.result === 'link' && fragment.code) {
      navigate('/oauth/google/link', {
        replace: true,
        state: { linkToken: fragment.code, remember: fragment.remember, from: fragment.from },
      });
      return;
    }
    if (fragment.result !== 'signed-in' || !fragment.code) {
      showError(GOOGLE_ERROR_MESSAGES[fragment.reason]);
      return;
    }
    exchangeGoogleCode(fragment.code, readDeviceToken())
      .then((response) => {
        // V21 (TF-FR-18): with two-factor sign-in on, Google is only the first factor.
        continueSignIn(response, { remember: fragment.remember, from: fragment.from, navigate, setSession });
      })
      .catch((error: unknown) => showError(error instanceof Error ? error.message : undefined));
  }, [navigate, setSession]);

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
        <p className="auth-status-text">{tr('Signing you in with Google…')}</p>
        <p className="auth-status-note">{tr('Please wait while we securely authenticate your account.')}</p>
      </div>
    </AuthLayout>
  );
}
