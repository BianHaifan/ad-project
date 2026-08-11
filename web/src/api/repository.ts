import {mockRecruiterRepository} from '../mocks/mockRecruiterRepository';
import {jobHttpClient} from './jobHttpClient';
import type {RecruiterRepository} from './recruiterRepository';

export type RecruiterBusinessRepository = Omit<RecruiterRepository, 'signIn' | 'register'>;

// Auth and jobs use real HTTP clients. Dashboard, applications, and messages remain intentionally mocked.
export const recruiterRepository: RecruiterBusinessRepository = {
  ...mockRecruiterRepository,
  listJobs: params => jobHttpClient.listJobs(params),
  getJob: jobId => jobHttpClient.getJob(jobId),
  createJob: input => jobHttpClient.createJob(input),
  updateJob: (jobId, input, expectedVersion) => jobHttpClient.updateJob(jobId, input, expectedVersion),
  publishJob: (jobId, expectedVersion) => jobHttpClient.publishJob(jobId, expectedVersion),
  changeJobStatus: (jobId, status, reason, expectedVersion) =>
    jobHttpClient.changeJobStatus(jobId, status, reason, expectedVersion),
};
