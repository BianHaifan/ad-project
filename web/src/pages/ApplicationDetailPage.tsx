import {useState, type FormEvent} from 'react';
import {Link, useNavigate, useParams} from 'react-router-dom';
import {AuthApiError} from '../api/authClient';
import {useApplication, useCreateInterview, useGoogleConnection, useUpdateApplication, useUpdateInterview} from '../api/queries';
import type {ApplicationStatus, CreateInterviewRequest, Interview, InterviewMode, InterviewStatus, MeetingSyncStatus, UpdateInterviewRequest} from '../models/recruiter';
import type {RecruiterTransitionStatus} from '../api/recruiterRepository';
import {ErrorState, LoadingState} from '../components/AsyncState';
import {StatusBadge} from '../components/StatusBadge';
import {isValidTimeZone, localToUtcIso, resolvedTimeZone, utcToLocalInput} from '../lib/interviewTime';

export function ApplicationDetailPage() {
  const {applicationId = ''} = useParams();
  const nav = useNavigate();
  const query = useApplication(applicationId);
  const update = useUpdateApplication();
  const createInterview = useCreateInterview();
  const updateInterview = useUpdateInterview();
  const connection = useGoogleConnection();
  const [target, setTarget] = useState<RecruiterTransitionStatus | ''>('');
  const [reason, setReason] = useState('');
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [rescheduleOpen, setRescheduleOpen] = useState(false);
  const [scheduledAt, setScheduledAt] = useState('');
  const [timezone, setTimezone] = useState('');
  const [duration, setDuration] = useState('60');
  const [mode, setMode] = useState<InterviewMode>('ONLINE');
  const [location, setLocation] = useState('');
  const [note, setNote] = useState('');
  const [interviewError, setInterviewError] = useState<string | null>(null);
  const [timezoneNotice, setTimezoneNotice] = useState<string | null>(null);

  if (query.isLoading) return <LoadingState label="Loading application…"/>;
  if (query.isError || !query.data) return <ErrorState onRetry={() => query.refetch()}/>;
  const application = query.data;
  const interview = application.interview;
  const targets = allowedTargets(application.status);
  const isGoogleMeet = interview?.meetingProvider === 'GOOGLE_MEET';
  const retryingSync = isGoogleMeet && interview?.meetingSyncStatus === 'FAILED';
  const meetLink = interview ? googleMeetLink(interview) : null;
  const retainedMeetLink = interview ? retainedGoogleMeetLink(interview) : null;
  const googleConnectNote = (() => {
    if (connection.isLoading) return <small className="muted">Checking Google connection…</small>;
    if (connection.isError || !connection.data) return (
      <small className="muted">We could not verify your Google connection. <Link to="/recruiter/google-oauth">Go to Integrations</Link>.</small>
    );
    if (connection.data.status === 'CONNECTED') return null;
    return connection.data.status === 'REVOKED'
      ? <small className="muted">Your Google authorization has expired. <Link to="/recruiter/google-oauth">Reconnect Google</Link> to schedule a Google Meet.</small>
      : <small className="muted">Connect Google Calendar to schedule a Google Meet. <Link to="/recruiter/google-oauth">Go to Integrations</Link>.</small>;
  })();

  const submitTransition = (event: FormEvent) => {
    event.preventDefault();
    if (!target || !reason.trim()) return;
    update.mutate({id: application.applicationId, status: target, reason, expectedVersion: application.version}, {
      onSuccess: () => { setTarget(''); setReason(''); },
    });
  };

  const openSchedule = () => {
    setInterviewError(null);
    setTimezoneNotice(null);
    setScheduledAt(''); setTimezone(resolvedTimeZone()); setDuration('60'); setMode('ONLINE'); setLocation(''); setNote('');
    setScheduleOpen(true);
  };

  const openReschedule = () => {
    if (!interview) return;
    setInterviewError(null);
    const savedTimezone = interview.timezone;
    const valid = isValidTimeZone(savedTimezone);
    const tz = valid ? savedTimezone : resolvedTimeZone();
    setScheduledAt(utcToLocalInput(interview.scheduledAt, tz));
    setTimezone(tz);
    setTimezoneNotice(valid ? null
      : `The saved timezone "${savedTimezone}" is not recognized. Using your browser timezone (${tz}).`);
    setDuration(String(interview.durationMinutes));
    setMode(interview.mode);
    setLocation(interview.locationOrMeetingUrl ?? '');
    setNote(interview.note ?? '');
    setRescheduleOpen(true);
  };

  const googleMeetAvailable = mode === 'ONLINE' && connection.data?.status === 'CONNECTED';

  const validTimeAndDuration = () => {
    const minutes = Number(duration);
    return localToUtcIso(scheduledAt, timezone) !== null && timezone.trim() !== '' &&
      Number.isInteger(minutes) && minutes >= 1 && minutes <= 1440;
  };

  const validSchedule = () => {
    if (!validTimeAndDuration()) return false;
    if (mode === 'ONLINE') return googleMeetAvailable;
    return validLocation(mode, location);
  };

  const validReschedule = () => {
    if (!interview || !validTimeAndDuration()) return false;
    if (interview.meetingProvider === 'GOOGLE_MEET') return mode === 'ONLINE';
    return validLocation(mode, location);
  };

  const submitSchedule = (event: FormEvent) => {
    event.preventDefault();
    if (!validSchedule()) return;
    setInterviewError(null);
    const input: CreateInterviewRequest = {
      scheduledAt: localToUtcIso(scheduledAt, timezone)!,
      timezone: timezone.trim(),
      durationMinutes: Number(duration),
      mode,
      note: note.trim() || undefined,
      expectedApplicationVersion: application.version,
    };
    if (mode === 'ONLINE') {
      input.meetingProvider = 'GOOGLE_MEET';
    } else {
      input.locationOrMeetingUrl = location.trim();
    }
    createInterview.mutate({applicationId: application.applicationId, input}, {
      onSuccess: () => setScheduleOpen(false),
      onError: caught => setInterviewError(presentInterviewError(caught).message),
    });
  };

  const submitReschedule = (event: FormEvent) => {
    event.preventDefault();
    if (!interview || !validReschedule()) return;
    setInterviewError(null);
    const input: UpdateInterviewRequest = {
      scheduledAt: localToUtcIso(scheduledAt, timezone)!,
      timezone: timezone.trim(),
      durationMinutes: Number(duration),
      note: note.trim() || undefined,
      expectedVersion: interview.version,
    };
    if (interview.meetingProvider !== 'GOOGLE_MEET') {
      input.mode = mode;
      input.locationOrMeetingUrl = location.trim();
    }
    updateInterview.mutate({interviewId: interview.interviewId, input}, {
      onSuccess: () => setRescheduleOpen(false),
      onError: caught => setInterviewError(presentInterviewError(caught).message),
    });
  };

  const completeInterview = () => {
    if (!interview) return;
    updateInterview.mutate({interviewId: interview.interviewId,
      input: {status: 'COMPLETED', expectedVersion: interview.version}});
  };

  const cancelInterview = () => {
    if (!interview) return;
    updateInterview.mutate({interviewId: interview.interviewId,
      input: {status: 'CANCELLED', expectedVersion: interview.version}});
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
      {interview && <section className="panel interview-card"><div className="section-title"><div><h2>Interview</h2>
        <small>Scheduled with this candidate</small></div>
        <div className="actions">
          {interview.status === 'SCHEDULED' && interview.meetingProvider === 'GOOGLE_MEET' &&
            <MeetingSyncBadge status={interview.meetingSyncStatus}/>}
          <InterviewStatusBadge status={interview.status}/>
        </div></div>
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
                  ? <button className="button soft" disabled={updateInterview.isPending} onClick={openReschedule}>Retry Google Meet</button>
                  : <button className="button secondary" disabled={updateInterview.isPending} onClick={openReschedule}>Reschedule</button>}
                <button className="button primary" disabled={updateInterview.isPending} onClick={completeInterview}>Mark completed</button>
                <button className="button danger" disabled={updateInterview.isPending} onClick={cancelInterview}>Cancel interview</button>
              </div>
          : <p className="muted">This interview is {interview.status.toLowerCase()}; no further changes are allowed.</p>}
        {updateInterview.isError && <small role="alert">The interview could not be updated. Refresh and try again.</small>}
      </section>}
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
        <small>{application.matchAnalysis.modelVersion} · advisory only</small></div></div><span className="match-badge">{application.matchScore}% match</span>
        {application.matchAnalysis.evidence.map(evidence => <div className="evidence" key={evidence}><b>{clean(evidence)}</b></div>)}
        <div><b>Strong matches</b><p>{application.matchAnalysis.strongMatches.map(clean).join(' · ') || 'No strong signals supplied'}</p></div>
        <div><b>Gaps to review</b><p>{application.matchAnalysis.gaps.map(clean).join(' · ') || 'No gaps supplied'}</p></div>
      </section>}
      <section className="panel decision"><div className="section-title"><h2>Review decision</h2>
        <StatusBadge status={application.status}/></div>
        {application.status === 'IN_REVIEW' && !interview &&
          <button className="button primary" onClick={openSchedule}>Schedule interview</button>}
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
    {scheduleOpen && <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="schedule-title">
      <section className="modal"><h2 id="schedule-title">Schedule interview</h2>
        <p>Scheduling moves this application from in review to interview.</p>
        <form className="form-grid" onSubmit={submitSchedule}>
          <label>MODE<select value={mode} onChange={event => setMode(event.target.value as InterviewMode)} aria-label="Mode">
            <option value="ONLINE">Online — Google Meet</option>
            <option value="ONSITE">On-site — in-person location</option>
            <option value="PHONE">Phone — call details</option>
          </select></label>
          <label>DATE / TIME (LOCAL)<input type="datetime-local" value={scheduledAt}
            onChange={event => setScheduledAt(event.target.value)} aria-label="Date and time"/></label>
          <p className="muted" style={{gridColumn: '1 / -1', margin: 0}}>Your browser timezone: {timezone}</p>
          <label>DURATION (MINUTES)<input type="number" min={1} max={1440} value={duration}
            onChange={event => setDuration(event.target.value)} aria-label="Duration minutes"/></label>
          {mode === 'ONLINE'
            ? <div style={{gridColumn: '1 / -1'}}>
                <p className="muted" style={{margin: 0}}>Google Meet — meeting link and Calendar invitation will be created automatically.</p>
                {googleConnectNote}
              </div>
            : <label style={{gridColumn: '1 / -1'}}>{scheduleLocationField(mode).label}<input value={location} maxLength={1000}
                placeholder={scheduleLocationField(mode).placeholder} onChange={event => setLocation(event.target.value)} aria-label={scheduleLocationField(mode).label}/></label>}
          <label style={{gridColumn: '1 / -1'}}>NOTE (OPTIONAL)<textarea value={note} maxLength={500} rows={2}
            onChange={event => setNote(event.target.value)} aria-label="Note"/></label>
          {timezoneNotice && <small role="status" style={{gridColumn: '1 / -1'}}>{timezoneNotice}</small>}
          {interviewError && <small role="alert" style={{gridColumn: '1 / -1'}}>{interviewError}</small>}
          <div className="actions" style={{gridColumn: '1 / -1'}}>
            <button type="button" className="button secondary" disabled={createInterview.isPending} onClick={() => setScheduleOpen(false)}>Cancel</button>
            <button type="submit" className="button primary" disabled={createInterview.isPending || !validSchedule()}>
              {createInterview.isPending ? 'Scheduling…' : 'Confirm schedule'}</button>
          </div>
        </form>
      </section>
    </div>}
    {rescheduleOpen && interview && <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="reschedule-title">
      <section className="modal"><h2 id="reschedule-title">{retryingSync ? 'Retry Google Meet sync' : 'Reschedule interview'}</h2>
        <p>{retryingSync ? 'Retrying updates the existing Google Meet — no new interview is created.' : 'Updating the time keeps the interview scheduled.'}</p>
        <form className="form-grid" onSubmit={submitReschedule}>
          <label>DATE / TIME (LOCAL)<input type="datetime-local" value={scheduledAt}
            onChange={event => setScheduledAt(event.target.value)} aria-label="Date and time"/></label>
          <label>TIMEZONE<input value={timezone} readOnly aria-label="Timezone"/></label>
          <label>DURATION (MINUTES)<input type="number" min={1} max={1440} value={duration}
            onChange={event => setDuration(event.target.value)} aria-label="Duration minutes"/></label>
          {isGoogleMeet
            ? <label>MODE<input value="Online" readOnly aria-label="Mode"/></label>
            : <label>MODE<select value={mode} onChange={event => setMode(event.target.value as InterviewMode)} aria-label="Mode">
                <option value="ONLINE">Online</option><option value="ONSITE">On-site</option><option value="PHONE">Phone</option>
              </select></label>}
          {isGoogleMeet
            ? <label style={{gridColumn: '1 / -1'}}>MEETING PROVIDER<input value="Google Meet" readOnly aria-label="Meeting provider"/></label>
            : <label style={{gridColumn: '1 / -1'}}>{locationField(mode).label}<input value={location} maxLength={1000}
                placeholder={locationField(mode).placeholder} onChange={event => setLocation(event.target.value)} aria-label="Location or meeting link"/></label>}
          <label style={{gridColumn: '1 / -1'}}>NOTE (OPTIONAL)<textarea value={note} maxLength={500} rows={2}
            onChange={event => setNote(event.target.value)} aria-label="Note"/></label>
          {timezoneNotice && <small role="status" style={{gridColumn: '1 / -1'}}>{timezoneNotice}</small>}
          {interviewError && <small role="alert" style={{gridColumn: '1 / -1'}}>{interviewError}</small>}
          <div className="actions" style={{gridColumn: '1 / -1'}}>
            <button type="button" className="button secondary" disabled={updateInterview.isPending} onClick={() => setRescheduleOpen(false)}>Cancel</button>
            <button type="submit" className="button primary" disabled={updateInterview.isPending || !validReschedule()}>
              {updateInterview.isPending ? 'Saving…' : retryingSync ? 'Retry sync' : 'Save changes'}</button>
          </div>
        </form>
      </section>
    </div>}
  </>;
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
// scheduled and the backend returned a verified HTTPS meet.google.com URL. Any other
// combination (PENDING, FAILED, cancelled, completed, or a non-HTTPS link) returns null.
function googleMeetLink(interview: Interview): string | null {
  if (interview.meetingProvider !== 'GOOGLE_MEET' || interview.meetingSyncStatus !== 'READY' ||
      interview.status !== 'SCHEDULED') return null;
  return isGoogleMeetUrl(interview.locationOrMeetingUrl) ? interview.locationOrMeetingUrl : null;
}

// A FAILED sync that still holds the previously verified Meet link means an
// external reschedule/cancel failed and the old link is still valid. The recruiter
// should still see it, clearly marked as unchanged — never as "synced".
function retainedGoogleMeetLink(interview: Interview): string | null {
  if (interview.meetingProvider !== 'GOOGLE_MEET' || interview.meetingSyncStatus !== 'FAILED' ||
      interview.status !== 'SCHEDULED') return null;
  return isGoogleMeetUrl(interview.locationOrMeetingUrl) ? interview.locationOrMeetingUrl : null;
}

const initials = (fullName: string) => fullName.split(' ').map(part => part[0]).join('').slice(0, 2);
const clean = (value: string | null | undefined) => value?.replaceAll('Â·', '·').replaceAll('Â', '').trim() ?? '';
const transitionStatus = (value: string): RecruiterTransitionStatus | undefined =>
  value === 'IN_REVIEW' || value === 'REJECTED' ? value : undefined;
const allowedTargets = (status: ApplicationStatus): RecruiterTransitionStatus[] => {
  if (status === 'APPLIED') return ['IN_REVIEW', 'REJECTED'];
  if (status === 'IN_REVIEW') return ['REJECTED'];
  if (status === 'INTERVIEW') return ['REJECTED'];
  return [];
};
const statusLabel = (status: RecruiterTransitionStatus) =>
  status === 'IN_REVIEW' ? 'Move to review' : 'Reject';
const modeLabel = (mode: InterviewMode) => mode === 'ONLINE' ? 'Online' : mode === 'ONSITE' ? 'On-site' : 'Phone';
const interviewStatusLabel = (status: InterviewStatus) =>
  status === 'SCHEDULED' ? 'Scheduled' : status === 'COMPLETED' ? 'Completed' : 'Cancelled';
const isHttpUrl = (value: string) => /^https?:\/\//i.test(value);
const validLocation = (mode: InterviewMode, value: string) => {
  const trimmed = value.trim();
  if (!trimmed) return false;
  return mode !== 'ONLINE' || isHttpUrl(trimmed);
};
const locationField = (mode: InterviewMode) => mode === 'ONLINE'
  ? {label: 'MEETING LINK', placeholder: 'https://meet.example.com/…'}
  : mode === 'ONSITE'
    ? {label: 'LOCATION', placeholder: 'e.g. 12 Marina Blvd, Singapore'}
    : {label: 'PHONE / CONTACT', placeholder: 'e.g. +65 1234 5678'};
const locationLabel = (mode: InterviewMode) => mode === 'ONLINE' ? 'Meeting link' : mode === 'ONSITE' ? 'Location' : 'Phone / contact';
// The new-schedule form only exposes on-site/phone detail fields (online is
// always Google Meet and never accepts a pasted link).
const scheduleLocationField = (mode: InterviewMode) => mode === 'ONSITE'
  ? {label: 'Interview location', placeholder: 'e.g. 12 Marina Blvd, Singapore'}
  : {label: 'Phone number or calling instructions', placeholder: 'e.g. +65 1234 5678'};

function presentInterviewError(caught: unknown): {title: string; message: string; reload: boolean} {
  if (!(caught instanceof AuthApiError)) return {title: 'Unable to update interview', message: 'Unable to update this interview. Please try again.', reload: false};
  if (caught.code === 'VERSION_CONFLICT') return {title: 'Unable to update interview', message: 'This record changed after you opened it. Reload the latest state before trying again.', reload: true};
  if (caught.code === 'INTERVIEW_ALREADY_EXISTS') return {title: 'Unable to schedule interview', message: 'An interview is already scheduled for this application. Reload the latest state.', reload: true};
  if (caught.code === 'INVALID_APPLICATION_TRANSITION') return {title: 'Unable to schedule interview', message: 'An interview can only be scheduled while the application is in review. Reload the latest state.', reload: true};
  if (caught.code === 'INVALID_INTERVIEW_TRANSITION') return {title: 'Unable to update interview', message: 'A completed or cancelled interview cannot be changed. Reload the latest state.', reload: true};
  if (caught.code === 'GOOGLE_MEET_NOT_CONNECTED') return {title: 'Unable to schedule interview', message: 'Connect Google Calendar in Integrations before scheduling a Google Meet.', reload: false};
  if (caught.code === 'GOOGLE_MEET_RECONNECT_REQUIRED') return {title: 'Unable to schedule interview', message: 'Your Google authorization has expired. Reconnect Google in Integrations and try again.', reload: false};
  if (caught.code === 'GOOGLE_MEET_PROVISIONING_UNAVAILABLE') return {title: 'Unable to schedule interview', message: 'Google Meet is unavailable right now. Please try again.', reload: false};
  if (caught.code === 'GOOGLE_MEET_SYNC_IN_PROGRESS') return {title: 'Unable to update interview', message: 'A Google Meet sync is already in progress. Wait for it to finish before making another change.', reload: false};
  if (caught.status === 403) return {title: 'Unable to update interview', message: 'You do not have permission to manage this interview.', reload: false};
  if (caught.status === 404) return {title: 'Unable to update interview', message: 'This interview or application no longer exists or is not part of your company.', reload: false};
  if (caught.code === 'NETWORK_ERROR') return {title: 'Unable to update interview', message: 'Unable to reach the server. Check your connection and try again.', reload: false};
  return {title: 'Unable to update interview', message: 'Unable to update this interview. Please try again.', reload: false};
}
