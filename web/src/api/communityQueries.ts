import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {communityHttpClient as api} from './communityHttpClient';

export const communityKeys={posts:['community','posts'] as const,post:(id:string)=>['community','post',id] as const,comments:(id:string)=>['community','comments',id] as const};
export const useCommunityPosts=(page:number)=>useQuery({queryKey:[...communityKeys.posts,page],queryFn:()=>api.listPosts(page)});
export const useCommunityPost=(id:string)=>useQuery({queryKey:communityKeys.post(id),queryFn:()=>api.getPost(id),enabled:!!id});
export const useCommunityComments=(id:string,page:number)=>useQuery({queryKey:[...communityKeys.comments(id),page],queryFn:()=>api.listComments(id,page),enabled:!!id});
export function useCreateCommunityPost(){const qc=useQueryClient();return useMutation({mutationFn:(body:string)=>api.createPost(body),onSuccess:()=>qc.invalidateQueries({queryKey:communityKeys.posts})})}
export function useSetCommunityLike(){const qc=useQueryClient();return useMutation({mutationFn:({id,liked}:{id:string;liked:boolean})=>api.setLike(id,liked),onSuccess:(state,{id})=>{qc.setQueryData(communityKeys.post(id),(post:object|undefined)=>post?{...post,...state}:post);qc.invalidateQueries({queryKey:communityKeys.posts})}})}
export function useCreateCommunityComment(){const qc=useQueryClient();return useMutation({mutationFn:({id,body}:{id:string;body:string})=>api.createComment(id,body),onSuccess:(result,{id})=>{qc.setQueryData(communityKeys.post(id),(post:object|undefined)=>post?{...post,commentCount:result.commentCount}:post);qc.invalidateQueries({queryKey:communityKeys.comments(id)});qc.invalidateQueries({queryKey:communityKeys.posts})}})}
