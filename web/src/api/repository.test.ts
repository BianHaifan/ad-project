import {describe, expect, it} from 'vitest';
import {mockRecruiterRepository} from '../mocks/mockRecruiterRepository';
import {recruiterRepository} from './repository';

describe('repository data-source boundaries', () => {
  it('keeps dashboard, applications, and messages mocked while jobs are not exposed by the mock', () => {
    expect(recruiterRepository.getDashboard).toBe(mockRecruiterRepository.getDashboard);
    expect(recruiterRepository.listApplications).toBe(mockRecruiterRepository.listApplications);
    expect(recruiterRepository.listConversations).toBe(mockRecruiterRepository.listConversations);
    expect('listJobs' in mockRecruiterRepository).toBe(false);
    expect('getJob' in mockRecruiterRepository).toBe(false);
    expect('createJob' in mockRecruiterRepository).toBe(false);
    expect('publishJob' in mockRecruiterRepository).toBe(false);
  });
});
