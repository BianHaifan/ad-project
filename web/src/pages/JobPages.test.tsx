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
    expect(screen.getByText('Edit job')).toBeEnabled();
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

  it('loads a persisted draft into the edit form, saves once, and refreshes job caches', async () => {
    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue(testJob);
    let finish!: (value: typeof updatedDraft) => void;
    const pending = new Promise<typeof updatedDraft>(resolve => {finish = resolve;});
    const update = vi.spyOn(recruiterRepository, 'updateJob').mockReturnValue(pending);
    const {router, client} = renderRoute('/recruiter/jobs/job-real-1/edit', [
      {path: '/recruiter/jobs/:jobId/edit', element: <JobFormPage/>},
      {path: '/recruiter/jobs/:jobId', element: <div>Updated detail route</div>},
    ]);
    client.setQueryData(['jobs', {page: 1}], {data: [testJob], meta: {page: 1, pageSize: 20, total: 1, hasNext: false}});
    const title = await screen.findByDisplayValue('Backend Engineer');
    expect(screen.getByText('Editing server version 1')).toBeInTheDocument();
    fireEvent.change(title, {target: {value: 'Senior Backend Engineer'}});
    const save = screen.getByRole('button', {name: 'Save changes'});
    fireEvent.click(save);
    await waitFor(() => expect(update).toHaveBeenCalledWith('job-real-1',
      expect.objectContaining({title: 'Senior Backend Engineer'}), 1));
    fireEvent.submit(save.closest('form')!);
    expect(update).toHaveBeenCalledTimes(1);
    expect(save).toBeDisabled();
    finish(updatedDraft);
    expect(await screen.findByText('Updated detail route')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/recruiter/jobs/job-real-1');
    expect(client.getQueryData(['job', 'job-real-1'])).toEqual(updatedDraft);
    expect(client.getQueryState(['jobs', {page: 1}])?.isInvalidated).toBe(true);
  });

  it('blocks direct editing of non-draft jobs', async () => {
    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue(activeJob);
    const update = vi.spyOn(recruiterRepository, 'updateJob');
    renderRoute('/recruiter/jobs/job-real-1/edit', [
      {path: '/recruiter/jobs/:jobId/edit', element: <JobFormPage/>},
      {path: '/recruiter/jobs/:jobId', element: <div/>},
    ]);
    expect(await screen.findByText('Job cannot be edited')).toBeInTheDocument();
    expect(screen.getByText('Only DRAFT jobs can be edited. Reload the detail page to see its latest status.')).toBeInTheDocument();
    expect(update).not.toHaveBeenCalled();
  });

  it.each([
    [new AuthApiError(422, 'VALIDATION_ERROR', 'raw validation', {title: 'Server edit title error'}), 'Server edit title error', false],
    [new AuthApiError(409, 'VERSION_CONFLICT', 'raw conflict'), 'This draft changed after you opened it', true],
    [new AuthApiError(409, 'INVALID_JOB_TRANSITION', 'raw state'), 'Only DRAFT jobs can be edited', true],
    [new AuthApiError(403, 'FORBIDDEN', 'raw forbidden'), 'You do not have permission to edit this job', false],
    [new AuthApiError(404, 'NOT_FOUND', 'raw hidden'), 'no longer exists or is not part of your company', false],
    [new AuthApiError(0, 'NETWORK_ERROR', 'private network'), 'Unable to reach the server', false],
  ])('shows safe edit errors for %#', async (error, expected, reload) => {
    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue(testJob);
    vi.spyOn(recruiterRepository, 'updateJob').mockRejectedValue(error);
    renderRoute('/recruiter/jobs/job-real-1/edit', [{path: '/recruiter/jobs/:jobId/edit', element: <JobFormPage/>}]);
    await screen.findByDisplayValue('Backend Engineer');
    fireEvent.click(screen.getByRole('button', {name: 'Save changes'}));
    expect(await screen.findByText(new RegExp(expected))).toBeInTheDocument();
    expect(screen.queryByText(error.message)).not.toBeInTheDocument();
    if (reload) expect(screen.getByRole('button', {name: 'Reload draft'})).toBeInTheDocument();
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

  it('shows only the lifecycle actions allowed by the current server status', async () => {
    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue(activeJob);
    renderRoute('/recruiter/jobs/job-real-1', [{path: '/recruiter/jobs/:jobId', element: <JobDetailPage/>}]);
    await screen.findByText('Job overview');
    expect(screen.queryByRole('button', {name: 'Publish job'})).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Pause job'})).toBeEnabled();
    expect(screen.getByRole('button', {name: 'Close job'})).toBeEnabled();
    expect(screen.queryByRole('button', {name: 'Resume job'})).not.toBeInTheDocument();
    cleanup();
    vi.restoreAllMocks();

    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue(pausedJob);
    renderRoute('/recruiter/jobs/job-real-1', [{path: '/recruiter/jobs/:jobId', element: <JobDetailPage/>}]);
    await screen.findByText('Job overview');
    expect(screen.getByRole('button', {name: 'Resume job'})).toBeEnabled();
    expect(screen.getByRole('button', {name: 'Close job'})).toBeEnabled();
    expect(screen.queryByRole('button', {name: 'Pause job'})).not.toBeInTheDocument();
    cleanup();
    vi.restoreAllMocks();

    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue(closedJob);
    renderRoute('/recruiter/jobs/job-real-1', [{path: '/recruiter/jobs/:jobId', element: <JobDetailPage/>}]);
    await screen.findByText('Job overview');
    expect(screen.getByText('This job is closed and cannot transition to another status.')).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: /Pause job|Resume job|Close job/})).not.toBeInTheDocument();
  });

  it('asks for a reason and cancellation does not change status', async () => {
    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue(activeJob);
    const change = vi.spyOn(recruiterRepository, 'changeJobStatus');
    renderRoute('/recruiter/jobs/job-real-1', [{path: '/recruiter/jobs/:jobId', element: <JobDetailPage/>}]);
    fireEvent.click(await screen.findByRole('button', {name: 'Pause job'}));
    expect(screen.getByRole('dialog')).toHaveTextContent('from ACTIVE to PAUSED');
    expect(screen.getByRole('button', {name: 'Confirm pause'})).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Reason'), {target: {value: '   '}});
    expect(screen.getByRole('button', {name: 'Confirm pause'})).toBeDisabled();
    fireEvent.click(screen.getByRole('button', {name: 'Cancel'}));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(change).not.toHaveBeenCalled();
  });

  it('pauses once, updates detail cache, and invalidates real job lists', async () => {
    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue(activeJob);
    let finish!: (value: typeof pausedJob) => void;
    const pending = new Promise<typeof pausedJob>(resolve => {finish = resolve;});
    const change = vi.spyOn(recruiterRepository, 'changeJobStatus').mockReturnValue(pending);
    const {client} = renderRoute('/recruiter/jobs/job-real-1', [{path: '/recruiter/jobs/:jobId', element: <JobDetailPage/>}]);
    client.setQueryData(['jobs', {page: 1}], {data: [activeJob], meta: {page: 1, pageSize: 20, total: 1, hasNext: false}});
    fireEvent.click(await screen.findByRole('button', {name: 'Pause job'}));
    fireEvent.change(screen.getByLabelText('Reason'), {target: {value: ' Pause for planning '}});
    const confirm = screen.getByRole('button', {name: 'Confirm pause'});
    fireEvent.click(confirm);
    await waitFor(() => expect(change).toHaveBeenCalledWith('job-real-1', 'PAUSED', 'Pause for planning', 2));
    fireEvent.click(confirm);
    expect(change).toHaveBeenCalledTimes(1);
    expect(confirm).toBeDisabled();
    finish(pausedJob);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getAllByText('PAUSED').length).toBeGreaterThan(0);
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(client.getQueryData(['job', 'job-real-1'])).toEqual(pausedJob);
    expect(client.getQueryState(['jobs', {page: 1}])?.isInvalidated).toBe(true);
  });

  it.each([
    ['Resume job', pausedJob, 'ACTIVE', 'Resume after review', resumedJob],
    ['Close job', activeJob, 'CLOSED', 'Position filled', closedJob],
  ] as const)('%s confirms and renders the real returned status', async (button, current, target, reason, result) => {
    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue(current);
    const change = vi.spyOn(recruiterRepository, 'changeJobStatus').mockResolvedValue(result);
    renderRoute('/recruiter/jobs/job-real-1', [{path: '/recruiter/jobs/:jobId', element: <JobDetailPage/>}]);
    fireEvent.click(await screen.findByRole('button', {name: button}));
    expect(screen.getByRole('dialog')).toHaveTextContent(`from ${current.status} to ${target}`);
    fireEvent.change(screen.getByLabelText('Reason'), {target: {value: reason}});
    fireEvent.click(screen.getByRole('button', {name: new RegExp(`Confirm ${button.split(' ')[0].toLowerCase()}`)}));
    await waitFor(() => expect(change).toHaveBeenCalledWith('job-real-1', target, reason, current.version));
    expect(await screen.findAllByText(target)).not.toHaveLength(0);
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

  it.each([
    [new AuthApiError(403, 'FORBIDDEN', 'raw forbidden'), 'Your company must be approved to resume'],
    [new AuthApiError(409, 'VERSION_CONFLICT', 'raw conflict'), 'This job changed after you opened it'],
    [new AuthApiError(409, 'INVALID_JOB_TRANSITION', 'raw transition'), 'no longer in a state that allows'],
    [new AuthApiError(404, 'NOT_FOUND', 'raw hidden'), 'no longer exists or is not part of your company'],
    [new AuthApiError(0, 'NETWORK_ERROR', 'private network'), 'Unable to reach the server'],
  ])('shows safe status errors for %#', async (error, expected) => {
    vi.spyOn(recruiterRepository, 'getJob').mockResolvedValue(activeJob);
    vi.spyOn(recruiterRepository, 'changeJobStatus').mockRejectedValue(error);
    renderRoute('/recruiter/jobs/job-real-1', [{path: '/recruiter/jobs/:jobId', element: <JobDetailPage/>}]);
    fireEvent.click(await screen.findByRole('button', {name: 'Pause job'}));
    fireEvent.change(screen.getByLabelText('Reason'), {target: {value: 'Safe reason'}});
    fireEvent.click(screen.getByRole('button', {name: 'Confirm pause'}));
    expect(await screen.findByText(new RegExp(expected))).toBeInTheDocument();
    expect(screen.queryByText(error.message)).not.toBeInTheDocument();
    if (error.code === 'VERSION_CONFLICT' || error.code === 'INVALID_JOB_TRANSITION') {
      expect(screen.getByRole('button', {name: 'Reload job'})).toBeInTheDocument();
    }
  });
});

const activeJob = {...testJob, status: 'ACTIVE' as const, version: 2,
  publishedAt: '2026-08-11T02:00:00Z', updatedAt: '2026-08-11T02:00:00Z'};
const pausedJob = {...activeJob, status: 'PAUSED' as const, version: 3, updatedAt: '2026-08-11T03:00:00Z'};
const resumedJob = {...pausedJob, status: 'ACTIVE' as const, version: 4, updatedAt: '2026-08-11T04:00:00Z'};
const closedJob = {...activeJob, status: 'CLOSED' as const, version: 3, updatedAt: '2026-08-11T05:00:00Z'};
const updatedDraft = {...testJob, title: 'Senior Backend Engineer', version: 2,
  updatedAt: '2026-08-11T06:00:00Z'};

function fillRequiredForm() {
  fireEvent.change(screen.getByLabelText('Job title *'), {target: {value: 'Real created role'}});
  fireEvent.change(screen.getByLabelText('Job description *'), {target: {value: 'Build real backend services'}});
  fireEvent.change(screen.getByLabelText('Requirements *'), {target: {value: 'Build reliable APIs'}});
}
