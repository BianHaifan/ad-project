import {useEffect, useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {useApplications, useJobs} from '../api/queries';
import type {ApplicationStatus, RecruiterApplicationCounts} from '../models/recruiter';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';
import {StatusBadge} from '../components/StatusBadge';
import {rankApplicationsByMatch} from './applicationRanking';
import {AiRankApplicants} from './AiRankApplicants';

const stages: [keyof RecruiterApplicationCounts, ApplicationStatus, string, string][] = [
  ['applied', 'APPLIED', 'New applications', 'Awaiting review'],
  ['inReview', 'IN_REVIEW', 'In review', 'Under recruiter review'],
  ['interview', 'INTERVIEW', 'Interview', 'Moved to interview'],
  ['offered', 'OFFERED', 'Offer made', 'Hiring decision issued'],
  ['rejected', 'REJECTED', 'Rejected', 'Recruiter decisions'],
];

export function ApplicationsPage() {
  const nav = useNavigate();
  const [urlParams, setUrlParams] = useSearchParams();
  const selected = applicationStatus(urlParams.get('stage'));
  const page = positiveInt(urlParams.get('page'));
  const [search, setSearch] = useState('');
  const [rankByMatch, setRankByMatch] = useState(true);
  const [rankJobId, setRankJobId] = useState<string | undefined>(undefined);
  const jobsQuery = useJobs({page: 1, pageSize: 100});
  const query = useApplications({status: selected, q: search, page, pageSize: 20, sort: 'appliedAt,desc'});
  const rankJobTitle = (jobsQuery.data?.data ?? []).find(job => job.jobId === rankJobId)?.title ?? 'Selected job';

  useEffect(() => setSearch(''), [selected]);
  const chooseStage = (status?: ApplicationStatus) => {
    const next = new URLSearchParams();
    if (status) next.set('stage', status);
    setUrlParams(next);
  };

  if (query.isLoading) return <LoadingState label="Loading applications…"/>;
  if (query.isError || !query.data) return <ErrorState onRetry={() => query.refetch()}/>;
  const {data: applications, meta} = query.data;
  const rows = rankByMatch ? rankApplicationsByMatch(applications) : applications;
  const first = meta.total === 0 ? 0 : (meta.page - 1) * meta.pageSize + 1;
  const last = Math.min(meta.total, (meta.page - 1) * meta.pageSize + applications.length);

  return <>
    <PageHeader title="Applications" subtitle="Review candidates and record every supported stage decision."/>
    <section className="metric-grid stage-cards">{stages.map(([countKey, status, label, help]) =>
      <button key={status} className={`metric-card stage-${status.toLowerCase()} ${selected === status ? 'selected' : ''}`}
        onClick={() => chooseStage(selected === status ? undefined : status)}>
        <strong>{meta.counts[countKey]}</strong><b>{label}</b><small>{help}</small>
      </button>)}</section>
    <section className="table-panel">
      <div className="table-toolbar"><div className="grow"><h2>{selected ?? 'All applications'}</h2>
        <small>{meta.total} application{meta.total === 1 ? '' : 's'}</small></div>
        <button className={`button ${rankByMatch ? 'soft' : 'secondary'}`}
          aria-pressed={rankByMatch} onClick={() => setRankByMatch(value => !value)}>
          Rank by match</button>
        <input value={search} onChange={event => {setSearch(event.target.value); chooseStage(selected);}}
          placeholder="⌕ Search candidate name or email"/>
        <select value={selected ?? 'ALL'} onChange={event => chooseStage(applicationStatus(event.target.value))}>
          <option value="ALL">All stages</option>{stages.map(([, status, label]) =>
            <option key={status} value={status}>{label}</option>)}<option value="WITHDRAWN">Withdrawn</option>
        </select>
        <select value={rankJobId ?? ''} aria-label="AI rank applicants for job"
          onChange={event => setRankJobId(event.target.value || undefined)}>
          <option value="">AI rank: choose a job…</option>
          {(jobsQuery.data?.data ?? []).map(job => <option key={job.jobId} value={job.jobId}>{job.title}</option>)}
        </select>
      </div>
      {rankByMatch && <p className="meta-note">Sorted by the match scores returned by the service. Review the evidence before making a decision.</p>}
      {applications.length === 0 ? <EmptyState title="No applications found"
        description="Applications matching this stage or search will appear here."/> :
        <div className="data-table app-table with-match"><div className="table-head"><span>Candidate</span><span>Applied role</span>
          <span>AI fit score</span><span>Applied</span><span>Stage</span><span>Owner</span><span>Action</span></div>
          {rows.map(application => <div className="table-row" key={application.applicationId}>
            <span className="person"><i className="avatar">{initials(application.candidate.fullName)}</i><span>
              <b>{application.candidate.fullName}</b><small>{application.candidate.email}</small></span></span>
            <span className="cell-stack"><b className="cell-ellipsis">{application.jobTitle}</b><small>Updated {new Date(application.updatedAt).toLocaleDateString()}</small></span>
            <span className="fit-score">{application.matchScore === null ? '—' : `${application.matchScore} / 100`}</span>
            <span>{new Date(application.appliedAt).toLocaleDateString()}</span><StatusBadge status={application.status}/>
            <span className="cell-ellipsis">{application.owner?.fullName ?? 'Unassigned'}</span><span className="row-actions"><button className="button tiny secondary" aria-label={`View application for ${application.candidate.fullName}`} onClick={() => nav(`/recruiter/applications/${application.applicationId}`)}>View</button></span>
          </div>)}</div>}
      <footer className="pagination"><span>Showing {first}–{last} of {meta.total} applications</span><div className="actions">
        <button className="button tiny secondary" disabled={meta.page <= 1}
          onClick={() => setPage(urlParams, setUrlParams, meta.page - 1)}>Previous</button>
        <button className="button tiny secondary" disabled={!meta.hasNext}
          onClick={() => setPage(urlParams, setUrlParams, meta.page + 1)}>Next</button>
      </div></footer>
    </section>
    {rankJobId && <AiRankApplicants jobId={rankJobId} jobTitle={rankJobTitle}/>}
  </>;
}

const initials = (fullName: string) => fullName.split(' ').map(part => part[0]).join('').slice(0, 2);
const applicationStatus = (value: string | null): ApplicationStatus | undefined =>
  value === 'APPLIED' || value === 'IN_REVIEW' || value === 'INTERVIEW' || value === 'OFFERED' || value === 'REJECTED' || value === 'WITHDRAWN'
    ? value : undefined;
const positiveInt = (value: string | null) => value && Number.isInteger(Number(value)) && Number(value) > 0 ? Number(value) : 1;
const setPage = (current: URLSearchParams, setParams: (params: URLSearchParams) => void, page: number) => {
  const next = new URLSearchParams(current); next.set('page', String(page)); setParams(next);
};
