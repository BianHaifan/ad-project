import type {RecruiterRepository} from '../api/recruiterRepository';
import type {Message} from '../models/recruiter';
import {applications, company, conversations, jobs, recruiter} from './data';

const delay = (ms = 260) => new Promise(resolve => setTimeout(resolve, ms));
const failIfDemoError = async () => {
  await delay();
  if (sessionStorage.getItem('ad_mock_error') === 'true') throw new Error('Mock service is unavailable.');
};

type MockOnlyRepository = Omit<RecruiterRepository,
  'listJobs' | 'getJob' | 'createJob' | 'updateJob' | 'publishJob' | 'changeJobStatus' |
  'listApplications' | 'getApplication' | 'updateApplicationStatus'>;

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
