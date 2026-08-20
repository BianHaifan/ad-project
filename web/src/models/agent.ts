import type {ApplicationStatus} from './recruiter';

export type AgentRunStatus = 'PROCESSING' | 'AWAITING_CONFIRMATION' | 'NEEDS_CLARIFICATION' |
  'NO_ACTION_REQUIRED' | 'FAILED' | 'CANCELLED' | 'EXECUTING' | 'COMPLETED';
export type AgentConfirmationStatus = 'NOT_REQUIRED' | 'PENDING' | 'EXPIRED';

export interface AgentTarget { type: string; id: string }

export interface AgentFieldChange { field: string; oldValue: unknown; newValue: unknown }

export interface AgentPreview {
  confirmationId: string;
  targetType: string;
  targetId: string;
  expectedVersion: number;
  expiresAt: string;
  changes: AgentFieldChange[];
}

export interface AgentQueryResult {
  section: string | null;
  summary: string | null;
  skills: string[];
  experiences: unknown[];
}

export interface AgentExecutionResult {
  operation: string;
  targetType: string;
  targetId: string;
  previousVersion: number;
  newVersion: number;
  completedAt: string;
  appliedChanges: AgentFieldChange[];
  queryResult: AgentQueryResult | null;
}

export interface AgentStep {
  sequence: number;
  type: string;
  tool: string | null;
  status: string;
  inputSummary: string | null;
  outputSummary: string | null;
  errorCode: string | null;
  durationMs: number;
  createdAt: string;
}

export interface AgentRankedCandidate {
  candidateId: string;
  applicationId: string | null;
  fullName: string;
  applicationStatus: ApplicationStatus | null;
  rank: number;
  strongMatches: string[];
  gaps: string[];
  recommendation?: string | null;
}

export interface AgentScreeningResult {
  jobId: string;
  jobTitle: string;
  ranked: AgentRankedCandidate[];
  message: string;
}

export interface AgentRun {
  runId: string;
  conversationId: string | null;
  instruction: string;
  status: AgentRunStatus;
  confirmationStatus: AgentConfirmationStatus;
  target: AgentTarget | null;
  steps: AgentStep[];
  preview: AgentPreview | null;
  result: AgentExecutionResult | null;
  screening: AgentScreeningResult | null;
  message: string;
  errorCode: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface AgentConversation { conversationId: string | null; runs: AgentRun[] }

export interface AgentConversationSummary {
  conversationId: string;
  lastInstruction: string;
  lastMessage: string;
  updatedAt: string;
}
