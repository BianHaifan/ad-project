import {describe, expect, it, vi} from 'vitest';
import type {AuthClient} from './authClient';
import {RecruiterProfileHttpClient} from './recruiterProfileHttpClient';
import type {RecruiterProfileDetail} from '../models/recruiter';

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

function setup(result: unknown) {
  const requestWithAuth = vi.fn().mockResolvedValue(result);
  return {requestWithAuth, client: new RecruiterProfileHttpClient({requestWithAuth} as Pick<AuthClient, 'requestWithAuth'>)};
}

describe('RecruiterProfileHttpClient', () => {
  it('loads and parses the recruiter profile envelope', async () => {
    const {client, requestWithAuth} = setup({data: profile});
    await expect(client.getProfile()).resolves.toEqual(profile);
    expect(requestWithAuth).toHaveBeenCalledWith('/recruiter/profile');
  });

  it('updates only editable profile fields through PATCH', async () => {
    const updated = {...profile, title: 'VP Talent', fullName: 'Mia Chen Updated'};
    const {client, requestWithAuth} = setup({data: updated});
    await expect(client.updateProfile({fullName: 'Mia Chen Updated', title: 'VP Talent', bio: null, avatarUrl: null}))
      .resolves.toEqual(updated);
    const [, init] = requestWithAuth.mock.calls[0] as [string, RequestInit];
    expect(init.method).toBe('PATCH');
    expect(JSON.parse(String(init.body))).toEqual({fullName: 'Mia Chen Updated', title: 'VP Talent', bio: null, avatarUrl: null});
  });

  it('rejects malformed profile responses', async () => {
    const {client} = setup({data: {userId: 'broken'}});
    await expect(client.getProfile()).rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
  });
});
