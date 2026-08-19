import '@testing-library/jest-dom/vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {agentHttpClient} from '../api/agentHttpClient';
import {AuthApiError} from '../api/authClient';
import {recruiterRepository} from '../api/repository';
import type {AgentConversationSummary, AgentRun} from '../models/agent';
import {jobs} from '../mocks/data';
import {AgentPage} from './AgentPage';

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
    changes: [
      {field: 'scheduledAt', oldValue: null, newValue: '2026-08-21T07:00:00Z'},
      {field: 'mode', oldValue: null, newValue: 'ONLINE'},
      {field: 'durationMinutes', oldValue: null, newValue: 60},
    ],
  },
  message: 'I can prepare an interview preview.', errorCode: null, version: 1,
  createdAt: '2026-08-18T08:00:00Z', updatedAt: '2026-08-18T08:00:00Z',
};

const failedRun: AgentRun = {
  ...screeningRun, runId: 'run-3', status: 'FAILED', screening: null, target: null,
  message: 'The resume screening service failed. Please try again.',
  errorCode: 'http_500',
};

const summary: AgentConversationSummary = {
  conversationId: 'conv-1', lastInstruction: 'Screen candidates for the Backend Engineer role',
  lastMessage: 'Screening finished.', updatedAt: '2026-08-18T08:00:00Z',
};

function renderAgent(path: string) {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
  const router = createMemoryRouter([
    {path: '/recruiter/agent', element: <AgentPage/>},
    {path: '/recruiter/applications/:applicationId', element: <div>Application detail page</div>},
  ], {initialEntries: [path]});
  const view = render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>);
  return {router, client, ...view};
}

describe('AgentPage', () => {
  beforeEach(() => {
    vi.spyOn(agentHttpClient, 'listConversations').mockResolvedValue([]);
    vi.spyOn(agentHttpClient, 'getConversation').mockResolvedValue({conversationId: 'conv-1', runs: []});
    vi.spyOn(agentHttpClient, 'createRun').mockResolvedValue(screeningRun);
    vi.spyOn(agentHttpClient, 'confirmRun').mockResolvedValue({...previewRun, status: 'COMPLETED',
      confirmationStatus: 'NOT_REQUIRED', preview: null, result: {operation: 'SCHEDULE_INTERVIEW',
        targetType: 'APPLICATION', targetId: 'app-1', previousVersion: 2, newVersion: 1,
        completedAt: '2026-08-18T08:01:00Z', appliedChanges: [], queryResult: null}});
    vi.spyOn(agentHttpClient, 'cancelRun').mockResolvedValue({...previewRun, status: 'CANCELLED'});
    vi.spyOn(agentHttpClient, 'deleteConversation').mockResolvedValue(undefined);
  });
  afterEach(() => {cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals();});

  it('welcomes with suggestions and sends one to start a conversation', async () => {
    const {router} = renderAgent('/recruiter/agent');
    expect(await screen.findByText('Screen candidates for the Backend Engineer role')).toBeInTheDocument();
    expect(agentHttpClient.getConversation).not.toHaveBeenCalled();

    vi.mocked(agentHttpClient.getConversation).mockResolvedValue({conversationId: 'conv-1', runs: [screeningRun]});
    fireEvent.click(screen.getByRole('button', {name: 'Screen candidates for the Backend Engineer role'}));

    await waitFor(() => expect(agentHttpClient.createRun).toHaveBeenCalledWith(expect.objectContaining({
      instruction: 'Screen candidates for the Backend Engineer role',
      timezone: expect.any(String),
    })));
    expect(router.state.location.search).toBe('?conversation=conv-1');
    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
  });

  it('offers the composer on the new-conversation screen', async () => {
    const {router} = renderAgent('/recruiter/agent');
    expect(await screen.findByText('Screen candidates for the Backend Engineer role')).toBeInTheDocument();
    const composer = screen.getByLabelText('Message the AI assistant');
    expect(composer).toBeInTheDocument();

    fireEvent.change(composer, {target: {value: 'Screen candidates for the Backend Engineer role'}});
    fireEvent.click(screen.getByRole('button', {name: 'Send'}));
    await waitFor(() => expect(agentHttpClient.createRun).toHaveBeenCalledWith(expect.objectContaining({
      instruction: 'Screen candidates for the Backend Engineer role',
    })));
    expect(router.state.location.search).toBe('?conversation=conv-1');
  });

  it('carries a ?jobId= deep link as context for the first run', async () => {
    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue({...jobs[0], jobId: 'job-9', title: 'Backend Engineer'});
    renderAgent('/recruiter/agent?jobId=job-9');
    expect(await screen.findByText(/Job context: “Backend Engineer”/)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Screen candidates for the Backend Engineer role'})).toBeInTheDocument();

    vi.mocked(agentHttpClient.createRun).mockResolvedValue(screeningRun);
    fireEvent.click(screen.getByRole('button', {name: 'Screen candidates for the Backend Engineer role'}));
    await waitFor(() => expect(agentHttpClient.createRun).toHaveBeenCalledWith(expect.objectContaining({
      instruction: 'Screen candidates for the Backend Engineer role', jobId: 'job-9',
    })));
  });

  it('renders the screening ranking with prefill and application navigation', async () => {
    vi.mocked(agentHttpClient.listConversations).mockResolvedValue([summary]);
    vi.mocked(agentHttpClient.getConversation).mockResolvedValue({conversationId: 'conv-1', runs: [screeningRun]});
    const {router} = renderAgent('/recruiter/agent?conversation=conv-1');

    expect(await screen.findByText('Top candidates for Backend Engineer')).toBeInTheDocument();
    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument();
    expect(screen.getByText('#1')).toBeInTheDocument();
    expect(screen.getByText('✓ Python')).toBeInTheDocument();
    expect(screen.getByText('Ada is the top pick for her hands-on Python experience.')).toBeInTheDocument();
    expect(screen.getByText('✗ No Python')).toBeInTheDocument();
    expect(screen.getByText('Grace Hopper')).toBeInTheDocument();
    expect(screen.getByText('Not applied')).toBeInTheDocument();
    expect(screen.getByText('Screening finished.', {selector: '.agent-thread span'}))
      .toBeInTheDocument();
    // Only candidates who applied offer scheduling and a link to the application.
    expect(screen.getAllByRole('button', {name: /^Schedule interview for/})).toHaveLength(1);

    fireEvent.click(screen.getByRole('button', {name: 'Schedule interview for Ada Lovelace'}));
    expect(screen.getByLabelText('Message the AI assistant')).toHaveValue('Schedule an interview with the #1 candidate (Ada Lovelace)');

    fireEvent.click(screen.getByRole('button', {name: 'View'}));
    await waitFor(() => expect(router.state.location.pathname).toBe('/recruiter/applications/app-1'));
  });

  it('previews interview changes and confirms with an idempotency key', async () => {
    vi.mocked(agentHttpClient.listConversations).mockResolvedValue([summary]);
    vi.mocked(agentHttpClient.getConversation).mockResolvedValue({conversationId: 'conv-1', runs: [previewRun]});
    renderAgent('/recruiter/agent?conversation=conv-1');

    expect(await screen.findByText('Interview preview')).toBeInTheDocument();
    expect(screen.getByText('I can prepare an interview preview.')).toBeInTheDocument();
    expect(screen.getByText('Mode')).toBeInTheDocument();
    expect(screen.getByText('ONLINE')).toBeInTheDocument();
    expect(screen.getByText('60 minutes')).toBeInTheDocument();
    expect(screen.getByText(/Preview expires/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: 'Confirm'}));
    await waitFor(() => expect(agentHttpClient.confirmRun).toHaveBeenCalledWith({
      runId: 'run-2', confirmationId: 'conf-1', expectedRunVersion: 1,
      idempotencyKey: expect.any(String),
    }));
  });

  it('cancels a pending preview', async () => {
    vi.mocked(agentHttpClient.listConversations).mockResolvedValue([summary]);
    vi.mocked(agentHttpClient.getConversation).mockResolvedValue({conversationId: 'conv-1', runs: [previewRun]});
    renderAgent('/recruiter/agent?conversation=conv-1');
    expect(await screen.findByText('Interview preview')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Cancel'}));
    await waitFor(() => expect(agentHttpClient.cancelRun).toHaveBeenCalledWith('run-2'));
  });

  it('shows failed runs as an error bubble with the safe error code', async () => {
    vi.mocked(agentHttpClient.listConversations).mockResolvedValue([summary]);
    vi.mocked(agentHttpClient.getConversation).mockResolvedValue({conversationId: 'conv-1', runs: [failedRun]});
    renderAgent('/recruiter/agent?conversation=conv-1');
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('The resume screening service failed. Please try again.');
    expect(alert).toHaveTextContent('http_500');
  });

  it('keeps a send failure behind a safe message', async () => {
    vi.mocked(agentHttpClient.createRun).mockRejectedValue(
      new AuthApiError(0, 'NETWORK_ERROR', 'Unable to reach the server. Check your connection and try again.'));
    renderAgent('/recruiter/agent');
    fireEvent.click(await screen.findByRole('button', {name: 'Screen candidates for the Backend Engineer role'}));
    expect(await screen.findByRole('alert')).toHaveTextContent('Connection lost. Please try again.');
    expect(screen.queryByText('Unable to reach the server. Check your connection and try again.')).not.toBeInTheDocument();
  });

  it('starts a fresh conversation from the sidebar button', async () => {
    vi.mocked(agentHttpClient.listConversations).mockResolvedValue([summary]);
    vi.mocked(agentHttpClient.getConversation).mockResolvedValue({conversationId: 'conv-1', runs: [screeningRun]});
    const {router} = renderAgent('/recruiter/agent?conversation=conv-1');
    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: '+ New conversation'}));
    expect(await screen.findByText('HireX AI Agent')).toBeInTheDocument();
    expect(router.state.location.search).toBe('');
  });

  it('deletes an active conversation after confirmation and returns to a fresh chat', async () => {
    vi.stubGlobal('confirm', vi.fn(() => true));
    vi.mocked(agentHttpClient.listConversations).mockResolvedValue([summary]);
    vi.mocked(agentHttpClient.getConversation).mockResolvedValue({conversationId: 'conv-1', runs: [screeningRun]});
    const {router} = renderAgent('/recruiter/agent?conversation=conv-1');
    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button',
      {name: 'Delete conversation “Screen candidates for the Backend Engineer role”'}));
    expect(confirm).toHaveBeenCalledWith(
      'Delete this conversation? The chat history will be permanently removed.');
    await waitFor(() => expect(agentHttpClient.deleteConversation).toHaveBeenCalledWith('conv-1'));
    expect(await screen.findByText('HireX AI Agent')).toBeInTheDocument();
    expect(router.state.location.search).toBe('');
  });

  it('keeps the conversation when the delete confirmation is dismissed', async () => {
    vi.stubGlobal('confirm', vi.fn(() => false));
    vi.mocked(agentHttpClient.listConversations).mockResolvedValue([summary]);
    vi.mocked(agentHttpClient.getConversation).mockResolvedValue({conversationId: 'conv-1', runs: [screeningRun]});
    const {router} = renderAgent('/recruiter/agent?conversation=conv-1');
    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button',
      {name: 'Delete conversation “Screen candidates for the Backend Engineer role”'}));
    expect(agentHttpClient.deleteConversation).not.toHaveBeenCalled();
    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument();
    expect(router.state.location.search).toBe('?conversation=conv-1');
  });

  it('shows a safe message when deleting a conversation fails', async () => {
    vi.stubGlobal('confirm', vi.fn(() => true));
    vi.mocked(agentHttpClient.listConversations).mockResolvedValue([summary]);
    vi.mocked(agentHttpClient.getConversation).mockResolvedValue({conversationId: 'conv-1', runs: [screeningRun]});
    vi.mocked(agentHttpClient.deleteConversation).mockRejectedValue(
      new AuthApiError(404, 'NOT_FOUND', 'Agent conversation not found'));
    renderAgent('/recruiter/agent?conversation=conv-1');
    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button',
      {name: 'Delete conversation “Screen candidates for the Backend Engineer role”'}));
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('This conversation is no longer available.'));
  });
});
