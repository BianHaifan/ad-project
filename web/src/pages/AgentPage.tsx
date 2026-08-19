import {useEffect, useRef, useState, type FormEvent} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {useAgentConversation, useAgentConversations, useCancelAgentRun, useConfirmAgentRun, useCreateAgentRun, useDeleteAgentConversation} from '../api/agentQueries';
import {AuthApiError} from '../api/authClient';
import {useJob} from '../api/queries';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';
import {StatusBadge} from '../components/StatusBadge';
import {resolvedTimeZone} from '../lib/interviewTime';
import type {AgentFieldChange, AgentPreview, AgentRankedCandidate, AgentRun} from '../models/agent';

const WELCOME_SUGGESTIONS = [
  'Screen candidates for the Backend Engineer role',
  'Screen candidates for the Senior Engineer role',
  'What can you help me with?',
];

const FIELD_LABELS: Record<string, string> = {
  mode: 'Mode',
  scheduledAt: 'When',
  timezone: 'Timezone',
  durationMinutes: 'Duration',
  status: 'Status',
};

export function AgentPage() {
  const [params, setParams] = useSearchParams();
  const nav = useNavigate();
  const conversationId = params.get('conversation') ?? null;
  const jobId = params.get('jobId') ?? null;
  const list = useAgentConversations();
  const detail = useAgentConversation(conversationId ?? '');
  const contextJob = useJob(jobId ?? '', !!jobId && !conversationId);
  const create = useCreateAgentRun();
  const confirm = useConfirmAgentRun();
  const cancel = useCancelAgentRun();
  const del = useDeleteAgentConversation();
  const [draft, setDraft] = useState('');
  const [sendError, setSendError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  const runs = detail.data?.runs ?? [];
  useEffect(() => {
    scrollRef.current?.scrollTo?.({top: scrollRef.current.scrollHeight});
  }, [runs.length, create.isPending, conversationId]);

  const selectConversation = (id: string) => {
    setSendError(null);
    setParams({conversation: id});
  };
  const startNewConversation = () => {
    setSendError(null);
    setDraft('');
    setParams({});
  };
  const removeConversation = (id: string) => {
    if (del.isPending) return;
    if (!window.confirm('Delete this conversation? The chat history will be permanently removed.')) return;
    setSendError(null);
    del.mutate(id, {
      onSuccess: () => {
        if (id === conversationId) {
          setDraft('');
          setParams({});
        }
      },
      onError: caught => setSendError(presentRunError(caught)),
    });
  };
  const viewApplication = (applicationId: string) => nav(`/recruiter/applications/${applicationId}`);

  const submit = (text: string) => {
    const instruction = text.trim();
    if (!instruction || create.isPending) return;
    setSendError(null);
    create.mutate({
      instruction,
      conversationId: conversationId ?? undefined,
      jobId: !conversationId && jobId ? jobId : undefined,
      timezone: resolvedTimeZone(),
    }, {
      onSuccess: run => {
        setDraft('');
        if (!conversationId && run.conversationId) setParams({conversation: run.conversationId});
      },
      onError: caught => setSendError(presentRunError(caught)),
    });
  };

  const confirmRun = (run: AgentRun) => {
    if (!run.preview || confirm.isPending) return;
    setSendError(null);
    confirm.mutate({
      runId: run.runId,
      confirmationId: run.preview.confirmationId,
      expectedRunVersion: run.version,
      idempotencyKey: crypto.randomUUID(),
    });
  };

  const cancelRun = (runId: string) => {
    if (cancel.isPending) return;
    setSendError(null);
    cancel.mutate(runId, {onError: caught => setSendError(presentRunError(caught))});
  };

  const prefillSchedule = (candidate: AgentRankedCandidate) => {
    setSendError(null);
    setDraft(`Schedule an interview with the #${candidate.rank} candidate (${candidate.fullName})`);
  };

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    submit(draft);
  };

  const summaries = list.data ?? [];
  const busyRunId = confirm.isPending ? confirm.variables?.runId
    : cancel.isPending ? cancel.variables
      : null;

  return <>
    <PageHeader title="AI Agent" subtitle="Screen candidates for a job and schedule interviews — every change is previewed for your confirmation."/>
    <section className="messages-panel agent-panel">
      <aside className="conversation-list">
        <div className="section-title">
          <div><h2>Conversations</h2><small>{summaries.length} chat{summaries.length === 1 ? '' : 's'}</small></div>
        </div>
        <button className={`button primary agent-new-button${conversationId ? '' : ' selected'}`}
          onClick={startNewConversation}>+ New conversation</button>
        {summaries.map(summary => (
          <div className="conversation-item" key={summary.conversationId}>
            <button type="button"
              className={`conversation-select${summary.conversationId === conversationId ? ' selected' : ''}`}
              onClick={() => selectConversation(summary.conversationId)}>
              <span className="avatar">AI</span>
              <span className="grow">
                <b className="truncate">{summary.lastInstruction}</b>
                <small className="truncate">{summary.lastMessage}</small>
              </span>
            </button>
            <button type="button" className="conversation-delete"
              aria-label={`Delete conversation “${summary.lastInstruction}”`}
              title="Delete conversation" disabled={del.isPending}
              onClick={() => removeConversation(summary.conversationId)}>✕</button>
          </div>
        ))}
        {!list.isLoading && summaries.length === 0 &&
          <p className="muted agent-side-note">Your chats with the hiring assistant will appear here.</p>}
      </aside>
      <article className="chat-view">
        {!conversationId ? (
          <>
            <WelcomeView jobTitle={contextJob.data?.title ?? null} onSuggestion={submit}/>
            <MessageComposer draft={draft} pending={create.isPending} error={sendError}
              onDraft={text => {setDraft(text); setSendError(null);}} onSubmit={onSubmit}/>
          </>
        ) : detail.isLoading ? (
          <LoadingState label="Loading conversation…"/>
        ) : detail.isError || !detail.data ? (
          <ErrorState onRetry={() => detail.refetch()}/>
        ) : detail.data.runs.length === 0 ? (
          <EmptyState title="This conversation is empty" description="Start a new conversation instead."
            action={<button className="button primary" onClick={startNewConversation}>New conversation</button>}/>
        ) : (
          <>
            <header>
              <span className="avatar">AI</span>
              <span className="grow"><b>AI hiring assistant</b><small>Screening and interview scheduling</small></span>
            </header>
            <div className="messages agent-messages" ref={scrollRef}>
              {runs.map(run => (
                <RunThread key={run.runId} run={run} busy={busyRunId === run.runId}
                  onPrefill={prefillSchedule} onConfirm={confirmRun} onCancel={cancelRun}
                  onViewApplication={viewApplication}/>
              ))}
              {create.isPending && create.variables && (
                <div className="agent-thread">
                  <div className="message recruiter">{create.variables.instruction}</div>
                  <div className="message agent agent-pending" aria-live="polite"><span className="spinner"/>Thinking…</div>
                </div>
              )}
            </div>
            <MessageComposer draft={draft} pending={create.isPending} error={sendError}
              onDraft={text => {setDraft(text); setSendError(null);}} onSubmit={onSubmit}/>
          </>
        )}
      </article>
    </section>
  </>;
}

function MessageComposer({draft, pending, error, onDraft, onSubmit}: {
  draft: string;
  pending: boolean;
  error: string | null;
  onDraft: (text: string) => void;
  onSubmit: (event: FormEvent) => void;
}) {
  return <div className="composer-area">
    <form className="message-composer" onSubmit={onSubmit}>
      <div className="composer-row">
        <textarea value={draft} rows={2}
          onChange={event => onDraft(event.target.value)}
          placeholder="Ask the assistant, e.g. Screen candidates for the Backend Engineer role"
          disabled={pending} aria-label="Message the AI assistant"/>
        <button className="button primary" disabled={!draft.trim() || pending}>
          {pending ? 'Sending…' : 'Send'}</button>
      </div>
    </form>
    {error && <small role="alert" className="form-error">{error}</small>}
  </div>;
}

function WelcomeView({jobTitle, onSuggestion}: {
  jobTitle: string | null;
  onSuggestion: (text: string) => void;
}) {
  const suggestions = jobTitle
    ? [`Screen candidates for the ${jobTitle} role`, ...WELCOME_SUGGESTIONS.slice(1)]
    : WELCOME_SUGGESTIONS;
  return <>
    <header>
      <span className="avatar">AI</span>
      <span className="grow"><b>AI hiring assistant</b><small>Screening and interview scheduling</small></span>
    </header>
    <div className="agent-welcome">
      <h2>HireX AI Agent</h2>
      <p>I rank candidates for one of your jobs by how their resumes fit the role, and I can prepare interview
        schedules. Nothing is applied until you confirm a preview.</p>
      {jobTitle && <p className="agent-context">Job context: “{jobTitle}” — screening requests in this new
        conversation will target that job.</p>}
      <div className="agent-suggestions">
        {suggestions.map(suggestion => (
          <button key={suggestion} className="agent-suggestion" onClick={() => onSuggestion(suggestion)}>
            {suggestion}</button>
        ))}
      </div>
    </div>
  </>;
}

function RunThread({run, busy, onPrefill, onConfirm, onCancel, onViewApplication}: {
  run: AgentRun;
  busy: boolean;
  onPrefill: (candidate: AgentRankedCandidate) => void;
  onConfirm: (run: AgentRun) => void;
  onCancel: (runId: string) => void;
  onViewApplication: (applicationId: string) => void;
}) {
  return <div className="agent-thread">
    <div className="message recruiter">{run.instruction}</div>
    <AgentBubble run={run} busy={busy} onPrefill={onPrefill} onConfirm={onConfirm} onCancel={onCancel}
      onViewApplication={onViewApplication}/>
  </div>;
}

function AgentBubble({run, busy, onPrefill, onConfirm, onCancel, onViewApplication}: {
  run: AgentRun;
  busy: boolean;
  onPrefill: (candidate: AgentRankedCandidate) => void;
  onConfirm: (run: AgentRun) => void;
  onCancel: (runId: string) => void;
  onViewApplication: (applicationId: string) => void;
}) {
  if (run.status === 'PROCESSING' || run.status === 'EXECUTING') {
    return <div className="message agent agent-pending" aria-live="polite">
      <span className="spinner"/>{run.status === 'EXECUTING' ? 'Applying changes…' : 'Thinking…'}</div>;
  }
  if (run.status === 'FAILED') {
    return <div className="message agent agent-failed" role="alert">
      <span>{run.message}</span>
      {run.errorCode && <small className="agent-error-code">{run.errorCode}</small>}
    </div>;
  }
  if (run.status === 'CANCELLED') {
    return <div className="message agent"><span>{run.message || 'This request was cancelled.'}</span></div>;
  }
  if (run.status === 'AWAITING_CONFIRMATION') {
    return <div className="message agent agent-wide">
      <span>{run.message}</span>
      {run.preview && <PreviewCard preview={run.preview} busy={busy}
        onConfirm={() => onConfirm(run)} onCancel={() => onCancel(run.runId)}/>}
    </div>;
  }
  if (run.screening && run.screening.ranked.length > 0) {
    return <div className="message agent agent-wide">
      <span>{run.message}</span>
      <ScreeningCard screening={run.screening} onPrefill={onPrefill} onViewApplication={onViewApplication}/>
    </div>;
  }
  return <div className="message agent">
    <span>{run.message}</span>
    {run.result?.targetType === 'APPLICATION' &&
      <button className="text-button" onClick={() => onViewApplication(run.result!.targetId)}>View application</button>}
  </div>;
}

function ScreeningCard({screening, onPrefill, onViewApplication}: {
  screening: {jobTitle: string; ranked: AgentRankedCandidate[]};
  onPrefill: (candidate: AgentRankedCandidate) => void;
  onViewApplication: (applicationId: string) => void;
}) {
  return <div className="agent-card">
    <b>Top candidates for {screening.jobTitle}</b>
    <div className="agent-ranked">
      {screening.ranked.map(candidate => {
        const strong = candidate.strongMatches.slice(0, 2);
        const gaps = candidate.gaps.slice(0, 2);
        return <div className="agent-rank-row" key={candidate.candidateId}>
          <span className="rank-badge">#{candidate.rank}</span>
          <span className="agent-person">
            <b>{candidate.fullName}</b>
            {candidate.applicationStatus
              ? <StatusBadge status={candidate.applicationStatus}/>
              : <span className="pill">Not applied</span>}
          </span>
          <span className="cell-stack">
            {candidate.recommendation && <small className="agent-reason">{candidate.recommendation}</small>}
            {strong.length > 0 && <small className="match-line">✓ {strong.join(' · ')}</small>}
            {gaps.length > 0 && <small className="gap-line">✗ {gaps.join(' · ')}</small>}
          </span>
          <span className="row-actions">
            {candidate.applicationId &&
              <button className="button tiny secondary"
                onClick={() => onViewApplication(candidate.applicationId!)}>View</button>}
            {candidate.applicationId &&
              <button className="button tiny primary"
                aria-label={`Schedule interview for ${candidate.fullName}`}
                onClick={() => onPrefill(candidate)}>Schedule interview</button>}
          </span>
        </div>;
      })}
    </div>
  </div>;
}

function PreviewCard({preview, busy, onConfirm, onCancel}: {
  preview: AgentPreview;
  busy: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const isNewInterview = preview.targetType === 'APPLICATION';
  return <div className="agent-card agent-preview">
    <b>{isNewInterview ? 'Interview preview' : 'Interview update preview'}</b>
    <ul className="agent-changes">
      {preview.changes.map(change => (
        <li key={change.field}>
          <b>{FIELD_LABELS[change.field] ?? change.field}</b>
          <span>{formatChangeValue(change, change.oldValue)}</span>
          <span className="agent-arrow">→</span>
          <span>{formatChangeValue(change, change.newValue)}</span>
        </li>
      ))}
    </ul>
    <small>Preview expires {new Date(preview.expiresAt).toLocaleString()} · nothing has been applied yet.</small>
    <div className="actions">
      <button className="button tiny primary" disabled={busy} onClick={onConfirm}>Confirm</button>
      <button className="button tiny secondary" disabled={busy} onClick={onCancel}>Cancel</button>
    </div>
  </div>;
}

function formatChangeValue(change: AgentFieldChange, value: unknown): string {
  if (value === null || value === undefined) return '—';
  if (change.field === 'durationMinutes') return `${value} minutes`;
  if (change.field === 'scheduledAt' && typeof value === 'string') {
    const date = new Date(value);
    if (!Number.isNaN(date.getTime())) return date.toLocaleString();
  }
  if (typeof value === 'string') return value;
  return JSON.stringify(value);
}

function presentRunError(caught: unknown): string {
  if (caught instanceof AuthApiError) {
    if (caught.status === 403) return 'You are not allowed to do that here.';
    if (caught.status === 404) return 'This conversation is no longer available.';
    if (caught.status === 0 || caught.code === 'NETWORK_ERROR') return 'Connection lost. Please try again.';
    if (caught.code === 'AGENT_PLANNER_UNAVAILABLE') return 'The AI planner is unavailable right now. Please try again.';
  }
  return 'The assistant could not complete this request. Please try again.';
}
