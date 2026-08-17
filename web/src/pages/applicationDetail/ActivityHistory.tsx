import type {AuditEvent} from '../../models/recruiter';
import {StatusBadge} from '../../components/StatusBadge';

// The full audit trail is shown as activity history, not as fake process nodes.
// Each row keeps the recruiter's reason alongside the status change and time.
export function ActivityHistory({timeline}: {timeline: AuditEvent[]}) {
  return (
    <section className="panel">
      <div className="section-title">
        <div><h2>Activity history</h2><small>Full audit trail including recruiter decisions</small></div>
      </div>
      {timeline.length === 0
        ? <p className="muted">No activity recorded yet.</p>
        : <ol className="activity-history">
            {timeline.map(event => (
              <li key={event.eventId} className="activity-event">
                <span className="activity-dot"/>
                <div className="grow">
                  <b>{event.toStatus ? <StatusBadge status={event.toStatus}/> : '—'}</b>
                  <small>{new Date(event.occurredAt).toLocaleString()}</small>
                  {event.reason && <small className="muted">{event.reason}</small>}
                </div>
              </li>
            ))}
          </ol>}
    </section>
  );
}
