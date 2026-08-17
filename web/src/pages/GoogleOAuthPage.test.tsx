import '@testing-library/jest-dom/vitest';
import {cleanup, render, waitFor} from '@testing-library/react';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {afterEach, describe, expect, it} from 'vitest';
import {GoogleOAuthPage} from './GoogleOAuthPage';

function renderRelay(path = '/recruiter/google-oauth') {
  const router = createMemoryRouter(
    [
      {path: '/recruiter/google-oauth', element: <GoogleOAuthPage/>},
      {path: '/recruiter/profile', element: <div>Profile page</div>},
    ],
    {initialEntries: [path]},
  );
  render(<RouterProvider router={router}/>);
  return {router};
}

describe('GoogleOAuthPage (callback relay)', () => {
  afterEach(cleanup);

  it.each([
    ['connected', '?googleOAuth=connected'],
    ['denied', '?googleOAuth=denied'],
    ['failed', '?googleOAuth=failed'],
  ])('forwards the safe "%s" result to the profile page', async (value, query) => {
    const {router} = renderRelay(`/recruiter/google-oauth${query}`);
    await waitFor(() => expect(router.state.location.pathname).toBe('/recruiter/profile'));
    expect(router.state.location.search).toBe(`?googleOAuth=${value}`);
  });

  it('drops unknown params, OAuth codes, and state and lands clean on profile', async () => {
    const {router} = renderRelay('/recruiter/google-oauth?googleOAuth=success&code=abc&state=xyz');
    await waitFor(() => expect(router.state.location.pathname).toBe('/recruiter/profile'));
    expect(router.state.location.search).toBe('');
  });

  it('redirects a direct visit to the old integrations path to profile with no query', async () => {
    const {router} = renderRelay('/recruiter/google-oauth');
    await waitFor(() => expect(router.state.location.pathname).toBe('/recruiter/profile'));
    expect(router.state.location.search).toBe('');
  });
});
