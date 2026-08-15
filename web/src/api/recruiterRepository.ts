import type {
  ApplicationStatus, ConversationDetail, ConversationListResult, CreateInterviewRequest, Dashboard,
  EmploymentType, GoogleAuthorizeResponse, GoogleConnection, Interview, JobDraft, JobStatus, Message,
  MessageListResult, PageMeta, RecruiterApplicationDetail, RecruiterApplicationListResult, RecruiterJobSummary,
  RecruiterProfile, UpdateInterviewRequest,
} from '../models/recruiter';

export type RecruiterTransitionStatus = Extract<ApplicationStatus, 'IN_REVIEW' | 'REJECTED'>;
export type RecruiterJobStatusTarget = Extract<JobStatus, 'ACTIVE' | 'PAUSED' | 'CLOSED'>;
export type ApplicationSort = 'appliedAt,desc' | 'appliedAt,asc' | 'updatedAt,desc' | 'updatedAt,asc';
export interface ListApplicationsParams {
  status?: ApplicationStatus;
  q?: string;
  jobId?: string;
  page?: number;
  pageSize?: number;
  sort?: ApplicationSort;
}
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
  updateJob(jobId: string, input: JobDraft, expectedVersion: number): Promise<RecruiterJobSummary>;
  publishJob(jobId: string, expectedVersion: number): Promise<RecruiterJobSummary>;
  changeJobStatus(jobId: string, status: RecruiterJobStatusTarget, reason: string,
                  expectedVersion: number): Promise<RecruiterJobSummary>;
  listApplications(params?: ListApplicationsParams): Promise<RecruiterApplicationListResult>;
  getApplication(applicationId: string): Promise<RecruiterApplicationDetail>;
  updateApplicationStatus(applicationId: string, status: RecruiterTransitionStatus, reason: string,
                          expectedVersion: number): Promise<RecruiterApplicationDetail>;
  createInterview(applicationId: string, input: CreateInterviewRequest): Promise<Interview>;
  updateInterview(interviewId: string, input: UpdateInterviewRequest): Promise<Interview>;
  listConversations(): Promise<ConversationListResult>;
  getConversation(conversationId: string): Promise<ConversationDetail>;
  listMessages(conversationId: string): Promise<MessageListResult>;
  sendMessage(conversationId: string, body: string): Promise<Message>;
  markRead(conversationId: string, lastReadMessageId: string): Promise<void>;
  beginGoogleConnection(): Promise<GoogleAuthorizeResponse>;
  getGoogleConnection(): Promise<GoogleConnection>;
  disconnectGoogle(): Promise<void>;
}
