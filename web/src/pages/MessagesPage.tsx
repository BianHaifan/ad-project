import {useEffect, useRef, useState, type FormEvent} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {useConversation, useConversations, useMarkConversationRead, useMessages, useSendMessage} from '../api/queries';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';

export function MessagesPage() {
  const {conversationId} = useParams();
  const nav = useNavigate();
  const list = useConversations();
  const activeId = conversationId ?? list.data?.data[0]?.conversationId ?? '';
  const detail = useConversation(activeId);
  const messages = useMessages(activeId);
  const send = useSendMessage();
  const {mutate: markRead} = useMarkConversationRead();
  const [body, setBody] = useState('');

  useEffect(() => {
    if (!conversationId && list.data?.data[0]) {
      nav(`/recruiter/messages/${list.data.data[0].conversationId}`, {replace: true});
    }
  }, [conversationId, list.data, nav]);

  const conversations = list.data?.data ?? [];
  const activeSummary = conversations.find(conversation => conversation.conversationId === activeId);
  const messageList = messages.data?.data ?? [];
  const lastMessage = messageList[messageList.length - 1];

  const markedReadRef = useRef<string | null>(null);
  useEffect(() => {
    const key = activeId && lastMessage ? `${activeId}:${lastMessage.messageId}` : null;
    if (key && (activeSummary?.unreadCount ?? 0) > 0 && lastMessage?.senderType === 'CANDIDATE' &&
        markedReadRef.current !== key) {
      markedReadRef.current = key;
      markRead({id: activeId, lastReadMessageId: lastMessage.messageId});
    }
  }, [activeId, lastMessage, activeSummary, markRead]);

  if (list.isLoading) return <LoadingState label="Loading conversations…"/>;
  if (list.isError || !list.data) return <ErrorState onRetry={() => list.refetch()}/>;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (body.trim() && activeId) {
      send.mutate({id: activeId, body: body.trim()}, {onSuccess: () => setBody('')});
    }
  };

  return <>
    <PageHeader title="Messages" subtitle="Chat with candidates and keep hiring conversations in one place."/>
    <section className="messages-panel">
      <aside className="conversation-list">
        <div className="section-title">
          <div><h2>Conversations</h2><small>{conversations.length} candidates</small></div>
        </div>
        <input placeholder="Search candidate" aria-label="Search candidates"/>
        {conversations.length === 0
          ? <EmptyState title="No conversations" description="Conversations with candidates will appear here."/>
          : conversations.map(conversation => (
            <button className={conversation.conversationId === activeId ? 'selected' : ''}
              key={conversation.conversationId}
              onClick={() => nav(`/recruiter/messages/${conversation.conversationId}`)}>
              <span className="avatar large">{conversation.participant.fullName[0]}</span>
              <span className="grow">
                <b className="truncate">{conversation.participant.fullName}</b>
                <small className="truncate">{conversation.jobTitle}{conversation.lastMessage ? ` · ${conversation.lastMessage.body}` : ''}</small>
              </span>
              {conversation.unreadCount > 0 && <span className="unread">{conversation.unreadCount}</span>}
            </button>
          ))}
      </aside>
      <article className="chat-view">
        {!activeId ? (
          <EmptyState title="Select a conversation" description="Choose a conversation to view its messages."/>
        ) : detail.isError || messages.isError ? (
          <ErrorState onRetry={() => {detail.refetch(); messages.refetch();}}/>
        ) : detail.isLoading || messages.isLoading ? (
          <LoadingState label="Loading conversation…"/>
        ) : detail.data && activeSummary ? (
          <>
            <header>
              <span className="avatar">{detail.data.participant.fullName[0]}</span>
              <span className="grow"><b>{detail.data.participant.fullName}</b>
                <small>Applied to {activeSummary.jobTitle}</small></span>
              <button className="button secondary"
                onClick={() => nav(`/recruiter/applications/${detail.data!.applicationId}`)}>View application</button>
            </header>
            <div className="messages">
              {messageList.length === 0
                ? <EmptyState title="No messages yet" description="Start the conversation by sending a message."/>
                : messageList.map(message => (
                  <div key={message.messageId} className={`message ${message.senderType.toLowerCase()}`}>
                    <span>{message.body}</span>
                    {message.senderType !== 'SYSTEM' &&
                      <small>{new Date(message.sentAt).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})}</small>}
                  </div>
                ))}
            </div>
            <form className="message-composer" onSubmit={submit}>
              <input value={body} onChange={event => setBody(event.target.value)} placeholder="Write a message…"
                disabled={send.isPending}/>
              <button className="button primary" disabled={!body.trim() || send.isPending}>
                {send.isPending ? 'Sending…' : 'Send'}</button>
            </form>
            {send.isError && <small role="alert" className="form-error">Message could not be sent. Please try again.</small>}
          </>
        ) : (
          <EmptyState title="Conversation not found" description="This conversation is no longer available."/>
        )}
      </article>
    </section>
  </>;
}
