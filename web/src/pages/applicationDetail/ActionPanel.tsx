import {useState, type FormEvent} from 'react';
import {useUpdateApplication} from '../../api/queries';
import {StatusBadge} from '../../components/StatusBadge';
import type {RecruiterApplicationDetail} from '../../models/recruiter';
import type {RecruiterTransitionStatus} from '../../api/recruiterRepository';

// Context decision cards replace the old NEXT STAGE dropdown. Every action is
// derived from the real application state machine: APPLIED → start review or
// reject; IN_REVIEW → schedule interview or reject; INTERVIEW → make offer or
// reject; OFFERED/REJECTED/WITHDRAWN → read-only result summary.
export function ActionPanel({application, onSchedule}: {
  application: RecruiterApplicationDetail;
  onSchedule: () => void;
}) {
  const update = useUpdateApplication();
  const [target, setTarget] = useState<RecruiterTransitionStatus | ''>('');
  const [reason, setReason] = useState('');
  const status = application.status;
  const hasInterview = application.interview !== null;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!target || !reason.trim() || update.isPending) return;
    update.mutate({id: application.applicationId, status: target, reason, expectedVersion: application.version}, {
      onSuccess: () => {setTarget(''); setReason('');},
    });
  };

  return (
    <section className="panel action-panel">
      <div className="section-title">
        <div><h2>Next step</h2><small>Decision for this application</small></div>
        <StatusBadge status={status}/>
      </div>
      <div className="action-stack">
        {status === 'APPLIED' && (
          <>
            <button className="button primary" onClick={() => setTarget('IN_REVIEW')}>Start review</button>
            <button className="button danger" onClick={() => setTarget('REJECTED')}>Reject</button>
          </>
        )}
        {status === 'IN_REVIEW' && (
          <>
            {!hasInterview && <button className="button primary" onClick={onSchedule}>Schedule interview</button>}
            <button className="button danger" onClick={() => setTarget('REJECTED')}>Reject</button>
          </>
        )}
        {status === 'INTERVIEW' && (
          <>
            <button className="button primary" onClick={() => setTarget('OFFERED')}>Make offer</button>
            <button className="button danger" onClick={() => setTarget('REJECTED')}>Reject application</button>
          </>
        )}
        {status === 'OFFERED' && (
          <p>Offer made. This application is now in a terminal stage; no further transition is available.</p>
        )}
        {(status === 'REJECTED' || status === 'WITHDRAWN') && (
          <p>This application is in a terminal stage. No further Recruiter transition is available.</p>
        )}
      </div>
      {target && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="transition-title">
          <section className="modal">
            <h2 id="transition-title">{transitionTitle(target)}</h2>
            <p>{transitionHelp(target)}</p>
            <form className="form-grid" onSubmit={submit}>
              <label style={{gridColumn: '1 / -1'}}>DECISION REASON
                <textarea value={reason} maxLength={500} onChange={event => setReason(event.target.value)}
                  placeholder="Add a reason for this decision"/></label>
              {update.isError && <small role="alert" style={{gridColumn: '1 / -1'}}>
                The stage change could not be saved. Refresh and try again.</small>}
              <div className="actions" style={{gridColumn: '1 / -1'}}>
                <button type="button" className="button secondary" disabled={update.isPending}
                  onClick={() => setTarget('')}>Cancel</button>
                <button type="submit" className="button primary" disabled={!reason.trim() || update.isPending}>
                  {update.isPending ? 'Saving…' : confirmLabel(target)}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </section>
  );
}

const transitionTitle = (target: RecruiterTransitionStatus) => target === 'IN_REVIEW' ? 'Start review'
  : target === 'OFFERED' ? 'Make offer' : 'Reject application';
const transitionHelp = (target: RecruiterTransitionStatus) => target === 'IN_REVIEW'
  ? 'Moving this application into review so you can evaluate the candidate.'
  : target === 'OFFERED'
    ? 'Recording that you have issued a hiring decision for this candidate.'
    : 'Rejecting this application ends the hiring process for this candidate.';
const confirmLabel = (target: RecruiterTransitionStatus) => target === 'IN_REVIEW' ? 'Confirm start review'
  : target === 'OFFERED' ? 'Confirm offer' : 'Confirm reject';
