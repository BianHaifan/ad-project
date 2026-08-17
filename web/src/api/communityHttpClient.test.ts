import {describe,expect,it,vi} from 'vitest';
import {CommunityHttpClient} from './communityHttpClient';

const author={userId:'u1',fullName:'Recruiter One',avatarUrl:null,role:'RECRUITER' as const,companyName:'Acme'};
const post={id:'p/1',author,body:'Hello',likeCount:1,commentCount:2,likedByCurrentUser:false,createdAt:'2026-08-16T00:00:00Z',updatedAt:'2026-08-16T00:00:00Z'};
const comment={id:'c1',postId:'p/1',author,body:'Reply',createdAt:'2026-08-16T00:01:00Z',updatedAt:'2026-08-16T00:01:00Z'};

describe('CommunityHttpClient',()=>{
  it('uses all real Community paths, methods, pagination and bodies',async()=>{
    const requestWithAuth=vi.fn()
      .mockResolvedValueOnce({data:[post],meta:{page:2,pageSize:20,total:21,hasNext:false}})
      .mockResolvedValueOnce({data:post}).mockResolvedValueOnce({data:post})
      .mockResolvedValueOnce({data:{postId:'p/1',likeCount:2,likedByCurrentUser:true}})
      .mockResolvedValueOnce({data:{postId:'p/1',likeCount:1,likedByCurrentUser:false}})
      .mockResolvedValueOnce({data:[comment],meta:{page:1,pageSize:20,total:1,hasNext:false}})
      .mockResolvedValueOnce({data:{comment,commentCount:3}});
    const api=new CommunityHttpClient({requestWithAuth});
    await api.listPosts(2);await api.createPost(' kept ');await api.getPost('p/1');
    await api.setLike('p/1',true);await api.setLike('p/1',false);await api.listComments('p/1');await api.createComment('p/1',' reply ');
    expect(requestWithAuth.mock.calls).toEqual([
      ['/community/posts?page=2&pageSize=20'],['/community/posts',{method:'POST',body:'{"body":" kept "}'}],
      ['/community/posts/p%2F1'],['/community/posts/p%2F1/like',{method:'PUT'}],
      ['/community/posts/p%2F1/like',{method:'DELETE'}],['/community/posts/p%2F1/comments?page=1&pageSize=20'],
      ['/community/posts/p%2F1/comments',{method:'POST',body:'{"body":" reply "}'}],
    ]);
  });
});
