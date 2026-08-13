import type {AuthClient} from './authClient';
import {authClient, AuthApiError} from './authClient';
import {apiPaths} from './contract';
import type {
  ApplicationStatus, AuditEvent, RecruiterApplicationCounts, RecruiterApplicationDetail,
  RecruiterApplicationListMeta, RecruiterApplicationListResult, RecruiterApplicationSummary,
} from '../models/recruiter';
import type {ListApplicationsParams, RecruiterTransitionStatus} from './recruiterRepository';

export class ApplicationHttpClient {
  constructor(private readonly client: Pick<AuthClient, 'requestWithAuth'> = authClient) {}

  async listApplications(params: ListApplicationsParams = {}): Promise<RecruiterApplicationListResult> {
    const search = new URLSearchParams();
    if (params.status) search.set('status', params.status);
    if (params.jobId?.trim()) search.set('jobId', params.jobId.trim());
    if (params.q?.trim()) search.set('q', params.q.trim());
    search.set('page', String(params.page ?? 1));
    search.set('pageSize', String(params.pageSize ?? 20));
    if (params.sort) search.set('sort', params.sort);
    const payload = await this.client.requestWithAuth<unknown>(`${apiPaths.applications}?${search}`);
    return parseList(payload);
  }

  async getApplication(applicationId: string): Promise<RecruiterApplicationDetail> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.application(encodeURIComponent(applicationId)));
    return parseDetailEnvelope(payload);
  }

  async updateApplicationStatus(applicationId: string, status: RecruiterTransitionStatus, reason: string,
                                expectedVersion: number): Promise<RecruiterApplicationDetail> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.transitions(encodeURIComponent(applicationId)), {
      method: 'POST', body: JSON.stringify({toStatus: status, reason: reason.trim(), expectedVersion}),
    });
    if (!isRecord(payload) || !isRecord(payload.data) || !isRecord(payload.data.application) ||
        !isRecord(payload.data.event)) throw unexpectedResponse();
    parseAuditEvent(payload.data.event);
    return parseDetail(payload.data.application);
  }
}

function parseList(payload: unknown): RecruiterApplicationListResult {
  if (!isRecord(payload) || !Array.isArray(payload.data) || !isMeta(payload.meta)) throw unexpectedResponse();
  return {data: payload.data.map(parseSummary), meta: payload.meta};
}

function parseDetailEnvelope(payload: unknown): RecruiterApplicationDetail {
  if (!isRecord(payload) || !isRecord(payload.data)) throw unexpectedResponse();
  return parseDetail(payload.data);
}

export function parseSummary(value: unknown): RecruiterApplicationSummary {
  if (!isRecord(value) || typeof value.applicationId !== 'string' || typeof value.jobId !== 'string' ||
      !isStatus(value.status) || typeof value.appliedAt !== 'string' || typeof value.updatedAt !== 'string' ||
      typeof value.version !== 'number' || !isCandidate(value.candidate) || typeof value.jobTitle !== 'string' ||
      !(value.matchScore === null || typeof value.matchScore === 'number') ||
      !(value.owner === null || isRecord(value.owner))) throw unexpectedResponse();
  return value as unknown as RecruiterApplicationSummary;
}

function parseDetail(value: unknown): RecruiterApplicationDetail {
  const summary = parseSummary(value);
  if (!isRecord(value) || !isRecord(value.resumeSnapshot) || !Array.isArray(value.resumeSnapshot.experiences) ||
      !Array.isArray(value.timeline) || !Array.isArray(value.notes) ||
      !(value.matchAnalysis === null || isRecord(value.matchAnalysis)) ||
      !(value.interview === null || isRecord(value.interview))) throw unexpectedResponse();
  value.timeline.forEach(parseAuditEvent);
  return {...value, ...summary} as unknown as RecruiterApplicationDetail;
}

function parseAuditEvent(value: unknown): AuditEvent {
  if (!isRecord(value) || typeof value.eventId !== 'string' || typeof value.actorId !== 'string' ||
      !(value.companyId === null || typeof value.companyId === 'string') ||
      !(value.fromStatus === null || isStatus(value.fromStatus)) || !(value.toStatus === null || isStatus(value.toStatus)) ||
      typeof value.occurredAt !== 'string' || !(value.reason === null || typeof value.reason === 'string') ||
      typeof value.requestId !== 'string') throw unexpectedResponse();
  return value as unknown as AuditEvent;
}

function isCandidate(value: unknown): boolean {
  return isRecord(value) && typeof value.candidateId === 'string' && typeof value.fullName === 'string' &&
    typeof value.email === 'string';
}

function isMeta(value: unknown): value is RecruiterApplicationListMeta {
  return isRecord(value) && typeof value.page === 'number' && typeof value.pageSize === 'number' &&
    typeof value.total === 'number' && typeof value.hasNext === 'boolean' && isCounts(value.counts);
}

function isCounts(value: unknown): value is RecruiterApplicationCounts {
  return isRecord(value) && typeof value.applied === 'number' && typeof value.inReview === 'number' &&
    typeof value.interview === 'number' && typeof value.rejected === 'number';
}

function isStatus(value: unknown): value is ApplicationStatus {
  return value === 'APPLIED' || value === 'IN_REVIEW' || value === 'INTERVIEW' ||
    value === 'REJECTED' || value === 'WITHDRAWN';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function unexpectedResponse(): AuthApiError {
  return new AuthApiError(0, 'UNEXPECTED_RESPONSE', 'The server returned an unexpected response.');
}

export const applicationHttpClient = new ApplicationHttpClient();
