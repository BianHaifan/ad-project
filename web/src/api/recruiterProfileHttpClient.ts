import type {AuthClient} from './authClient';
import {authClient, AuthApiError} from './authClient';
import {apiPaths} from './contract';
import type {RecruiterProfileDetail, UpdateRecruiterProfileInput} from '../models/recruiter';

export class RecruiterProfileHttpClient {
  constructor(private readonly client: Pick<AuthClient, 'requestWithAuth'> = authClient) {}

  async getProfile(): Promise<RecruiterProfileDetail> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.recruiterProfile);
    return parseProfileEnvelope(payload);
  }

  async updateProfile(input: UpdateRecruiterProfileInput): Promise<RecruiterProfileDetail> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.recruiterProfile, {
      method: 'PATCH',
      body: JSON.stringify(input),
    });
    return parseProfileEnvelope(payload);
  }
}

export function parseProfile(value: unknown): RecruiterProfileDetail {
  if (!isRecord(value) || typeof value.userId !== 'string' || typeof value.fullName !== 'string' ||
      !(value.avatarUrl === null || typeof value.avatarUrl === 'string') ||
      typeof value.title !== 'string' || !(value.bio === null || typeof value.bio === 'string') ||
      !isCompany(value.company) || typeof value.email !== 'string' ||
      typeof value.createdAt !== 'string' || typeof value.updatedAt !== 'string') {
    throw unexpectedResponse();
  }
  return value as unknown as RecruiterProfileDetail;
}

function parseProfileEnvelope(payload: unknown): RecruiterProfileDetail {
  if (!isRecord(payload) || !isRecord(payload.data)) throw unexpectedResponse();
  return parseProfile(payload.data);
}

function isCompany(value: unknown): boolean {
  return isRecord(value) && typeof value.companyId === 'string' && typeof value.name === 'string' &&
    (value.logoUrl === null || typeof value.logoUrl === 'string') &&
    (value.verificationStatus === null || typeof value.verificationStatus === 'string');
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function unexpectedResponse(): AuthApiError {
  return new AuthApiError(0, 'UNEXPECTED_RESPONSE', 'The server returned an unexpected response.');
}

export const recruiterProfileHttpClient = new RecruiterProfileHttpClient();
