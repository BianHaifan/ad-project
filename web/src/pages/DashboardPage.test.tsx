import '@testing-library/jest-dom/vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {cleanup, fireEvent, render, screen} from '@testing-library/react';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {AuthApiError} from '../api/authClient';
import {recruiterRepository} from '../api/repository';
import type {Dashboard, RecruiterApplicationSummary, RecruiterJobSummary} from '../models/recruiter';
import {DashboardPage} from './DashboardPage';

const summary: RecruiterApplicationSummary = {
  applicationId: 'app-real-1', jobId: 'job-real-1', status: 'APPLIED',
  appliedAt: '2026-08-12T01:00:00Z', updatedAt: '2026-08-12T02:00:00Z', version: 1,
  candidate: {candidateId: 'cand_1', fullName: 'Yan Bohao', email: 'yan@example.com',
    headline: 'Backend engineer', avatarUrl: null, location: 'Singapore'},
  jobTitle: 'AI Backend Engineer', matchScore: null, owner: null,
};

const job: RecruiterJobSummary = {
  jobId: 'job-real-1', title: 'AI Backend Engineer',
  company: {companyId: 'company_1', name: 'Moonshot AI', logoUrl: null, stage: null, employeeRange: null,
    verificationStatus: 'APPROVED', website: null, description: null, location: 'Singapore', version: 1,
    createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-08-12T00:00:00Z'},
  employmentType: 'FULL_TIME', workplaceType: 'HYBRID', location: 'Singapore',
  salary: {min: 5000, max: 8000, currency: 'SGD', period: 'MONTH'},
  description: 'Build services', requirements: ['Reliable APIs'], skills: ['TypeScript'],
  deadline: null, visibility: 'PUBLIC', status: 'ACTIVE', publishedAt: '2026-08-01T00:00:00Z',
  version: 1, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-12T00:00:00Z',
  applicantCount: 3, owner: null,
};

const dashboard: Dashboard = {
  metrics: {activeJobs: 4, appliedApplications: 1, inReviewApplications: 2,
    interviewApplications: 3, companyVerificationStatus: 'APPROVED'},
  recentApplications: [summary], recentJobs: [job],
};

function renderDashboard(path = '/recruiter/dashboard',
                         routes: {path: string; element: React.ReactNode}[] = []) {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
  const allRoutes = [{path: '/recruiter/dashboard', element: <DashboardPage/>}, ...routes];
  const router = createMemoryRouter(allRoutes, {initialEntries: [path]});
  render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>);
  return {router, client};
}

describe('real recruiter dashboard', () => {
  afterEach(() => {cleanup(); vi.restoreAllMocks();});

  it('renders real metrics and recent items without mock or ML copy', async () => {
    vi.spyOn(recruiterRepository, 'getDashboard').mockResolvedValue(dashboard);
    renderDashboard();
    expect(screen.getByText('Loading dashboard…')).toBeInTheDocument();
    expect(await screen.findByText('Yan Bohao')).toBeInTheDocument();
    expect(screen.getByText('Open Roles')).toBeInTheDocument();
    expect(screen.getByText('New Applications')).toBeInTheDocument();
    expect(screen.getByText('In Review')).toBeInTheDocument();
    expect(screen.getByText('Interviews')).toBeInTheDocument();
    expect(screen.getByText('Verification')).toBeInTheDocument();
    expect(screen.getByText('4')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('APPROVED')).toBeInTheDocument();
    expect(screen.getByText('Recent applications')).toBeInTheDocument();
    expect(screen.getByText('Recent job postings')).toBeInTheDocument();
    expect(screen.getAllByText('AI Backend Engineer').length).toBeGreaterThan(0);
    expect(screen.queryByText('Talent Pool Recommendations')).not.toBeInTheDocument();
    expect(screen.queryByText(/Recommended by ML algorithm|Demo data|Dashboard mock/)).not.toBeInTheDocument();
    expect(screen.queryByText(/% match/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: /Create job/i})).not.toBeInTheDocument();
  });

  it('navigates to the real application detail from a recent application', async () => {
    vi.spyOn(recruiterRepository, 'getDashboard').mockResolvedValue(dashboard);
    const {router} = renderDashboard('/recruiter/dashboard', [
      {path: '/recruiter/applications/:applicationId', element: <div>Application detail route</div>},
    ]);
    fireEvent.click(await screen.findByRole('button', {name: /Yan Bohao/}));
    expect(router.state.location.pathname).toBe('/recruiter/applications/app-real-1');
  });

  it('shows empty states when there are no applications or jobs', async () => {
    vi.spyOn(recruiterRepository, 'getDashboard').mockResolvedValue({
      metrics: dashboard.metrics, recentApplications: [], recentJobs: [],
    });
    renderDashboard();
    expect(await screen.findByText('No applications yet')).toBeInTheDocument();
    expect(screen.getByText('No job postings yet')).toBeInTheDocument();
  });

  it('does not offer a create-job entry when recent jobs are empty', async () => {
    vi.spyOn(recruiterRepository, 'getDashboard').mockResolvedValue({
      metrics: dashboard.metrics, recentApplications: [], recentJobs: [],
    });
    renderDashboard();
    expect(await screen.findByText('No job postings yet')).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: /Create job/i})).not.toBeInTheDocument();
  });

  it('shows a safe error state', async () => {
    vi.spyOn(recruiterRepository, 'getDashboard').mockRejectedValue(
      new AuthApiError(0, 'NETWORK_ERROR', 'private failure'));
    renderDashboard();
    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.queryByText('private failure')).not.toBeInTheDocument();
  });
});
