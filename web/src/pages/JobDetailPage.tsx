import {useRef, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {AuthApiError} from '../api/authClient';
import {useJob, usePublishJob} from '../api/queries';
import {ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';
import {StatusBadge} from '../components/StatusBadge';

type PublishError = {message: string; reload: boolean};

export function JobDetailPage() {
  const {jobId = ''} = useParams();
  const nav = useNavigate();
  const query = useJob(jobId);
  const publish = usePublishJob();
  const publishingRef = useRef(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [publishError, setPublishError] = useState<PublishError | null>(null);
  if (query.isLoading) return <LoadingState label="Loading real job details…"/>;
  if (query.isError || !query.data) {
    if (query.error instanceof AuthApiError && query.error.status === 404) {
      return <div className="state-card error"><strong>Job not found</strong><span>This job does not exist or is not part of your company.</span><button className="button secondary" onClick={() => nav('/recruiter/jobs')}>Back to jobs</button></div>;
    }
    return <ErrorState onRetry={() => query.refetch()}/>;
  }
  const job = query.data;

  const confirmPublish = async () => {
    if (publishingRef.current || job.status !== 'DRAFT') return;
    publishingRef.current = true;
    setPublishError(null);
    try {
      await publish.mutateAsync({jobId: job.jobId, expectedVersion: job.version});
      setShowConfirm(false);
    } catch (caught) {
      setShowConfirm(false);
      setPublishError(presentPublishError(caught));
    } finally {
      publishingRef.current = false;
    }
  };

  const reload = async () => {
    setPublishError(null);
    await query.refetch();
  };

  return <><PageHeader title={job.title} subtitle={`${job.company.name} · Persisted backend job`} actions={<button className="button secondary" onClick={() => nav('/recruiter/jobs')}>Back to jobs</button>}/>
    {publishError && <div className="state-card error" role="alert"><strong>Unable to publish job</strong><span>{publishError.message}</span>{publishError.reload && <button className="button secondary" onClick={reload}>Reload job</button>}</div>}
    <div className="detail-grid"><div className="detail-main"><section className="panel"><div className="section-title"><div><h2>Job overview</h2><small>Job ID: {job.jobId}</small></div><StatusBadge status={job.status}/></div><p>{job.description}</p><div className="form-grid"><p><b>Employment</b><br/>{job.employmentType.replace('_', ' ')}</p><p><b>Workplace</b><br/>{job.workplaceType}</p><p><b>Location</b><br/>{job.location}</p><p><b>Salary</b><br/>{job.salary.currency} {job.salary.min}–{job.salary.max} / {job.salary.period.toLowerCase()}</p></div></section>
      <section className="panel"><h2>Requirements</h2><ul>{job.requirements.map(requirement => <li key={requirement}>{requirement}</li>)}</ul><h2>Skills</h2><div className="actions">{job.skills.map(skill => <span className="skill" key={skill}>{skill}</span>)}</div></section></div>
      <aside className="detail-side"><section className="panel"><h2>Server-managed fields</h2><p><b>Status</b><br/>{job.status}</p><p><b>Applicants</b><br/>{job.applicantCount}</p><p><b>Owner</b><br/>{job.owner?.fullName ?? 'Unassigned'}</p><p><b>Version</b><br/>{job.version}</p><p><b>Published</b><br/>{job.publishedAt ? new Date(job.publishedAt).toLocaleString() : 'Not published'}</p><p><b>Created</b><br/>{new Date(job.createdAt).toLocaleString()}</p><p><b>Updated</b><br/>{new Date(job.updatedAt).toLocaleString()}</p><p><b>Deadline</b><br/>{job.deadline ? new Date(job.deadline).toLocaleString() : 'None'}</p></section>
      <section className="panel"><h2>Job actions</h2>{job.status === 'DRAFT' && <button className="button primary" disabled={publish.isPending} onClick={() => {setPublishError(null); setShowConfirm(true);}}>Publish job</button>}<p className="muted">Editing, pausing, and closing are intentionally unavailable in this slice.</p><button className="button secondary" disabled>Edit / pause / close unavailable</button></section></aside></div>
    {showConfirm && <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="publish-title"><section className="modal"><h2 id="publish-title">Publish this job?</h2><p>Publishing “{job.title}” will change its status from DRAFT to ACTIVE. This slice does not support pausing or closing it afterward.</p><div className="actions"><button type="button" className="button secondary" disabled={publish.isPending} onClick={() => setShowConfirm(false)}>Cancel</button><button type="button" className="button primary" disabled={publish.isPending} onClick={confirmPublish}>{publish.isPending ? 'Publishing…' : 'Confirm publish'}</button></div></section></div>}
  </>;
}

function presentPublishError(caught: unknown): PublishError {
  if (!(caught instanceof AuthApiError)) return {message: 'Unable to publish this job. Please try again.', reload: false};
  if (caught.status === 403) return {message: 'Your company must be approved and you must have permission to publish this job.', reload: false};
  if (caught.code === 'VERSION_CONFLICT') return {message: 'This job changed after you opened it. Reload the latest version before publishing.', reload: true};
  if (caught.status === 404) return {message: 'This job no longer exists or is not part of your company.', reload: false};
  if (caught.code === 'INVALID_JOB_TRANSITION') return {message: 'Only a draft job can be published. Reload the latest job state.', reload: true};
  if (caught.code === 'NETWORK_ERROR') return {message: 'Unable to reach the server. Check your connection and try again.', reload: false};
  return {message: 'Unable to publish this job. Please try again.', reload: false};
}
