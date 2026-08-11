import {useEffect, useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {useApplications} from '../api/queries';
import type {ApplicationStatus, RecruiterApplicationCounts} from '../models/recruiter';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';
import {StatusBadge} from '../components/StatusBadge';

const stages: [keyof RecruiterApplicationCounts, ApplicationStatus, string, string][] = [
  ['applied', 'APPLIED', 'New applications', 'Awaiting review'],
  ['inReview', 'IN_REVIEW', 'In review', 'Under recruiter review'],
  ['interview', 'INTERVIEW', 'Interview', 'Moved to interview'],
  ['rejected', 'REJECTED', 'Rejected', 'Recruiter decisions'],
];

export function ApplicationsPage() {
  const nav = useNavigate();
  const [urlParams, setUrlParams] = useSearchParams();
  const selected = applicationStatus(urlParams.get('stage'));
  const page = positiveInt(urlParams.get('page'));
  const [search, setSearch] = useState('');
  const query = useApplications({status: selected, q: search, page, pageSize: 20, sort: 'appliedAt,desc'});

  useEffect(() => setSearch(''), [selected]);
  const chooseStage = (status?: ApplicationStatus) => {
    const next = new URLSearchParams();
    if (status) next.set('stage', status);
    setUrlParams(next);
  };

  if (query.isLoading) return <LoadingState label="Loading applications…"/>;
  if (query.isError || !query.data) return <ErrorState onRetry={() => query.refetch()}/>;
  const {data: applications, meta} = query.data;
  const first = meta.total === 0 ? 0 : (meta.page - 1) * meta.pageSize + 1;
  const last = Math.min(meta.total, (meta.page - 1) * meta.pageSize + applications.length);

  return <>
    <PageHeader title="Applications" subtitle="Review candidates and record every supported stage decision."
      actions={<button className="button primary" onClick={() => nav('/recruiter/jobs/new')}>Create job posting</button>}/>
    <section className="metric-grid four stage-cards">{stages.map(([countKey, status, label, help]) =>
      <button key={status} className={`metric-card stage-${status.toLowerCase()} ${selected === status ? 'selected' : ''}`}
        onClick={() => chooseStage(selected === status ? undefined : status)}>
        <strong>{meta.counts[countKey]}</strong><b>{label}</b><small>{help}</small>
      </button>)}</section>
    <section className="table-panel">
      <div className="table-toolbar"><div className="grow"><h2>{selected ?? 'All applications'}</h2>
        <small>{meta.total} persisted applications</small></div>
        <input value={search} onChange={event => {setSearch(event.target.value); chooseStage(selected);}}
          placeholder="⌕ Search candidate name or email"/>
        <select value={selected ?? 'ALL'} onChange={event => chooseStage(applicationStatus(event.target.value))}>
          <option value="ALL">All stages</option>{stages.map(([, status, label]) =>
            <option key={status} value={status}>{label}</option>)}<option value="WITHDRAWN">Withdrawn</option>
        </select>
      </div>
      {applications.length === 0 ? <EmptyState title="No applications found"
        description="Applications matching this stage or search will appear here."/> :
        <div className="data-table app-table"><div className="table-head"><span>Candidate</span><span>Applied role</span>
          <span>ML match</span><span>Applied</span><span>Stage</span><span>Owner</span><span/></div>
          {applications.map(application => <button className="table-row" key={application.applicationId}
            onClick={() => nav(`/recruiter/applications/${application.applicationId}`)}>
            <span className="person"><i className="avatar">{initials(application.candidate.fullName)}</i><span>
              <b>{application.candidate.fullName}</b><small>{application.candidate.email}</small></span></span>
            <span><b>{application.jobTitle}</b><small>{application.jobId}</small></span>
            <span>{application.matchScore === null ? 'Unavailable' : `${application.matchScore}% match`}</span>
            <span>{new Date(application.appliedAt).toLocaleDateString()}</span><StatusBadge status={application.status}/>
            <span>{application.owner?.fullName ?? 'Unassigned'}</span><span className="button tiny secondary">View</span>
          </button>)}</div>}
      <footer className="pagination"><span>Showing {first}–{last} of {meta.total} applications</span><div className="actions">
        <button className="button tiny secondary" disabled={meta.page <= 1}
          onClick={() => setPage(urlParams, setUrlParams, meta.page - 1)}>Previous</button>
        <button className="button tiny secondary" disabled={!meta.hasNext}
          onClick={() => setPage(urlParams, setUrlParams, meta.page + 1)}>Next</button>
      </div></footer>
    </section>
  </>;
}

const initials = (fullName: string) => fullName.split(' ').map(part => part[0]).join('').slice(0, 2);
const applicationStatus = (value: string | null): ApplicationStatus | undefined =>
  value === 'APPLIED' || value === 'IN_REVIEW' || value === 'INTERVIEW' || value === 'REJECTED' || value === 'WITHDRAWN'
    ? value : undefined;
const positiveInt = (value: string | null) => value && Number.isInteger(Number(value)) && Number(value) > 0 ? Number(value) : 1;
const setPage = (current: URLSearchParams, setParams: (params: URLSearchParams) => void, page: number) => {
  const next = new URLSearchParams(current); next.set('page', String(page)); setParams(next);
};
