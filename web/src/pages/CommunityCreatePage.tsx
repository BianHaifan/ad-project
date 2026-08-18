import {useState} from 'react';
import {Link,useNavigate} from 'react-router-dom';
import {useCreateCommunityPost} from '../api/communityQueries';
import type {CommunityCategory} from '../api/communityHttpClient';
import {CATEGORY_OPTIONS} from './communityCategories';

export function CommunityCreatePage(){
  const nav=useNavigate();const create=useCreateCommunityPost();const [body,setBody]=useState('');const [category,setCategory]=useState<CommunityCategory>('GENERAL');const [images,setImages]=useState<File[]>([]);const length=[...body].length;
  const submit=async(e:React.FormEvent)=>{e.preventDefault();if(!body.trim()||length>2000||images.length>4||create.isPending)return;try{const post=await create.mutateAsync({body,category,images});nav(`/recruiter/community/${post.id}`)}catch{/* preserve all inputs */}};
  return <><div className="detail-header"><div><Link className="auth-link" to="/recruiter/community">← Community</Link><h1>Create post</h1></div></div><form className="panel community-composer" onSubmit={submit}>
    <label htmlFor="post-category">Category</label><select id="post-category" value={category} onChange={e=>setCategory(e.target.value as CommunityCategory)}>{CATEGORY_OPTIONS.map(([value,label])=><option key={value} value={value}>{label}</option>)}</select>
    <label htmlFor="community-post">Post</label><textarea id="community-post" value={body} onChange={e=>setBody(e.target.value)} placeholder="What would you like to share?"/>
    <label htmlFor="post-images">Images (PNG, JPEG, WebP; up to 4)</label><input id="post-images" type="file" accept="image/png,image/jpeg,image/webp" multiple onChange={e=>setImages(Array.from(e.target.files??[]).slice(0,4))}/>
    {images.length>0&&<div className="community-image-preview">{images.map((file,index)=><span key={`${file.name}-${index}`}>{file.name}<button type="button" onClick={()=>setImages(current=>current.filter((_,i)=>i!==index))}>Remove</button></span>)}</div>}
    <div><small>{length}/2000 · {images.length}/4 images</small>{length>2000&&<em>Maximum 2000 characters.</em>}{create.isError&&<em>Could not publish. Your input has been kept.</em>}<button className="button primary" disabled={create.isPending||!body.trim()||length>2000}>{create.isPending?'Publishing…':'Publish post'}</button></div>
  </form></>;
}
