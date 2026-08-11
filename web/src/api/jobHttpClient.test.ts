import {describe, expect, it, vi} from 'vitest';
import {AuthApiError, type AuthClient} from './authClient';
import {JobHttpClient} from './jobHttpClient';
import type {RecruiterJobSummary} from '../models/recruiter';

const job: RecruiterJobSummary = {
  jobId: 'job-real-1', title: 'Backend Engineer',
  company: {companyId: 'company-1', name: 'Real Company', logoUrl: null, stage: null, employeeRange: null,
    verificationStatus: 'APPROVED', website: null, description: null, location: null, version: 1,
    createdAt: '2026-08-11T01:00:00Z', updatedAt: '2026-08-11T01:00:00Z'},
  employmentType: 'FULL_TIME', workplaceType: 'HYBRID', location: 'Singapore',
  salary: {min: 5000, max: 8000, currency: 'SGD', period: 'MONTH'}, description: 'Build APIs',
  requirements: ['Build reliable APIs'], skills: ['Java'], deadline: null, visibility: 'PUBLIC', status: 'DRAFT',
  publishedAt: null, version: 1, createdAt: '2026-08-11T01:00:00Z', updatedAt: '2026-08-11T01:00:00Z',
  applicantCount: 0, owner: null,
};

function setup(result: unknown) {
  const requestWithAuth = vi.fn().mockResolvedValue(result);
  return {requestWithAuth, client: new JobHttpClient({requestWithAuth} as Pick<AuthClient, 'requestWithAuth'>)};
}

describe('JobHttpClient', () => {
  it('loads and parses a real paginated recruiter job list with all filters', async () => {
    const {client, requestWithAuth} = setup({data: [job], meta: {page: 2, pageSize: 10, total: 11, hasNext: false}});
    await expect(client.listJobs({q: ' Backend ', status: 'DRAFT', employmentType: 'FULL_TIME', location: ' SG ', ownerId: 'owner-1', page: 2, pageSize: 10}))
      .resolves.toEqual({data: [job], meta: {page: 2, pageSize: 10, total: 11, hasNext: false}});
    expect(requestWithAuth).toHaveBeenCalledWith('/recruiter/jobs?q=Backend&status=DRAFT&employmentType=FULL_TIME&location=SG&ownerId=owner-1&page=2&pageSize=10');
  });

  it('accepts an empty persisted list', async () => {
    const {client} = setup({data: [], meta: {page: 1, pageSize: 20, total: 0, hasNext: false}});
    await expect(client.listJobs()).resolves.toMatchObject({data: [], meta: {total: 0}});
  });

  it('creates only the OpenAPI request fields and returns the server draft', async () => {
    const {client, requestWithAuth} = setup({data: job});
    const result = await client.createJob({title: ' Backend Engineer ', employmentType: 'FULL_TIME', workplaceType: 'HYBRID',
      location: ' Singapore ', salaryMin: 5000, salaryMax: 8000, description: ' Build APIs ',
      requirements: 'Build reliable APIs\n\nWork across teams', skills: ['Java'], deadline: '2026-09-30', visibility: 'PUBLIC'});
    expect(result).toEqual(job);
    const [, init] = requestWithAuth.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(String(init.body));
    expect(body).toEqual({title: 'Backend Engineer', employmentType: 'FULL_TIME', workplaceType: 'HYBRID',
      location: 'Singapore', salary: {min: 5000, max: 8000, currency: 'SGD', period: 'MONTH'},
      description: 'Build APIs', requirements: ['Build reliable APIs', 'Work across teams'], skills: ['Java'],
      deadline: '2026-09-30T15:59:59.000Z', visibility: 'PUBLIC'});
    expect(body).not.toHaveProperty('companyId');
    expect(body).not.toHaveProperty('status');
    expect(body).not.toHaveProperty('createdBy');
    expect(body).not.toHaveProperty('version');
  });

  it('updates a draft with only editable OpenAPI fields and expectedVersion', async () => {
    const updated = {...job, title: 'Senior Backend Engineer', version: 2};
    const {client, requestWithAuth} = setup({data: updated});
    const input = {title: ' Senior Backend Engineer ', employmentType: 'FULL_TIME' as const,
      workplaceType: 'HYBRID' as const, location: ' Singapore ', salaryMin: 6000, salaryMax: 9000,
      description: ' Updated APIs ', requirements: 'Reliable APIs\nProduction ownership', skills: ['Java'],
      deadline: '', visibility: 'PRIVATE' as const};
    await expect(client.updateJob(job.jobId, input, 1)).resolves.toEqual(updated);
    expect(requestWithAuth).toHaveBeenCalledWith('/recruiter/jobs/job-real-1', expect.objectContaining({method: 'PATCH'}));
    const body = JSON.parse(String((requestWithAuth.mock.calls[0][1] as RequestInit).body));
    expect(body).toEqual({title: 'Senior Backend Engineer', employmentType: 'FULL_TIME', workplaceType: 'HYBRID',
      location: 'Singapore', salary: {min: 6000, max: 9000, currency: 'SGD', period: 'MONTH'},
      description: 'Updated APIs', requirements: ['Reliable APIs', 'Production ownership'], skills: ['Java'],
      deadline: null, visibility: 'PRIVATE', expectedVersion: 1});
    for (const serverField of ['companyId', 'createdBy', 'status', 'publishedAt', 'updatedAt']) {
      expect(body).not.toHaveProperty(serverField);
    }
  });

  it('publishes with only expectedVersion through the authenticated client', async () => {
    const active = {...job, status: 'ACTIVE' as const, version: 2, publishedAt: '2026-08-11T02:00:00Z'};
    const {client, requestWithAuth} = setup({data: active});
    await expect(client.publishJob(job.jobId, 1)).resolves.toEqual(active);
    expect(requestWithAuth).toHaveBeenCalledWith('/recruiter/jobs/job-real-1/publish', {
      method: 'POST', body: JSON.stringify({expectedVersion: 1}),
    });
    const body = JSON.parse(String((requestWithAuth.mock.calls[0][1] as RequestInit).body));
    expect(Object.keys(body)).toEqual(['expectedVersion']);
  });

  it('changes lifecycle status with only status, reason, and expectedVersion', async () => {
    const paused = {...job, status: 'PAUSED' as const, version: 3, publishedAt: '2026-08-11T02:00:00Z'};
    const {client, requestWithAuth} = setup({data: paused});
    await expect(client.changeJobStatus(job.jobId, 'PAUSED', ' Pause for planning ', 2)).resolves.toEqual(paused);
    expect(requestWithAuth).toHaveBeenCalledWith('/recruiter/jobs/job-real-1/status', {
      method: 'POST', body: JSON.stringify({status: 'PAUSED', reason: 'Pause for planning', expectedVersion: 2}),
    });
    const body = JSON.parse(String((requestWithAuth.mock.calls[0][1] as RequestInit).body));
    expect(Object.keys(body)).toEqual(['status', 'reason', 'expectedVersion']);
  });

  it('loads a real detail and safely rejects malformed success payloads', async () => {
    const valid = setup({data: job});
    await expect(valid.client.getJob(job.jobId)).resolves.toEqual(job);
    expect(valid.requestWithAuth).toHaveBeenCalledWith('/recruiter/jobs/job-real-1');
    const invalid = setup({data: {jobId: 'broken'}});
    await expect(invalid.client.getJob('broken')).rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
  });

  it('preserves safe network and ErrorResponse failures from the authenticated client', async () => {
    const requestWithAuth = vi.fn().mockRejectedValue(new AuthApiError(0, 'NETWORK_ERROR', 'Safe network message'));
    const client = new JobHttpClient({requestWithAuth} as Pick<AuthClient, 'requestWithAuth'>);
    await expect(client.listJobs()).rejects.toMatchObject({status: 0, code: 'NETWORK_ERROR'});
  });
});
