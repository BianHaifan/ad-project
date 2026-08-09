import {describe, expect, it} from 'vitest';
import {apiPaths, readApiError, readData, readList} from './contract';
import type {ApplicationStatus, InterviewMode, InterviewStatus, JobStatus, SenderType} from '../models/recruiter';

describe('final API contract helpers', () => {
  it('uses the canonical recruiter paths', () => {
    expect(apiPaths.transitions('app_001')).toBe('/recruiter/applications/app_001/transitions');
    expect(apiPaths.resumeSnapshot('app_001')).toBe('/recruiter/applications/app_001/resume-snapshot');
    expect(apiPaths.messages('conv_001')).toBe('/recruiter/conversations/conv_001/messages');
  });

  it('reads data, meta, and error envelopes', () => {
    expect(readData({data: {userId: 'rec_001'}})).toEqual({userId: 'rec_001'});
    expect(readList({data: ['job_001'], meta: {page: 1, pageSize: 20, total: 1, hasNext: false}}).meta.total).toBe(1);
    expect(readApiError({error: {code: 'VALIDATION_ERROR', message: 'Invalid input', fieldErrors: {fullName: 'Required'}, requestId: 'req_001'}})).toEqual({code: 'VALIDATION_ERROR', message: 'Invalid input', fieldErrors: {fullName: 'Required'}, requestId: 'req_001'});
  });

  it('keeps shared enum values constrained at compile time', () => {
    const applicationStatuses: ApplicationStatus[] = ['APPLIED', 'IN_REVIEW', 'INTERVIEW', 'REJECTED', 'WITHDRAWN'];
    const jobStatuses: JobStatus[] = ['DRAFT', 'ACTIVE', 'PAUSED', 'CLOSED'];
    const interviewStatuses: InterviewStatus[] = ['SCHEDULED', 'COMPLETED', 'CANCELLED'];
    const modes: InterviewMode[] = ['ONLINE', 'ONSITE', 'PHONE'];
    const senderTypes: SenderType[] = ['CANDIDATE', 'RECRUITER', 'SYSTEM'];
    expect(applicationStatuses).toEqual(['APPLIED', 'IN_REVIEW', 'INTERVIEW', 'REJECTED', 'WITHDRAWN']);
    expect(jobStatuses).toEqual(['DRAFT', 'ACTIVE', 'PAUSED', 'CLOSED']);
    expect(interviewStatuses).toEqual(['SCHEDULED', 'COMPLETED', 'CANCELLED']);
    expect(modes).toEqual(['ONLINE', 'ONSITE', 'PHONE']);
    expect(senderTypes).toEqual(['CANDIDATE', 'RECRUITER', 'SYSTEM']);
  });
});
