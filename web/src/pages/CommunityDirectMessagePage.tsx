import {useCallback,useEffect,useState} from 'react';
import {Link,useParams} from 'react-router-dom';
import {communityHttpClient,type CommunityDirectConversation,type CommunityDirectMessage} from '../api/communityHttpClient';
import {ErrorState,LoadingState} from '../components/AsyncState';

export function CommunityDirectMessagePage(){
  const {conversationId=''}=useParams();const [conversation,setConversation]=useState<CommunityDirectConversation|null>(null);const [messages,setMessages]=useState<CommunityDirectMessage[]>([]);const [draft,setDraft]=useState('');const [loading,setLoading]=useState(true);const [sending,setSending]=useState(false);const [error,setError]=useState('');
  const load=useCallback(async()=>{setLoading(true);setError('');try{const [detail,page]=await Promise.all([communityHttpClient.directConversation(conversationId),communityHttpClient.directMessages(conversationId)]);setConversation(detail);setMessages(page.data)}catch{setError('Unable to load this Community conversation.')}finally{setLoading(false)}},[conversationId]);
  useEffect(()=>{void load()},[load]);
  if(loading)return <LoadingState label="Loading conversation…"/>;if(error||!conversation)return <ErrorState error={new Error(error)} onRetry={load}/>;
  const send=async(e:React.FormEvent)=>{e.preventDefault();if(!draft.trim()||sending)return;setSending(true);setError('');try{const message=await communityHttpClient.sendDirectMessage(conversationId,draft.trim());setMessages(current=>[...current,message]);setDraft('')}catch{setError('Message was not sent.')}finally{setSending(false)}};
  return <><div className="detail-header"><div><Link className="auth-link" to="/recruiter/community">← Community</Link><h1>{conversation.participant.fullName}</h1><p>Community direct message</p></div></div><section className="panel community-direct-messages">{messages.length===0?<p>No messages yet. Say hello.</p>:messages.map(message=><article key={message.messageId}><p>{message.body}</p><time>{new Date(message.sentAt).toLocaleString()}</time></article>)}</section><form className="panel community-composer" onSubmit={send}><label htmlFor="direct-message">Message</label><textarea id="direct-message" value={draft} onChange={e=>setDraft(e.target.value)} maxLength={2000}/>{error&&<em>{error}</em>}<button className="button primary" disabled={sending||!draft.trim()}>{sending?'Sending…':'Send'}</button></form></>;
}
