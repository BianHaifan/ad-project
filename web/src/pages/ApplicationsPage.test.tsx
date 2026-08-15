import {describe, expect, it} from 'vitest';
import type {RecruiterApplicationSummary} from '../models/recruiter';
import {rankApplicationsByMatch} from './applicationRanking';

const base: RecruiterApplicationSummary = {
  applicationId: 'application-base',
  jobId: 'job-1',
  status: 'APPLIED',
  appliedAt: '2026-08-12T08:00:00Z',
  updatedAt: '2026-08-12T08:00:00Z',
  version: 1,
  candidate: {
    candidateId: 'candidate-1',
    fullName: 'Candidate One',
    email: 'candidate@example.com',
    headline: 'Backend Engineer',
    avatarUrl: null,
    location: 'Singapore',
  },
  jobTitle: 'Backend Engineer',
  matchScore: null,
  owner: null,
};

describe('recruiter application demo ranking', () => {
  it('ranks scored candidates descending, puts missing scores last, and preserves source data', () => {
    const input = [
      {...base, applicationId: 'low', matchScore: 42},
      {...base, applicationId: 'missing'},
      {...base, applicationId: 'high', matchScore: 96},
    ];

    expect(rankApplicationsByMatch(input).map(value => value.applicationId))
      .toEqual(['high', 'low', 'missing']);
    expect(input.map(value => value.applicationId)).toEqual(['low', 'missing', 'high']);
  });
});
