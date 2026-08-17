import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {act, renderHook} from '@testing-library/react';
import type {ReactNode} from 'react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import type {AvatarMetadata, RecruiterProfileDetail} from '../models/recruiter';
import {keys, useDeleteAvatar, useUploadAvatar} from './queries';
import {recruiterRepository} from './repository';

const profile: RecruiterProfileDetail = {
  userId: 'rec-1',
  fullName: 'Mia Chen',
  avatarUrl: null,
  title: 'Head of Talent',
  bio: 'Hiring builders.',
  company: {companyId: 'company-1', name: 'Moonshot AI', logoUrl: null, verificationStatus: 'APPROVED'},
  email: 'mia@example.com',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-08-11T00:00:00Z',
};

const metadata: AvatarMetadata = {
  userId: 'rec-1',
  avatarUrl: '/api/v1/avatars/rec-1',
  contentType: 'image/png',
  sizeBytes: 42,
  updatedAt: '2026-08-16T00:00:00Z',
};

function wrapper(client: QueryClient) {
  return ({children}: {children: ReactNode}) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
}

describe('avatar queries', () => {
  afterEach(() => vi.restoreAllMocks());

  it('uploadAvatar writes the returned URL into the recruiter profile cache', async () => {
    const client = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
    client.setQueryData(keys.recruiterProfile, profile);
    const upload = vi.spyOn(recruiterRepository, 'uploadAvatar').mockResolvedValue(metadata);
    const file = new File(['x'], 'avatar.png', {type: 'image/png'});

    const {result} = renderHook(() => useUploadAvatar(), {wrapper: wrapper(client)});
    await act(async () => { await result.current.mutateAsync(file); });

    expect(upload).toHaveBeenCalledWith(file);
    expect(client.getQueryData<RecruiterProfileDetail>(keys.recruiterProfile)?.avatarUrl)
      .toBe('/api/v1/avatars/rec-1');
  });

  it('deleteAvatar clears the avatar URL in the recruiter profile cache', async () => {
    const client = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
    client.setQueryData(keys.recruiterProfile, {...profile, avatarUrl: '/api/v1/avatars/rec-1'});
    const remove = vi.spyOn(recruiterRepository, 'deleteAvatar').mockResolvedValue(undefined);

    const {result} = renderHook(() => useDeleteAvatar(), {wrapper: wrapper(client)});
    await act(async () => { await result.current.mutateAsync(); });

    expect(remove).toHaveBeenCalledTimes(1);
    expect(client.getQueryData<RecruiterProfileDetail>(keys.recruiterProfile)?.avatarUrl).toBeNull();
  });
});
