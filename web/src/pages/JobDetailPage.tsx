import {useRef, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {AuthApiError} from '../api/authClient';
import {useChangeJobStatus, useJob, usePublishJob} from '../api/queries';
import type {RecruiterJobStatusTarget} from '../api/recruiterRepository';
import {ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';
import {StatusBadge} from '../components/StatusBadge';

type ActionError = {message: string; reload: boolean; title: string};

export function JobDetailPage() {
  const {jobId = ''} = useParams();
  const nav = useNavigate();
  const query = useJob(jobId);
  const publish = usePublishJob();
  const changeStatus = useChangeJobStatus();
  const submittingRef = useRef(false);
  const [showPublishConfirm, setShowPublishConfirm] = useState(false);
  const [statusTarget, setStatusTarget] = useState<RecruiterJobStatusTarget | null>(null);
  const [reason, setReason] = useState('');
  const [actionError, setActionError] = useState<ActionError | null>(null);
  if (query.isLoading) return <LoadingState label="Loading real job details…"/>;
  if (query.isError || !query.data) {
    if (query.error instanceof AuthApiError && query.error.status === 404) {
      return <div className="state-card error"><strong>Job not found</strong><span>This job does not exist or is not part of your company.</span><button className="button secondary" onClick={() => nav('/recruiter/jobs')}>Back to jobs</button></div>;
    }
    return <ErrorState onRetry={() => query.refetch()}/>;
  }
  const job = query.data;
  const isSubmitting = publish.isPending || changeStatus.isPending;

  const confirmPublish = async () => {
    if (submittingRef.current || job.status !== 'DRAFT') return;
    submittingRef.current = true;
    setActionError(null);
    try {
      await publish.mutateAsync({jobId: job.jobId, expectedVersion: job.version});
      setShowPublishConfirm(false);
    } catch (caught) {
      setShowPublishConfirm(false);
      setActionError(presentPublishError(caught));
    } finally {
      submittingRef.current = false;
    }
  };

  const openStatusConfirm = (target: RecruiterJobStatusTarget) => {
    setActionError(null);
    setReason('');
    setStatusTarget(target);
  };

  const confirmStatusChange = async () => {
    if (submittingRef.current || !statusTarget || !reason.trim()) return;
    submittingRef.current = true;
    setActionError(null);
    try {
      await changeStatus.mutateAsync({
        jobId: job.jobId,
        status: statusTarget,
        reason: reason.trim(),
        expectedVersion: job.version,
      });
      setStatusTarget(null);
      setReason('');
    } catch (caught) {
      setStatusTarget(null);
      setReason('');
      setActionError(presentStatusError(caught));
    } finally {
      submittingRef.current = false;
    }
  };

  const reload = async () => {
    setActionError(null);
    await query.refetch();
  };

  return <><PageHeader title={job.title} subtitle={`${job.company.name} · ${label(job.employmentType)} · ${job.location}`} actions={<button className="button secondary" onClick={() => nav('/recruiter/jobs')}>Back to jobs</button>}/>
    {actionError && <div className="state-card error" role="alert"><strong>{actionError.title}</strong><span>{actionError.message}</span>{actionError.reload && <button className="button secondary" onClick={reload}>Reload job</button>}</div>}
    <div className="detail-layout job-detail-layout"><div className="detail-main"><section className="panel"><div className="section-title"><h2>Job overview</h2><StatusBadge status={job.status}/></div><p className="detail-description">{job.description}</p><div className="overview-grid"><p><b>Employment</b><br/>{label(job.employmentType)}</p><p><b>Workplace</b><br/>{label(job.workplaceType)}</p><p><b>Location</b><br/>{job.location}</p><p><b>Salary</b><br/>{job.salary.currency} {job.salary.min}–{job.salary.max} / {job.salary.period.toLowerCase()}</p></div></section>
      <section className="panel"><h2>Requirements</h2><ul>{job.requirements.map(requirement => <li key={requirement}>{requirement}</li>)}</ul><h2>Skills</h2><div className="actions">{job.skills.map(skill => <span className="skill" key={skill}>{skill}</span>)}</div></section></div>
      <aside className="detail-side"><section className="panel job-actions-panel"><div className="section-title"><h2>Job actions</h2><StatusBadge status={job.status}/></div><div className="actions">{job.status === 'DRAFT' && <><button className="button secondary" disabled={isSubmitting} onClick={() => nav(`/recruiter/jobs/${job.jobId}/edit`)}>Edit job</button><button className="button primary" disabled={isSubmitting} onClick={() => {setActionError(null); setShowPublishConfirm(true);}}>Publish job</button></>}{job.status === 'ACTIVE' && <><button className="button secondary" disabled={isSubmitting} onClick={() => openStatusConfirm('PAUSED')}>Pause job</button><button className="button danger" disabled={isSubmitting} onClick={() => openStatusConfirm('CLOSED')}>Close job</button></>}{job.status === 'PAUSED' && <><button className="button primary" disabled={isSubmitting} onClick={() => openStatusConfirm('ACTIVE')}>Resume job</button><button className="button danger" disabled={isSubmitting} onClick={() => openStatusConfirm('CLOSED')}>Close job</button></>}<button className="button soft" onClick={() => nav(`/recruiter/agent?jobId=${job.jobId}`)}>AI screen candidates</button></div>{job.status === 'CLOSED' && <p className="muted">This job is closed and cannot transition to another status.</p>}{job.status !== 'DRAFT' && <p className="muted">Editing is available only while a job is a draft.</p>}</section>
      <section className="panel"><h2>Job details</h2><dl className="metadata-list"><div><dt>Applicants</dt><dd>{job.applicantCount}</dd></div><div><dt>Owner</dt><dd>{job.owner?.fullName ?? 'Unassigned'}</dd></div><div><dt>Published</dt><dd>{job.publishedAt ? new Date(job.publishedAt).toLocaleString() : 'Not published'}</dd></div><div><dt>Created</dt><dd>{new Date(job.createdAt).toLocaleString()}</dd></div><div><dt>Last updated</dt><dd>{new Date(job.updatedAt).toLocaleString()}</dd></div><div><dt>Application deadline</dt><dd>{job.deadline ? new Date(job.deadline).toLocaleString() : 'No deadline'}</dd></div></dl></section></aside></div>
    {showPublishConfirm && <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="publish-title"><section className="modal"><h2 id="publish-title">Publish this job?</h2><p>Publishing “{job.title}” will change its status from DRAFT to ACTIVE.</p><div className="actions"><button type="button" className="button secondary" disabled={publish.isPending} onClick={() => setShowPublishConfirm(false)}>Cancel</button><button type="button" className="button primary" disabled={publish.isPending} onClick={confirmPublish}>{publish.isPending ? 'Publishing…' : 'Confirm publish'}</button></div></section></div>}
    {statusTarget && <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="status-title"><section className="modal"><h2 id="status-title">{statusAction(statusTarget)} this job?</h2><p>This will change “{job.title}” from {job.status} to {statusTarget}. {statusImpact(statusTarget)}</p><label>Reason<textarea aria-label="Reason" maxLength={500} rows={3} value={reason} onChange={event => setReason(event.target.value)} placeholder="Explain why this status change is needed"/></label><div className="actions"><button type="button" className="button secondary" disabled={changeStatus.isPending} onClick={() => {setStatusTarget(null); setReason('');}}>Cancel</button><button type="button" className={statusTarget === 'CLOSED' ? 'button danger' : 'button primary'} disabled={changeStatus.isPending || !reason.trim()} onClick={confirmStatusChange}>{changeStatus.isPending ? 'Updating…' : `Confirm ${statusAction(statusTarget).toLowerCase()}`}</button></div></section></div>}
  </>;
}

function statusAction(target: RecruiterJobStatusTarget): string {
  if (target === 'PAUSED') return 'Pause';
  if (target === 'ACTIVE') return 'Resume';
  return 'Close';
}

const label = (value: string) => value.toLowerCase().replaceAll('_', ' ').replace(/^./, first => first.toUpperCase());

function statusImpact(target: RecruiterJobStatusTarget): string {
  if (target === 'PAUSED') return 'Candidates will no longer see it as an active opening.';
  if (target === 'ACTIVE') return 'It will become active again; your company must still be approved.';
  return 'Closing is permanent and this job cannot be reopened.';
}

function presentPublishError(caught: unknown): ActionError {
  if (!(caught instanceof AuthApiError)) return {title: 'Unable to publish job', message: 'Unable to publish this job. Please try again.', reload: false};
  if (caught.status === 403) return {title: 'Unable to publish job', message: 'Your company must be approved and you must have permission to publish this job.', reload: false};
  if (caught.code === 'VERSION_CONFLICT') return {title: 'Unable to publish job', message: 'This job changed after you opened it. Reload the latest version before publishing.', reload: true};
  if (caught.status === 404) return {title: 'Unable to publish job', message: 'This job no longer exists or is not part of your company.', reload: false};
  if (caught.code === 'INVALID_JOB_TRANSITION') return {title: 'Unable to publish job', message: 'Only a draft job can be published. Reload the latest job state.', reload: true};
  if (caught.code === 'NETWORK_ERROR') return {title: 'Unable to publish job', message: 'Unable to reach the server. Check your connection and try again.', reload: false};
  return {title: 'Unable to publish job', message: 'Unable to publish this job. Please try again.', reload: false};
}

function presentStatusError(caught: unknown): ActionError {
  if (!(caught instanceof AuthApiError)) return {title: 'Unable to update job status', message: 'Unable to update this job. Please try again.', reload: false};
  if (caught.status === 403) return {title: 'Unable to update job status', message: 'Your company must be approved to resume this job, and you must have permission to manage it.', reload: false};
  if (caught.code === 'VERSION_CONFLICT') return {title: 'Unable to update job status', message: 'This job changed after you opened it. Reload the latest version before trying again.', reload: true};
  if (caught.code === 'INVALID_JOB_TRANSITION') return {title: 'Unable to update job status', message: 'This job is no longer in a state that allows that action. Reload the latest job state.', reload: true};
  if (caught.status === 404) return {title: 'Unable to update job status', message: 'This job no longer exists or is not part of your company.', reload: false};
  if (caught.code === 'NETWORK_ERROR') return {title: 'Unable to update job status', message: 'Unable to reach the server. Check your connection and try again.', reload: false};
  return {title: 'Unable to update job status', message: 'Unable to update this job. Please try again.', reload: false};
}
