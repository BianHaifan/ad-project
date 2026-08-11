import type {
  ApplicationStatus, ConversationView, CreateInterviewRequest, Dashboard, JobDraft,
  EmploymentType, JobStatus, PageMeta, RecruiterApplicationCounts, RecruiterApplicationDetail,
  RecruiterJobSummary, RecruiterProfile,
} from '../models/recruiter';

export type RecruiterTransitionStatus = Extract<ApplicationStatus, 'IN_REVIEW' | 'INTERVIEW' | 'REJECTED'>;
export interface ListApplicationsParams { status?: ApplicationStatus; search?: string; jobId?: string }
export interface ListJobsParams {
  q?: string;
  status?: JobStatus;
  employmentType?: EmploymentType;
  location?: string;
  ownerId?: string;
  page?: number;
  pageSize?: number;
}
export interface JobListResult { data: RecruiterJobSummary[]; meta: PageMeta }

export interface RecruiterRepository {
  signIn(email: string, password: string): Promise<RecruiterProfile>;
  register(fullName: string, companyName: string, email: string, password: string): Promise<RecruiterProfile>;
  getMe(): Promise<RecruiterProfile>;
  getDashboard(): Promise<Dashboard>;
  listJobs(params?: ListJobsParams): Promise<JobListResult>;
  getJob(jobId: string): Promise<RecruiterJobSummary>;
  createJob(input: JobDraft): Promise<RecruiterJobSummary>;
  publishJob(jobId: string, expectedVersion: number): Promise<RecruiterJobSummary>;
  getApplicationCounts(): Promise<RecruiterApplicationCounts>;
  listApplications(params: ListApplicationsParams): Promise<RecruiterApplicationDetail[]>;
  getApplication(applicationId: string): Promise<RecruiterApplicationDetail | undefined>;
  updateApplicationStatus(applicationId: string, status: RecruiterTransitionStatus): Promise<RecruiterApplicationDetail>;
  saveApplicationNote(applicationId: string, body: string): Promise<RecruiterApplicationDetail>;
  scheduleInterview(applicationId: string, request: CreateInterviewRequest): Promise<RecruiterApplicationDetail>;
  listConversations(): Promise<ConversationView[]>;
  getConversation(conversationId: string): Promise<ConversationView | undefined>;
  sendMessage(conversationId: string, body: string): Promise<MessageResult>;
}

export interface MessageResult { conversation: ConversationView }
