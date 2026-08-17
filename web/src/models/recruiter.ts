export type ISODateTime = string;
export type UserRole = 'CANDIDATE' | 'RECRUITER' | 'ADMIN';
export type ApplicationStatus = 'APPLIED' | 'IN_REVIEW' | 'INTERVIEW' | 'OFFERED' | 'REJECTED' | 'WITHDRAWN';
export type JobStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'CLOSED';
export type InterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';
export type InterviewMode = 'ONLINE' | 'ONSITE' | 'PHONE';
export type MeetingProvider = 'MANUAL' | 'GOOGLE_MEET';
export type MeetingSyncStatus = 'NOT_APPLICABLE' | 'PENDING' | 'READY' | 'FAILED';
export type GoogleConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'REVOKED';
export type SenderType = 'CANDIDATE' | 'RECRUITER' | 'SYSTEM';
export type EmploymentType = 'FULL_TIME' | 'INTERNSHIP' | 'PART_TIME';
export type WorkplaceType = 'ONSITE' | 'HYBRID' | 'REMOTE';
export type Visibility = 'PUBLIC' | 'PRIVATE';

export interface DataEnvelope<T> { data: T }
export interface ListEnvelope<T, M = PageMeta> { data: T[]; meta: M }
export interface ApiError {
  code: string;
  message: string;
  fieldErrors: Record<string, string>;
  requestId: string;
}
export interface ErrorEnvelope { error: ApiError }
export interface PageMeta { page: number; pageSize: number; total: number; hasNext: boolean }

export interface User {
  userId: string;
  role: UserRole;
  fullName: string;
  email: string;
  avatarUrl: string | null;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface Company {
  companyId: string;
  name: string;
  logoUrl: string | null;
  stage: 'PRE_SEED' | 'SEED' | 'SERIES_A' | 'SERIES_B' | 'SERIES_C' | 'LATE_STAGE' | 'PUBLIC' | 'BOOTSTRAPPED' | null;
  employeeRange: string | null;
  verificationStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | null;
  website: string | null;
  description: string | null;
  location: string | null;
  version: number;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}
export interface RecruiterCompanySummary {
  companyId: string;
  name: string;
  logoUrl: string | null;
  verificationStatus: CompanyVerificationStatus | null;
}

export interface RecruiterProfile {
  userId: string;
  role: 'RECRUITER';
  fullName: string;
  avatarUrl?: string | null;
  title?: string;
  bio?: string | null;
  email: string;
  company: Company;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface RecruiterProfileDetail {
  userId: string;
  fullName: string;
  avatarUrl: string | null;
  title: string;
  bio: string | null;
  company: RecruiterCompanySummary;
  email: string;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface UpdateRecruiterProfileInput {
  fullName?: string;
  title?: string;
  bio?: string | null;
}

export interface RecruiterContact {
  recruiterId: string;
  fullName: string;
  title: string;
  avatarUrl: string | null;
}

export interface Salary { min: number; max: number; currency: 'SGD'; period: 'HOUR' | 'MONTH' | 'YEAR' }

export interface Job {
  jobId: string;
  title: string;
  company: Company;
  employmentType: EmploymentType;
  workplaceType: WorkplaceType;
  location: string;
  salary: Salary;
  description: string;
  requirements: string[];
  skills: string[];
  deadline: ISODateTime | null;
  visibility: Visibility;
  status: JobStatus;
  publishedAt: ISODateTime | null;
  version: number;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface RecruiterJobSummary extends Job {
  applicantCount: number;
  owner: User | null;
}

export interface CandidateSummary {
  candidateId: string;
  fullName: string;
  email: string;
  headline: string | null;
  avatarUrl: string | null;
  location: string | null;
}

export interface Experience {
  experienceId: string | null;
  title: string;
  company: string;
  description: string;
  startDate: string;
  endDate: string | null;
}

export interface ResumeSnapshot {
  snapshotId: string;
  capturedAt: ISODateTime;
  resumeId: string;
  fullName: string;
  age: number;
  location: string;
  headline: string;
  summary: string;
  experiences: Experience[];
  version: number;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface AuditEvent {
  eventId: string;
  actorId: string;
  companyId: string | null;
  fromStatus: ApplicationStatus | null;
  toStatus: ApplicationStatus | null;
  occurredAt: ISODateTime;
  reason: string | null;
  requestId: string;
}

export interface MatchAnalysis {
  score: number;
  evidence: string[];
  strongMatches: string[];
  gaps: string[];
  modelVersion: string;
  generatedAt: ISODateTime;
}

export interface ApplicantCandidateSummary {
  candidateId: string;
  fullName: string;
  headline: string | null;
  avatarUrl: string | null;
  location: string | null;
}

export interface ApplicantMatchAnalysis {
  strongMatches: string[];
  gaps: string[];
  evidence: string[];
}

export interface RecommendedApplicant {
  applicationId: string;
  candidate: ApplicantCandidateSummary;
  status: ApplicationStatus;
  appliedAt: ISODateTime;
  matchScore: number;
  rank: number;
  matchAnalysis: ApplicantMatchAnalysis;
}

export type RecommendationSource = 'MODEL' | 'FALLBACK' | 'NONE';
export type RecommendationModelStatus = 'ACTIVE' | 'DEGRADED' | 'NOT_APPLICABLE';

export interface RecommendationMeta {
  source: RecommendationSource;
  modelVersion: string;
  featureVersion: string;
  modelStatus: RecommendationModelStatus;
  inferenceMs: number;
  generatedAt: ISODateTime;
  page: number;
  pageSize: number;
  total: number;
  hasNext: boolean;
}

export interface RecommendedApplicantListResult {
  data: RecommendedApplicant[];
  meta: RecommendationMeta;
}

export interface RecruiterNote {
  noteId: string;
  author: User;
  body: string;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface Interview {
  interviewId: string;
  applicationId: string;
  scheduledAt: ISODateTime;
  timezone: string;
  durationMinutes: number;
  mode: InterviewMode;
  locationOrMeetingUrl: string | null;
  note: string | null;
  status: InterviewStatus;
  version: number;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
  meetingProvider: MeetingProvider;
  meetingSyncStatus: MeetingSyncStatus;
}

export interface RecruiterApplicationSummary {
  applicationId: string;
  jobId: string;
  status: ApplicationStatus;
  appliedAt: ISODateTime;
  updatedAt: ISODateTime;
  version: number;
  candidate: CandidateSummary;
  jobTitle: string;
  matchScore: number | null;
  owner: User | null;
}

export interface RecruiterApplicationDetail extends RecruiterApplicationSummary {
  resumeSnapshot: ResumeSnapshot;
  timeline: AuditEvent[];
  matchAnalysis: MatchAnalysis | null;
  interview: Interview | null;
  notes: RecruiterNote[];
}

export interface MessageAttachment {
  attachmentId: string;
  fileName: string;
  sizeBytes: number;
  contentType: string;
}

export interface Message {
  messageId: string;
  conversationId: string;
  body: string;
  senderType: SenderType;
  sentAt: ISODateTime;
  clientMessageId: string | null;
  deliveryStatus: 'SENDING' | 'SENT' | 'DELIVERED' | 'READ' | 'FAILED';
  attachment: MessageAttachment | null;
}

export interface ConversationParticipant {
  userId: string;
  fullName: string;
  avatarUrl: string | null;
  title: string | null;
  company: Company | null;
  online: boolean;
}

export interface ConversationSummary {
  conversationId: string;
  applicationId: string;
  jobId: string;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
  participant: ConversationParticipant;
  lastMessage: Message | null;
  unreadCount: number;
  jobTitle: string;
}

export interface ConversationDetail {
  conversationId: string;
  applicationId: string;
  jobId: string;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
  participant: ConversationParticipant;
  context: InterviewContext | null;
}

export interface InterviewContext {
  type: 'INTERVIEW_INVITATION';
  interviewId: string;
  applicationId: string;
  jobId: string;
  jobTitle: string;
  scheduledAt: ISODateTime;
  mode: InterviewMode;
  timezone: string;
  durationMinutes: number;
  locationOrMeetingUrl: string | null;
  status: InterviewStatus;
}

export interface MessageListMeta { nextCursor: string | null; hasMore: boolean }
export interface MessageListResult { data: Message[]; meta: MessageListMeta }
export interface ConversationListResult { data: ConversationSummary[]; meta: PageMeta }

export type CompanyVerificationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface DashboardMetrics {
  activeJobs: number;
  appliedApplications: number;
  inReviewApplications: number;
  interviewApplications: number;
  companyVerificationStatus: CompanyVerificationStatus;
}

export interface Dashboard {
  metrics: DashboardMetrics;
  recentApplications: RecruiterApplicationSummary[];
  recentJobs: RecruiterJobSummary[];
}

export interface RecruiterApplicationCounts {
  applied: number;
  inReview: number;
  interview: number;
  offered: number;
  rejected: number;
}

export interface RecruiterApplicationListMeta extends PageMeta { counts: RecruiterApplicationCounts }
export interface RecruiterApplicationListResult {
  data: RecruiterApplicationSummary[];
  meta: RecruiterApplicationListMeta;
}

export interface CreateJobRequest {
  title: string;
  employmentType: EmploymentType;
  workplaceType: WorkplaceType;
  location: string;
  salary: Salary;
  description: string;
  requirements: string[];
  skills: string[];
  deadline: ISODateTime | null;
  visibility: Visibility;
}

export interface JobDraft {
  title: string;
  employmentType: EmploymentType;
  workplaceType: WorkplaceType;
  location: string;
  salaryMin: number;
  salaryMax: number;
  description: string;
  requirements: string;
  skills: string[];
  deadline: string;
  visibility: Visibility;
}

export interface UpdateCompanyRequest {
  name?: string;
  website?: string;
  description?: string;
  logoAssetId?: string;
  location?: string;
  expectedVersion: number;
}

export interface ApplicationTransitionRequest { toStatus: 'IN_REVIEW' | 'OFFERED' | 'REJECTED'; reason: string; expectedVersion: number }
export interface CreateNoteRequest { body: string }
export interface CreateInterviewRequest {
  scheduledAt: ISODateTime;
  timezone: string;
  durationMinutes: number;
  mode: InterviewMode;
  locationOrMeetingUrl?: string;
  note?: string;
  meetingProvider?: MeetingProvider;
  expectedApplicationVersion: number;
}
export interface UpdateInterviewRequest {
  scheduledAt?: ISODateTime;
  timezone?: string;
  durationMinutes?: number;
  mode?: InterviewMode;
  locationOrMeetingUrl?: string;
  note?: string;
  status?: InterviewStatus;
  expectedVersion: number;
}
export interface SendMessageRequest { body: string; clientMessageId: string }

export interface GoogleAuthorizeResponse { authorizationUrl: string }
export interface GoogleConnection {
  connected: boolean;
  status: GoogleConnectionStatus;
  connectedAt: ISODateTime | null;
}

export interface AvatarMetadata {
  userId: string;
  avatarUrl: string;
  contentType: 'image/png' | 'image/jpeg';
  sizeBytes: number;
  updatedAt: ISODateTime;
}
