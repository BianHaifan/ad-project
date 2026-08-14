import {describe, expect, it} from 'vitest';
import {mockRecruiterRepository} from '../mocks/mockRecruiterRepository';
import {recruiterRepository} from './repository';

describe('repository data-source boundaries', () => {
  it('keeps only auth mocked while dashboard, jobs, applications, and conversations use real clients', () => {
    expect('getDashboard' in mockRecruiterRepository).toBe(false);
    expect('listConversations' in mockRecruiterRepository).toBe(false);
    expect('getConversation' in mockRecruiterRepository).toBe(false);
    expect('listMessages' in mockRecruiterRepository).toBe(false);
    expect('sendMessage' in mockRecruiterRepository).toBe(false);
    expect('markRead' in mockRecruiterRepository).toBe(false);
    expect(typeof recruiterRepository.listConversations).toBe('function');
    expect(typeof recruiterRepository.getConversation).toBe('function');
    expect(typeof recruiterRepository.listMessages).toBe('function');
    expect(typeof recruiterRepository.sendMessage).toBe('function');
    expect(typeof recruiterRepository.markRead).toBe('function');
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
