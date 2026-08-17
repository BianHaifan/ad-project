import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useApplicantRecommendations} from '../api/queries';
import type {RecommendedApplicant} from '../models/recruiter';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';
import {StatusBadge} from '../components/StatusBadge';

const PAGE_SIZE = 20;

export function AiRankApplicants({jobId, jobTitle}: {jobId: string; jobTitle: string}) {
  const nav = useNavigate();
  const [open, setOpen] = useState(false);
  const [page, setPage] = useState(1);
  const query = useApplicantRecommendations(open ? jobId : undefined, {page, pageSize: PAGE_SIZE});

  return <section className="table-panel rank-panel">
    <div className="table-toolbar">
      <div className="grow"><h2>AI ranked applicants</h2><small>{jobTitle}</small></div>
      <button className={`button ${open ? 'secondary' : 'soft'}`} aria-pressed={open}
        onClick={() => setOpen(value => !value)}>
        {open ? 'Hide ranking' : 'AI rank applicants'}
      </button>
    </div>
    {!open ? <EmptyState title="AI rank applicants"
      description={`Rank candidates for “${jobTitle}” by how closely their resume matches the role.`}/> :
      query.isLoading ? <LoadingState label="Ranking applicants…"/> :
        query.isError || !query.data ? <ErrorState onRetry={() => query.refetch()}/> :
          query.data.data.length === 0 ? <EmptyState title="No eligible applicants"
            description="There are no applied, in-review or interview candidates to rank for this job."/> :
            <RankedList data={query.data.data} total={query.data.meta.total} hasNext={query.data.meta.hasNext}
              degraded={query.data.meta.modelStatus === 'DEGRADED'} modelVersion={query.data.meta.modelVersion}
              source={query.data.meta.source} page={page} onPage={setPage}
              onOpen={applicationId => nav(`/recruiter/applications/${applicationId}`)}/>}
  </section>;
}

function RankedList({data, total, hasNext, degraded, modelVersion, source, page, onPage, onOpen}: {
  data: RecommendedApplicant[]; total: number; hasNext: boolean; degraded: boolean; modelVersion: string;
  source: string; page: number; onPage: (page: number) => void; onOpen: (applicationId: string) => void;
}) {
  const first = total === 0 ? 0 : (page - 1) * PAGE_SIZE + 1;
  const last = Math.min(total, (page - 1) * PAGE_SIZE + data.length);
  return <>
    {degraded && <p className="demo-notice">The AI model is currently unavailable, so these results use a
      deterministic rule-based ranking. Review the evidence before deciding.</p>}
    {source === 'MODEL' && <p className="meta-note">Ranked by ML model {modelVersion || 'current'} · {total} candidate{total === 1 ? '' : 's'}</p>}
    <div className="data-table app-table with-match rank-table">
      <div className="table-head"><span>Rank</span><span>Candidate</span><span>AI fit score</span><span>Applied</span>
        <span>Stage</span><span>Why they match</span><span>Action</span></div>
      {data.map(row => <RankRow key={row.applicationId} row={row} onOpen={onOpen}/>)}
    </div>
    <footer className="pagination"><span>Showing {first}–{last} of {total} candidates</span><div className="actions">
      <button className="button tiny secondary" disabled={page <= 1} onClick={() => onPage(page - 1)}>Previous</button>
      <button className="button tiny secondary" disabled={!hasNext} onClick={() => onPage(page + 1)}>Next</button>
    </div></footer>
  </>;
}

function RankRow({row, onOpen}: {row: RecommendedApplicant; onOpen: (applicationId: string) => void}) {
  const strong = row.matchAnalysis.strongMatches.slice(0, 2);
  const gaps = row.matchAnalysis.gaps.slice(0, 2);
  return <div className="table-row">
    <span className="rank-badge">#{row.rank}</span>
    <span className="person"><i className="avatar">{initials(row.candidate.fullName)}</i><span>
      <b>{row.candidate.fullName}</b><small>{row.candidate.headline ?? row.candidate.location ?? '—'}</small></span></span>
    <span className="fit-score">{row.matchScore} / 100</span>
    <span>{new Date(row.appliedAt).toLocaleDateString()}</span>
    <StatusBadge status={row.status}/>
    <span className="cell-stack">
      {strong.length > 0 && <small className="match-line">✓ {strong.join(' · ')}</small>}
      {gaps.length > 0 && <small className="gap-line">✗ {gaps.join(' · ')}</small>}
      {strong.length === 0 && gaps.length === 0 && <small>No summary available</small>}
    </span>
    <span className="row-actions"><button className="button tiny secondary"
      aria-label={`View application for ${row.candidate.fullName}`} onClick={() => onOpen(row.applicationId)}>
      View</button></span>
  </div>;
}

const initials = (fullName: string) => fullName.split(' ').map(part => part[0]).join('').slice(0, 2);
