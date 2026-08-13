import {useNavigate} from 'react-router-dom';
import {useDashboard} from '../api/queries';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';
import {StatusBadge} from '../components/StatusBadge';
import type {Dashboard} from '../models/recruiter';

export function DashboardPage() {
  const nav = useNavigate();
  const query = useDashboard();
  if (query.isLoading) return <LoadingState label="Loading dashboard…"/>;
  if (query.isError || !query.data) return <ErrorState onRetry={() => query.refetch()}/>;
  const dashboard = query.data;
  const cards = metricCards(dashboard);
  return <>
    <PageHeader title="Recruiter Dashboard" subtitle="Track your company's active roles and incoming applications."/>
    <section className="metric-grid">{cards.map(card =>
      <button className="metric-card" key={card.label} onClick={() => card.to && nav(card.to)}>
        <strong>{card.value}</strong><b>{card.label}</b><small>{card.help}</small>
      </button>)}</section>
    <section className="dashboard-grid">
      <article className="panel"><h2>Recent applications</h2>
        {dashboard.recentApplications.length === 0
          ? <EmptyState title="No applications yet" description="Applications to your company's jobs will appear here."/>
          : <div className="stack-list">{dashboard.recentApplications.map(application =>
            <button className="candidate-card" key={application.applicationId}
              onClick={() => nav(`/recruiter/applications/${application.applicationId}`)}>
              <span className="avatar large">{initials(application.candidate.fullName)}</span>
              <span className="grow"><b>{application.candidate.fullName}</b>
                <small>{application.jobTitle}</small></span>
              <StatusBadge status={application.status}/>
            </button>)}</div>}
      </article>
      <article className="panel"><h2>Recent job postings</h2>
        {dashboard.recentJobs.length === 0
          ? <EmptyState title="No job postings yet" description="Create a job posting to get started."/>
          : <div className="stack-list">{dashboard.recentJobs.map(job =>
            <button className="job-card" key={job.jobId} onClick={() => nav(`/recruiter/jobs/${job.jobId}`)}>
              <span><b>{job.title}</b><small><StatusBadge status={job.status}/> · {job.applicantCount} applicants</small></span>
            </button>)}</div>}
      </article>
    </section>
  </>;
}

const metricCards = (dashboard: Dashboard): {label: string; value: number | string; help: string; to: string}[] => [
  {label: 'Open Roles', value: dashboard.metrics.activeJobs, help: 'Active job postings', to: '/recruiter/jobs?status=ACTIVE'},
  {label: 'New Applications', value: dashboard.metrics.appliedApplications, help: 'Awaiting review', to: '/recruiter/applications?stage=APPLIED'},
  {label: 'In Review', value: dashboard.metrics.inReviewApplications, help: 'Under recruiter review', to: '/recruiter/applications?stage=IN_REVIEW'},
  {label: 'Interviews', value: dashboard.metrics.interviewApplications, help: 'Moved to interview', to: '/recruiter/applications?stage=INTERVIEW'},
  {label: 'Verification', value: dashboard.metrics.companyVerificationStatus, help: 'Company verification status', to: ''},
];

const initials = (fullName: string) => fullName.split(' ').map(part => part[0]).join('').slice(0, 2);
