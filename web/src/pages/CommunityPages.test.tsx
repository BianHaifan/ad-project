import '@testing-library/jest-dom/vitest';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {createMemoryRouter,RouterProvider} from 'react-router-dom';
import {afterEach,describe,expect,it,vi} from 'vitest';
import {AuthApiError} from '../api/authClient';
import {communityHttpClient} from '../api/communityHttpClient';
import {CommunityDetailPage} from './CommunityDetailPage';
import {CommunityPage} from './CommunityPage';
import {CommunityCreatePage} from './CommunityCreatePage';

const author={userId:'u1',fullName:'Recruiter One',avatarUrl:null,role:'RECRUITER' as const,companyName:'Acme'};
const post={id:'p1',author,body:'Persisted post',likeCount:2,commentCount:1,likedByCurrentUser:false,createdAt:'2026-08-16T00:00:00Z',updatedAt:'2026-08-16T00:00:00Z'};
const meta={page:1,pageSize:20,total:1,hasNext:false};

describe('Community pages',()=>{
  afterEach(()=>{cleanup();vi.restoreAllMocks()});
  it('renders feed, routes to detail and pages with server metadata',async()=>{
    vi.spyOn(communityHttpClient,'listPosts').mockResolvedValue({data:[post],meta:{...meta,hasNext:true}});
    const router=renderAt('/recruiter/community');
    expect(await screen.findByText('Persisted post')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'Persisted post'}));
    expect(router.state.location.pathname).toBe('/recruiter/community/p1');
    await router.navigate('/recruiter/community');
    await screen.findByText('Persisted post');
    fireEvent.click(screen.getByRole('button',{name:'Next'}));
    await waitFor(()=>expect(communityHttpClient.listPosts).toHaveBeenLastCalledWith(2));
  });

  it('prevents duplicate publishing and preserves input on failure',async()=>{
    vi.spyOn(communityHttpClient,'listPosts').mockResolvedValue({data:[],meta:{...meta,total:0}});
    let reject!:(reason:unknown)=>void;const pending=new Promise<never>((_,r)=>{reject=r});
    const create=vi.spyOn(communityHttpClient,'createPost').mockReturnValue(pending);
    const router=renderAt('/recruiter/community');await screen.findByText('No matching posts');
    fireEvent.click(screen.getByRole('button',{name:'Create post'}));await waitFor(()=>expect(router.state.location.pathname).toBe('/recruiter/community/new'));
    const input=screen.getByLabelText('Post');fireEvent.change(input,{target:{value:'Keep this text'}});
    const submit=screen.getByRole('button',{name:'Publish post'});fireEvent.click(submit);
    await waitFor(()=>expect(create).toHaveBeenCalledTimes(1));expect(submit).toBeDisabled();fireEvent.click(submit);expect(create).toHaveBeenCalledTimes(1);reject(new AuthApiError(0,'NETWORK_ERROR','private'));
    expect(await screen.findByText('Could not publish. Your input has been kept.')).toBeInTheDocument();
    expect(input).toHaveValue('Keep this text');
  });

  it('uses server Like state, posts comments once and preserves failed comment text',async()=>{
    vi.spyOn(communityHttpClient,'getPost').mockResolvedValue(post);
    vi.spyOn(communityHttpClient,'listComments').mockResolvedValue({data:[],meta:{...meta,total:0}});
    const like=vi.spyOn(communityHttpClient,'setLike').mockResolvedValue({postId:'p1',likeCount:3,likedByCurrentUser:true});
    const create=vi.spyOn(communityHttpClient,'createComment').mockRejectedValue(new AuthApiError(422,'VALIDATION_ERROR','private'));
    renderAt('/recruiter/community/p1');
    fireEvent.click(await screen.findByRole('button',{name:'Like · 2'}));
    await waitFor(()=>expect(like).toHaveBeenCalledWith('p1',true));
    expect(await screen.findByRole('button',{name:'Unlike · 3'})).toBeInTheDocument();
    const input=screen.getByLabelText('Add a comment');fireEvent.change(input,{target:{value:'Keep reply'}});
    fireEvent.click(screen.getByRole('button',{name:'Post comment'}));
    expect(await screen.findByText('Could not comment. Your text has been kept.')).toBeInTheDocument();
    expect(create).toHaveBeenCalledTimes(1);expect(input).toHaveValue('Keep reply');
  });

  it('renders safe loading, empty and error states',async()=>{
    vi.spyOn(communityHttpClient,'listPosts').mockRejectedValue(new Error('private'));
    renderAt('/recruiter/community');expect(screen.getByText('Loading community feed…')).toBeInTheDocument();
    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();expect(screen.queryByText('private')).not.toBeInTheDocument();
  });
});

function renderAt(path:string){
  const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
  const router=createMemoryRouter([{path:'/recruiter/community',element:<CommunityPage/>},{path:'/recruiter/community/new',element:<CommunityCreatePage/>},{path:'/recruiter/community/:postId',element:<CommunityDetailPage/>}],{initialEntries:[path]});
  render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>);return router;
}
