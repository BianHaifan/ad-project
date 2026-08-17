import type {AuthClient} from './authClient';
import {authClient, AuthApiError} from './authClient';
import {apiPaths} from './contract';
import type {
  ApplicantCandidateSummary, ApplicantMatchAnalysis, ApplicationStatus, AuditEvent, CreateInterviewRequest,
  Interview, InterviewMode, InterviewStatus, MatchAnalysis, MeetingProvider, MeetingSyncStatus, RecruiterApplicationCounts,
  RecruiterApplicationDetail, RecruiterApplicationListMeta, RecruiterApplicationListResult,
  RecruiterApplicationSummary, RecommendedApplicant, RecommendedApplicantListResult, RecommendationMeta,
  UpdateInterviewRequest,
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

  async listApplicantRecommendations(jobId: string,
                                    params: {page?: number; pageSize?: number} = {}): Promise<RecommendedApplicantListResult> {
    const search = new URLSearchParams();
    search.set('page', String(params.page ?? 1));
    search.set('pageSize', String(params.pageSize ?? 20));
    const payload = await this.client.requestWithAuth<unknown>(
      `${apiPaths.applicantRecommendations(encodeURIComponent(jobId))}?${search}`);
    return parseRecommendationList(payload);
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

  async createInterview(applicationId: string, input: CreateInterviewRequest): Promise<Interview> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.interviews(encodeURIComponent(applicationId)), {
      method: 'POST', body: JSON.stringify(input),
    });
    return parseInterviewEnvelope(payload);
  }

  async updateInterview(interviewId: string, input: UpdateInterviewRequest): Promise<Interview> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.interview(encodeURIComponent(interviewId)), {
      method: 'PATCH', body: JSON.stringify(input),
    });
    return parseInterviewEnvelope(payload);
  }
}

function parseList(payload: unknown): RecruiterApplicationListResult {
  if (!isRecord(payload) || !Array.isArray(payload.data) || !isMeta(payload.meta)) throw unexpectedResponse();
  return {data: payload.data.map(parseSummary), meta: payload.meta};
}

function parseRecommendationList(payload: unknown): RecommendedApplicantListResult {
  if (!isRecord(payload) || !Array.isArray(payload.data) || !isRecommendationMeta(payload.meta)) {
    throw unexpectedResponse();
  }
  return {data: payload.data.map(parseRecommendedApplicant), meta: payload.meta};
}

function parseRecommendedApplicant(value: unknown): RecommendedApplicant {
  if (!isRecord(value) || typeof value.applicationId !== 'string' || !isApplicantCandidate(value.candidate) ||
      !isStatus(value.status) || typeof value.appliedAt !== 'string' || typeof value.matchScore !== 'number' ||
      typeof value.rank !== 'number' || !isApplicantMatchAnalysis(value.matchAnalysis)) throw unexpectedResponse();
  return value as unknown as RecommendedApplicant;
}

function isApplicantCandidate(value: unknown): value is ApplicantCandidateSummary {
  return isRecord(value) && typeof value.candidateId === 'string' && typeof value.fullName === 'string' &&
    (value.headline === null || typeof value.headline === 'string') &&
    (value.avatarUrl === null || typeof value.avatarUrl === 'string') &&
    (value.location === null || typeof value.location === 'string');
}

function isApplicantMatchAnalysis(value: unknown): value is ApplicantMatchAnalysis {
  return isRecord(value) && isStringArray(value.strongMatches) && isStringArray(value.gaps) &&
    isStringArray(value.evidence);
}

function isMatchAnalysis(value: unknown): value is MatchAnalysis {
  return isRecord(value) && typeof value.score === 'number' && isStringArray(value.evidence) &&
    isStringArray(value.strongMatches) && isStringArray(value.gaps) &&
    typeof value.modelVersion === 'string' && typeof value.generatedAt === 'string';
}

function isRecommendationMeta(value: unknown): value is RecommendationMeta {
  return isRecord(value) && typeof value.source === 'string' && typeof value.modelVersion === 'string' &&
    typeof value.featureVersion === 'string' && typeof value.modelStatus === 'string' &&
    typeof value.inferenceMs === 'number' && typeof value.generatedAt === 'string' &&
    typeof value.page === 'number' && typeof value.pageSize === 'number' && typeof value.total === 'number' &&
    typeof value.hasNext === 'boolean';
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every(item => typeof item === 'string');
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
      !(value.matchAnalysis === null || isMatchAnalysis(value.matchAnalysis)) ||
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
    typeof value.interview === 'number' && typeof value.offered === 'number' && typeof value.rejected === 'number';
}

function parseInterviewEnvelope(payload: unknown): Interview {
  if (!isRecord(payload) || !isRecord(payload.data)) throw unexpectedResponse();
  return parseInterview(payload.data);
}

function parseInterview(value: unknown): Interview {
  if (!isRecord(value) || typeof value.interviewId !== 'string' || typeof value.applicationId !== 'string' ||
      typeof value.scheduledAt !== 'string' || typeof value.timezone !== 'string' ||
      typeof value.durationMinutes !== 'number' || !isInterviewMode(value.mode) ||
      !(value.locationOrMeetingUrl === null || typeof value.locationOrMeetingUrl === 'string') ||
      !(value.note === null || typeof value.note === 'string') || !isInterviewStatus(value.status) ||
      typeof value.version !== 'number' || typeof value.createdAt !== 'string' ||
      typeof value.updatedAt !== 'string' || !isMeetingProvider(value.meetingProvider) ||
      !isMeetingSyncStatus(value.meetingSyncStatus)) throw unexpectedResponse();
  return value as unknown as Interview;
}

function isInterviewMode(value: unknown): value is InterviewMode {
  return value === 'ONLINE' || value === 'ONSITE' || value === 'PHONE';
}

function isInterviewStatus(value: unknown): value is InterviewStatus {
  return value === 'SCHEDULED' || value === 'COMPLETED' || value === 'CANCELLED';
}

function isMeetingProvider(value: unknown): value is MeetingProvider {
  return value === 'MANUAL' || value === 'GOOGLE_MEET';
}

function isMeetingSyncStatus(value: unknown): value is MeetingSyncStatus {
  return value === 'NOT_APPLICABLE' || value === 'PENDING' || value === 'READY' || value === 'FAILED';
}

function isStatus(value: unknown): value is ApplicationStatus {
  return value === 'APPLIED' || value === 'IN_REVIEW' || value === 'INTERVIEW' || value === 'OFFERED' ||
    value === 'REJECTED' || value === 'WITHDRAWN';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function unexpectedResponse(): AuthApiError {
  return new AuthApiError(0, 'UNEXPECTED_RESPONSE', 'The server returned an unexpected response.');
}

export const applicationHttpClient = new ApplicationHttpClient();
