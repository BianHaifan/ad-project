import '@testing-library/jest-dom/vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {AuthApiError} from '../api/authClient';
import {recruiterRepository} from '../api/repository';
import type {RecruiterProfileDetail} from '../models/recruiter';
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

function renderPage() {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
  render(<QueryClientProvider client={client}><ProfilePage/></QueryClientProvider>);
  return client;
}

describe('ProfilePage', () => {
  afterEach(() => {cleanup(); vi.restoreAllMocks();});

  it('loads and displays recruiter profile with read-only fields', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    renderPage();
    expect(screen.getByText('Loading your profile…')).toBeInTheDocument();
    expect(await screen.findByDisplayValue('Mia Chen')).toBeInTheDocument();
    expect((await screen.findAllByText('Moonshot AI')).length).toBeGreaterThan(0);
    expect(screen.getByText('mia@example.com')).toBeInTheDocument();
    expect(screen.getByText('Registered')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Head of Talent')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Hiring builders across engineering.')).toBeInTheDocument();
  });

  it('validates required fields before saving', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
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

  it('saves successfully and shows a confirmation', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
    const updated = {...profile, fullName: 'Mia Chen Updated', title: 'VP Talent', updatedAt: '2026-08-11T01:00:00Z'};
    const update = vi.spyOn(recruiterRepository, 'updateRecruiterProfile').mockResolvedValue(updated);
    renderPage();
    await screen.findByDisplayValue('Mia Chen');
    fireEvent.change(screen.getByDisplayValue('Mia Chen'), {target: {value: ' Mia Chen Updated '}});
    fireEvent.change(screen.getByDisplayValue('Head of Talent'), {target: {value: ' VP Talent '}});
    fireEvent.click(screen.getByRole('button', {name: 'Save profile'}));
    await waitFor(() => expect(update).toHaveBeenCalledWith({fullName: 'Mia Chen Updated', title: 'VP Talent', bio: 'Hiring builders across engineering.', avatarUrl: null}));
    expect(await screen.findByText('Profile saved')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Mia Chen Updated')).toBeInTheDocument();
  });

  it('maps server field errors and keeps the input', async () => {
    vi.spyOn(recruiterRepository, 'getRecruiterProfile').mockResolvedValue(profile);
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
});
