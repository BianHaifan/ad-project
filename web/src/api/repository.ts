import {mockRecruiterRepository} from '../mocks/mockRecruiterRepository';
import {jobHttpClient} from './jobHttpClient';
import {applicationHttpClient} from './applicationHttpClient';
import {dashboardHttpClient} from './dashboardHttpClient';
import {conversationHttpClient} from './conversationHttpClient';
import {googleOAuthHttpClient} from './googleOAuthHttpClient';
import {recruiterProfileHttpClient} from './recruiterProfileHttpClient';
import {avatarHttpClient} from './avatarHttpClient';
import type {RecruiterRepository} from './recruiterRepository';

export type RecruiterBusinessRepository = Omit<RecruiterRepository, 'signIn' | 'register'>;

// Dashboard, jobs, applications, and conversations all use real HTTP clients.
export const recruiterRepository: RecruiterBusinessRepository = {
  ...mockRecruiterRepository,
  getDashboard: () => dashboardHttpClient.getDashboard(),
    getRecruiterProfile: () => recruiterProfileHttpClient.getProfile(),
    updateRecruiterProfile: input => recruiterProfileHttpClient.updateProfile(input),
  uploadAvatar: file => avatarHttpClient.upload(file),
  deleteAvatar: () => avatarHttpClient.delete(),
  listJobs: params => jobHttpClient.listJobs(params),
  getJob: jobId => jobHttpClient.getJob(jobId),
  createJob: input => jobHttpClient.createJob(input),
  updateJob: (jobId, input, expectedVersion) => jobHttpClient.updateJob(jobId, input, expectedVersion),
  publishJob: (jobId, expectedVersion) => jobHttpClient.publishJob(jobId, expectedVersion),
  changeJobStatus: (jobId, status, reason, expectedVersion) =>
    jobHttpClient.changeJobStatus(jobId, status, reason, expectedVersion),
  listApplications: params => applicationHttpClient.listApplications(params),
  listApplicantRecommendations: (jobId, params) => applicationHttpClient.listApplicantRecommendations(jobId, params),
  getApplication: applicationId => applicationHttpClient.getApplication(applicationId),
  updateApplicationStatus: (applicationId, status, reason, expectedVersion) =>
    applicationHttpClient.updateApplicationStatus(applicationId, status, reason, expectedVersion),
  createInterview: (applicationId, input) => applicationHttpClient.createInterview(applicationId, input),
  updateInterview: (interviewId, input) => applicationHttpClient.updateInterview(interviewId, input),
  listConversations: applicationId => conversationHttpClient.listConversations(applicationId),
  getConversation: conversationId => conversationHttpClient.getConversation(conversationId),
  listMessages: conversationId => conversationHttpClient.listMessages(conversationId),
  sendMessage: (conversationId, body) => conversationHttpClient.sendMessage(conversationId, body),
  sendMessageWithAttachment: (conversationId, body, file) =>
    conversationHttpClient.sendMessageWithAttachment(conversationId, body, file),
  downloadAttachment: (conversationId, messageId) =>
    conversationHttpClient.downloadAttachment(conversationId, messageId),
  markRead: (conversationId, lastReadMessageId) => conversationHttpClient.markRead(conversationId, lastReadMessageId),
  beginGoogleConnection: () => googleOAuthHttpClient.beginConnection(),
  getGoogleConnection: () => googleOAuthHttpClient.getConnection(),
  disconnectGoogle: () => googleOAuthHttpClient.disconnect(),
};
