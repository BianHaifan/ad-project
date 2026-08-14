import {useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {useJobs} from '../api/queries';
import type {JobStatus} from '../models/recruiter';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';
import {StatusBadge} from '../components/StatusBadge';

export function JobsPage() {
  const nav = useNavigate();
  const [urlParams, setUrlParams] = useSearchParams();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const status = jobStatus(urlParams.get('status'));
  const query = useJobs({q: search, status, page, pageSize: 20});
  const chooseStatus = (value?: JobStatus) => {
    const next = new URLSearchParams();
    if (value) next.set('status', value);
    setUrlParams(next);
    setPage(1);
  };
  if (query.isLoading) return <LoadingState label="Loading real job postings…"/>;
  if (query.isError || !query.data) return <ErrorState onRetry={() => query.refetch()}/>;
  const {data: items, meta} = query.data;
  return <><PageHeader title="Job management" subtitle="This list is loaded from your company’s real backend data." actions={<button className="button primary" onClick={() => nav('/recruiter/jobs/new')}>Create job</button>}/>
    <section className="table-panel"><div className="table-toolbar"><div className="grow"><h2>Company jobs</h2><small>{meta.total} persisted job posting{meta.total === 1 ? '' : 's'}</small></div><input aria-label="Search jobs" value={search} onChange={event => {setSearch(event.target.value); setPage(1);}} placeholder="⌕ Search job by title"/><select aria-label="Filter status" value={status ?? 'ALL'} onChange={event => chooseStatus(jobStatus(event.target.value))}><option value="ALL">All statuses</option><option value="ACTIVE">Active</option><option value="DRAFT">Draft</option><option value="PAUSED">Paused</option><option value="CLOSED">Closed</option></select></div>
    {items.length === 0 ? <EmptyState title="No job postings found" description="Create a draft or try a different server-side filter." action={<button className="button primary" onClick={() => nav('/recruiter/jobs/new')}>Create job</button>}/> : <div className="data-table jobs-table"><div className="table-head"><span>Job title</span><span>Employment type</span><span>Status</span><span>Created</span><span>Applicants</span><span>Owner</span><span/></div>{items.map(job => <div className="table-row" key={job.jobId}><span className="person"><i className="avatar">{job.title.split(' ').map(word => word[0]).slice(0, 2).join('')}</i><span><b>{job.title}</b><small>{job.company.name}</small></span></span><span><b>{job.employmentType.replace('_', ' ')}</b><small>{job.location} · {job.workplaceType}</small></span><StatusBadge status={job.status}/><span>{new Date(job.createdAt).toLocaleDateString()}</span><span className="pill">{job.applicantCount} applicants</span><span>{job.owner?.fullName ?? 'Unassigned'}</span><span className="row-actions"><button className="button tiny secondary" onClick={() => nav(`/recruiter/jobs/${job.jobId}`)}>View details</button><span className="muted">DRAFT jobs editable</span></span></div>)}</div>}
    <footer className="pagination"><span>Page {meta.page} · {items.length} shown of {meta.total}</span><div className="actions"><button className="button tiny secondary" disabled={meta.page <= 1 || query.isFetching} onClick={() => setPage(value => value - 1)}>Previous</button><button className="button tiny secondary" disabled={!meta.hasNext || query.isFetching} onClick={() => setPage(value => value + 1)}>Next</button></div></footer></section></>;
}

const jobStatus = (value: string | null): JobStatus | undefined =>
  value === 'DRAFT' || value === 'ACTIVE' || value === 'PAUSED' || value === 'CLOSED' ? value : undefined;
