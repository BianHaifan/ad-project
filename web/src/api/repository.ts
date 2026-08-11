import {mockRecruiterRepository} from '../mocks/mockRecruiterRepository';
import {jobHttpClient} from './jobHttpClient';
import {applicationHttpClient} from './applicationHttpClient';
import type {RecruiterRepository} from './recruiterRepository';

export type RecruiterBusinessRepository = Omit<RecruiterRepository, 'signIn' | 'register'>;

// Auth, jobs, and applications use real HTTP clients. Dashboard and messages remain intentionally mocked.
export const recruiterRepository: RecruiterBusinessRepository = {
  ...mockRecruiterRepository,
  listJobs: params => jobHttpClient.listJobs(params),
  getJob: jobId => jobHttpClient.getJob(jobId),
  createJob: input => jobHttpClient.createJob(input),
  updateJob: (jobId, input, expectedVersion) => jobHttpClient.updateJob(jobId, input, expectedVersion),
  publishJob: (jobId, expectedVersion) => jobHttpClient.publishJob(jobId, expectedVersion),
  changeJobStatus: (jobId, status, reason, expectedVersion) =>
    jobHttpClient.changeJobStatus(jobId, status, reason, expectedVersion),
  listApplications: params => applicationHttpClient.listApplications(params),
  getApplication: applicationId => applicationHttpClient.getApplication(applicationId),
  updateApplicationStatus: (applicationId, status, reason, expectedVersion) =>
    applicationHttpClient.updateApplicationStatus(applicationId, status, reason, expectedVersion),
};
