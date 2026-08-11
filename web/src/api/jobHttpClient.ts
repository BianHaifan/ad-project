import type {AuthClient} from './authClient';
import {authClient, AuthApiError} from './authClient';
import {apiPaths} from './contract';
import type {JobDraft, PageMeta, RecruiterJobSummary} from '../models/recruiter';
import type {JobListResult, ListJobsParams} from './recruiterRepository';

export class JobHttpClient {
  constructor(private readonly client: Pick<AuthClient, 'requestWithAuth'> = authClient) {}

  async listJobs(params: ListJobsParams = {}): Promise<JobListResult> {
    const search = new URLSearchParams();
    if (params.q?.trim()) search.set('q', params.q.trim());
    if (params.status) search.set('status', params.status);
    if (params.employmentType) search.set('employmentType', params.employmentType);
    if (params.location?.trim()) search.set('location', params.location.trim());
    if (params.ownerId?.trim()) search.set('ownerId', params.ownerId.trim());
    search.set('page', String(params.page ?? 1));
    search.set('pageSize', String(params.pageSize ?? 20));
    const suffix = search.toString();
    const payload = await this.client.requestWithAuth<unknown>(`${apiPaths.jobs}?${suffix}`);
    return parseJobList(payload);
  }

  async getJob(jobId: string): Promise<RecruiterJobSummary> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.job(encodeURIComponent(jobId)));
    return parseJobEnvelope(payload);
  }

  async createJob(input: JobDraft): Promise<RecruiterJobSummary> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.jobs, {
      method: 'POST',
      body: JSON.stringify(jobDraftPayload(input)),
    });
    return parseJobEnvelope(payload);
  }

  async updateJob(jobId: string, input: JobDraft, expectedVersion: number): Promise<RecruiterJobSummary> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.job(encodeURIComponent(jobId)), {
      method: 'PATCH',
      body: JSON.stringify({...jobDraftPayload(input), expectedVersion}),
    });
    return parseJobEnvelope(payload);
  }

  async publishJob(jobId: string, expectedVersion: number): Promise<RecruiterJobSummary> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.publishJob(encodeURIComponent(jobId)), {
      method: 'POST',
      body: JSON.stringify({expectedVersion}),
    });
    return parseJobEnvelope(payload);
  }

  async changeJobStatus(jobId: string, status: 'ACTIVE' | 'PAUSED' | 'CLOSED', reason: string,
                        expectedVersion: number): Promise<RecruiterJobSummary> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.jobStatus(encodeURIComponent(jobId)), {
      method: 'POST',
      body: JSON.stringify({status, reason: reason.trim(), expectedVersion}),
    });
    return parseJobEnvelope(payload);
  }
}

function jobDraftPayload(input: JobDraft) {
  return {
    title: input.title.trim(),
    employmentType: input.employmentType,
    workplaceType: input.workplaceType,
    location: input.location.trim(),
    salary: {min: input.salaryMin, max: input.salaryMax, currency: 'SGD' as const, period: 'MONTH' as const},
    description: input.description.trim(),
    requirements: input.requirements.split('\n').map(value => value.trim()).filter(Boolean),
    skills: input.skills,
    deadline: input.deadline ? new Date(`${input.deadline}T15:59:59Z`).toISOString() : null,
    visibility: input.visibility,
  };
}

function parseJobEnvelope(payload: unknown): RecruiterJobSummary {
  if (!isRecord(payload) || !isRecord(payload.data)) throw unexpectedResponse();
  return parseJob(payload.data);
}

function parseJobList(payload: unknown): JobListResult {
  if (!isRecord(payload) || !Array.isArray(payload.data) || !isPageMeta(payload.meta)) throw unexpectedResponse();
  return {data: payload.data.map(item => parseJob(item)), meta: payload.meta};
}

function parseJob(value: unknown): RecruiterJobSummary {
  if (!isRecord(value) || typeof value.jobId !== 'string' || typeof value.title !== 'string' ||
      !isRecord(value.company) || typeof value.company.companyId !== 'string' || typeof value.company.name !== 'string' ||
      !isRecord(value.salary) || typeof value.salary.min !== 'number' || typeof value.salary.max !== 'number' ||
      !Array.isArray(value.requirements) || !value.requirements.every(item => typeof item === 'string') ||
      !Array.isArray(value.skills) || !value.skills.every(item => typeof item === 'string') ||
      typeof value.description !== 'string' || typeof value.location !== 'string' ||
      typeof value.applicantCount !== 'number' || typeof value.version !== 'number' ||
      typeof value.createdAt !== 'string' || typeof value.updatedAt !== 'string') {
    throw unexpectedResponse();
  }
  return value as unknown as RecruiterJobSummary;
}

function isPageMeta(value: unknown): value is PageMeta {
  return isRecord(value) && typeof value.page === 'number' && typeof value.pageSize === 'number' &&
    typeof value.total === 'number' && typeof value.hasNext === 'boolean';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function unexpectedResponse(): AuthApiError {
  return new AuthApiError(0, 'UNEXPECTED_RESPONSE', 'The server returned an unexpected response.');
}

export const jobHttpClient = new JobHttpClient();
