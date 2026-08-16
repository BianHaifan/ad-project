import type {RecruiterRepository} from '../api/recruiterRepository';
import {company, recruiter} from './data';

const delay = (ms = 260) => new Promise(resolve => setTimeout(resolve, ms));
const failIfDemoError = async () => {
  await delay();
  if (sessionStorage.getItem('ad_mock_error') === 'true') throw new Error('Mock service is unavailable.');
};

type MockOnlyRepository = Omit<RecruiterRepository,
  'listJobs' | 'getJob' | 'createJob' | 'updateJob' | 'publishJob' | 'changeJobStatus' |
  'listApplications' | 'getApplication' | 'updateApplicationStatus' | 'getDashboard' |
    'getRecruiterProfile' | 'updateRecruiterProfile' |
  'createInterview' | 'updateInterview' |
  'listConversations' | 'getConversation' | 'listMessages' | 'sendMessage' | 'sendMessageWithAttachment' |
  'downloadAttachment' | 'markRead' |
  'beginGoogleConnection' | 'getGoogleConnection' | 'disconnectGoogle'>;

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
};
