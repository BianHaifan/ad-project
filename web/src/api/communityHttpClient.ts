import type {AuthClient} from './authClient';
import {authClient, AuthApiError} from './authClient';
import {apiPaths} from './contract';

export interface CommunityAuthor {userId:string;fullName:string;avatarUrl:string|null;role:'CANDIDATE'|'RECRUITER';companyName:string|null}
export interface CommunityPost {id:string;author:CommunityAuthor;body:string;likeCount:number;commentCount:number;likedByCurrentUser:boolean;createdAt:string;updatedAt:string}
export interface CommunityComment {id:string;postId:string;author:CommunityAuthor;body:string;createdAt:string;updatedAt:string}
export interface PageMeta {page:number;pageSize:number;total:number;hasNext:boolean}
export interface CommunityPage<T> {data:T[];meta:PageMeta}
export interface CommunityInteraction {postId:string;likeCount:number;likedByCurrentUser:boolean}

export class CommunityHttpClient {
  constructor(private readonly client: Pick<AuthClient,'requestWithAuth'> = authClient) {}
  listPosts(page=1,pageSize=20){return this.page<CommunityPost>(`${apiPaths.communityPosts}?page=${page}&pageSize=${pageSize}`,parsePost)}
  async createPost(body:string){return this.data(await this.client.requestWithAuth<unknown>(apiPaths.communityPosts,{method:'POST',body:JSON.stringify({body})}),parsePost)}
  async getPost(id:string){return this.data(await this.client.requestWithAuth<unknown>(apiPaths.communityPost(encodeURIComponent(id))),parsePost)}
  async setLike(id:string,liked:boolean){return this.data(await this.client.requestWithAuth<unknown>(apiPaths.communityLike(encodeURIComponent(id)),{method:liked?'PUT':'DELETE'}),parseInteraction)}
  listComments(id:string,page=1,pageSize=20){return this.page<CommunityComment>(`${apiPaths.communityComments(encodeURIComponent(id))}?page=${page}&pageSize=${pageSize}`,parseComment)}
  async createComment(id:string,body:string){
    const value=await this.client.requestWithAuth<unknown>(apiPaths.communityComments(encodeURIComponent(id)),{method:'POST',body:JSON.stringify({body})});
    if(!record(value)||!record(value.data)||typeof value.data.commentCount!=='number') throw unexpected();
    return {comment:parseComment(value.data.comment),commentCount:value.data.commentCount};
  }
  private async page<T>(path:string,parse:(value:unknown)=>T):Promise<CommunityPage<T>>{
    const value=await this.client.requestWithAuth<unknown>(path);
    if(!record(value)||!Array.isArray(value.data)||!meta(value.meta)) throw unexpected();
    return {data:value.data.map(parse),meta:value.meta};
  }
  private data<T>(value:unknown,parse:(value:unknown)=>T):T{
    if(!record(value)||!record(value.data)) throw unexpected();
    return parse(value.data);
  }
}

function parseAuthor(value:unknown):CommunityAuthor{
  if(!record(value)||typeof value.userId!=='string'||typeof value.fullName!=='string'||
    !(value.avatarUrl===null||typeof value.avatarUrl==='string')||
    !(value.role==='CANDIDATE'||value.role==='RECRUITER')||
    !(value.companyName===null||typeof value.companyName==='string')) throw unexpected();
  return value as unknown as CommunityAuthor;
}
function parsePost(value:unknown):CommunityPost{
  if(!record(value)||typeof value.id!=='string'||!record(value.author)||typeof value.body!=='string'||
    typeof value.likeCount!=='number'||typeof value.commentCount!=='number'||typeof value.likedByCurrentUser!=='boolean'||
    typeof value.createdAt!=='string'||typeof value.updatedAt!=='string') throw unexpected();
  return {...value,author:parseAuthor(value.author)} as CommunityPost;
}
function parseComment(value:unknown):CommunityComment{
  if(!record(value)||typeof value.id!=='string'||typeof value.postId!=='string'||!record(value.author)||
    typeof value.body!=='string'||typeof value.createdAt!=='string'||typeof value.updatedAt!=='string') throw unexpected();
  return {...value,author:parseAuthor(value.author)} as CommunityComment;
}
function parseInteraction(value:unknown):CommunityInteraction{
  if(!record(value)||typeof value.postId!=='string'||typeof value.likeCount!=='number'||typeof value.likedByCurrentUser!=='boolean') throw unexpected();
  return value as unknown as CommunityInteraction;
}
function meta(value:unknown):value is PageMeta{return record(value)&&typeof value.page==='number'&&typeof value.pageSize==='number'&&typeof value.total==='number'&&typeof value.hasNext==='boolean'}
function record(value:unknown):value is Record<string,unknown>{return typeof value==='object'&&value!==null}
function unexpected(){return new AuthApiError(0,'UNEXPECTED_RESPONSE','The server returned an unexpected response.')}
export const communityHttpClient=new CommunityHttpClient();
