import {describe, expect, it, vi} from 'vitest';
import type {AuthClient} from './authClient';
import {AvatarHttpClient} from './avatarHttpClient';

const metadata = {
  userId: 'rec-1',
  avatarUrl: '/api/v1/avatars/rec-1',
  contentType: 'image/png' as const,
  sizeBytes: 42,
  updatedAt: '2026-08-16T00:00:00Z',
};

type AvatarAuthClient = Pick<AuthClient, 'requestWithAuth' | 'requestWithAuthForm'>;

function setup() {
  const requestWithAuth = vi.fn();
  const requestWithAuthForm = vi.fn();
  const client = new AvatarHttpClient({requestWithAuth, requestWithAuthForm} as unknown as AvatarAuthClient);
  return {client, requestWithAuth, requestWithAuthForm};
}

describe('AvatarHttpClient', () => {
  it('uploads a file through multipart and parses the envelope', async () => {
    const {client, requestWithAuthForm} = setup();
    requestWithAuthForm.mockResolvedValue({data: metadata});
    const file = new File(['x'], 'avatar.png', {type: 'image/png'});
    await expect(client.upload(file)).resolves.toEqual(metadata);
    expect(requestWithAuthForm).toHaveBeenCalledTimes(1);
    const [path, formData] = requestWithAuthForm.mock.calls[0] as [string, FormData];
    expect(path).toBe('/profile/avatar');
    expect(formData.get('file')).toBe(file);
  });

  it('deletes through the DELETE endpoint', async () => {
    const {client, requestWithAuth} = setup();
    requestWithAuth.mockResolvedValue(undefined);
    await expect(client.delete()).resolves.toBeUndefined();
    expect(requestWithAuth).toHaveBeenCalledWith('/profile/avatar', {method: 'DELETE'});
  });

  it('rejects a malformed avatar envelope', async () => {
    const {client, requestWithAuthForm} = setup();
    requestWithAuthForm.mockResolvedValue({data: {userId: 'rec-1'}});
    const file = new File(['x'], 'a.png', {type: 'image/png'});
    await expect(client.upload(file)).rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
  });

  it('rejects an unexpected content type', async () => {
    const {client, requestWithAuthForm} = setup();
    requestWithAuthForm.mockResolvedValue({data: {...metadata, contentType: 'image/svg+xml'}});
    const file = new File(['x'], 'a.png', {type: 'image/png'});
    await expect(client.upload(file)).rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
  });
});
