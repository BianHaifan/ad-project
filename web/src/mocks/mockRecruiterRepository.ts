import type {ListApplicationsParams, RecruiterRepository, RecruiterTransitionStatus} from '../api/recruiterRepository';
import type {Message, RecruiterApplicationCounts} from '../models/recruiter';
import {applications, company, conversations, jobs, owner, recruiter} from './data';

const delay = (ms = 260) => new Promise(resolve => setTimeout(resolve, ms));
const failIfDemoError = async () => {
  await delay();
  if (sessionStorage.getItem('ad_mock_error') === 'true') throw new Error('Mock service is unavailable.');
};

type MockOnlyRepository = Omit<RecruiterRepository,
  'listJobs' | 'getJob' | 'createJob' | 'updateJob' | 'publishJob' | 'changeJobStatus'>;

export const mockRecruiterRepository: MockOnlyRepository = {
  async signIn(email, password) {
    await failIfDemoError();
    if (!email.includes('@') || password.length < 8) throw new Error('Invalid email or password.');
    localStorage.setItem('ad_session', 'true');
    return recruiter;
  },
  async register(fullName, companyName, email, password) {
    await failIfDemoError();
    if (!fullName || !companyName || !email.includes('@') || password.length < 8) throw new Error('Please check the account details.');
    localStorage.setItem('ad_session', 'true');
    return {...recruiter, fullName, email, company: {...company, name: companyName}};
  },
  async getMe() { await failIfDemoError(); return recruiter; },
  async getDashboard() {
    await failIfDemoError();
    return {
      metrics: {openRoles: 12, newMatches: 248, pendingResumes: 73, interviews: 18, verification: 'Approved'},
      recommendedApplications: applications.slice(0, 3), recentJobs: jobs.slice(0, 3),
    };
  },
  async getApplicationCounts() {
    await failIfDemoError();
    return applications.reduce<RecruiterApplicationCounts>((counts, application) => {
      counts[application.status] += 1; return counts;
    }, {APPLIED: 0, IN_REVIEW: 0, INTERVIEW: 0, REJECTED: 0, WITHDRAWN: 0});
  },
  async listApplications(params: ListApplicationsParams) {
    await failIfDemoError();
    return applications.filter(application =>
      (!params.status || application.status === params.status) &&
      (!params.jobId || application.jobId === params.jobId) &&
      (!params.search || `${application.candidate.fullName} ${application.candidate.email}`.toLowerCase().includes(params.search.toLowerCase())));
  },
  async getApplication(applicationId) { await failIfDemoError(); return applications.find(application => application.applicationId === applicationId); },
  async updateApplicationStatus(applicationId, status: RecruiterTransitionStatus) {
    await delay(500);
    const application = applications.find(item => item.applicationId === applicationId);
    if (!application) throw new Error('Application not found.');
    const fromStatus = application.status;
    application.status = status; application.version += 1; application.updatedAt = new Date().toISOString();
    application.timeline.push({eventId: `event_${Date.now()}`, actorId: owner.userId, companyId: company.companyId,
      fromStatus, toStatus: status, occurredAt: application.updatedAt, reason: `Moved to ${status}`,
      requestId: `mock_${Date.now()}`});
    return application;
  },
  async saveApplicationNote(applicationId, body) {
    await delay(400);
    const application = applications.find(item => item.applicationId === applicationId);
    if (!application) throw new Error('Application not found.');
    const timestamp = new Date().toISOString();
    application.notes.push({noteId: `note_${Date.now()}`, author: owner, body, createdAt: timestamp, updatedAt: timestamp});
    return application;
  },
  async scheduleInterview(applicationId, request) {
    await delay(550);
    const application = applications.find(item => item.applicationId === applicationId);
    if (!application) throw new Error('Application not found.');
    const timestamp = new Date().toISOString();
    application.interview = {interviewId: `interview_${Date.now()}`, applicationId,
      scheduledAt: request.scheduledAt, timezone: request.timezone, durationMinutes: request.durationMinutes,
      mode: request.mode, locationOrMeetingUrl: request.locationOrMeetingUrl, note: request.note ?? null,
      status: 'SCHEDULED', version: 1, createdAt: timestamp, updatedAt: timestamp};
    if (application.status !== 'INTERVIEW') await this.updateApplicationStatus(applicationId, 'INTERVIEW');
    return application;
  },
  async listConversations() { await failIfDemoError(); return conversations; },
  async getConversation(conversationId) { await failIfDemoError(); return conversations.find(item => item.conversationId === conversationId); },
  async sendMessage(conversationId, body) {
    await delay(350);
    const conversation = conversations.find(item => item.conversationId === conversationId);
    if (!conversation) throw new Error('Conversation not found.');
    const sentAt = new Date().toISOString();
    const message: Message = {messageId: `msg_${Date.now()}`, conversationId, body, senderType: 'RECRUITER',
      sentAt, clientMessageId: crypto.randomUUID(), deliveryStatus: 'SENT'};
    conversation.messages.push(message); conversation.lastMessage = message; conversation.updatedAt = sentAt;
    return {conversation};
  },
};
