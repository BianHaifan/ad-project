import {describe, expect, it, vi} from 'vitest';
import type {AuthClient} from './authClient';
import {AuthApiError} from './authClient';
import {ApplicationHttpClient} from './applicationHttpClient';
import {applications} from '../mocks/data';

const detail = {...applications[0], matchScore: null, matchAnalysis: null, owner: null, interview: null, notes: []};
const summary = {
  applicationId: detail.applicationId, jobId: detail.jobId, status: detail.status, appliedAt: detail.appliedAt,
  updatedAt: detail.updatedAt, version: detail.version, candidate: detail.candidate, jobTitle: detail.jobTitle,
  matchScore: null, owner: null,
};
const meta = {page: 1, pageSize: 20, total: 1, hasNext: false,
  counts: {applied: 1, inReview: 0, interview: 0, rejected: 0}};

function setup(result: unknown) {
  const requestWithAuth = vi.fn().mockResolvedValue(result);
  return {requestWithAuth,
    client: new ApplicationHttpClient({requestWithAuth} as Pick<AuthClient, 'requestWithAuth'>)};
}

describe('ApplicationHttpClient', () => {
  it('loads the real paginated list with supported filters and server counts', async () => {
    const {client, requestWithAuth} = setup({data: [summary], meta});
    await expect(client.listApplications({status: 'APPLIED', jobId: ' job-1 ', q: ' Ada ', page: 1, pageSize: 20,
      sort: 'updatedAt,asc'})).resolves.toEqual({data: [summary], meta});
    expect(requestWithAuth).toHaveBeenCalledWith(
      '/recruiter/applications?status=APPLIED&jobId=job-1&q=Ada&page=1&pageSize=20&sort=updatedAt%2Casc');
  });

  it('accepts an empty persisted list', async () => {
    const emptyMeta = {...meta, total: 0, counts: {applied: 0, inReview: 0, interview: 0, rejected: 0}};
    const {client} = setup({data: [], meta: emptyMeta});
    await expect(client.listApplications()).resolves.toEqual({data: [], meta: emptyMeta});
  });

  it('loads a persisted detail and rejects malformed success payloads', async () => {
    const valid = setup({data: detail});
    await expect(valid.client.getApplication('app/id')).resolves.toEqual(detail);
    expect(valid.requestWithAuth).toHaveBeenCalledWith('/recruiter/applications/app%2Fid');
    const invalid = setup({data: {applicationId: 'broken'}});
    await expect(invalid.client.getApplication('broken')).rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
  });

  it('sends only transition contract fields and returns the updated application', async () => {
    const event = detail.timeline[0];
    const {client, requestWithAuth} = setup({data: {application: detail, event}});
    await expect(client.updateApplicationStatus(detail.applicationId, 'IN_REVIEW', ' Strong evidence ', detail.version))
      .resolves.toEqual(detail);
    expect(requestWithAuth).toHaveBeenCalledWith(`/recruiter/applications/${detail.applicationId}/transitions`, {
      method: 'POST', body: JSON.stringify({toStatus: 'IN_REVIEW', reason: 'Strong evidence', expectedVersion: detail.version}),
    });
    expect(Object.keys(JSON.parse(String((requestWithAuth.mock.calls[0][1] as RequestInit).body))))
      .toEqual(['toStatus', 'reason', 'expectedVersion']);
  });

  it('preserves safe authenticated-client errors', async () => {
    const requestWithAuth = vi.fn().mockRejectedValue(new AuthApiError(409, 'VERSION_CONFLICT', 'Refresh and retry.'));
    const client = new ApplicationHttpClient({requestWithAuth} as Pick<AuthClient, 'requestWithAuth'>);
    await expect(client.getApplication('app-1')).rejects.toMatchObject({status: 409, code: 'VERSION_CONFLICT'});
  });
});
