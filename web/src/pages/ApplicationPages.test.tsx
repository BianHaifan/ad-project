import '@testing-library/jest-dom/vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {AuthApiError} from '../api/authClient';
import {recruiterRepository} from '../api/repository';
import {applications} from '../mocks/data';
import {ApplicationDetailPage} from './ApplicationDetailPage';
import {ApplicationsPage} from './ApplicationsPage';

const detail = {...applications[0], status: 'APPLIED' as const, version: 1, matchScore: null,
  matchAnalysis: null, interview: null, notes: [], owner: null};
const summary = {applicationId: detail.applicationId, jobId: detail.jobId, status: detail.status,
  appliedAt: detail.appliedAt, updatedAt: detail.updatedAt, version: detail.version, candidate: detail.candidate,
  jobTitle: detail.jobTitle, matchScore: null, owner: null};
const meta = {page: 1, pageSize: 20, total: 1, hasNext: false,
  counts: {applied: 1, inReview: 0, interview: 0, rejected: 0}};

function renderRoute(path: string, routes: {path: string; element: React.ReactNode}[]) {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
  const router = createMemoryRouter(routes, {initialEntries: [path]});
  render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>);
  return {router, client};
}

describe('real recruiter application pages', () => {
  afterEach(() => {cleanup(); vi.restoreAllMocks();});

  it('renders the real list without inventing match or owner data', async () => {
    const list = vi.spyOn(recruiterRepository, 'listApplications').mockResolvedValue({data: [summary], meta});
    renderRoute('/recruiter/applications', [{path: '/recruiter/applications', element: <ApplicationsPage/>}]);
    expect(screen.getByText('Loading applications…')).toBeInTheDocument();
    expect(await screen.findByText(detail.candidate.fullName)).toBeInTheDocument();
    expect(screen.getByText('Unavailable')).toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
    expect(screen.queryByText('0% match')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: /Create job/i})).not.toBeInTheDocument();
    expect(list).toHaveBeenCalledWith({status: undefined, q: '', page: 1, pageSize: 20, sort: 'appliedAt,desc'});
  });

  it('handles empty and safe error list states', async () => {
    vi.spyOn(recruiterRepository, 'listApplications').mockResolvedValue({data: [], meta: {...meta, total: 0,
      counts: {applied: 0, inReview: 0, interview: 0, rejected: 0}}});
    renderRoute('/recruiter/applications', [{path: '/recruiter/applications', element: <ApplicationsPage/>}]);
    expect(await screen.findByText('No applications found')).toBeInTheDocument();
    cleanup(); vi.restoreAllMocks();
    vi.spyOn(recruiterRepository, 'listApplications').mockRejectedValue(new AuthApiError(0, 'NETWORK_ERROR', 'private error'));
    renderRoute('/recruiter/applications', [{path: '/recruiter/applications', element: <ApplicationsPage/>}]);
    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.queryByText('private error')).not.toBeInTheDocument();
  });

  it('loads real detail and hides unsupported action entry points', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue(detail);
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    expect(await screen.findByText('Submitted resume snapshot')).toBeInTheDocument();
    expect(screen.getByText('Match score and analysis are unavailable.')).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: /Message|Schedule interview|Download PDF|Save note/})).not.toBeInTheDocument();
  });

  it('requires a reason and submits the server version only once while pending', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue(detail);
    let finish!: (value: typeof updated) => void;
    const pending = new Promise<typeof updated>(resolve => {finish = resolve;});
    const transition = vi.spyOn(recruiterRepository, 'updateApplicationStatus').mockReturnValue(pending);
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByText('Review decision');
    const submit = screen.getByRole('button', {name: 'Confirm stage change'});
    expect(submit).toBeDisabled();
    fireEvent.change(screen.getByLabelText('NEXT STAGE'), {target: {value: 'IN_REVIEW'}});
    expect(submit).toBeDisabled();
    fireEvent.change(screen.getByLabelText('DECISION REASON'), {target: {value: ' Strong evidence '}});
    fireEvent.click(submit);
    await waitFor(() => expect(transition).toHaveBeenCalledWith(detail.applicationId, 'IN_REVIEW',
      ' Strong evidence ', detail.version));
    fireEvent.submit(submit.closest('form')!);
    expect(transition).toHaveBeenCalledTimes(1);
    expect(submit).toBeDisabled();
    finish(updated);
    expect((await screen.findAllByText('In review')).length).toBeGreaterThan(0);
  });

  it('does not offer transitions for withdrawn applications', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'WITHDRAWN'});
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    expect(await screen.findByText(/terminal stage/)).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Confirm stage change'})).not.toBeInTheDocument();
  });
});

const updated = {...detail, status: 'IN_REVIEW' as const, version: 2, updatedAt: '2026-08-12T02:00:00Z'};
