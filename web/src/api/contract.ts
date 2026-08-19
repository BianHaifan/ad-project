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
  passwordResetRequest: '/auth/password-reset/request',
  passwordResetConfirm: '/auth/password-reset/confirm',
  adminMe: '/admin/me',
  adminUsers: '/admin/users',
  adminUser: (userId: string) => `/admin/users/${userId}`,
  adminUserStatus: (userId: string) => `/admin/users/${userId}/status`,
  adminAccess: (userId: string) => `/admin/users/${userId}/admin-access`,
  companyReviews: '/admin/company-reviews',
  companyReview: (companyId: string) => `/admin/company-reviews/${companyId}`,
  updateCompany: (companyId: string) => `/admin/companies/${companyId}`,
  approveCompany: (companyId: string) => `/admin/companies/${companyId}/approve`,
  rejectCompany: (companyId: string) => `/admin/companies/${companyId}/reject`,
  auditEvents: '/admin/audit-events',
  me: '/recruiter/me',
  avatar: '/profile/avatar',
  recruiterProfile: '/recruiter/profile',
  company: '/recruiter/company',
  dashboard: '/recruiter/dashboard',
  jobs: '/recruiter/jobs',
  job: (jobId: string) => `/recruiter/jobs/${jobId}`,
  publishJob: (jobId: string) => `/recruiter/jobs/${jobId}/publish`,
  jobStatus: (jobId: string) => `/recruiter/jobs/${jobId}/status`,
  applications: '/recruiter/applications',
  applicantRecommendations: (jobId: string) => `/recruiter/jobs/${jobId}/applicant-recommendations`,
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
  agentRuns: '/agent/runs',
  agentRun: (runId: string) => `/agent/runs/${runId}`,
  agentRunConfirm: (runId: string) => `/agent/runs/${runId}/confirm`,
  agentRunCancel: (runId: string) => `/agent/runs/${runId}/cancel`,
  agentConversations: '/agent/conversations',
  agentConversation: (conversationId: string) => `/agent/conversations/${conversationId}`,
  communityPosts: '/community/posts',
  communityPost: (postId: string) => `/community/posts/${postId}`,
  communityLike: (postId: string) => `/community/posts/${postId}/like`,
  communityComments: (postId: string) => `/community/posts/${postId}/comments`,
  communityDirectConversation: (postId: string) => `/community/posts/${postId}/direct-conversation`,
  communityDirectConversationDetail: (id: string) => `/community/direct-conversations/${id}`,
  communityDirectMessages: (id: string) => `/community/direct-conversations/${id}/messages`,
} as const;

export function readData<T>(response: DataEnvelope<T>): T { return response.data }
export function readList<T, M>(response: ListEnvelope<T, M>): {data: T[]; meta: M} { return response }
export function readApiError(response: ErrorEnvelope) { return response.error }
