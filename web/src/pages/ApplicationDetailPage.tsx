import {useState, type FormEvent} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {useApplication, useUpdateApplication} from '../api/queries';
import type {ApplicationStatus} from '../models/recruiter';
import type {RecruiterTransitionStatus} from '../api/recruiterRepository';
import {ErrorState, LoadingState} from '../components/AsyncState';
import {StatusBadge} from '../components/StatusBadge';

export function ApplicationDetailPage() {
  const {applicationId = ''} = useParams();
  const nav = useNavigate();
  const query = useApplication(applicationId);
  const update = useUpdateApplication();
  const [target, setTarget] = useState<RecruiterTransitionStatus | ''>('');
  const [reason, setReason] = useState('');

  if (query.isLoading) return <LoadingState label="Loading application…"/>;
  if (query.isError || !query.data) return <ErrorState onRetry={() => query.refetch()}/>;
  const application = query.data;
  const targets = allowedTargets(application.status);
  const submitTransition = (event: FormEvent) => {
    event.preventDefault();
    if (!target || !reason.trim()) return;
    update.mutate({id: application.applicationId, status: target, reason, expectedVersion: application.version}, {
      onSuccess: () => { setTarget(''); setReason(''); },
    });
  };

  return <>
    <section className="detail-header"><div>
      <button className="text-button" onClick={() => nav('/recruiter/applications')}>‹ Applications / {application.jobTitle}</button>
      <h1>{application.candidate.fullName}</h1>
      <p>Applied for {application.jobTitle} on {new Date(application.appliedAt).toLocaleString()}</p>
    </div><div className="actions"><StatusBadge status={application.status}/></div></section>
    <div className="detail-layout"><div className="detail-main">
      <section className="panel candidate-summary"><span className="avatar xl">{initials(application.candidate.fullName)}</span>
        <div className="grow"><h2>{application.candidate.fullName}</h2><b>{clean(application.candidate.headline) || 'No headline provided'}</b>
          <p>{application.candidate.email}{application.candidate.location ? ` · ${application.candidate.location}` : ''}</p></div>
        <div className="resume-meta"><small>RESUME CAPTURED</small><b>{new Date(application.resumeSnapshot.capturedAt).toLocaleString()}</b></div>
      </section>
      <section className="panel resume-snapshot"><div className="section-title"><div><h2>Submitted resume snapshot</h2>
        <small>Resume submitted with this application</small></div>
        <button className="button tiny soft" onClick={() => nav(`/recruiter/applications/${application.applicationId}/review`)}>
          Open full resume</button></div>
        <h3>Summary</h3><p>{clean(application.resumeSnapshot.summary)}</p><h3>Experience</h3>
        {application.resumeSnapshot.experiences.map(experience => <p key={experience.experienceId ?? `${experience.title}-${experience.company}`}>
          <b>{clean(experience.title)} · {clean(experience.company)}</b><br/>{clean(experience.description)}</p>)}
      </section>
      <section className="panel timeline"><div className="section-title"><h2>Application timeline</h2>
        <small>Application activity and recruiter decisions</small></div><div className="timeline-row">
        {application.timeline.map(event => <div className="done" key={event.eventId}><b>● {event.toStatus && <StatusBadge status={event.toStatus}/>}</b>
          <small>{new Date(event.occurredAt).toLocaleString()}</small>{event.reason && <small>{event.reason}</small>}</div>)}
      </div></section>
    </div><aside className="detail-side">
      {application.matchScore !== null && application.matchAnalysis !== null && <section className="panel fit-card"><div className="section-title"><div><h2>Candidate fit</h2>
        <small>Signals from the current matching model</small></div></div><span className="match-badge">{application.matchScore}% match</span>
        {application.matchAnalysis.evidence.map(evidence => <div className="evidence" key={evidence}><b>{clean(evidence)}</b></div>)}
      </section>}
      <section className="panel decision"><div className="section-title"><h2>Review decision</h2>
        <StatusBadge status={application.status}/></div>
        {targets.length === 0 ? <p>This application is in a terminal stage. No further Recruiter transition is available.</p> :
          <form onSubmit={submitTransition} className="decision-form">
            <label>NEXT STAGE<select value={target} onChange={event => setTarget(transitionStatus(event.target.value) ?? '')}>
              <option value="">Select a stage</option>{targets.map(status => <option value={status} key={status}>{statusLabel(status)}</option>)}
            </select></label>
            <label>DECISION REASON<textarea value={reason} maxLength={500} onChange={event => setReason(event.target.value)}
              placeholder="Add a reason for this decision"/></label>
            <button className="button primary" disabled={!target || !reason.trim() || update.isPending}>
              {update.isPending ? 'Saving decision…' : 'Confirm stage change'}</button>
            {update.isError && <small role="alert">The stage change could not be saved. Refresh and try again.</small>}
          </form>}
      </section>
    </aside></div>
  </>;
}

const initials = (fullName: string) => fullName.split(' ').map(part => part[0]).join('').slice(0, 2);
const clean = (value: string | null | undefined) => value?.replaceAll('Â·', '·').replaceAll('Â', '').trim() ?? '';
const transitionStatus = (value: string): RecruiterTransitionStatus | undefined =>
  value === 'IN_REVIEW' || value === 'INTERVIEW' || value === 'REJECTED' ? value : undefined;
const allowedTargets = (status: ApplicationStatus): RecruiterTransitionStatus[] => {
  if (status === 'APPLIED') return ['IN_REVIEW', 'REJECTED'];
  if (status === 'IN_REVIEW') return ['INTERVIEW', 'REJECTED'];
  if (status === 'INTERVIEW') return ['REJECTED'];
  return [];
};
const statusLabel = (status: RecruiterTransitionStatus) =>
  status === 'IN_REVIEW' ? 'Move to review' : status === 'INTERVIEW' ? 'Move to interview' : 'Reject';
