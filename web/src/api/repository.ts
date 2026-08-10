import {mockRecruiterRepository} from '../mocks/mockRecruiterRepository';
import type {RecruiterRepository} from './recruiterRepository';

export type RecruiterBusinessRepository = Omit<RecruiterRepository, 'signIn' | 'register'>;

// Auth uses authClient. Dashboard, jobs, applications, and messages remain intentionally mocked.
export const recruiterRepository: RecruiterBusinessRepository = mockRecruiterRepository;
