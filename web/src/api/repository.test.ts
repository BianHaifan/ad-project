import {describe, expect, it} from 'vitest';
import {mockRecruiterRepository} from '../mocks/mockRecruiterRepository';
import {recruiterRepository} from './repository';
import {applicationHttpClient} from './applicationHttpClient';

describe('repository data-source boundaries', () => {
  it('keeps only dashboard and messages mocked while jobs and applications use real clients', () => {
    expect(recruiterRepository.getDashboard).toBe(mockRecruiterRepository.getDashboard);
    expect(recruiterRepository.listConversations).toBe(mockRecruiterRepository.listConversations);
    expect(recruiterRepository.listApplications).not.toBe(applicationHttpClient.listApplications);
    expect('listApplications' in mockRecruiterRepository).toBe(false);
    expect('getApplication' in mockRecruiterRepository).toBe(false);
    expect('updateApplicationStatus' in mockRecruiterRepository).toBe(false);
    expect('listJobs' in mockRecruiterRepository).toBe(false);
    expect('getJob' in mockRecruiterRepository).toBe(false);
    expect('createJob' in mockRecruiterRepository).toBe(false);
    expect('updateJob' in mockRecruiterRepository).toBe(false);
    expect('publishJob' in mockRecruiterRepository).toBe(false);
    expect('changeJobStatus' in mockRecruiterRepository).toBe(false);
  });
});
