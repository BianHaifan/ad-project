import {useNavigate} from 'react-router-dom';
import {useConversationByApplication} from '../../api/queries';

// Exact, company-scoped conversation lookup by applicationId (Package 1). Never
// scans the paginated conversation list and never creates a conversation as a
// side effect of opening the application page.
export function MessageCandidateButton({applicationId}: {applicationId: string}) {
  const nav = useNavigate();
  const lookup = useConversationByApplication(applicationId);

  if (lookup.isLoading) {
    return <button className="button primary" disabled>Message candidate…</button>;
  }

  if (lookup.isError) {
    return (
      <div className="message-candidate">
        <button className="button primary" onClick={() => lookup.refetch()}>Message candidate</button>
        <small role="alert" className="muted">Could not look up the conversation. Click to retry.</small>
      </div>
    );
  }

  const conversation = lookup.data?.data[0] ?? null;
  if (!conversation) {
    return (
      <div className="message-candidate">
        <button className="button primary" disabled>Message candidate</button>
        <small className="muted">No conversation with this candidate yet.</small>
      </div>
    );
  }

  return (
    <button className="button primary"
      onClick={() => nav(`/recruiter/messages/${conversation.conversationId}`)}>Message candidate</button>
  );
}
