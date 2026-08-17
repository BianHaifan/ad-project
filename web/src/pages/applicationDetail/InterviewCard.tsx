import type {Interview, InterviewMode, InterviewStatus, MeetingSyncStatus} from '../../models/recruiter';
import {isValidTimeZone} from '../../lib/interviewTime';

// Interview lifecycle (Scheduled / Completed / Cancelled) rendered separately
// from application progression. Scheduling/rescheduling stays in a modal owned
// by the page; this card only summarises the interview and exposes the actions
// already permitted by the existing endpoints.
export function InterviewCard({interview, updatePending, updateError, onReschedule, onComplete, onCancel}: {
  interview: Interview;
  updatePending: boolean;
  updateError: boolean;
  onReschedule: () => void;
  onComplete: () => void;
  onCancel: () => void;
}) {
  const meetLink = googleMeetLink(interview);
  const retainedMeetLink = retainedGoogleMeetLink(interview);

  return (
    <section className="panel interview-card">
      <div className="section-title">
        <div><h2>Interview</h2><small>Scheduled with this candidate</small></div>
        <div className="actions">
          {interview.status === 'SCHEDULED' && interview.meetingProvider === 'GOOGLE_MEET' &&
            <MeetingSyncBadge status={interview.meetingSyncStatus}/>}
          <InterviewStatusBadge status={interview.status}/>
        </div>
      </div>
      <div className="form-grid">
        <p><b>When</b><br/>{new Date(interview.scheduledAt).toLocaleString()}</p>
        <p><b>Timezone</b><br/>{interview.timezone}</p>
        <p><b>Duration</b><br/>{interview.durationMinutes} minutes</p>
        <p><b>Mode</b><br/>{modeLabel(interview.mode)}</p>
      </div>
      {!isValidTimeZone(interview.timezone) &&
        <p className="muted">Saved timezone is not recognized; times are shown in your browser timezone.</p>}
      {interview.meetingProvider === 'GOOGLE_MEET'
        ? <>
            {interview.meetingSyncStatus === 'PENDING' &&
              <p className="muted" role="status">Creating or syncing the Google Meet. The link appears once it is ready.</p>}
            {interview.meetingSyncStatus === 'FAILED' &&
              <p className="muted" role="status">Google Meet sync failed. The candidate still sees the original meeting details.</p>}
            {meetLink &&
              <p><b>Google Meet</b><br/><a href={meetLink} target="_blank" rel="noreferrer">{meetLink}</a><span className="muted"> · Synced</span></p>}
            {retainedMeetLink &&
              <p><b>Existing Google Meet link (unchanged)</b><br/><a href={retainedMeetLink} target="_blank" rel="noreferrer">{retainedMeetLink}</a></p>}
          </>
        : interview.locationOrMeetingUrl && <p><b>{locationLabel(interview.mode)}</b><br/>
            {interview.mode === 'ONLINE' && isHttpUrl(interview.locationOrMeetingUrl)
              ? <a href={interview.locationOrMeetingUrl} target="_blank" rel="noreferrer">{interview.locationOrMeetingUrl}</a>
              : <span>{interview.locationOrMeetingUrl}</span>}</p>}
      {interview.note && <p><b>Note</b><br/>{interview.note}</p>}
      {interview.status === 'SCHEDULED'
        ? (interview.meetingProvider === 'GOOGLE_MEET' && interview.meetingSyncStatus === 'PENDING')
          ? <p className="muted">A Google Meet sync is in progress. Reschedule, complete and cancel are unavailable until it finishes.</p>
          : <div className="actions">
              {interview.meetingProvider === 'GOOGLE_MEET' && interview.meetingSyncStatus === 'FAILED'
                ? <button className="button soft" disabled={updatePending} onClick={onReschedule}>Retry Google Meet</button>
                : <button className="button secondary" disabled={updatePending} onClick={onReschedule}>Reschedule</button>}
              <button className="button primary" disabled={updatePending} onClick={onComplete}>Mark completed</button>
              <button className="button danger" disabled={updatePending} onClick={onCancel}>Cancel interview</button>
            </div>
        : <p className="muted">This interview is {interview.status.toLowerCase()}; no further changes are allowed.</p>}
      {updateError && <small role="alert">The interview could not be updated. Refresh and try again.</small>}
    </section>
  );
}

function InterviewStatusBadge({status}: {status: InterviewStatus}) {
  return <span className={`badge ${status.toLowerCase()}`}>{interviewStatusLabel(status)}</span>;
}

function MeetingSyncBadge({status}: {status: MeetingSyncStatus}) {
  if (status === 'NOT_APPLICABLE') return null;
  const label = status === 'PENDING' ? 'Syncing' : status === 'READY' ? 'Synced' : 'Sync failed';
  const className = status === 'PENDING' ? 'sync_pending' : status === 'READY' ? 'sync_ready' : 'sync_failed';
  return <span className={`badge ${className}`}>{label}</span>;
}

const isGoogleMeetUrl = (value: string | null): value is string =>
  !!value && /^https:\/\/meet\.google\.com\//i.test(value);

// The Meet link is only a safe, ready, clickable link when the interview is still
// scheduled and the backend returned a verified HTTPS meet.google.com URL.
function googleMeetLink(interview: Interview): string | null {
  if (interview.meetingProvider !== 'GOOGLE_MEET' || interview.meetingSyncStatus !== 'READY' ||
      interview.status !== 'SCHEDULED') return null;
  return isGoogleMeetUrl(interview.locationOrMeetingUrl) ? interview.locationOrMeetingUrl : null;
}

// A FAILED sync that still holds the previously verified Meet link means an
// external reschedule/cancel failed and the old link is still valid.
function retainedGoogleMeetLink(interview: Interview): string | null {
  if (interview.meetingProvider !== 'GOOGLE_MEET' || interview.meetingSyncStatus !== 'FAILED' ||
      interview.status !== 'SCHEDULED') return null;
  return isGoogleMeetUrl(interview.locationOrMeetingUrl) ? interview.locationOrMeetingUrl : null;
}

const modeLabel = (mode: InterviewMode) => mode === 'ONLINE' ? 'Online' : mode === 'ONSITE' ? 'On-site' : 'Phone';
const interviewStatusLabel = (status: InterviewStatus) =>
  status === 'SCHEDULED' ? 'Scheduled' : status === 'COMPLETED' ? 'Completed' : 'Cancelled';
const isHttpUrl = (value: string) => /^https?:\/\//i.test(value);
const locationLabel = (mode: InterviewMode) => mode === 'ONLINE' ? 'Meeting link' : mode === 'ONSITE' ? 'Location' : 'Phone / contact';
