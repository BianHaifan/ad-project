import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useCommunityPosts,useCreateCommunityPost} from '../api/communityQueries';
import {EmptyState,ErrorState,LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';

export function CommunityPage(){
  const nav=useNavigate();const [page,setPage]=useState(1);const [body,setBody]=useState('');
  const posts=useCommunityPosts(page);const create=useCreateCommunityPost();const bodyLength=[...body].length;
  const submit=async(event:React.FormEvent)=>{event.preventDefault();if(!body.trim()||bodyLength>2000||create.isPending)return;try{await create.mutateAsync(body);setBody('');setPage(1)}catch{/* preserve input */}};
  return <><PageHeader title="Community" subtitle="Share updates and connect with candidates."/>
    <form className="panel community-composer" onSubmit={submit}><label htmlFor="community-post">Share an update</label><textarea id="community-post" value={body} onChange={e=>setBody(e.target.value)} placeholder="What would you like to share?"/><div><small>{bodyLength}/2000</small>{bodyLength>2000&&<em>Maximum 2000 characters.</em>}{create.isError&&<em>Could not publish. Your text has been kept.</em>}<button className="button primary" disabled={create.isPending||!body.trim()||bodyLength>2000}>{create.isPending?'Publishing…':'Publish'}</button></div></form>
    {posts.isLoading?<LoadingState label="Loading community feed…"/>:posts.isError||!posts.data?<ErrorState onRetry={()=>posts.refetch()}/>:posts.data.data.length===0?<EmptyState title="No posts yet" description="Start the community conversation with the first update."/>:<section className="community-feed">{posts.data.data.map(post=><article className="panel community-post" key={post.id}><Author name={post.author.fullName} detail={post.author.companyName??post.author.role}/><button className="community-body" onClick={()=>nav(`/recruiter/community/${post.id}`)}>{post.body}</button><footer><time>{new Date(post.createdAt).toLocaleString()}</time><span>{post.likedByCurrentUser?'♥':'♡'} {post.likeCount}</span><span>Comments {post.commentCount}</span></footer></article>)}</section>}
    {posts.data&&<div className="pagination"><span>{posts.data.meta.total} posts</span><button className="button secondary tiny" disabled={page===1} onClick={()=>setPage(p=>p-1)}>Previous</button><b>Page {page}</b><button className="button secondary tiny" disabled={!posts.data.meta.hasNext} onClick={()=>setPage(p=>p+1)}>Next</button></div>}
  </>;
}
export function Author({name,detail}:{name:string;detail:string}){return <header className="community-author"><span className="avatar">{name.slice(0,1).toUpperCase()}</span><span><b>{name}</b><small>{detail}</small></span></header>}
