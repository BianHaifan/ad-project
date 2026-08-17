import {useNavigate} from 'react-router-dom';
import type {RecruiterApplicationDetail} from '../../models/recruiter';
import {StatusBadge} from '../../components/StatusBadge';
import {MessageCandidateButton} from './MessageCandidateButton';

// Fixed right-sidebar card summarising the candidate from already-authorized
// application + resume-snapshot data. It renders regardless of whether an AI
// analysis exists, so the sidebar is never a large blank space. It deliberately
// omits phone / date-of-birth / gender, which the recruiter API never returns.
export function CandidateAtAGlance({application}: {application: RecruiterApplicationDetail}) {
  const nav = useNavigate();
  const {candidate, resumeSnapshot} = application;
  const recent = resumeSnapshot.experiences.slice(0, 2);
  return <section className="panel glance-card">
    <div className="glance-head">
      {candidate.avatarUrl
        ? <img className="avatar xl" src={candidate.avatarUrl} alt=""/>
        : <span className="avatar xl">{initials(candidate.fullName)}</span>}
      <div className="grow"><h2>{candidate.fullName}</h2><b>{clean(candidate.headline) || 'No headline provided'}</b></div>
      <StatusBadge status={application.status}/>
    </div>
    <dl className="metadata-list">
      <div><dt>Email</dt><dd>{candidate.email}</dd></div>
      <div><dt>Location</dt><dd>{candidate.location || '—'}</dd></div>
      <div><dt>Applied role</dt><dd>{application.jobTitle}</dd></div>
      <div><dt>Applied</dt><dd>{new Date(application.appliedAt).toLocaleString()}</dd></div>
    </dl>
    <div className="glance-preview">
      <h3>Resume summary</h3>
      <p>{clean(resumeSnapshot.summary) || 'No summary provided'}</p>
      {recent.length > 0 && <ul>{recent.map(experience => <li key={experience.experienceId ?? `${experience.title}-${experience.company}`}>
        <b>{clean(experience.title)} · {clean(experience.company)}</b>
      </li>)}</ul>}
    </div>
    <div className="glance-actions">
      <MessageCandidateButton applicationId={application.applicationId}/>
      <button className="button secondary"
        onClick={() => nav(`/recruiter/applications/${application.applicationId}/review`)}>Open full resume</button>
    </div>
  </section>;
}

const initials = (fullName: string) => fullName.split(' ').map(part => part[0]).join('').slice(0, 2);
const clean = (value: string | null | undefined) => value?.replaceAll('Â·', '·').replaceAll('Â', '').trim() ?? '';
