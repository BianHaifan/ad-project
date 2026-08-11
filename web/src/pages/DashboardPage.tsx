import {useNavigate} from 'react-router-dom';
import {useDashboard} from '../api/queries';
import {ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';
import {StatusBadge} from '../components/StatusBadge';

export function DashboardPage() {
  const nav = useNavigate(); const query = useDashboard();
  if (query.isLoading) return <LoadingState/>;
  if (query.isError || !query.data) return <ErrorState onRetry={() => query.refetch()}/>;
  const dashboard = query.data;
  const metrics = [
    ['Open Roles', dashboard.metrics.openRoles, '4 AI/LLM roles need updates', '/recruiter/jobs'],
    ['New Matches', dashboard.metrics.newMatches, 'Recommended by ML algorithm', '/recruiter/applications'],
    ['Pending Resumes', dashboard.metrics.pendingResumes, 'Need HR review', '/recruiter/applications?stage=APPLIED'],
    ['Interviews', dashboard.metrics.interviews, 'Scheduled this week', '/recruiter/applications?stage=INTERVIEW'],
    ['Verification', dashboard.metrics.verification, 'Company profile verified', ''],
  ];
  return <><PageHeader title="Recruiter Dashboard" subtitle="Manage AI roles, review resumes, and discover matching candidates." actions={<button className="button primary" onClick={() => nav('/recruiter/jobs/new')}>Create Job Posting</button>}/><section className="metric-grid">{metrics.map(([label, value, help, to]) => <button className="metric-card" key={label} onClick={() => to && nav(String(to))}><strong>{value}</strong><b>{label}</b><small>{help}</small></button>)}</section><section className="dashboard-grid"><article className="panel"><h2>Talent Pool Recommendations</h2><div className="stack-list">{dashboard.recommendedApplications.map(application => <button className="candidate-card" key={application.applicationId} onClick={() => nav(`/recruiter/applications/${application.applicationId}`)}><span className="avatar large">{initials(application.candidate.fullName)}</span><span className="grow"><b>{application.candidate.fullName}</b><small>{application.candidate.headline}</small></span><span className="match-badge">{application.matchScore ?? 0}% match</span><StatusBadge status={application.status}/></button>)}</div></article><article className="panel"><h2>Job Postings <small>Demo data</small></h2><div className="stack-list">{dashboard.recentJobs.map(job => <div className="job-card" key={job.jobId}><span><b>{job.title}</b><small><StatusBadge status={job.status}/> · {job.applicantCount} applicants</small></span><span className="muted">Dashboard mock</span></div>)}</div></article></section></>;
}

const initials = (fullName: string) => fullName.split(' ').map(part => part[0]).join('').slice(0, 2);
