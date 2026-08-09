import {describe, expect, it} from 'vitest';
import {applications, jobs} from './data';

describe('OpenAPI-shaped mock data', () => {
  it('provides complete resume snapshots and distinct owner semantics', () => {
    for (const application of applications) {
      expect(application.resumeSnapshot.snapshotId).not.toBe('');
      expect(application.resumeSnapshot.capturedAt.endsWith('Z')).toBe(true);
      expect(application.resumeSnapshot.experiences.length).toBeGreaterThan(0);
      expect(application.owner?.userId).toBe('rec_001');
    }
  });

  it('uses contract currency, timestamps, and IDs', () => {
    for (const job of jobs) {
      expect(job.jobId).toMatch(/^job_/);
      expect(job.salary.currency).toBe('SGD');
      expect(job.createdAt.endsWith('Z')).toBe(true);
    }
  });
});
