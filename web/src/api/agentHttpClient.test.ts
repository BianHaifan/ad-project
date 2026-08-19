import {describe, expect, it, vi} from 'vitest';
import type {AuthClient} from './authClient';
import {AuthApiError} from './authClient';
import {AgentHttpClient} from './agentHttpClient';
import type {AgentConversationSummary, AgentRun} from '../models/agent';

const screeningRun: AgentRun = {
  runId: 'run-1', conversationId: 'conv-1', instruction: 'Screen candidates for the Backend Engineer role',
  status: 'COMPLETED', confirmationStatus: 'NOT_REQUIRED', target: {type: 'JOB', id: 'job-1'},
  steps: [{sequence: 1, type: 'TOOL', tool: 'screen_applicants', status: 'SUCCEEDED',
    inputSummary: 'jobId=job-1', outputSummary: 'ranked=2', errorCode: null, durationMs: 12,
    createdAt: '2026-08-18T08:00:00Z'}],
  preview: null, result: null,
  screening: {
    jobId: 'job-1', jobTitle: 'Backend Engineer',
    ranked: [
      {candidateId: 'cand-1', applicationId: 'app-1', fullName: 'Ada Lovelace', applicationStatus: 'IN_REVIEW',
        rank: 1, strongMatches: ['Python'], gaps: [],
        recommendation: 'Ada is the top pick for her hands-on Python experience.'},
      {candidateId: 'cand-2', applicationId: null, fullName: 'Grace Hopper', applicationStatus: null,
        rank: 2, strongMatches: [], gaps: ['No Python']},
    ],
    message: 'Ada is the best fit.',
  },
  message: 'Screening finished.', errorCode: null, version: 1,
  createdAt: '2026-08-18T08:00:00Z', updatedAt: '2026-08-18T08:00:00Z',
};

const previewRun: AgentRun = {
  runId: 'run-2', conversationId: 'conv-1', instruction: 'Schedule an interview with the #1 candidate',
  status: 'AWAITING_CONFIRMATION', confirmationStatus: 'PENDING', target: null, steps: [], result: null,
  screening: null,
  preview: {
    confirmationId: 'conf-1', targetType: 'APPLICATION', targetId: 'app-1', expectedVersion: 2,
    expiresAt: '2026-08-18T08:15:00Z',
    changes: [{field: 'mode', oldValue: null, newValue: 'ONLINE'}],
  },
  message: 'I can prepare an interview preview.', errorCode: null, version: 1,
  createdAt: '2026-08-18T08:00:00Z', updatedAt: '2026-08-18T08:00:00Z',
};

const summaries: AgentConversationSummary[] = [
  {conversationId: 'conv-1', lastInstruction: 'Screen candidates', lastMessage: 'Screening finished.',
    updatedAt: '2026-08-18T08:00:00Z'},
];

function setup(result: unknown) {
  const requestWithAuth = vi.fn().mockResolvedValue(result);
  return {requestWithAuth, client: new AgentHttpClient({requestWithAuth} as Pick<AuthClient, 'requestWithAuth'>)};
}

describe('AgentHttpClient', () => {
  it('creates a run with only the supplied contract fields', async () => {
    const {client, requestWithAuth} = setup({data: previewRun});
    await expect(client.createRun({
      instruction: ' Schedule an interview with the #1 candidate ', conversationId: 'conv-1', timezone: 'Asia/Shanghai',
    })).resolves.toEqual(previewRun);
    expect(requestWithAuth).toHaveBeenCalledWith('/agent/runs', {
      method: 'POST',
      body: JSON.stringify({
        instruction: 'Schedule an interview with the #1 candidate', conversationId: 'conv-1', timezone: 'Asia/Shanghai',
      }),
    });
    expect(Object.keys(JSON.parse(String((requestWithAuth.mock.calls[0][1] as RequestInit).body))))
      .toEqual(['instruction', 'conversationId', 'timezone']);
  });

  it('creates a first run with the job context and no conversation', async () => {
    const {client, requestWithAuth} = setup({data: screeningRun});
    await expect(client.createRun({instruction: 'Screen candidates', jobId: 'job-1'}))
      .resolves.toEqual(screeningRun);
    expect(requestWithAuth).toHaveBeenCalledWith('/agent/runs', {
      method: 'POST', body: JSON.stringify({instruction: 'Screen candidates', jobId: 'job-1'}),
    });
  });

  it('loads a run and rejects a malformed one', async () => {
    const {client, requestWithAuth} = setup({data: screeningRun});
    await expect(client.getRun('run/1')).resolves.toEqual(screeningRun);
    expect(requestWithAuth).toHaveBeenCalledWith('/agent/runs/run%2F1');
    const broken = setup({data: {runId: 'run-1'}});
    await expect(broken.client.getRun('run-1')).rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
  });

  it('confirms a preview with an idempotency key', async () => {
    const completed = {...previewRun, status: 'COMPLETED' as const, confirmationStatus: 'NOT_REQUIRED' as const,
      preview: null, result: {operation: 'SCHEDULE_INTERVIEW', targetType: 'APPLICATION', targetId: 'app-1',
        previousVersion: 2, newVersion: 1, completedAt: '2026-08-18T08:01:00Z', appliedChanges: [],
        queryResult: null}};
    const {client, requestWithAuth} = setup({data: completed});
    await expect(client.confirmRun({
      runId: 'run-2', confirmationId: 'conf-1', expectedRunVersion: 1, idempotencyKey: 'key-9',
    })).resolves.toEqual(completed);
    expect(requestWithAuth).toHaveBeenCalledWith('/agent/runs/run-2/confirm', {
      method: 'POST',
      headers: {'Idempotency-Key': 'key-9'},
      body: JSON.stringify({confirmationId: 'conf-1', expectedRunVersion: 1}),
    });
  });

  it('cancels a run without a body', async () => {
    const {client, requestWithAuth} = setup({data: {...previewRun, status: 'CANCELLED'}});
    await expect(client.cancelRun('run-2')).resolves.toMatchObject({status: 'CANCELLED'});
    expect(requestWithAuth).toHaveBeenCalledWith('/agent/runs/run-2/cancel', {method: 'POST'});
  });

  it('lists conversations and rejects malformed summaries', async () => {
    const {client, requestWithAuth} = setup({data: summaries});
    await expect(client.listConversations()).resolves.toEqual(summaries);
    expect(requestWithAuth).toHaveBeenCalledWith('/agent/conversations');
    const broken = setup({data: [{conversationId: 'conv-1'}]});
    await expect(broken.client.listConversations()).rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
  });

  it('loads a conversation with its runs and rejects malformed payloads', async () => {
    const {client, requestWithAuth} = setup({data: {conversationId: 'conv-1', runs: [screeningRun]}});
    await expect(client.getConversation('conv/1')).resolves.toEqual({conversationId: 'conv-1', runs: [screeningRun]});
    expect(requestWithAuth).toHaveBeenCalledWith('/agent/conversations/conv%2F1');
    const broken = setup({data: {conversationId: 'conv-1', runs: [{runId: 'run-1'}]}});
    await expect(broken.client.getConversation('conv-1')).rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
  });

  it('deletes a conversation and expects no content', async () => {
    const {client, requestWithAuth} = setup(undefined);
    await expect(client.deleteConversation('conv/1')).resolves.toBeUndefined();
    expect(requestWithAuth).toHaveBeenCalledWith('/agent/conversations/conv%2F1', {method: 'DELETE'});
  });

  it('preserves safe authenticated-client errors', async () => {
    const requestWithAuth = vi.fn().mockRejectedValue(new AuthApiError(404, 'NOT_FOUND', 'Agent run not found'));
    const client = new AgentHttpClient({requestWithAuth} as Pick<AuthClient, 'requestWithAuth'>);
    await expect(client.getRun('run-1')).rejects.toMatchObject({status: 404, code: 'NOT_FOUND'});
  });
});
