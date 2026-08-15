import type {
  Company, EmploymentType, JobStatus, RecruiterApplicationDetail,
  RecruiterJobSummary, RecruiterProfile, ResumeSnapshot, User, WorkplaceType,
} from '../models/recruiter';

const now = '2026-08-09T01:42:00Z';

export const company: Company = {
  companyId: 'company_001', name: 'Moonshot AI', logoUrl: null, stage: 'SERIES_B',
  employeeRange: '500-999', verificationStatus: 'APPROVED', website: 'https://moonshot.example.com',
  description: 'AI product company', location: 'Singapore', version: 2,
  createdAt: '2026-01-01T00:00:00Z', updatedAt: now,
};

export const owner: User = {
  userId: 'rec_001', role: 'RECRUITER', fullName: 'Mia Chen', email: 'mia@moonshot.ai',
  avatarUrl: null, createdAt: '2026-01-01T00:00:00Z', updatedAt: now,
};

export const recruiter: RecruiterProfile = {
  userId: owner.userId, role: 'RECRUITER', fullName: owner.fullName, email: owner.email,
  company, createdAt: owner.createdAt, updatedAt: owner.updatedAt,
};

const makeJob = (jobId: string, title: string, employmentType: EmploymentType, workplaceType: WorkplaceType,
  location: string, status: JobStatus, applicantCount: number, index: number): RecruiterJobSummary => ({
  jobId, title, company, employmentType, workplaceType, location,
  salary: {min: 5000 + index * 200, max: 8000 + index * 200, currency: 'SGD', period: 'MONTH'},
  description: 'Build production services and reliable product capabilities.',
  requirements: ['Relevant engineering experience', 'Strong communication skills'],
  skills: index === 1 ? ['PyTorch', 'Python', 'Kubernetes'] : ['Python', 'TypeScript', 'Kubernetes'],
  deadline: '2026-09-30T15:59:59Z', visibility: 'PUBLIC', status,
  publishedAt: status === 'DRAFT' ? null : '2026-07-28T03:00:00Z', version: 1,
  createdAt: '2026-07-01T03:00:00Z', updatedAt: now, applicantCount, owner,
});

export const jobs: RecruiterJobSummary[] = [
  makeJob('job_001', 'AI Backend Engineer', 'FULL_TIME', 'HYBRID', 'Singapore', 'ACTIVE', 124, 0),
  makeJob('job_002', 'ML Platform Intern', 'INTERNSHIP', 'ONSITE', 'Singapore', 'DRAFT', 0, 1),
  makeJob('job_003', 'AI Product Engineer', 'FULL_TIME', 'REMOTE', 'Remote', 'PAUSED', 36, 2),
  makeJob('job_004', 'Data Engineer', 'FULL_TIME', 'HYBRID', 'Singapore', 'ACTIVE', 82, 3),
  makeJob('job_005', 'Backend Engineer', 'FULL_TIME', 'ONSITE', 'Singapore', 'ACTIVE', 51, 4),
];

const candidateRows = [
  ['cand_001', 'Yan Bohao', 'bohao.yan@example.com', 'Computer Science Student · AI Backend Engineer'],
  ['cand_002', 'Ava Zhang', 'ava.zhang@example.com', 'MLOps intern'],
  ['cand_003', 'Leo Wang', 'leo.wang@example.com', 'Full-stack AI tools engineer'],
  ['cand_004', 'Nina Wu', 'nina.wu@example.com', 'AI product engineer'],
  ['cand_005', 'Omar Chen', 'omar.chen@example.com', 'Backend engineering student'],
  ['cand_006', 'Emma Liu', 'emma.liu@example.com', 'ML infrastructure intern'],
  ['cand_007', 'Grace Xu', 'grace.xu@example.com', 'Backend AI engineer'],
] as const;

const snapshot = (index: number): ResumeSnapshot => ({
  snapshotId: `snapshot_${index + 1}`, capturedAt: now, resumeId: `resume_${index + 1}`,
  fullName: candidateRows[index][1], age: 24, location: 'Singapore', headline: candidateRows[index][3],
  summary: 'Backend-focused engineer building production AI services and evaluation pipelines.',
  experiences: [{experienceId: `exp_${index + 1}`, title: 'AI Engineering Intern', company: 'ByteLab',
    description: 'Implemented backend APIs, vector search experiments, and monitoring dashboards.',
    startDate: '2025-06', endDate: '2025-12'}],
  version: 3, createdAt: '2026-01-10T02:00:00Z', updatedAt: now,
});

const makeApplication = (
  applicationId: string, candidateIndex: number, jobId: string,
  status: RecruiterApplicationDetail['status'], matchScore: number, appliedAt: string,
): RecruiterApplicationDetail => {
  const candidate = candidateRows[candidateIndex];
  const job = jobs.find(item => item.jobId === jobId) ?? jobs[0];
  const reviewAt = status === 'APPLIED' ? null : '2026-08-09T03:00:00Z';
  const timeline: RecruiterApplicationDetail['timeline'] = [
    {eventId: `${applicationId}_applied`, actorId: candidate[0], companyId: company.companyId,
      fromStatus: null, toStatus: 'APPLIED', occurredAt: appliedAt, reason: 'Application submitted', requestId: `req_${applicationId}_1`},
  ];
  if (reviewAt) timeline.push({eventId: `${applicationId}_review`, actorId: owner.userId, companyId: company.companyId,
    fromStatus: 'APPLIED', toStatus: status === 'REJECTED' ? 'REJECTED' : 'IN_REVIEW', occurredAt: reviewAt,
    reason: 'Hiring team review', requestId: `req_${applicationId}_2`});
  if (status === 'INTERVIEW') timeline.push({eventId: `${applicationId}_interview`, actorId: owner.userId, companyId: company.companyId,
    fromStatus: 'IN_REVIEW', toStatus: 'INTERVIEW', occurredAt: '2026-08-10T03:00:00Z',
    reason: 'Interview scheduled', requestId: `req_${applicationId}_3`});
  return {
    applicationId, jobId, status, appliedAt, updatedAt: now, version: timeline.length,
    candidate: {candidateId: candidate[0], fullName: candidate[1], email: candidate[2], headline: candidate[3], avatarUrl: null, location: 'Singapore'},
    jobTitle: job.title, matchScore, owner, resumeSnapshot: snapshot(candidateIndex), timeline,
    matchAnalysis: {score: matchScore, evidence: ['Python / API experience', 'AI product delivery'],
      strongMatches: ['Python / FastAPI', 'LLM / RAG'], gaps: ['Latency optimization evidence'], modelVersion: 'v1.0', generatedAt: now},
    interview: status === 'INTERVIEW' ? {interviewId: `interview_${applicationId}`, applicationId,
      scheduledAt: '2026-08-11T06:00:00Z', timezone: 'Asia/Singapore', durationMinutes: 30, mode: 'ONLINE',
      locationOrMeetingUrl: 'https://meet.example.com/interview', note: null, status: 'SCHEDULED', version: 1,
      meetingProvider: 'MANUAL', meetingSyncStatus: 'NOT_APPLICABLE', createdAt: now, updatedAt: now} : null,
    notes: applicationId === 'app_001' ? [{noteId: 'note_001', author: owner,
      body: 'Strong backend projects; ask about production latency.', createdAt: now, updatedAt: now}] : [],
  };
};

export const applications: RecruiterApplicationDetail[] = [
  makeApplication('app_001', 0, 'job_001', 'APPLIED', 96, '2026-08-09T01:42:00Z'),
  makeApplication('app_002', 1, 'job_001', 'IN_REVIEW', 92, '2026-08-08T07:00:00Z'),
  makeApplication('app_003', 2, 'job_002', 'INTERVIEW', 88, '2026-08-02T07:00:00Z'),
  makeApplication('app_004', 3, 'job_003', 'REJECTED', 84, '2026-08-01T07:00:00Z'),
  makeApplication('app_005', 4, 'job_001', 'APPLIED', 81, '2026-07-31T07:00:00Z'),
  makeApplication('app_006', 5, 'job_002', 'APPLIED', 89, '2026-08-09T00:15:00Z'),
  makeApplication('app_007', 6, 'job_001', 'INTERVIEW', 91, '2026-08-01T07:00:00Z'),
];
