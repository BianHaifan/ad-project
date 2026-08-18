import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useCommunityPosts} from '../api/communityQueries';
import {EmptyState,ErrorState,LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';
import {CATEGORY_OPTIONS,categoryLabel} from './communityCategories';
import {CommunityAuthor} from './CommunityAuthor';

export function CommunityPage(){
  const nav=useNavigate();const [page,setPage]=useState(1);const [draftQuery,setDraftQuery]=useState('');const [q,setQ]=useState('');const [category,setCategory]=useState('');
  const posts=useCommunityPosts(page,q,category);
  return <><PageHeader title="Community" subtitle="Share updates and connect with candidates."/>
    <form className="panel community-toolbar" onSubmit={event=>{event.preventDefault();setPage(1);setQ(draftQuery)}}>
      <input aria-label="Search posts" value={draftQuery} onChange={e=>setDraftQuery(e.target.value)} placeholder="Search post text"/>
      <select aria-label="Filter category" value={category} onChange={e=>{setCategory(e.target.value);setPage(1)}}><option value="">All categories</option>{CATEGORY_OPTIONS.map(([value,label])=><option key={value} value={value}>{label}</option>)}</select>
      <button className="button secondary">Search</button><button type="button" className="button primary" onClick={()=>nav('/recruiter/community/new')}>Create post</button>
    </form>
    {posts.isLoading?<LoadingState label="Loading community feed…"/>:posts.isError||!posts.data?<ErrorState onRetry={()=>posts.refetch()}/>:posts.data.data.length===0?<EmptyState title="No matching posts" description="Try another search or category."/>:<section className="community-feed">{posts.data.data.map(post=><article className="panel community-post" key={post.id}><CommunityAuthor name={post.author.fullName} detail={post.author.companyName??post.author.role}/><span className="portal-badge">{categoryLabel(post.category??'GENERAL')}</span><button className="community-body" onClick={()=>nav(`/recruiter/community/${post.id}`)}>{post.body}</button>{(post.images?.length??0)>0&&<div className="community-images">{post.images?.map(image=><img key={image.imageId} src={image.url} alt="Post attachment"/>)}</div>}<footer><time>{new Date(post.createdAt).toLocaleString()}</time><span>{post.likedByCurrentUser?'♥':'♡'} {post.likeCount}</span><span>Comments {post.commentCount}</span></footer></article>)}</section>}
    {posts.data&&<div className="pagination"><span>{posts.data.meta.total} posts</span><button className="button secondary tiny" disabled={page===1} onClick={()=>setPage(p=>p-1)}>Previous</button><b>Page {page}</b><button className="button secondary tiny" disabled={!posts.data.meta.hasNext} onClick={()=>setPage(p=>p+1)}>Next</button></div>}
  </>;
}
