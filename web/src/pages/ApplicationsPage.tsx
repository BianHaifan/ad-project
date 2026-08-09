import {useEffect, useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {useApplicationCounts, useApplications} from '../api/queries';
import type {ApplicationStatus, RecruiterApplicationCounts} from '../models/recruiter';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';
import {StatusBadge} from '../components/StatusBadge';

const stages: [keyof RecruiterApplicationCounts, string, string][] = [
  ['APPLIED', 'New applications', 'Since last review'], ['IN_REVIEW', 'In review', 'Assigned to hiring team'],
  ['INTERVIEW', 'Interview', 'Scheduled or completed'], ['REJECTED', 'Rejected', 'Archived this month'],
];

export function ApplicationsPage() {
  const nav = useNavigate(); const [params, setParams] = useSearchParams();
  const raw = params.get('stage');
  const selected = applicationStatus(raw);
  const [search, setSearch] = useState(''); const counts = useApplicationCounts(); const query = useApplications(selected, search);
  useEffect(() => setSearch(''), [selected]);
  if (query.isLoading || counts.isLoading) return <LoadingState label="Loading applications…"/>;
  if (query.isError || counts.isError || !query.data || !counts.data) return <ErrorState onRetry={() => {query.refetch(); counts.refetch();}}/>;
  return <><PageHeader title="Applications" subtitle="Review candidates, update stages, and keep every hiring decision traceable." actions={<button className="button primary" onClick={() => nav('/recruiter/jobs/new')}>Create job posting</button>}/><section className="metric-grid four stage-cards">{stages.map(([key, label, help]) => <button key={key} className={`metric-card stage-${key.toLowerCase()} ${selected === key ? 'selected' : ''}`} onClick={() => setParams(selected === key ? {} : {stage: key})}><strong>{counts.data[key]}</strong><b>{label}</b><small>{help}</small></button>)}</section><section className="table-panel"><div className="table-toolbar"><div className="grow"><h2>{selected ?? 'All applications'}</h2><small>{query.data.length} candidates across active roles</small></div><input value={search} onChange={event => setSearch(event.target.value)} placeholder="⌕ Search candidate"/><select value={selected ?? 'ALL'} onChange={event => setParams(event.target.value === 'ALL' ? {} : {stage: event.target.value})}><option value="ALL">All stages</option>{stages.map(stage => <option key={stage[0]} value={stage[0]}>{stage[1]}</option>)}<option value="WITHDRAWN">Withdrawn</option></select></div>{query.data.length === 0 ? <EmptyState title="No applications found" description="Applications matching this stage or search will appear here."/> : <div className="data-table app-table"><div className="table-head"><span>Candidate</span><span>Applied role</span><span>ML match</span><span>Applied</span><span>Stage</span><span>Owner</span><span/></div>{query.data.map(application => <button className="table-row" key={application.applicationId} onClick={() => nav(`/recruiter/applications/${application.applicationId}`)}><span className="person"><i className="avatar">{initials(application.candidate.fullName)}</i><span><b>{application.candidate.fullName}</b><small>{application.candidate.email}</small></span></span><span><b>{application.jobTitle}</b><small>{application.jobId}</small></span><span className="match-badge">{application.matchScore ?? 0}% match</span><span>{new Date(application.appliedAt).toLocaleDateString()}</span><StatusBadge status={application.status}/><span>{application.owner?.fullName ?? 'Unassigned'}</span><span className="button tiny secondary">View</span></button>)}</div>}<footer className="pagination"><span>Showing 1–{query.data.length} applications</span></footer></section></>;
}

const initials = (fullName: string) => fullName.split(' ').map(part => part[0]).join('').slice(0, 2);
const applicationStatus = (value: string | null): ApplicationStatus | undefined =>
  value === 'APPLIED' || value === 'IN_REVIEW' || value === 'INTERVIEW' || value === 'REJECTED' || value === 'WITHDRAWN' ? value : undefined;
