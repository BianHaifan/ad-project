// Client-side guards for the recruiter Google OAuth handoff. The backend callback
// redirects back with a single safe `googleOAuth` query parameter and the authorize
// endpoint returns a fixed Google URL; both are validated here before any navigation
// or notice is rendered so a malformed or unexpected value can never reach the user.

export type GoogleOAuthCallbackResult = 'connected' | 'denied' | 'failed';

const CALLBACK_RESULTS: ReadonlySet<string> = new Set(['connected', 'denied', 'failed']);

// Returns the only three safe callback results, or null for anything else. Unknown
// values are ignored entirely and never rendered or forwarded.
export function parseOAuthCallbackResult(value: string | null | undefined): GoogleOAuthCallbackResult | null {
  if (value === null || value === undefined) return null;
  return CALLBACK_RESULTS.has(value) ? (value as GoogleOAuthCallbackResult) : null;
}

// True only for the fixed Google authorization endpoint. The host, scheme, and path are
// all pinned so an unexpected URL (for example a client-injected redirect) is rejected.
export function isGoogleAuthorizationUrl(value: string): boolean {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return false;
  }
  return url.protocol === 'https:' &&
    url.hostname === 'accounts.google.com' &&
    url.pathname === '/o/oauth2/v2/auth' &&
    url.port === '';
}
