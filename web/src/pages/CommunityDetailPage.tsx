import {useState} from 'react';
import {Link,useNavigate,useParams} from 'react-router-dom';
import {communityHttpClient} from '../api/communityHttpClient';
import {categoryLabel} from './communityCategories';
import {useCommunityComments,useCommunityPost,useCreateCommunityComment,useSetCommunityLike} from '../api/communityQueries';
import {EmptyState,ErrorState,LoadingState} from '../components/AsyncState';
import {CommunityAuthor} from './CommunityAuthor';

export function CommunityDetailPage(){
  const {postId=''}=useParams();const nav=useNavigate();const [page,setPage]=useState(1);const [body,setBody]=useState('');const [messageError,setMessageError]=useState('');
  const post=useCommunityPost(postId);const comments=useCommunityComments(postId,page);const like=useSetCommunityLike();const create=useCreateCommunityComment();const bodyLength=[...body].length;
  if(post.isLoading)return <LoadingState label="Loading post…"/>;
  if(post.isError||!post.data)return <ErrorState onRetry={()=>post.refetch()}/>;
  const toggle=()=>{if(!like.isPending)like.mutate({id:postId,liked:!post.data.likedByCurrentUser})};
  const submit=async(event:React.FormEvent)=>{event.preventDefault();if(!body.trim()||bodyLength>500||create.isPending)return;try{await create.mutateAsync({id:postId,body});setBody('')}catch{/* preserve input */}};
  return <><div className="detail-header"><div><Link className="auth-link" to="/recruiter/community">← Community</Link><h1>Post detail</h1></div></div>
    <article className="panel community-post detail"><CommunityAuthor name={post.data.author.fullName} detail={post.data.author.companyName??post.data.author.role}/><span className="portal-badge">{categoryLabel(post.data.category??'GENERAL')}</span><p>{post.data.body}</p>{(post.data.images?.length??0)>0&&<div className="community-images">{post.data.images?.map(image=><img key={image.imageId} src={image.url} alt="Post attachment"/>)}</div>}<footer><time>{new Date(post.data.createdAt).toLocaleString()}</time><button className="button soft tiny" disabled={like.isPending} onClick={toggle}>{like.isPending?'Updating…':`${post.data.likedByCurrentUser?'Unlike':'Like'} · ${post.data.likeCount}`}</button><button className="button soft tiny" onClick={async()=>{setMessageError('');try{const conversation=await communityHttpClient.startDirectConversation(postId);nav(`/recruiter/community/messages/${conversation.conversationId}`)}catch{setMessageError('Could not open a conversation with this author.')}}}>Message author</button><span>Comments {post.data.commentCount}</span></footer>{like.isError&&<em>Could not update your Like. Try again.</em>}{messageError&&<em>{messageError}</em>}</article>
    <form className="panel community-composer" onSubmit={submit}><label htmlFor="community-comment">Add a comment</label><textarea id="community-comment" value={body} onChange={e=>setBody(e.target.value)} placeholder="Write a comment…"/><div><small>{bodyLength}/500</small>{bodyLength>500&&<em>Maximum 500 characters.</em>}{create.isError&&<em>Could not comment. Your text has been kept.</em>}<button className="button primary" disabled={create.isPending||!body.trim()||bodyLength>500}>{create.isPending?'Posting…':'Post comment'}</button></div></form>
    <section className="community-comments"><h2>Comments</h2>{comments.isLoading?<LoadingState label="Loading comments…"/>:comments.isError||!comments.data?<ErrorState onRetry={()=>comments.refetch()}/>:comments.data.data.length===0?<EmptyState title="No comments yet" description="Be the first to respond."/>:comments.data.data.map(comment=><article className="panel community-comment" key={comment.id}><CommunityAuthor name={comment.author.fullName} detail={comment.author.companyName??comment.author.role}/><p>{comment.body}</p><time>{new Date(comment.createdAt).toLocaleString()}</time></article>)}</section>
    {comments.data&&<div className="pagination"><span>{comments.data.meta.total} comments</span><button className="button secondary tiny" disabled={page===1} onClick={()=>setPage(p=>p-1)}>Previous</button><b>Page {page}</b><button className="button secondary tiny" disabled={!comments.data.meta.hasNext} onClick={()=>setPage(p=>p+1)}>Next</button></div>}
  </>;
}
