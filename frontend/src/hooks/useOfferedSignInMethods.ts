import { useEffect, useState } from 'react';
import { getOfferedSignInMethods } from '../api/securityAccessApi';
import type { OfferedSignInMethods } from '../types/securityAccess';

/**
 * V24 (SA-FR-03): the sign-in methods the server offers. One request per page
 * load. Until the answer arrives, or when it cannot be loaded, password
 * sign-in shows and Google does not, so a button that could only fail is never
 * offered (V14 D-14-4).
 */
export const DEFAULT_SIGN_IN_METHODS: OfferedSignInMethods = { password: true, google: false };

let request: Promise<OfferedSignInMethods> | null = null;

export function offeredSignInMethods(): Promise<OfferedSignInMethods> {
  request ??= Promise.resolve()
    .then(() => getOfferedSignInMethods())
    .then((answer) => ({ password: answer?.password !== false, google: answer?.google === true }))
    .catch(() => DEFAULT_SIGN_IN_METHODS);
  return request;
}

/** For tests: forget the cached answer. */
export function resetOfferedSignInMethods() {
  request = null;
}

export function useOfferedSignInMethods(): OfferedSignInMethods {
  const [methods, setMethods] = useState<OfferedSignInMethods>(DEFAULT_SIGN_IN_METHODS);
  useEffect(() => {
    let active = true;
    void offeredSignInMethods().then((value) => {
      if (active) setMethods(value);
    });
    return () => {
      active = false;
    };
  }, []);
  return methods;
}
