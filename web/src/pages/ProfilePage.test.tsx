import '@testing-library/jest-dom/vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {AuthApiError} from '../api/authClient';
import {AuthSessionStore} from '../api/authSession';
import {recruiterRepository} from '../api/repository';
import type {AvatarMetadata, GoogleConnection, RecruiterProfileDetail} from '../models/recruiter';
import {ProfilePage} from './ProfilePage';

const profile: RecruiterProfileDetail = {
  userId: 'rec-1',
  fullName: 'Mia Chen',
  avatarUrl: null,
  title: 'Head of Talent',
  bio: 'Hiring builders across engineering.',
  company: {companyId: 'company-1', name: 'Moonshot AI', logoUrl: null, verificationStatus: 'APPROVED'},
  email: 'mia@example.com',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-08-11T00:00:00Z',
};

const avatarMetadata: AvatarMetadata = {
  userId: 'rec-1',
  avatarUrl: '/api/v1/avatars/rec-1',
  contentType: 'image/png',
  sizeBytes: 42,
  updatedAt: '2026-08-16T00:00:00Z',
};

const connected: GoogleConnection = {connected: true, status: 'CONNECTED', connectedAt: '2026-08-15T01:00:00Z'};
const disconnected: GoogleConnection = {connected: false, status: 'DISCONNECTED', connectedAt: null};
const revoked: GoogleConnection = {connected: false, status: 'REVOKED', connectedAt: '2026-08-14T01:00:00Z'};
const authorizeUrl = 'https://accounts.google.com/o/oauth2/v2/auth?client_id=abc&scope=calendar';

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>();
  get length() { return this.values.size; }
  clear() { this.values.clear(); }
  getItem(key: string) { return this.values.get(key) ?? null; }
  key(index: number) { return [...this.values.keys()][index] ?? null; }
  removeItem(key: string) { this.values.delete(key); }
  setItem(key: string, value: string) { this.values.set(key, value); }
}

function renderPage(options: {
  path?: string;
  redirect?: (url: string) => void;
  sessions?: AuthSessionStore;
} = {}) {
  const {path = '/recruiter/profile', redirect = () => {}, sessions} = options;
  const client = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
  const router = createMemoryRouter(
    [{path: '/recruiter/profile', element: <ProfilePage redirect={redirect} sessions={sessions}/>}],
    {initialEntries: [path]},
  );
  const view = render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>);
  return {router, client, ...view};
}

const fileInput = () => document.querySelector('input[type="file"]') as HTMLInputElement;

function selectFile(file: File) {
  fireEvent.change(fileInput(), {target: {files: [file]}});
}

describe('ProfilePage', () => {
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:mock-url') as typeof URL.createObjectURL;
    URL.revokeObjectURL = vi.fn(() => {}) as typeof URL.revokeObjectURL;
  });

  afterEach(() => {cleanup(); vi.restoreAllMocks();});

  it('loads and displays recruiter profile with read-only fields', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    renderPage();
    expect(screen.getByText('Loading your profile…')).toBeInTheDocument();
    expect(await screen.findByDisplayValue('Mia Chen')).toBeInTheDocument();
    expect((await screen.findAllByText('Moonshot AI')).length).toBeGreaterThan(0);
    expect(screen.getByText('mia@example.com')).toBeInTheDocument();
    expect(screen.getByText('Registered')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Head of Talent')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Hiring builders across engineering.')).toBeInTheDocument();
  });

  it('does not render a writable avatar URL field', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    renderPage();
    await screen.findByDisplayValue('Mia Chen');
    expect(screen.queryByLabelText(/avatar url/i)).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/example\.com/)).not.toBeInTheDocument();
  });

  it('validates required fields before saving', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    const update = vi.spyOn(recruiterRepository, 'updateRecruiterProfile');
    renderPage();
    await screen.findByDisplayValue('Mia Chen');
    fireEvent.change(screen.getByDisplayValue('Mia Chen'), {target: {value: ''}});
    fireEvent.change(screen.getByDisplayValue('Head of Talent'), {target: {value: '  '}});
    fireEvent.click(screen.getByRole('button', {name: 'Save profile'}));
    expect(await screen.findByText('Full name is required.')).toBeInTheDocument();
    expect(screen.getByText('Title is required.')).toBeInTheDocument();
    expect(update).not.toHaveBeenCalled();
  });

  it('saves successfully without sending an avatarUrl', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    const updated = {...profile, fullName: 'Mia Chen Updated', title: 'VP Talent', updatedAt: '2026-08-11T01:00:00Z'};
    const update = vi.spyOn(recruiterRepository, 'updateRecruiterProfile').mockResolvedValue(updated);
    renderPage();
    await screen.findByDisplayValue('Mia Chen');
    fireEvent.change(screen.getByDisplayValue('Mia Chen'), {target: {value: ' Mia Chen Updated '}});
    fireEvent.change(screen.getByDisplayValue('Head of Talent'), {target: {value: ' VP Talent '}});
    fireEvent.click(screen.getByRole('button', {name: 'Save profile'}));
    await waitFor(() => expect(update).toHaveBeenCalledWith({
      fullName: 'Mia Chen Updated', title: 'VP Talent', bio: 'Hiring builders across engineering.',
    }));
    expect(await screen.findByText('Profile saved')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Mia Chen Updated')).toBeInTheDocument();
  });

  it('maps server field errors and keeps the input', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    const update = vi.spyOn(recruiterRepository, 'updateRecruiterProfile')
      .mockRejectedValue(new AuthApiError(422, 'VALIDATION_ERROR', 'raw', {title: 'Server title error'}, 'req-1'));
    renderPage();
    await screen.findByDisplayValue('Mia Chen');
    fireEvent.change(screen.getByDisplayValue('Head of Talent'), {target: {value: 'Senior Talent'}});
    fireEvent.click(screen.getByRole('button', {name: 'Save profile'}));
    expect(await screen.findByText('Server title error')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Senior Talent')).toBeInTheDocument();
    expect(screen.queryByText('raw')).not.toBeInTheDocument();
    expect(update).toHaveBeenCalledTimes(1);
  });

  it('shows a safe network failure and preserves entered values', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    vi.spyOn(recruiterRepository, 'updateRecruiterProfile')
      .mockRejectedValue(new AuthApiError(0, 'NETWORK_ERROR', 'private network detail'));
    renderPage();
    await screen.findByDisplayValue('Mia Chen');
    fireEvent.change(screen.getByDisplayValue('Head of Talent'), {target: {value: 'Still Here'}});
    fireEvent.click(screen.getByRole('button', {name: 'Save profile'}));
    expect(await screen.findByText('Unable to reach the server. Check your connection and try again.')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Still Here')).toBeInTheDocument();
    expect(screen.queryByText('private network detail')).not.toBeInTheDocument();
  });

  it('uploads a PNG, shows a preview, and reflects the new avatar', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile')
      .mockResolvedValueOnce(profile)
      .mockResolvedValue({...profile, avatarUrl: '/api/v1/avatars/rec-1'});
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    const upload = vi.spyOn(recruiterRepository, 'uploadAvatar').mockResolvedValue(avatarMetadata);
    renderPage();
    await screen.findByDisplayValue('Mia Chen');
    const file = new File(['abc'], 'avatar.png', {type: 'image/png'});
    selectFile(file);
    expect(screen.getByAltText('Avatar preview')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Upload'}));
    expect(await screen.findByText('Avatar updated')).toBeInTheDocument();
    expect(upload).toHaveBeenCalledWith(file);
    await waitFor(() => expect(document.querySelector('img.profile-avatar'))
      .toHaveAttribute('src', '/api/v1/avatars/rec-1'));
  });

  it('updates the stored session avatar URL after a successful upload', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    vi.spyOn(recruiterRepository, 'uploadAvatar').mockResolvedValue(avatarMetadata);
    const sessions = new AuthSessionStore(new MemoryStorage(), new MemoryStorage());
    sessions.save({
      accessToken: 'a', refreshToken: 'r', accessTokenExpiresAt: 10_000, refreshTokenExpiresAt: 20_000, remember: false,
      user: {
        userId: 'rec-1', role: 'RECRUITER', fullName: 'Mia Chen', email: 'mia@example.com', avatarUrl: null,
        company: {companyId: 'company-1', name: 'Moonshot AI'},
        createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-08-11T00:00:00Z',
      },
    });
    renderPage({sessions});
    await screen.findByDisplayValue('Mia Chen');
    selectFile(new File(['abc'], 'avatar.png', {type: 'image/png'}));
    fireEvent.click(screen.getByRole('button', {name: 'Upload'}));
    await waitFor(() => expect(sessions.getSnapshot()?.user.avatarUrl).toBe('/api/v1/avatars/rec-1'));
  });

  it('shows a safe message when the upload is rejected by the server', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    vi.spyOn(recruiterRepository, 'uploadAvatar')
      .mockRejectedValue(new AuthApiError(422, 'VALIDATION_ERROR', 'raw server detail'));
    renderPage();
    await screen.findByDisplayValue('Mia Chen');
    selectFile(new File(['abc'], 'avatar.png', {type: 'image/png'}));
    fireEvent.click(screen.getByRole('button', {name: 'Upload'}));
    expect(await screen.findByText('That image could not be used. Please choose a PNG or JPEG under 5 MB.')).toBeInTheDocument();
    expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
  });

  it('removes an existing avatar and falls back to initials', async () => {
    const withAvatar = {...profile, avatarUrl: '/api/v1/avatars/rec-1'};
    vi.spyOn(recruiterRepository, 'getRecruiterProfile')
      .mockResolvedValueOnce(withAvatar)
      .mockResolvedValue({...profile, avatarUrl: null});
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    const remove = vi.spyOn(recruiterRepository, 'deleteAvatar').mockResolvedValue(undefined);
    renderPage();
    await screen.findByDisplayValue('Mia Chen');
    expect(document.querySelector('img.profile-avatar')).toHaveAttribute('src', '/api/v1/avatars/rec-1');
    fireEvent.click(screen.getByRole('button', {name: 'Remove'}));
    expect(await screen.findByText('Avatar removed')).toBeInTheDocument();
    expect(remove).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(document.querySelector('img.profile-avatar')).not.toBeInTheDocument());
    expect(screen.getByText('MC')).toBeInTheDocument();
  });

  it('rejects a non-image file client-side without uploading', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    const upload = vi.spyOn(recruiterRepository, 'uploadAvatar');
    renderPage();
    await screen.findByDisplayValue('Mia Chen');
    selectFile(new File(['<svg/>'], 'avatar.svg', {type: 'image/svg+xml'}));
    expect(await screen.findByText('Please choose a PNG or JPEG image.')).toBeInTheDocument();
    expect(upload).not.toHaveBeenCalled();
  });

  it('rejects an oversized file client-side without uploading', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    const upload = vi.spyOn(recruiterRepository, 'uploadAvatar');
    renderPage();
    await screen.findByDisplayValue('Mia Chen');
    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.png', {type: 'image/png'});
    selectFile(big);
    expect(await screen.findByText('The image must be 5 MB or smaller.')).toBeInTheDocument();
    expect(upload).not.toHaveBeenCalled();
  });

  it('shows the connected Google state with a Disconnect action', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(connected);
    vi.spyOn(recruiterRepository, 'disconnectGoogle').mockResolvedValue(undefined);
    renderPage();
    expect(await screen.findByText('Connected')).toBeInTheDocument();
    expect(screen.getByText(/Connected since/)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Disconnect'})).toBeInTheDocument();
  });

  it('connects from disconnected and redirects to the fixed Google authorization URL', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    vi.spyOn(recruiterRepository, 'beginGoogleConnection').mockResolvedValue({authorizationUrl: authorizeUrl});
    const redirect = vi.fn();
    renderPage({redirect});
    expect(await screen.findByText('Disconnected')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Connect Google Calendar'}));
    await waitFor(() => expect(redirect).toHaveBeenCalledWith(authorizeUrl));
  });

  it('presents a revoked connection as expired and offers reconnect', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(revoked);
    renderPage();
    expect(await screen.findByText('Authorization expired')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Reconnect Google'})).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Disconnect'})).not.toBeInTheDocument();
  });

  it('shows the OAuth callback notice once and clears the query', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    const {router} = renderPage({path: '/recruiter/profile?googleOAuth=connected'});
    expect(await screen.findByText('Successfully connected to Google.')).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.search).toBe(''));
  });
});
