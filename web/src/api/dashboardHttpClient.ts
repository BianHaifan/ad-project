import type {AuthClient} from './authClient';
import {authClient, AuthApiError} from './authClient';
import {apiPaths} from './contract';
import {parseJob} from './jobHttpClient';
import {parseSummary} from './applicationHttpClient';
import type {CompanyVerificationStatus, Dashboard, DashboardMetrics} from '../models/recruiter';

export class DashboardHttpClient {
  constructor(private readonly client: Pick<AuthClient, 'requestWithAuth'> = authClient) {}

  async getDashboard(): Promise<Dashboard> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.dashboard);
    return parseDashboard(payload);
  }
}

function parseDashboard(payload: unknown): Dashboard {
  if (!isRecord(payload) || !isRecord(payload.data)) throw unexpectedResponse();
  const data = payload.data;
  if (!isRecord(data.metrics) || !Array.isArray(data.recentApplications) || !Array.isArray(data.recentJobs)) {
    throw unexpectedResponse();
  }
  return {
    metrics: parseMetrics(data.metrics),
    recentApplications: data.recentApplications.map(parseSummary),
    recentJobs: data.recentJobs.map(parseJob),
  };
}

function parseMetrics(value: unknown): DashboardMetrics {
  if (!isRecord(value) || typeof value.activeJobs !== 'number' ||
      typeof value.appliedApplications !== 'number' || typeof value.inReviewApplications !== 'number' ||
      typeof value.interviewApplications !== 'number' || !isVerificationStatus(value.companyVerificationStatus)) {
    throw unexpectedResponse();
  }
  return value as unknown as DashboardMetrics;
}

function isVerificationStatus(value: unknown): value is CompanyVerificationStatus {
  return value === 'PENDING' || value === 'APPROVED' || value === 'REJECTED';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function unexpectedResponse(): AuthApiError {
  return new AuthApiError(0, 'UNEXPECTED_RESPONSE', 'The server returned an unexpected response.');
}

export const dashboardHttpClient = new DashboardHttpClient();
