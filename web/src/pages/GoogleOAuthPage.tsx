import {Navigate, useSearchParams} from 'react-router-dom';
import {parseOAuthCallbackResult} from '../lib/googleOAuth';

// The Google OAuth backend redirects back to `/recruiter/google-oauth` with a single safe
// `googleOAuth` query value. This page is now only a relay: it forwards one of the three
// known results to the profile page (which shows the notice and clears the query) and drops
// everything else — unknown values, OAuth codes, state, and raw errors are never forwarded.
export function GoogleOAuthPage() {
  const [searchParams] = useSearchParams();
  const result = parseOAuthCallbackResult(searchParams.get('googleOAuth'));
  const target = result
    ? `/recruiter/profile?googleOAuth=${encodeURIComponent(result)}`
    : '/recruiter/profile';
  return <Navigate to={target} replace/>;
}
