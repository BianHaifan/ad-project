import type {AuthClient} from './authClient';
import {authClient, AuthApiError} from './authClient';
import {apiPaths} from './contract';
import type {
  AgentConversation, AgentConversationSummary, AgentFieldChange, AgentRun,
  AgentRunStatus, AgentStep,
} from '../models/agent';
import type {ApplicationStatus} from '../models/recruiter';

export interface CreateAgentRunInput {
  instruction: string;
  conversationId?: string;
  jobId?: string;
  timezone?: string;
}

export interface ConfirmAgentRunInput {
  runId: string;
  confirmationId: string;
  expectedRunVersion: number;
  idempotencyKey: string;
}

export class AgentHttpClient {
  constructor(private readonly client: Pick<AuthClient, 'requestWithAuth'> = authClient) {}

  async createRun(input: CreateAgentRunInput): Promise<AgentRun> {
    const body: Record<string, string> = {instruction: input.instruction.trim()};
    if (input.conversationId) body.conversationId = input.conversationId;
    if (input.jobId) body.jobId = input.jobId;
    if (input.timezone) body.timezone = input.timezone;
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.agentRuns, {
      method: 'POST', body: JSON.stringify(body),
    });
    return parseRunEnvelope(payload);
  }

  async getRun(runId: string): Promise<AgentRun> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.agentRun(encodeURIComponent(runId)));
    return parseRunEnvelope(payload);
  }

  async cancelRun(runId: string): Promise<AgentRun> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.agentRunCancel(encodeURIComponent(runId)), {
      method: 'POST',
    });
    return parseRunEnvelope(payload);
  }

  async confirmRun(input: ConfirmAgentRunInput): Promise<AgentRun> {
    const payload = await this.client.requestWithAuth<unknown>(
      apiPaths.agentRunConfirm(encodeURIComponent(input.runId)), {
        method: 'POST',
        headers: {'Idempotency-Key': input.idempotencyKey},
        body: JSON.stringify({
          confirmationId: input.confirmationId,
          expectedRunVersion: input.expectedRunVersion,
        }),
      });
    return parseRunEnvelope(payload);
  }

  async startOutreachConversation(runId: string, candidateId: string): Promise<string> {
    const payload = await this.client.requestWithAuth<unknown>(
      apiPaths.agentRunOutreachConversation(encodeURIComponent(runId), encodeURIComponent(candidateId)),
      {method: 'POST'});
    if (!isRecord(payload) || !isRecord(payload.data) || typeof payload.data.conversationId !== 'string') {
      throw unexpectedResponse();
    }
    return payload.data.conversationId;
  }

  async listConversations(): Promise<AgentConversationSummary[]> {
    const payload = await this.client.requestWithAuth<unknown>(apiPaths.agentConversations);
    if (!isRecord(payload) || !Array.isArray(payload.data)) throw unexpectedResponse();
    return payload.data.map(parseConversationSummary);
  }

  async deleteConversation(conversationId: string): Promise<void> {
    await this.client.requestWithAuth<void>(apiPaths.agentConversation(encodeURIComponent(conversationId)), {
      method: 'DELETE',
    });
  }

  async getConversation(conversationId: string): Promise<AgentConversation> {
    const payload = await this.client.requestWithAuth<unknown>(
      apiPaths.agentConversation(encodeURIComponent(conversationId)));
    if (!isRecord(payload) || !isRecord(payload.data) || !Array.isArray(payload.data.runs) ||
        !(payload.data.conversationId === null || typeof payload.data.conversationId === 'string')) {
      throw unexpectedResponse();
    }
    return {conversationId: payload.data.conversationId, runs: payload.data.runs.map(parseRun)};
  }
}

function parseRunEnvelope(payload: unknown): AgentRun {
  if (!isRecord(payload) || !isRecord(payload.data)) throw unexpectedResponse();
  return parseRun(payload.data);
}

function parseRun(value: unknown): AgentRun {
  if (!isRecord(value) || typeof value.runId !== 'string' ||
      !(value.conversationId === null || typeof value.conversationId === 'string') ||
      typeof value.instruction !== 'string' || !isRunStatus(value.status) ||
      typeof value.confirmationStatus !== 'string' || !isTarget(value.target) ||
      !Array.isArray(value.steps) || !isPreview(value.preview) || !isResult(value.result) ||
      !isScreening(value.screening) || typeof value.message !== 'string' ||
      !(value.errorCode === null || typeof value.errorCode === 'string') ||
      typeof value.version !== 'number' || typeof value.createdAt !== 'string' ||
      typeof value.updatedAt !== 'string') throw unexpectedResponse();
  value.steps.forEach(parseStep);
  return value as unknown as AgentRun;
}

function parseStep(value: unknown): AgentStep {
  if (!isRecord(value) || typeof value.sequence !== 'number' || typeof value.type !== 'string' ||
      !(value.tool === null || typeof value.tool === 'string') || typeof value.status !== 'string' ||
      !(value.inputSummary === null || typeof value.inputSummary === 'string') ||
      !(value.outputSummary === null || typeof value.outputSummary === 'string') ||
      !(value.errorCode === null || typeof value.errorCode === 'string') ||
      typeof value.durationMs !== 'number' || typeof value.createdAt !== 'string') throw unexpectedResponse();
  return value as unknown as AgentStep;
}

function isTarget(value: unknown): boolean {
  return value === null || (isRecord(value) && typeof value.type === 'string' && typeof value.id === 'string');
}

function isPreview(value: unknown): boolean {
  if (value === null) return true;
  if (!isRecord(value) || typeof value.confirmationId !== 'string' || typeof value.targetType !== 'string' ||
      typeof value.targetId !== 'string' || typeof value.expectedVersion !== 'number' ||
      typeof value.expiresAt !== 'string' || !Array.isArray(value.changes)) return false;
  return value.changes.every(isFieldChange);
}

function isFieldChange(value: unknown): value is AgentFieldChange {
  return isRecord(value) && typeof value.field === 'string';
}

function isResult(value: unknown): boolean {
  if (value === null) return true;
  if (!isRecord(value) || typeof value.operation !== 'string' || typeof value.targetType !== 'string' ||
      typeof value.targetId !== 'string' || typeof value.previousVersion !== 'number' ||
      typeof value.newVersion !== 'number' || typeof value.completedAt !== 'string' ||
      !Array.isArray(value.appliedChanges)) return false;
  return value.appliedChanges.every(isFieldChange);
}

function isScreening(value: unknown): boolean {
  if (value === null) return true;
  if (!isRecord(value) || typeof value.jobId !== 'string' || typeof value.jobTitle !== 'string' ||
      !Array.isArray(value.ranked) || typeof value.message !== 'string') return false;
  return value.ranked.every(isRankedCandidate);
}

function isRankedCandidate(value: unknown): boolean {
  return isRecord(value) && typeof value.candidateId === 'string' &&
    (value.applicationId === null || typeof value.applicationId === 'string') &&
    typeof value.fullName === 'string' && (value.applicationStatus === null || isApplicationStatus(value.applicationStatus)) &&
    typeof value.rank === 'number' && isStringArray(value.strongMatches) && isStringArray(value.gaps) &&
    (value.recommendation === undefined || value.recommendation === null || typeof value.recommendation === 'string');
}

function parseConversationSummary(value: unknown): AgentConversationSummary {
  if (!isRecord(value) || typeof value.conversationId !== 'string' ||
      typeof value.lastInstruction !== 'string' || typeof value.lastMessage !== 'string' ||
      typeof value.updatedAt !== 'string') throw unexpectedResponse();
  return value as unknown as AgentConversationSummary;
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every(item => typeof item === 'string');
}

function isRunStatus(value: unknown): value is AgentRunStatus {
  return value === 'PROCESSING' || value === 'AWAITING_CONFIRMATION' || value === 'NEEDS_CLARIFICATION' ||
    value === 'NO_ACTION_REQUIRED' || value === 'FAILED' || value === 'CANCELLED' || value === 'EXECUTING' ||
    value === 'COMPLETED';
}

function isApplicationStatus(value: unknown): value is ApplicationStatus {
  return value === 'APPLIED' || value === 'IN_REVIEW' || value === 'INTERVIEW' || value === 'OFFERED' ||
    value === 'REJECTED' || value === 'WITHDRAWN';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function unexpectedResponse(): AuthApiError {
  return new AuthApiError(0, 'UNEXPECTED_RESPONSE', 'The server returned an unexpected response.');
}

export const agentHttpClient = new AgentHttpClient();
