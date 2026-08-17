import '@testing-library/jest-dom/vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {cleanup, fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {recruiterRepository} from '../api/repository';
import type {RecommendedApplicant, RecommendationMeta} from '../models/recruiter';
import {jobs} from '../mocks/data';
import {AiRankApplicants} from './AiRankApplicants';
import {ApplicationsPage} from './ApplicationsPage';

function renderWithRouter(routes: {path: string; element: React.ReactNode}[]) {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
  const router = createMemoryRouter(routes, {initialEntries: ['/recruiter/applications']});
  render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>);
  return {router, client};
}

const ranked: RecommendedApplicant = {
  applicationId: 'app-1',
  candidate: {candidateId: 'cand-1', fullName: 'Ada Lovelace', headline: 'Backend Engineer',
    avatarUrl: null, location: 'Singapore'},
  status: 'APPLIED',
  appliedAt: '2026-08-09T01:42:00Z',
  matchScore: 88,
  rank: 1,
  matchAnalysis: {strongMatches: ['Python', 'FastAPI'], gaps: ['No Kubernetes experience'], evidence: ['python']},
};

const meta: RecommendationMeta = {
  source: 'FALLBACK', modelVersion: 'fallback-rules-v1', featureVersion: 'pair-features-v1',
  modelStatus: 'DEGRADED', inferenceMs: 0, generatedAt: '2026-08-12T08:00:00Z',
  page: 1, pageSize: 20, total: 1, hasNext: false,
};

describe('AiRankApplicants panel', () => {
  beforeEach(() => {
    vi.spyOn(recruiterRepository, 'listApplicantRecommendations')
      .mockResolvedValue({data: [ranked], meta});
  });
  afterEach(() => {cleanup(); vi.restoreAllMocks();});

  it('does not call the API until the recruiter opens the entry', async () => {
    const list = vi.mocked(recruiterRepository.listApplicantRecommendations);
    renderWithRouter([
      {path: '/recruiter/applications', element: <AiRankApplicants jobId="job-1" jobTitle="Backend Engineer"/>},
      {path: '/recruiter/applications/:applicationId', element: <div>detail view</div>},
    ]);
    expect(screen.getByRole('button', {name: 'AI rank applicants'})).toBeInTheDocument();
    expect(list).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', {name: 'AI rank applicants'}));
    expect(screen.getByText('Ranking applicants…')).toBeInTheDocument();
    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
    expect(list).toHaveBeenCalledWith('job-1', {page: 1, pageSize: 20});
  });

  it('renders rank, match score, evidence summary and a degraded-model notice', async () => {
    renderWithRouter([
      {path: '/recruiter/applications', element: <AiRankApplicants jobId="job-1" jobTitle="Backend Engineer"/>},
      {path: '/recruiter/applications/:applicationId', element: <div>detail view</div>},
    ]);
    fireEvent.click(screen.getByRole('button', {name: 'AI rank applicants'}));
    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
    expect(screen.getByText('88 / 100')).toBeInTheDocument();
    expect(screen.getByText('#1')).toBeInTheDocument();
    expect(screen.getByText(/rule-based ranking/)).toBeInTheDocument();
    expect(screen.getByText('✓ Python · FastAPI')).toBeInTheDocument();
    expect(screen.getByText('✗ No Kubernetes experience')).toBeInTheDocument();
  });

  it('navigates to the application detail when a ranked candidate is opened', async () => {
    const {router} = renderWithRouter([
      {path: '/recruiter/applications', element: <AiRankApplicants jobId="job-1" jobTitle="Backend Engineer"/>},
      {path: '/recruiter/applications/:applicationId', element: <div>detail view</div>},
    ]);
    fireEvent.click(screen.getByRole('button', {name: 'AI rank applicants'}));
    const view = await screen.findByRole('button', {name: 'View application for Ada Lovelace'});
    fireEvent.click(view);
    await waitFor(() => expect(router.state.location.pathname).toBe('/recruiter/applications/app-1'));
  });

  it('shows an empty state when a job has no eligible applicants', async () => {
    vi.mocked(recruiterRepository.listApplicantRecommendations)
      .mockResolvedValue({data: [], meta: {...meta, source: 'NONE', modelStatus: 'NOT_APPLICABLE', total: 0}});
    renderWithRouter([
      {path: '/recruiter/applications', element: <AiRankApplicants jobId="job-1" jobTitle="Backend Engineer"/>},
      {path: '/recruiter/applications/:applicationId', element: <div>detail view</div>},
    ]);
    fireEvent.click(screen.getByRole('button', {name: 'AI rank applicants'}));
    expect(await screen.findByText('No eligible applicants')).toBeInTheDocument();
  });

  it('shows a safe error state with retry', async () => {
    vi.mocked(recruiterRepository.listApplicantRecommendations)
      .mockRejectedValue(new Error('private network detail'));
    renderWithRouter([
      {path: '/recruiter/applications', element: <AiRankApplicants jobId="job-1" jobTitle="Backend Engineer"/>},
      {path: '/recruiter/applications/:applicationId', element: <div>detail view</div>},
    ]);
    fireEvent.click(screen.getByRole('button', {name: 'AI rank applicants'}));
    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.queryByText('private network detail')).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Try again'})).toBeInTheDocument();
  });
});

describe('ApplicationsPage AI rank entry', () => {
  beforeEach(() => {
    vi.spyOn(recruiterRepository, 'listApplications').mockResolvedValue({
      data: [], meta: {page: 1, pageSize: 20, total: 0, hasNext: false,
        counts: {applied: 0, inReview: 0, interview: 0, offered: 0, rejected: 0}},
    });
    vi.spyOn(recruiterRepository, 'listJobs')
      .mockResolvedValue({data: jobs.slice(0, 1), meta: {page: 1, pageSize: 100, total: 1, hasNext: false}});
    vi.spyOn(recruiterRepository, 'listApplicantRecommendations').mockResolvedValue({data: [ranked], meta});
  });
  afterEach(() => {cleanup(); vi.restoreAllMocks();});

  it('offers AI ranking only after a job is selected', async () => {
    renderWithRouter([
      {path: '/recruiter/applications', element: <ApplicationsPage/>},
      {path: '/recruiter/applications/:applicationId', element: <div>detail view</div>},
    ]);
    expect(await screen.findByText('No applications found')).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'AI rank applicants'})).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('AI rank applicants for job'), {target: {value: jobs[0].jobId}});
    expect(screen.getByRole('button', {name: 'AI rank applicants'})).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: 'AI rank applicants'}));
    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
    expect(within(screen.getByText('AI ranked applicants').closest('section')!).getByText('Ada Lovelace'))
      .toBeInTheDocument();
  });
});
