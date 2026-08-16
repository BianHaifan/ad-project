import {useEffect, useRef, useState, type ChangeEvent, type FormEvent} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {
  useConversation, useConversations, useDownloadAttachment, useMarkConversationRead, useMessages,
  useSendMessage, useSendMessageWithAttachment,
} from '../api/queries';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';
import type {Message} from '../models/recruiter';

const ACCEPTED_ATTACHMENT_TYPES = '.pdf,.doc,.docx,.txt,.png,.jpg,.jpeg';

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function MessagesPage() {
  const {conversationId} = useParams();
  const nav = useNavigate();
  const list = useConversations();
  const activeId = conversationId ?? list.data?.data[0]?.conversationId ?? '';
  const detail = useConversation(activeId);
  const messages = useMessages(activeId);
  const send = useSendMessage();
  const sendWithAttachment = useSendMessageWithAttachment();
  const download = useDownloadAttachment();
  const {mutate: markRead} = useMarkConversationRead();
  const [body, setBody] = useState('');
  const [attachment, setAttachment] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

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

  const sending = send.isPending || sendWithAttachment.isPending;
  const canSend = !!activeId && (body.trim() !== '' || attachment !== null) && !sending;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!activeId) return;
    if (attachment) {
      sendWithAttachment.mutate({id: activeId, body: body.trim(), file: attachment}, {
        onSuccess: () => {setBody(''); setAttachment(null);},
      });
    } else if (body.trim()) {
      send.mutate({id: activeId, body: body.trim()}, {onSuccess: () => setBody('')});
    }
  };

  const pickFile = () => fileInputRef.current?.click();
  const onFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    setAttachment(event.target.files?.[0] ?? null);
    event.target.value = '';
  };

  const onDownload = (message: Message) => {
    if (!message.attachment || !activeId) return;
    const fileName = message.attachment.fileName;
    download.mutate({id: activeId, messageId: message.messageId}, {
      onSuccess: blob => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = fileName;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);
      },
    });
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
                    {message.body && <span>{message.body}</span>}
                    {message.attachment && (
                      <button type="button" className="message-attachment"
                        disabled={download.isPending}
                        onClick={() => onDownload(message)}>
                        <span>📎 {message.attachment.fileName}</span>
                        <small>{formatBytes(message.attachment.sizeBytes)}</small>
                      </button>
                    )}
                    {message.senderType !== 'SYSTEM' &&
                      <small>{new Date(message.sentAt).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})}</small>}
                  </div>
                ))}
            </div>
            <form className="message-composer" onSubmit={submit}>
              {attachment && (
                <div className="attachment-preview">
                  <span className="file-name">📎 {attachment.name} · {formatBytes(attachment.size)}</span>
                  <button type="button" className="text-button" aria-label="Remove attachment"
                    onClick={() => setAttachment(null)}>Remove</button>
                </div>
              )}
              <div className="composer-row">
                <input ref={fileInputRef} type="file" accept={ACCEPTED_ATTACHMENT_TYPES}
                  style={{display: 'none'}} onChange={onFileChange}/>
                <button type="button" className="button secondary" aria-label="Attach a file"
                  disabled={sending} onClick={pickFile}>+</button>
                <input value={body} onChange={event => setBody(event.target.value)} placeholder="Write a message…"
                  disabled={sending}/>
                <button className="button primary" disabled={!canSend}>
                  {sending ? 'Sending…' : 'Send'}</button>
              </div>
            </form>
            {(send.isError || sendWithAttachment.isError) &&
              <small role="alert" className="form-error">
                {sendWithAttachment.isError ? 'Attachment could not be sent. Please try again.' :
                  'Message could not be sent. Please try again.'}
              </small>}
          </>
        ) : (
          <EmptyState title="Conversation not found" description="This conversation is no longer available."/>
        )}
      </article>
    </section>
  </>;
}
