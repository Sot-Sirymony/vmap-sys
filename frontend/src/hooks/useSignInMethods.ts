import { useEffect, useState } from 'react';
import { getSignInMethods, type SignInMethods } from '../api/oauthApi';

/**
 * V14 (GA-FR-26, GA-FR-27): how the signed-in person can sign in. Null until
 * the answer arrives, or when it cannot be loaded; callers then behave as for
 * an account with a password, which is every account made before V14.
 */
export function useSignInMethods(token: string | null) {
  const [methods, setMethods] = useState<SignInMethods | null>(null);

  useEffect(() => {
    if (!token) {
      return;
    }
    let active = true;
    getSignInMethods(token)
      .then((value) => {
        if (active) setMethods(value);
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [token]);

  return { methods, setMethods };
}
