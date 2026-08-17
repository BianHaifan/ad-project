import type {AuthClient} from './authClient';
import {authClient, AuthApiError} from './authClient';
import {apiPaths} from './contract';
import type {AvatarMetadata} from '../models/recruiter';

export type AvatarAuthClient = Pick<AuthClient, 'requestWithAuth' | 'requestWithAuthForm'>;

export class AvatarHttpClient {
  constructor(private readonly client: AvatarAuthClient = authClient) {}

  async upload(file: File): Promise<AvatarMetadata> {
    const formData = new FormData();
    formData.append('file', file);
    const payload = await this.client.requestWithAuthForm<unknown>(apiPaths.avatar, formData);
    return parseAvatarEnvelope(payload);
  }

  async delete(): Promise<void> {
    await this.client.requestWithAuth<unknown>(apiPaths.avatar, {method: 'DELETE'});
  }
}

function parseAvatarEnvelope(payload: unknown): AvatarMetadata {
  if (!isRecord(payload) || !isRecord(payload.data) || typeof payload.data.userId !== 'string' ||
      typeof payload.data.avatarUrl !== 'string' ||
      !(payload.data.contentType === 'image/png' || payload.data.contentType === 'image/jpeg') ||
      typeof payload.data.sizeBytes !== 'number' || typeof payload.data.updatedAt !== 'string') {
    throw unexpectedResponse();
  }
  return {
    userId: payload.data.userId,
    avatarUrl: payload.data.avatarUrl,
    contentType: payload.data.contentType,
    sizeBytes: payload.data.sizeBytes,
    updatedAt: payload.data.updatedAt,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function unexpectedResponse(): AuthApiError {
  return new AuthApiError(0, 'UNEXPECTED_RESPONSE', 'The server returned an unexpected response.');
}

export const avatarHttpClient = new AvatarHttpClient();
