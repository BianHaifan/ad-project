import '@testing-library/jest-dom/vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {AuthApiError} from '../api/authClient';
import {recruiterRepository} from '../api/repository';
import {jobs} from '../mocks/data';
import {JobDetailPage} from './JobDetailPage';
import {JobFormPage} from './JobFormPage';
import {JobsPage} from './JobsPage';

const testJob = {...jobs[0], jobId: 'job-real-1', title: 'Backend Engineer'};

function renderRoute(path: string, routes: {path: string; element: React.ReactNode}[]) {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
  const router = createMemoryRouter(routes, {initialEntries: [path]});
  render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>);
  return router;
}

describe('real recruiter job pages', () => {
  afterEach(() => {cleanup(); vi.restoreAllMocks();});

  it('loads and renders the authenticated real job list', async () => {
    const list = vi.spyOn(recruiterRepository, 'listJobs').mockResolvedValue({data: [testJob], meta: {page: 1, pageSize: 20, total: 1, hasNext: false}});
    renderRoute('/recruiter/jobs', [{path: '/recruiter/jobs', element: <JobsPage/>}, {path: '/recruiter/jobs/:jobId', element: <div>Detail route</div>}]);
    expect(screen.getByText('Loading real job postings…')).toBeInTheDocument();
    expect(await screen.findByText('Backend Engineer')).toBeInTheDocument();
    expect(screen.getByText('1 persisted job posting')).toBeInTheDocument();
    expect(list).toHaveBeenCalledWith({q: '', status: undefined, page: 1, pageSize: 20});
  });

  it('handles empty and network-error list states', async () => {
    vi.spyOn(recruiterRepository, 'listJobs').mockResolvedValue({data: [], meta: {page: 1, pageSize: 20, total: 0, hasNext: false}});
    renderRoute('/recruiter/jobs', [{path: '/recruiter/jobs', element: <JobsPage/>}]);
    expect(await screen.findByText('No job postings found')).toBeInTheDocument();
    cleanup();
    vi.restoreAllMocks();
    vi.spyOn(recruiterRepository, 'listJobs').mockRejectedValue(new AuthApiError(0, 'NETWORK_ERROR', 'private detail'));
    renderRoute('/recruiter/jobs', [{path: '/recruiter/jobs', element: <JobsPage/>}]);
    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.queryByText('private detail')).not.toBeInTheDocument();
  });

  it('creates once while pending and navigates to the persisted detail', async () => {
    let finish!: (value: typeof testJob) => void;
    const pending = new Promise<typeof testJob>(resolve => {finish = resolve;});
    const create = vi.spyOn(recruiterRepository, 'createJob').mockReturnValue(pending);
    const router = renderRoute('/recruiter/jobs/new', [
      {path: '/recruiter/jobs/new', element: <JobFormPage/>},
      {path: '/recruiter/jobs/:jobId', element: <div>Persisted detail route</div>},
      {path: '/recruiter/jobs', element: <div>Jobs route</div>},
    ]);
    fillRequiredForm();
    const button = screen.getByRole('button', {name: 'Save draft'});
    fireEvent.click(button);
    await waitFor(() => expect(create).toHaveBeenCalledTimes(1));
    fireEvent.submit(button.closest('form')!);
    expect(create).toHaveBeenCalledTimes(1);
    expect(button).toBeDisabled();
    finish(testJob);
    expect(await screen.findByText('Persisted detail route')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/recruiter/jobs/job-real-1');
  });

  it('maps server field errors and shows a clear pending-company denial', async () => {
    vi.spyOn(recruiterRepository, 'createJob').mockRejectedValueOnce(new AuthApiError(422, 'VALIDATION_ERROR', 'raw', {title: 'Server title error'}));
    renderRoute('/recruiter/jobs/new', [{path: '/recruiter/jobs/new', element: <JobFormPage/>}, {path: '/recruiter/jobs', element: <div/>}]);
    fillRequiredForm();
    fireEvent.click(screen.getByRole('button', {name: 'Save draft'}));
    expect(await screen.findByText('Server title error')).toBeInTheDocument();
    expect(screen.queryByText('raw')).not.toBeInTheDocument();
    cleanup();
    vi.restoreAllMocks();
    vi.spyOn(recruiterRepository, 'createJob').mockRejectedValue(new AuthApiError(403, 'FORBIDDEN', 'raw forbidden'));
    renderRoute('/recruiter/jobs/new', [{path: '/recruiter/jobs/new', element: <JobFormPage/>}, {path: '/recruiter/jobs', element: <div/>}]);
    fillRequiredForm();
    fireEvent.click(screen.getByRole('button', {name: 'Save draft'}));
    expect(await screen.findByText('Your company must be approved before you can create a job draft.')).toBeInTheDocument();
  });

  it('loads a real detail and presents a safe not-found state', async () => {
    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue(testJob);
    renderRoute('/recruiter/jobs/job-real-1', [{path: '/recruiter/jobs/:jobId', element: <JobDetailPage/>}, {path: '/recruiter/jobs', element: <div/>}]);
    expect(await screen.findByText('Job overview')).toBeInTheDocument();
    expect(screen.getByText('Edit / publish unavailable')).toBeDisabled();
    cleanup();
    vi.restoreAllMocks();
    vi.spyOn(recruiterRepository, 'getJob').mockRejectedValue(new AuthApiError(404, 'NOT_FOUND', 'hidden detail'));
    renderRoute('/recruiter/jobs/missing', [{path: '/recruiter/jobs/:jobId', element: <JobDetailPage/>}, {path: '/recruiter/jobs', element: <div/>}]);
    expect(await screen.findByText('Job not found')).toBeInTheDocument();
    expect(screen.queryByText('hidden detail')).not.toBeInTheDocument();
  });
});

function fillRequiredForm() {
  fireEvent.change(screen.getByLabelText('Job title *'), {target: {value: 'Real created role'}});
  fireEvent.change(screen.getByLabelText('Job description *'), {target: {value: 'Build real backend services'}});
  fireEvent.change(screen.getByLabelText('Requirements *'), {target: {value: 'Build reliable APIs'}});
}
