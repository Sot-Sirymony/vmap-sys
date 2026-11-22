/**
 * V21 (TF-FR-21, TF-FR-25): the token of a browser remembered after a second
 * step. It is sent only in the body of a sign-in request, never in a URL. The
 * server ties it to one account and ignores it for any other.
 */
const DEVICE_TOKEN_KEY = 'vms.deviceToken';

export function readDeviceToken(): string | undefined {
  try {
    return localStorage.getItem(DEVICE_TOKEN_KEY) ?? undefined;
  } catch {
    return undefined;
  }
}

export function saveDeviceToken(token: string | undefined) {
  if (!token) return;
  try {
    localStorage.setItem(DEVICE_TOKEN_KEY, token);
  } catch {
    // Private windows may refuse storage; the second step is simply asked again.
  }
}
