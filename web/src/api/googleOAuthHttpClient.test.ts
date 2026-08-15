import {describe, expect, it, vi} from 'vitest';
import {AuthApiError, type AuthClient} from './authClient';
import {GoogleOAuthHttpClient} from './googleOAuthHttpClient';
import type {GoogleConnection} from '../models/recruiter';

function setup(result: unknown) {
  const requestWithAuth = vi.fn().mockResolvedValue(result);
  return {requestWithAuth, client: new GoogleOAuthHttpClient({requestWithAuth} as Pick<AuthClient, 'requestWithAuth'>)};
}

describe('GoogleOAuthHttpClient', () => {
  it('begins a connection and parses the authorization URL', async () => {
    const {client, requestWithAuth} = setup({data: {authorizationUrl: 'https://accounts.google.com/o/oauth2/v2/auth'}});
    await expect(client.beginConnection()).resolves.toEqual({authorizationUrl: 'https://accounts.google.com/o/oauth2/v2/auth'});
    expect(requestWithAuth).toHaveBeenCalledWith('/recruiter/google-oauth/authorize', {method: 'POST'});
  });

  it('rejects a malformed authorize envelope', async () => {
    await expect(setup({data: {}}).client.beginConnection()).rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
    await expect(setup({data: {authorizationUrl: 42}}).client.beginConnection()).rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
  });

  it('parses all three legal connection statuses including REVOKED', async () => {
    const fixtures: GoogleConnection[] = [
      {connected: true, status: 'CONNECTED', connectedAt: '2026-08-15T01:00:00Z'},
      {connected: false, status: 'DISCONNECTED', connectedAt: null},
      {connected: false, status: 'REVOKED', connectedAt: '2026-08-14T01:00:00Z'},
    ];
    for (const fixture of fixtures) {
      const {client} = setup({data: fixture});
      await expect(client.getConnection()).resolves.toEqual(fixture);
    }
  });

  it('rejects an unknown status instead of treating it as a valid response', async () => {
    await expect(setup({data: {connected: true, status: 'BROKEN', connectedAt: null}}).client.getConnection())
      .rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
  });

  it('rejects a malformed connection envelope', async () => {
    await expect(setup({data: {connected: 'yes', status: 'CONNECTED', connectedAt: null}}).client.getConnection())
      .rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
    await expect(setup({data: {connected: true, status: 'CONNECTED', connectedAt: 123}}).client.getConnection())
      .rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
  });

  it('disconnects through the DELETE endpoint', async () => {
    const {client, requestWithAuth} = setup(undefined);
    await expect(client.disconnect()).resolves.toBeUndefined();
    expect(requestWithAuth).toHaveBeenCalledWith('/recruiter/google-oauth', {method: 'DELETE'});
  });

  it('preserves safe failures from the authenticated client', async () => {
    const requestWithAuth = vi.fn().mockRejectedValue(new AuthApiError(503, 'GOOGLE_OAUTH_NOT_CONFIGURED', 'private detail'));
    const client = new GoogleOAuthHttpClient({requestWithAuth} as Pick<AuthClient, 'requestWithAuth'>);
    await expect(client.beginConnection()).rejects.toMatchObject({status: 503, code: 'GOOGLE_OAUTH_NOT_CONFIGURED'});
  });
});
