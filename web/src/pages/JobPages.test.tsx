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

const testJob = {...jobs[0], jobId: 'job-real-1', title: 'Backend Engineer', status: 'DRAFT' as const,
  publishedAt: null, version: 1};

function renderRoute(path: string, routes: {path: string; element: React.ReactNode}[]) {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
  const router = createMemoryRouter(routes, {initialEntries: [path]});
  render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>);
  return {router, client};
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
    const {router} = renderRoute('/recruiter/jobs/new', [
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
    expect(screen.getByText('Publish job')).toBeEnabled();
    expect(screen.getByText('Edit / pause / close unavailable')).toBeDisabled();
    cleanup();
    vi.restoreAllMocks();
    vi.spyOn(recruiterRepository, 'getJob').mockRejectedValue(new AuthApiError(404, 'NOT_FOUND', 'hidden detail'));
    renderRoute('/recruiter/jobs/missing', [{path: '/recruiter/jobs/:jobId', element: <JobDetailPage/>}, {path: '/recruiter/jobs', element: <div/>}]);
    expect(await screen.findByText('Job not found')).toBeInTheDocument();
    expect(screen.queryByText('hidden detail')).not.toBeInTheDocument();
  });

  it('asks for confirmation and cancellation does not publish', async () => {
    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue(testJob);
    const publish = vi.spyOn(recruiterRepository, 'publishJob');
    renderRoute('/recruiter/jobs/job-real-1', [{path: '/recruiter/jobs/:jobId', element: <JobDetailPage/>}]);
    fireEvent.click(await screen.findByRole('button', {name: 'Publish job'}));
    expect(screen.getByRole('dialog')).toHaveTextContent('change its status from DRAFT to ACTIVE');
    fireEvent.click(screen.getByRole('button', {name: 'Cancel'}));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(publish).not.toHaveBeenCalled();
  });

  it('publishes once, updates detail cache, and invalidates real job lists', async () => {
    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue(testJob);
    let finish!: (value: typeof activeJob) => void;
    const pending = new Promise<typeof activeJob>(resolve => {finish = resolve;});
    const publish = vi.spyOn(recruiterRepository, 'publishJob').mockReturnValue(pending);
    const {client} = renderRoute('/recruiter/jobs/job-real-1', [{path: '/recruiter/jobs/:jobId', element: <JobDetailPage/>}]);
    client.setQueryData(['jobs', {page: 1}], {data: [testJob], meta: {page: 1, pageSize: 20, total: 1, hasNext: false}});
    fireEvent.click(await screen.findByRole('button', {name: 'Publish job'}));
    const confirm = screen.getByRole('button', {name: 'Confirm publish'});
    fireEvent.click(confirm);
    await waitFor(() => expect(publish).toHaveBeenCalledWith('job-real-1', testJob.version));
    fireEvent.click(confirm);
    expect(publish).toHaveBeenCalledTimes(1);
    expect(confirm).toBeDisabled();
    finish(activeJob);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getAllByText('ACTIVE').length).toBeGreaterThan(0);
    expect(screen.getAllByText(new Date(activeJob.publishedAt!).toLocaleString()).length).toBeGreaterThan(0);
    expect(client.getQueryData(['job', 'job-real-1'])).toEqual(activeJob);
    expect(client.getQueryState(['jobs', {page: 1}])?.isInvalidated).toBe(true);
  });

  it.each(['ACTIVE', 'PAUSED', 'CLOSED'] as const)('does not offer publish for %s jobs', async status => {
    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue({...testJob, status});
    renderRoute('/recruiter/jobs/job-real-1', [{path: '/recruiter/jobs/:jobId', element: <JobDetailPage/>}]);
    await screen.findByText('Job overview');
    expect(screen.queryByRole('button', {name: 'Publish job'})).not.toBeInTheDocument();
    expect(screen.getByText('Edit / pause / close unavailable')).toBeDisabled();
  });

  it.each([
    [new AuthApiError(403, 'FORBIDDEN', 'raw forbidden'), 'Your company must be approved'],
    [new AuthApiError(409, 'VERSION_CONFLICT', 'raw conflict'), 'This job changed after you opened it'],
    [new AuthApiError(404, 'NOT_FOUND', 'raw hidden'), 'This job no longer exists or is not part of your company'],
    [new AuthApiError(0, 'NETWORK_ERROR', 'private network'), 'Unable to reach the server'],
  ])('shows safe publish errors for %#', async (error, expected) => {
    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue(testJob);
    vi.spyOn(recruiterRepository, 'publishJob').mockRejectedValue(error);
    renderRoute('/recruiter/jobs/job-real-1', [{path: '/recruiter/jobs/:jobId', element: <JobDetailPage/>}]);
    fireEvent.click(await screen.findByRole('button', {name: 'Publish job'}));
    fireEvent.click(screen.getByRole('button', {name: 'Confirm publish'}));
    expect(await screen.findByText(new RegExp(expected))).toBeInTheDocument();
    expect(screen.queryByText(error.message)).not.toBeInTheDocument();
    if (error.code === 'VERSION_CONFLICT') expect(screen.getByRole('button', {name: 'Reload job'})).toBeInTheDocument();
  });
});

const activeJob = {...testJob, status: 'ACTIVE' as const, version: 2,
  publishedAt: '2026-08-11T02:00:00Z', updatedAt: '2026-08-11T02:00:00Z'};

function fillRequiredForm() {
  fireEvent.change(screen.getByLabelText('Job title *'), {target: {value: 'Real created role'}});
  fireEvent.change(screen.getByLabelText('Job description *'), {target: {value: 'Build real backend services'}});
  fireEvent.change(screen.getByLabelText('Requirements *'), {target: {value: 'Build reliable APIs'}});
}
