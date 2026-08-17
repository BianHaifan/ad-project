import type {DataEnvelope, ErrorEnvelope, ListEnvelope} from '../models/recruiter';

const configuredBaseUrl = (
  import.meta as ImportMeta & { env?: { VITE_API_BASE_URL?: string } }
).env?.VITE_API_BASE_URL?.trim();
export const API_BASE_URL = (configuredBaseUrl || '/api/v1').replace(/\/$/, '');

export const apiPaths = {
  register: '/auth/register',
  login: '/auth/login',
  refresh: '/auth/refresh',
  logout: '/auth/logout',
  me: '/recruiter/me',
  recruiterProfile: '/recruiter/profile',
  company: '/recruiter/company',
  dashboard: '/recruiter/dashboard',
  jobs: '/recruiter/jobs',
  job: (jobId: string) => `/recruiter/jobs/${jobId}`,
  publishJob: (jobId: string) => `/recruiter/jobs/${jobId}/publish`,
  jobStatus: (jobId: string) => `/recruiter/jobs/${jobId}/status`,
  applications: '/recruiter/applications',
  application: (applicationId: string) => `/recruiter/applications/${applicationId}`,
  transitions: (applicationId: string) => `/recruiter/applications/${applicationId}/transitions`,
  owner: (applicationId: string) => `/recruiter/applications/${applicationId}/owner`,
  notes: (applicationId: string) => `/recruiter/applications/${applicationId}/notes`,
  resumeSnapshot: (applicationId: string) => `/recruiter/applications/${applicationId}/resume-snapshot`,
  resumeSnapshotPdf: (applicationId: string) => `/recruiter/applications/${applicationId}/resume-snapshot/pdf`,
  interviews: (applicationId: string) => `/recruiter/applications/${applicationId}/interviews`,
  interview: (interviewId: string) => `/recruiter/interviews/${interviewId}`,
  conversations: '/recruiter/conversations',
  conversation: (conversationId: string) => `/recruiter/conversations/${conversationId}`,
  messages: (conversationId: string) => `/recruiter/conversations/${conversationId}/messages`,
  messageAttachmentUpload: (conversationId: string) => `/recruiter/conversations/${conversationId}/messages/attachment`,
  messageAttachmentDownload: (conversationId: string, messageId: string) =>
    `/recruiter/conversations/${conversationId}/messages/${messageId}/attachment`,
  readState: (conversationId: string) => `/recruiter/conversations/${conversationId}/read-state`,
  googleOAuthAuthorize: '/recruiter/google-oauth/authorize',
  googleOAuthStatus: '/recruiter/google-oauth/status',
  googleOAuth: '/recruiter/google-oauth',
  communityPosts: '/community/posts',
  communityPost: (postId: string) => `/community/posts/${postId}`,
  communityLike: (postId: string) => `/community/posts/${postId}/like`,
  communityComments: (postId: string) => `/community/posts/${postId}/comments`,
} as const;

export function readData<T>(response: DataEnvelope<T>): T { return response.data }
export function readList<T, M>(response: ListEnvelope<T, M>): {data: T[]; meta: M} { return response }
export function readApiError(response: ErrorEnvelope) { return response.error }
