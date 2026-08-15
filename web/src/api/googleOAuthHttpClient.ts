import type {AuthClient} from './authClient';
import {authClient, AuthApiError} from './authClient';
import {apiPaths} from './contract';
import type {GoogleAuthorizeResponse, GoogleConnection, GoogleConnectionStatus} from '../models/recruiter';

export class GoogleOAuthHttpClient {
  constructor(private readonly client: Pick<AuthClient, 'requestWithAuth'> = authClient) {}

  async beginConnection(): Promise<GoogleAuthorizeResponse> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.googleOAuthAuthorize, {method: 'POST'});
    return parseAuthorizeEnvelope(payload);
  }

  async getConnection(): Promise<GoogleConnection> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.googleOAuthStatus);
    return parseConnectionEnvelope(payload);
  }

  async disconnect(): Promise<void> {
    await this.client.requestWithAuth<unknown>(apiPaths.googleOAuth, {method: 'DELETE'});
  }
}

function parseAuthorizeEnvelope(payload: unknown): GoogleAuthorizeResponse {
  if (!isRecord(payload) || !isRecord(payload.data) || typeof payload.data.authorizationUrl !== 'string') {
    throw unexpectedResponse();
  }
  return {authorizationUrl: payload.data.authorizationUrl};
}

function parseConnectionEnvelope(payload: unknown): GoogleConnection {
  if (!isRecord(payload) || !isRecord(payload.data) || typeof payload.data.connected !== 'boolean' ||
      !isConnectionStatus(payload.data.status) ||
      !(payload.data.connectedAt === null || typeof payload.data.connectedAt === 'string')) {
    throw unexpectedResponse();
  }
  return {
    connected: payload.data.connected,
    status: payload.data.status,
    connectedAt: payload.data.connectedAt,
  };
}

function isConnectionStatus(value: unknown): value is GoogleConnectionStatus {
  return value === 'CONNECTED' || value === 'DISCONNECTED' || value === 'REVOKED';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function unexpectedResponse(): AuthApiError {
  return new AuthApiError(0, 'UNEXPECTED_RESPONSE', 'The server returned an unexpected response.');
}

export const googleOAuthHttpClient = new GoogleOAuthHttpClient();
