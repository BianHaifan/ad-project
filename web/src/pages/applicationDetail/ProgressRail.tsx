import type {ApplicationStatus, AuditEvent} from '../../models/recruiter';
import {StatusBadge} from '../../components/StatusBadge';

type StageState = 'completed' | 'current' | 'future' | 'skipped' | 'terminal' | 'success';

const STAGES = [
  {key: 'APPLIED', label: 'Submitted'},
  {key: 'IN_REVIEW', label: 'Review'},
  {key: 'INTERVIEW', label: 'Interview'},
  {key: 'OUTCOME', label: 'Outcome'},
] as const;

// The four-step application rail is deliberately separate from the interview
// lifecycle (Scheduled / Completed / Cancelled). Each stage is driven only by
// the real application status plus its audit events — a skipped stage is shown
// as "not reached" rather than invented. The outcome node is labelled from the
// real terminal status: Offer made (success) vs Rejected / Withdrawn (terminal).
export function ProgressRail({status, timeline}: {status: ApplicationStatus; timeline: AuditEvent[]}) {
  const currentIndex = stageIndex(status);
  const terminal = status === 'REJECTED' || status === 'WITHDRAWN';
  const offered = status === 'OFFERED';

  return (
    <section className="panel">
      <div className="section-title">
        <div><h2>Application progress</h2><small>Submitted → Review → Interview → Outcome</small></div>
        <StatusBadge status={status}/>
      </div>
      <ol className="progress-rail">
        {STAGES.map((stage, index) => {
          const state = stageState(index, currentIndex, terminal, offered, status, timeline);
          const event = timeline.find(item => item.toStatus === targetStatus(index, status));
          const label = index === 3 ? outcomeLabel(status) : stage.label;
          return (
            <li key={stage.key} className={`progress-stage ${state}`}>
              <span className="progress-dot"/>
              <div className="progress-body">
                <b>{label}</b>
                {event && <small>{new Date(event.occurredAt).toLocaleString()}</small>}
                {state === 'skipped' && <small className="muted">Not reached</small>}
                {(state === 'terminal' || state === 'success') && event?.reason &&
                  <small className="muted">{event.reason}</small>}
              </div>
            </li>
          );
        })}
      </ol>
    </section>
  );
}

function stageIndex(status: ApplicationStatus): number {
  switch (status) {
    case 'APPLIED': return 0;
    case 'IN_REVIEW': return 1;
    case 'INTERVIEW': return 2;
    case 'OFFERED':
    case 'REJECTED':
    case 'WITHDRAWN': return 3;
  }
}

function targetStatus(index: number, status: ApplicationStatus): ApplicationStatus | null {
  switch (index) {
    case 0: return 'APPLIED';
    case 1: return 'IN_REVIEW';
    case 2: return 'INTERVIEW';
    case 3: return status === 'OFFERED' || status === 'REJECTED' || status === 'WITHDRAWN' ? status : null;
    default: return null;
  }
}

function outcomeLabel(status: ApplicationStatus): string {
  switch (status) {
    case 'OFFERED': return 'Offer made';
    case 'REJECTED': return 'Rejected';
    case 'WITHDRAWN': return 'Withdrawn';
    default: return 'Outcome';
  }
}

function stageState(index: number, currentIndex: number, terminal: boolean, offered: boolean,
                    status: ApplicationStatus, timeline: AuditEvent[]): StageState {
  if (offered && index === 3) return 'success';
  if (terminal && index === 3) return 'terminal';
  if (index === currentIndex) return 'current';
  if (index < currentIndex) return timeline.some(item => item.toStatus === targetStatus(index, status)) ? 'completed' : 'skipped';
  return 'future';
}
