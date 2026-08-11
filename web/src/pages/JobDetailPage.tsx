import {useNavigate, useParams} from 'react-router-dom';
import {AuthApiError} from '../api/authClient';
import {useJob} from '../api/queries';
import {ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';
import {StatusBadge} from '../components/StatusBadge';

export function JobDetailPage() {
  const {jobId = ''} = useParams();
  const nav = useNavigate();
  const query = useJob(jobId);
  if (query.isLoading) return <LoadingState label="Loading real job details…"/>;
  if (query.isError || !query.data) {
    if (query.error instanceof AuthApiError && query.error.status === 404) {
      return <div className="state-card error"><strong>Job not found</strong><span>This job does not exist or is not part of your company.</span><button className="button secondary" onClick={() => nav('/recruiter/jobs')}>Back to jobs</button></div>;
    }
    return <ErrorState onRetry={() => query.refetch()}/>;
  }
  const job = query.data;
  return <><PageHeader title={job.title} subtitle={`${job.company.name} · Persisted backend job`} actions={<button className="button secondary" onClick={() => nav('/recruiter/jobs')}>Back to jobs</button>}/>
    <div className="detail-grid"><div className="detail-main"><section className="panel"><div className="section-title"><div><h2>Job overview</h2><small>Job ID: {job.jobId}</small></div><StatusBadge status={job.status}/></div><p>{job.description}</p><div className="form-grid"><p><b>Employment</b><br/>{job.employmentType.replace('_', ' ')}</p><p><b>Workplace</b><br/>{job.workplaceType}</p><p><b>Location</b><br/>{job.location}</p><p><b>Salary</b><br/>{job.salary.currency} {job.salary.min}–{job.salary.max} / {job.salary.period.toLowerCase()}</p></div></section>
      <section className="panel"><h2>Requirements</h2><ul>{job.requirements.map(requirement => <li key={requirement}>{requirement}</li>)}</ul><h2>Skills</h2><div className="actions">{job.skills.map(skill => <span className="skill" key={skill}>{skill}</span>)}</div></section></div>
      <aside className="detail-side"><section className="panel"><h2>Server-managed fields</h2><p><b>Status</b><br/>{job.status}</p><p><b>Applicants</b><br/>{job.applicantCount}</p><p><b>Owner</b><br/>{job.owner?.fullName ?? 'Unassigned'}</p><p><b>Version</b><br/>{job.version}</p><p><b>Created</b><br/>{new Date(job.createdAt).toLocaleString()}</p><p><b>Updated</b><br/>{new Date(job.updatedAt).toLocaleString()}</p><p><b>Deadline</b><br/>{job.deadline ? new Date(job.deadline).toLocaleString() : 'None'}</p></section><section className="panel"><h2>Not connected yet</h2><p className="muted">Editing, publishing, pausing, and closing are intentionally unavailable in this slice.</p><button className="button secondary" disabled>Edit / publish unavailable</button></section></aside></div></>;
}
