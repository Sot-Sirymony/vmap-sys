import type { NavigateFunction } from 'react-router';
import { isTwoFactorChallenge, type AuthResponse, type SignInAnswer } from '../types/auth';

/**
 * V21 (TF-FR-10, TF-FR-18): what every sign-in page does with the server's
 * answer: open the session, or go to the second step and come back to `from`.
 */
export function continueSignIn(
  answer: SignInAnswer,
  options: {
    remember: boolean;
    from: string;
    navigate: NavigateFunction;
    setSession: (response: AuthResponse, remember?: boolean) => void;
  },
) {
  if (isTwoFactorChallenge(answer)) {
    options.navigate('/sign-in/two-factor', {
      replace: true,
      state: { challenge: answer, remember: options.remember, from: options.from },
    });
    return;
  }
  options.setSession(answer, options.remember);
  options.navigate(options.from, { replace: true });
}
