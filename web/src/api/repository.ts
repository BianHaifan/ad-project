import {mockRecruiterRepository} from '../mocks/mockRecruiterRepository';
import {jobHttpClient} from './jobHttpClient';
import {applicationHttpClient} from './applicationHttpClient';
import {dashboardHttpClient} from './dashboardHttpClient';
import {conversationHttpClient} from './conversationHttpClient';
import type {RecruiterRepository} from './recruiterRepository';

export type RecruiterBusinessRepository = Omit<RecruiterRepository, 'signIn' | 'register'>;

// Dashboard, jobs, applications, and conversations all use real HTTP clients.
export const recruiterRepository: RecruiterBusinessRepository = {
  ...mockRecruiterRepository,
  getDashboard: () => dashboardHttpClient.getDashboard(),
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
  listConversations: () => conversationHttpClient.listConversations(),
  getConversation: conversationId => conversationHttpClient.getConversation(conversationId),
  listMessages: conversationId => conversationHttpClient.listMessages(conversationId),
  sendMessage: (conversationId, body) => conversationHttpClient.sendMessage(conversationId, body),
  markRead: (conversationId, lastReadMessageId) => conversationHttpClient.markRead(conversationId, lastReadMessageId),
};
