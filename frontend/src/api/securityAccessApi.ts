import { apiClient } from './apiClient';
import { stepUpHeader } from './stepUpApi';
import type { OfferedSignInMethods, SecurityAccess, SecurityAccessInput } from '../types/securityAccess';

/** V24 (BRD 24.0.0, SA-FR-02, SA-FR-03, SA-FR-05): the sign-in methods an Admin allows. */

/** SA-FR-03: public; which methods the Sign In, Sign Up and invitation pages offer. */
export function getOfferedSignInMethods() {
  return apiClient<OfferedSignInMethods>('/auth/sign-in-methods');
}

export function getSecurityAccess(token: string) {
  return apiClient<SecurityAccess>('/admin/security-access', { token });
}

export function updateSecurityAccess(token: string, input: SecurityAccessInput, stepUp?: number) {
  return apiClient<SecurityAccess>('/admin/security-access', {
    method: 'PUT', token, headers: stepUpHeader(stepUp), body: JSON.stringify(input),
  });
}
