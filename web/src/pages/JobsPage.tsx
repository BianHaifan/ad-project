import {useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {useJobs} from '../api/queries';
import type {JobStatus} from '../models/recruiter';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';
import {StatusBadge} from '../components/StatusBadge';

const stages: [JobStatus, string, string][] = [
  ['ACTIVE', 'Active', 'Published openings'],
  ['DRAFT', 'Draft', 'Ready to edit'],
  ['PAUSED', 'Paused', 'Temporarily hidden'],
  ['CLOSED', 'Closed', 'No longer hiring'],
];

export function JobsPage() {
  const nav = useNavigate();
  const [urlParams, setUrlParams] = useSearchParams();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const status = jobStatus(urlParams.get('status'));
  const query = useJobs({q: search, status, page, pageSize: 20});
  const activeCount = useJobs({status: 'ACTIVE', page: 1, pageSize: 1});
  const draftCount = useJobs({status: 'DRAFT', page: 1, pageSize: 1});
  const pausedCount = useJobs({status: 'PAUSED', page: 1, pageSize: 1});
  const closedCount = useJobs({status: 'CLOSED', page: 1, pageSize: 1});
  const counts: Record<JobStatus, number | null> = {
    ACTIVE: activeCount.data?.meta.total ?? null,
    DRAFT: draftCount.data?.meta.total ?? null,
    PAUSED: pausedCount.data?.meta.total ?? null,
    CLOSED: closedCount.data?.meta.total ?? null,
  };
  const chooseStatus = (value?: JobStatus) => {
    const next = new URLSearchParams();
    if (value) next.set('status', value);
    setUrlParams(next);
    setPage(1);
  };
  if (query.isLoading) return <LoadingState label="Loading real job postings…"/>;
  if (query.isError || !query.data) return <ErrorState onRetry={() => query.refetch()}/>;
  const {data: items, meta} = query.data;
  return <><PageHeader title="Job management" subtitle="Manage openings, publishing status, and applicant activity." actions={<button className="button primary" onClick={() => nav('/recruiter/jobs/new')}>Create job</button>}/>
    <section className="metric-grid four stage-cards job-stage-cards">{stages.map(([stage, stageLabel, help]) =>
      <button key={stage} className={`metric-card stage-${stage.toLowerCase()} ${status === stage ? 'selected' : ''}`}
        onClick={() => chooseStatus(status === stage ? undefined : stage)}>
        <strong>{counts[stage] ?? '—'}</strong><b>{stageLabel}</b><small>{help}</small>
      </button>)}</section>
    <section className="table-panel"><div className="table-toolbar"><div className="grow"><h2>{status ? `${label(status)} jobs` : 'Company jobs'}</h2><small>{meta.total} job posting{meta.total === 1 ? '' : 's'}</small></div><input aria-label="Search jobs" value={search} onChange={event => {setSearch(event.target.value); setPage(1);}} placeholder="⌕ Search job by title"/><select aria-label="Filter status" value={status ?? 'ALL'} onChange={event => chooseStatus(jobStatus(event.target.value))}><option value="ALL">All statuses</option>{stages.map(([stage, stageLabel]) => <option key={stage} value={stage}>{stageLabel}</option>)}</select></div>
    {items.length === 0 ? <EmptyState title="No job postings found" description="Create a draft or try a different filter." action={<button className="button primary" onClick={() => nav('/recruiter/jobs/new')}>Create job</button>}/> : <div className="data-table jobs-table"><div className="table-head"><span>Job</span><span>Work setup</span><span>Status</span><span>Applicants</span><span>Actions</span></div>{items.map(job => <div className="table-row" key={job.jobId}><span className="person"><i className="avatar">{job.title.split(' ').map(word => word[0]).slice(0, 2).join('')}</i><span className="cell-stack"><b className="cell-ellipsis">{job.title}</b><small>Created {new Date(job.createdAt).toLocaleDateString()}</small></span></span><span className="cell-stack"><b>{label(job.employmentType)}</b><small>{job.location} · {label(job.workplaceType)}</small></span><StatusBadge status={job.status}/><span className="pill">{job.applicantCount} applicant{job.applicantCount === 1 ? '' : 's'}</span><span className="row-actions"><button className="button tiny secondary" onClick={() => nav(`/recruiter/jobs/${job.jobId}`)}>View</button>{job.status === 'DRAFT' && <button className="button tiny soft" onClick={() => nav(`/recruiter/jobs/${job.jobId}/edit`)}>Edit</button>}</span></div>)}</div>}
    <footer className="pagination"><span>Page {meta.page} · {items.length} shown of {meta.total}</span><div className="actions"><button className="button tiny secondary" disabled={meta.page <= 1 || query.isFetching} onClick={() => setPage(value => value - 1)}>Previous</button><button className="button tiny secondary" disabled={!meta.hasNext || query.isFetching} onClick={() => setPage(value => value + 1)}>Next</button></div></footer></section></>;
}

const jobStatus = (value: string | null): JobStatus | undefined =>
  value === 'DRAFT' || value === 'ACTIVE' || value === 'PAUSED' || value === 'CLOSED' ? value : undefined;
const label = (value: string) => value.toLowerCase().replaceAll('_', ' ').replace(/^./, first => first.toUpperCase());
