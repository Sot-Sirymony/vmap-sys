/**
 * V10 (SEC-01-10): a link or image address from data (a user, an admin or the
 * AI) is used only when it is a plain web address. Anything else, such as
 * `javascript:` or `data:`, comes back as undefined so the caller shows plain
 * text instead of a link. The server validates these fields too; this guards
 * against older data and any path that skipped validation.
 */
export function safeHttpUrl(value: string | null | undefined): string | undefined {
  if (!value) {
    return undefined;
  }
  try {
    const url = new URL(value.trim());
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : undefined;
  } catch {
    return undefined;
  }
}
