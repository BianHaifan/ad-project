import '@testing-library/jest-dom/vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {AuthApiError} from '../api/authClient';
import {recruiterRepository} from '../api/repository';
import type {GoogleAuthorizeResponse, GoogleConnection} from '../models/recruiter';
import {GoogleOAuthPage} from './GoogleOAuthPage';

const connected: GoogleConnection = {connected: true, status: 'CONNECTED', connectedAt: '2026-08-15T01:00:00Z'};
const disconnected: GoogleConnection = {connected: false, status: 'DISCONNECTED', connectedAt: null};
const revoked: GoogleConnection = {connected: false, status: 'REVOKED', connectedAt: '2026-08-14T01:00:00Z'};
const authorizeUrl = 'https://accounts.google.com/o/oauth2/v2/auth?client_id=abc&scope=calendar';

function renderPage(path = '/recruiter/google-oauth', redirect: (url: string) => void = () => {}) {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
  const router = createMemoryRouter(
    [{path: '/recruiter/google-oauth', element: <GoogleOAuthPage redirect={redirect}/>}],
    {initialEntries: [path]},
  );
  const view = render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>);
  return {router, client, ...view};
}

describe('GoogleOAuthPage', () => {
  afterEach(() => {cleanup(); vi.restoreAllMocks();});

  it('shows a loading state while reading the connection', () => {
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockReturnValue(new Promise<GoogleConnection>(() => {}));
    renderPage();
    expect(screen.getByText('Loading Google connection…')).toBeInTheDocument();
  });

  it('shows a safe error state when the connection cannot be read', async () => {
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockRejectedValue(new AuthApiError(0, 'NETWORK_ERROR', 'private detail'));
    renderPage();
    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.queryByText('private detail')).not.toBeInTheDocument();
  });

  it('connects from disconnected and redirects to the fixed Google authorization URL', async () => {
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    vi.spyOn(recruiterRepository, 'beginGoogleConnection').mockResolvedValue({authorizationUrl: authorizeUrl});
    const redirect = vi.fn();
    renderPage('/recruiter/google-oauth', redirect);
    expect(await screen.findByText('Disconnected')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Connect Google Calendar'}));
    await waitFor(() => expect(redirect).toHaveBeenCalledWith(authorizeUrl));
  });

  it('shows the connected state with its time and disconnects back to disconnected', async () => {
    vi.spyOn(recruiterRepository, 'getGoogleConnection')
      .mockResolvedValueOnce(connected)
      .mockResolvedValue(disconnected);
    vi.spyOn(recruiterRepository, 'disconnectGoogle').mockResolvedValue(undefined);
    renderPage();
    expect(await screen.findByText('Connected')).toBeInTheDocument();
    expect(screen.getByText(/Connected since/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Disconnect'}));
    await waitFor(() => expect(screen.getByText('Disconnected')).toBeInTheDocument());
  });

  it('presents a revoked connection as expired and offers only reconnect', async () => {
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(revoked);
    vi.spyOn(recruiterRepository, 'beginGoogleConnection').mockResolvedValue({authorizationUrl: authorizeUrl});
    const redirect = vi.fn();
    renderPage('/recruiter/google-oauth', redirect);
    expect(await screen.findByText('Authorization expired')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Reconnect Google'})).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Disconnect'})).not.toBeInTheDocument();
    expect(screen.queryByText('Connected')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Reconnect Google'}));
    await waitFor(() => expect(redirect).toHaveBeenCalledWith(authorizeUrl));
  });

  it.each([
    ['connected', 'Successfully connected to Google.'],
    ['denied', 'You cancelled the Google authorization.'],
    ['failed', "The connection wasn't completed. You can try again."],
  ])('shows the "%s" callback notice and clears the URL', async (value, message) => {
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    const {router} = renderPage(`/recruiter/google-oauth?googleOAuth=${value}`);
    expect(await screen.findByText(message)).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.search).toBe(''));
  });

  it('keeps the connected notice but shows a safe error state when the status re-read fails', async () => {
    vi.spyOn(recruiterRepository, 'getGoogleConnection')
      .mockRejectedValue(new AuthApiError(0, 'NETWORK_ERROR', 'private failure'));
    const {router} = renderPage('/recruiter/google-oauth?googleOAuth=connected');
    expect(await screen.findByText('Successfully connected to Google.')).toBeInTheDocument();
    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.queryByText('Connected')).not.toBeInTheDocument();
    expect(screen.queryByText('Disconnected')).not.toBeInTheDocument();
    expect(screen.queryByText('private failure')).not.toBeInTheDocument();
    await waitFor(() => expect(router.state.location.search).toBe(''));
  });

  it('ignores an unknown googleOAuth value and clears the URL without a notice', async () => {
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    const {router} = renderPage('/recruiter/google-oauth?googleOAuth=success&unrelated=ignored');
    expect(await screen.findByText('Disconnected')).toBeInTheDocument();
    expect(screen.queryByText(/Successfully connected/)).not.toBeInTheDocument();
    expect(screen.queryByText(/cancelled/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/wasn't completed/i)).not.toBeInTheDocument();
    await waitFor(() => expect(router.state.location.search).toBe(''));
  });

  it('shows a safe demo-configuration message when Google OAuth is not configured', async () => {
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    vi.spyOn(recruiterRepository, 'beginGoogleConnection')
      .mockRejectedValue(new AuthApiError(503, 'GOOGLE_OAUTH_NOT_CONFIGURED', 'private detail'));
    renderPage();
    expect(await screen.findByText('Disconnected')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Connect Google Calendar'}));
    expect(await screen.findByRole('alert')).toHaveTextContent(/not configured/i);
    expect(screen.queryByText('private detail')).not.toBeInTheDocument();
  });

  it('does not redirect when the authorization URL is invalid', async () => {
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    vi.spyOn(recruiterRepository, 'beginGoogleConnection')
      .mockResolvedValue({authorizationUrl: 'https://evil.example.com/o/oauth2/v2/auth'});
    const redirect = vi.fn();
    renderPage('/recruiter/google-oauth', redirect);
    expect(await screen.findByText('Disconnected')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Connect Google Calendar'}));
    expect(await screen.findByRole('alert')).toHaveTextContent(/unable to start/i);
    expect(redirect).not.toHaveBeenCalled();
  });

  it('disables the connect button while a connection is starting', async () => {
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    const begin = vi.spyOn(recruiterRepository, 'beginGoogleConnection')
      .mockReturnValue(new Promise<GoogleAuthorizeResponse>(() => {}));
    renderPage();
    fireEvent.click(await screen.findByRole('button', {name: 'Connect Google Calendar'}));
    const pending = await screen.findByRole('button', {name: 'Connecting…'});
    expect(pending).toBeDisabled();
    fireEvent.click(pending);
    expect(begin).toHaveBeenCalledTimes(1);
  });
});
