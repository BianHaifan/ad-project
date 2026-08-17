import '@testing-library/jest-dom/vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {cleanup, fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {AuthApiError} from '../api/authClient';
import {recruiterRepository} from '../api/repository';
import {applications} from '../mocks/data';
import type {ApplicationStatus, GoogleConnection} from '../models/recruiter';
import {ApplicationDetailPage} from './ApplicationDetailPage';
import {ApplicationsPage} from './ApplicationsPage';

vi.mock('../lib/interviewTime', async importOriginal => {
  const actual = await importOriginal<typeof import('../lib/interviewTime')>();
  return {...actual, resolvedTimeZone: () => 'Asia/Singapore'};
});

const detail = {...applications[0], status: 'APPLIED' as const, version: 1, matchScore: null,
  matchAnalysis: null, interview: null, notes: [], owner: null};
const summary = {applicationId: detail.applicationId, jobId: detail.jobId, status: detail.status,
  appliedAt: detail.appliedAt, updatedAt: detail.updatedAt, version: detail.version, candidate: detail.candidate,
  jobTitle: detail.jobTitle, matchScore: null, owner: null};
const meta = {page: 1, pageSize: 20, total: 1, hasNext: false,
  counts: {applied: 1, inReview: 0, interview: 0, offered: 0, rejected: 0}};

function renderRoute(path: string, routes: {path: string; element: React.ReactNode}[]) {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
  const router = createMemoryRouter(routes, {initialEntries: [path]});
  render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>);
  return {router, client};
}

// Asserts `before` appears earlier in the document than `after` (source order, not visual layout).
function assertPrecedes(before: HTMLElement, after: HTMLElement) {
  expect(before.compareDocumentPosition(after) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
}

// Outcome node label mirrors ProgressRail's terminal-status rendering.
const outcomeLabel = (status: ApplicationStatus) => status === 'OFFERED' ? 'Offer made'
  : status === 'REJECTED' ? 'Rejected' : status === 'WITHDRAWN' ? 'Withdrawn' : 'Outcome';

describe('real recruiter application pages', () => {
  beforeEach(() => {
    vi.spyOn(recruiterRepository, 'listConversations')
      .mockResolvedValue({data: [], meta: {page: 1, pageSize: 100, total: 0, hasNext: false}});
  });
  afterEach(() => {cleanup(); vi.restoreAllMocks();});

  it('renders the real list without inventing match or owner data', async () => {
    const list = vi.spyOn(recruiterRepository, 'listApplications').mockResolvedValue({data: [summary], meta});
    renderRoute('/recruiter/applications', [{path: '/recruiter/applications', element: <ApplicationsPage/>}]);
    expect(screen.getByText('Loading applications…')).toBeInTheDocument();
    expect(await screen.findByText(detail.candidate.fullName)).toBeInTheDocument();
    expect(screen.queryByText('Unavailable')).not.toBeInTheDocument();
    expect(screen.queryByText(detail.jobId)).not.toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
    expect(screen.getByText('AI fit score')).toBeInTheDocument();
    expect(screen.getByText('—')).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: /Create job/i})).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: `View application for ${detail.candidate.fullName}`})).toBeInTheDocument();
    expect(list).toHaveBeenCalledWith({status: undefined, q: '', page: 1, pageSize: 20, sort: 'appliedAt,desc'});
  });

  it('shows the stored AI fit score as a badge when a valid score is present', async () => {
    vi.spyOn(recruiterRepository, 'listApplications')
      .mockResolvedValue({data: [{...summary, matchScore: 87}], meta});
    renderRoute('/recruiter/applications', [{path: '/recruiter/applications', element: <ApplicationsPage/>}]);
    expect(await screen.findByText('87 / 100')).toBeInTheDocument();
  });

  it('handles empty and safe error list states', async () => {
    vi.spyOn(recruiterRepository, 'listApplications').mockResolvedValue({data: [], meta: {...meta, total: 0,
      counts: {applied: 0, inReview: 0, interview: 0, offered: 0, rejected: 0}}});
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
    expect(screen.queryByText('Match score and analysis are unavailable.')).not.toBeInTheDocument();
    expect(screen.queryByText(detail.applicationId)).not.toBeInTheDocument();
    expect(screen.queryByText(detail.resumeSnapshot.snapshotId)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: /Schedule interview|Download PDF|Save note/})).not.toBeInTheDocument();
    expect(await screen.findByRole('button', {name: 'Message candidate'})).toBeDisabled();
    expect(screen.getByText('No conversation with this candidate yet.')).toBeInTheDocument();
  });

  it('renders the candidate at a glance card with an unavailable fit state without AI analysis', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue(detail);
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    expect(await screen.findByText('Candidate fit')).toBeInTheDocument();
    expect(screen.getByText('AI analysis unavailable')).toBeInTheDocument();
    expect(screen.getByText(detail.candidate.email)).toBeInTheDocument();
    expect(screen.getByText('Applied role')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Open full resume'})).toBeInTheDocument();
    expect(screen.queryByText(/phone|gender|birthday|date of birth/i)).not.toBeInTheDocument();
  });

  it('renders the explainable fit breakdown when an analysis exists', async () => {
    const analyzed = {...detail, matchScore: 87, matchAnalysis: {
      score: 87, evidence: ['Python / API experience'], strongMatches: ['Python / FastAPI'],
      gaps: ['Latency optimization evidence'], modelVersion: 'v1.0', generatedAt: '2026-08-09T01:42:00Z'}};
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue(analyzed);
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    expect(await screen.findByText('87 / 100')).toBeInTheDocument();
    expect(screen.getByText('Python / FastAPI')).toBeInTheDocument();
    expect(screen.getByText('Latency optimization evidence')).toBeInTheDocument();
    expect(screen.queryByText('AI analysis unavailable')).not.toBeInTheDocument();
  });

  it('requires a reason and submits the server version only once while pending', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue(detail);
    let finish!: (value: typeof updated) => void;
    const pending = new Promise<typeof updated>(resolve => {finish = resolve;});
    const transition = vi.spyOn(recruiterRepository, 'updateApplicationStatus').mockReturnValue(pending);
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByText('Application progress');
    fireEvent.click(screen.getByRole('button', {name: 'Start review'}));
    const submit = screen.getByRole('button', {name: 'Confirm start review'});
    expect(submit).toBeDisabled();
    fireEvent.change(screen.getByLabelText('DECISION REASON'), {target: {value: ' Strong evidence '}});
    expect(submit).toBeEnabled();
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

  it('schedules an on-site interview from the in-review decision panel', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'IN_REVIEW'});
    const create = vi.spyOn(recruiterRepository, 'createInterview').mockResolvedValue(interview);
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByText('Application progress');
    fireEvent.click(screen.getByRole('button', {name: 'Schedule interview'}));
    expect(await screen.findByRole('heading', {name: 'Schedule interview'})).toBeInTheDocument();
    expect(screen.getByText('Your browser timezone: Asia/Singapore')).toBeInTheDocument();
    // Mode is the first interactive field, ahead of date/time, timezone and duration.
    assertPrecedes(screen.getByLabelText('Mode'), screen.getByLabelText('Date and time'));
    assertPrecedes(screen.getByLabelText('Mode'), screen.getByText('Your browser timezone: Asia/Singapore'));
    assertPrecedes(screen.getByLabelText('Mode'), screen.getByLabelText('Duration minutes'));
    fireEvent.change(screen.getByLabelText('Date and time'), {target: {value: '2026-08-20T09:00'}});
    fireEvent.change(screen.getByLabelText('Duration minutes'), {target: {value: '45'}});
    fireEvent.change(screen.getByLabelText('Mode'), {target: {value: 'ONSITE'}});
    fireEvent.change(screen.getByLabelText('Interview location'), {target: {value: '12 Marina Blvd, Singapore'}});
    fireEvent.click(screen.getByRole('button', {name: 'Confirm schedule'}));
    await waitFor(() => expect(create).toHaveBeenCalledWith(detail.applicationId, {
      scheduledAt: '2026-08-20T01:00:00Z', timezone: 'Asia/Singapore', durationMinutes: 45, mode: 'ONSITE',
      locationOrMeetingUrl: '12 Marina Blvd, Singapore', note: undefined, expectedApplicationVersion: detail.version,
    }));
  });

  it('renders the interview card with reschedule, complete and cancel actions when scheduled', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'INTERVIEW', interview});
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    expect(await screen.findByRole('heading', {name: 'Interview'})).toBeInTheDocument();
    expect(screen.getByText('Scheduled')).toBeInTheDocument();
    expect(screen.getByText('Online')).toBeInTheDocument();
    expect(screen.getByText('Asia/Singapore')).toBeInTheDocument();
    expect(screen.getByText('45 minutes')).toBeInTheDocument();
    expect(screen.getByRole('link', {name: 'https://meet.example.com/abc'})).toBeInTheDocument();
    expect(screen.getByText('Bring portfolio')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Reschedule'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Mark completed'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Cancel interview'})).toBeInTheDocument();
  });

  it('does not offer scheduling when the application already has an interview', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'INTERVIEW', interview});
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    expect(await screen.findByRole('heading', {name: 'Interview'})).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Schedule interview'})).not.toBeInTheDocument();
  });

  it('backfills the saved instant into its timezone and submits the converted UTC on reschedule', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'INTERVIEW', interview});
    const update = vi.spyOn(recruiterRepository, 'updateInterview')
      .mockResolvedValue({...interview, scheduledAt: '2026-08-21T01:00:00Z', version: 2});
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByRole('heading', {name: 'Interview'});
    fireEvent.click(screen.getByRole('button', {name: 'Reschedule'}));
    await screen.findByRole('heading', {name: 'Reschedule interview'});
    expect(screen.getByLabelText('Date and time')).toHaveValue('2026-08-20T17:00');
    expect(screen.getByLabelText('Timezone')).toHaveValue('Asia/Singapore');
    fireEvent.change(screen.getByLabelText('Date and time'), {target: {value: '2026-08-21T09:00'}});
    fireEvent.click(screen.getByRole('button', {name: 'Save changes'}));
    await waitFor(() => expect(update).toHaveBeenCalledWith(interview.interviewId, {
      scheduledAt: '2026-08-21T01:00:00Z', timezone: 'Asia/Singapore', durationMinutes: 45, mode: 'ONLINE',
      locationOrMeetingUrl: 'https://meet.example.com/abc', note: 'Bring portfolio', expectedVersion: interview.version,
    }));
  });

  it('renders an on-site location as plain text rather than a link', async () => {
    const onSite = {...interview, mode: 'ONSITE' as const, locationOrMeetingUrl: '12 Marina Blvd, Singapore'};
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'INTERVIEW', interview: onSite});
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByRole('heading', {name: 'Interview'});
    expect(screen.getByText('12 Marina Blvd, Singapore')).toBeInTheDocument();
    expect(screen.queryByRole('link', {name: '12 Marina Blvd, Singapore'})).not.toBeInTheDocument();
    expect(screen.getAllByText('Location').length).toBeGreaterThan(0);
  });

  it('renders a phone contact as plain text without a URL prefix', async () => {
    const phone = {...interview, mode: 'PHONE' as const, locationOrMeetingUrl: '+65 1234 5678'};
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'INTERVIEW', interview: phone});
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByRole('heading', {name: 'Interview'});
    expect(screen.getByText('+65 1234 5678')).toBeInTheDocument();
    expect(screen.queryByRole('link', {name: '+65 1234 5678'})).not.toBeInTheDocument();
    expect(screen.getByText('Phone / contact')).toBeInTheDocument();
  });

  it('shows mode-specific fields and never offers a manual online link', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'IN_REVIEW'});
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(disconnected);
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByText('Application progress');
    fireEvent.click(screen.getByRole('button', {name: 'Schedule interview'}));
    await screen.findByRole('heading', {name: 'Schedule interview'});
    // Online is the default and shows the Google Meet explanation, never a link input or provider selector.
    expect(screen.getByText(/meeting link and Calendar invitation will be created automatically/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Location or meeting link')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Meeting provider')).not.toBeInTheDocument();
    // On-site shows an interview location field.
    fireEvent.change(screen.getByLabelText('Mode'), {target: {value: 'ONSITE'}});
    expect(screen.getByLabelText('Interview location')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('e.g. 12 Marina Blvd, Singapore')).toBeInTheDocument();
    // Phone shows a calling-details field.
    fireEvent.change(screen.getByLabelText('Mode'), {target: {value: 'PHONE'}});
    expect(screen.getByLabelText('Phone number or calling instructions')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('e.g. +65 1234 5678')).toBeInTheDocument();
  });

  it('falls back to the browser timezone when the saved timezone is invalid', async () => {
    const invalid = {...interview, timezone: 'Not/AZone'};
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'INTERVIEW', interview: invalid});
    const update = vi.spyOn(recruiterRepository, 'updateInterview')
      .mockResolvedValue({...interview, scheduledAt: '2026-08-21T01:00:00Z', version: 2});
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByRole('heading', {name: 'Interview'});
    expect(screen.getByText(/Saved timezone is not recognized/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Reschedule'}));
    await screen.findByRole('heading', {name: 'Reschedule interview'});
    expect(screen.getByLabelText('Timezone')).toHaveValue('Asia/Singapore');
    expect(screen.getByLabelText('Date and time')).toHaveValue('2026-08-20T17:00');
    expect(screen.getByText(/Using your browser timezone/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Date and time'), {target: {value: '2026-08-21T09:00'}});
    fireEvent.click(screen.getByRole('button', {name: 'Save changes'}));
    await waitFor(() => expect(update).toHaveBeenCalledWith(interview.interviewId, {
      scheduledAt: '2026-08-21T01:00:00Z', timezone: 'Asia/Singapore', durationMinutes: 45, mode: 'ONLINE',
      locationOrMeetingUrl: 'https://meet.example.com/abc', note: 'Bring portfolio', expectedVersion: interview.version,
    }));
  });

  it('schedules a Google Meet without a link input when connected', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'IN_REVIEW'});
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(connected);
    const create = vi.spyOn(recruiterRepository, 'createInterview').mockResolvedValue(googleMeetReady);
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByText('Application progress');
    fireEvent.click(screen.getByRole('button', {name: 'Schedule interview'}));
    await screen.findByRole('heading', {name: 'Schedule interview'});
    expect(screen.getByText(/meeting link and Calendar invitation will be created automatically/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Location or meeting link')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Meeting provider')).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Date and time'), {target: {value: '2026-08-20T09:00'}});
    fireEvent.change(screen.getByLabelText('Duration minutes'), {target: {value: '45'}});
    fireEvent.click(screen.getByRole('button', {name: 'Confirm schedule'}));
    await waitFor(() => expect(create).toHaveBeenCalledWith(detail.applicationId, {
      scheduledAt: '2026-08-20T01:00:00Z', timezone: 'Asia/Singapore', durationMinutes: 45, mode: 'ONLINE',
      note: undefined, expectedApplicationVersion: detail.version, meetingProvider: 'GOOGLE_MEET',
    }));
  });

  it.each<[string, GoogleConnection, string]>([
    ['DISCONNECTED', disconnected, 'Go to Integrations'],
    ['REVOKED', revoked, 'Reconnect Google'],
  ])('blocks online scheduling with a connect entry when %s', async (_label, conn, linkName) => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'IN_REVIEW'});
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(conn);
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByText('Application progress');
    fireEvent.click(screen.getByRole('button', {name: 'Schedule interview'}));
    await screen.findByRole('heading', {name: 'Schedule interview'});
    expect(screen.getByRole('link', {name: linkName})).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Date and time'), {target: {value: '2026-08-20T09:00'}});
    fireEvent.change(screen.getByLabelText('Duration minutes'), {target: {value: '45'}});
    expect(screen.getByRole('button', {name: 'Confirm schedule'})).toBeDisabled();
  });

  it('shows a syncing Google Meet with no link and no conflicting actions', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'INTERVIEW', interview: googleMeetPending});
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByRole('heading', {name: 'Interview'});
    expect(screen.getByText(/Creating or syncing the Google Meet/)).toBeInTheDocument();
    expect(screen.queryByRole('link', {name: /^https:\/\/meet\.google\.com\//})).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Reschedule'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Mark completed'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Cancel interview'})).not.toBeInTheDocument();
  });

  it('shows the ready Google Meet link when synced and scheduled', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'INTERVIEW', interview: googleMeetReady});
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByRole('heading', {name: 'Interview'});
    expect(screen.getByRole('link', {name: 'https://meet.google.com/abc-def'})).toBeInTheDocument();
    expect(screen.getByText('Synced')).toBeInTheDocument();
  });

  it('offers a safe retry for a failed Google Meet without creating a second interview', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'INTERVIEW', interview: googleMeetFailed});
    const update = vi.spyOn(recruiterRepository, 'updateInterview').mockResolvedValue(googleMeetReady);
    const create = vi.spyOn(recruiterRepository, 'createInterview');
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByRole('heading', {name: 'Interview'});
    expect(screen.getByText('Sync failed')).toBeInTheDocument();
    expect(screen.getByText(/candidate still sees the original meeting details/)).toBeInTheDocument();
    expect(screen.getByRole('link', {name: 'https://meet.google.com/abc-def'})).toBeInTheDocument();
    expect(screen.getByText('Existing Google Meet link (unchanged)')).toBeInTheDocument();
    expect(screen.queryByText('Synced')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Reschedule'})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Retry Google Meet'}));
    await screen.findByRole('heading', {name: 'Retry Google Meet sync'});
    expect(screen.getByText(/no new interview is created/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Retry sync'}));
    await waitFor(() => expect(update).toHaveBeenCalledWith(googleMeetFailed.interviewId, {
      scheduledAt: '2026-08-20T09:00:00Z', timezone: 'Asia/Singapore', durationMinutes: 45,
      note: 'Bring portfolio', expectedVersion: 1,
    }));
    expect(create).not.toHaveBeenCalled();
  });

  it('shows no Meet link for an initial provisioning failure with no link', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'INTERVIEW', interview: googleMeetFailedNoLink});
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByRole('heading', {name: 'Interview'});
    expect(screen.getByText('Sync failed')).toBeInTheDocument();
    expect(screen.getByText(/candidate still sees the original meeting details/)).toBeInTheDocument();
    expect(screen.queryByText('Existing Google Meet link (unchanged)')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', {name: /^https:\/\/meet\.google\.com\//})).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Retry Google Meet'})).toBeInTheDocument();
  });

  it('does not render a Meet link for a cancelled Google Meet', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'INTERVIEW', interview: googleMeetCancelled});
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByRole('heading', {name: 'Interview'});
    expect(screen.getByText('Cancelled')).toBeInTheDocument();
    expect(screen.queryByRole('link', {name: /^https:\/\/meet\.google\.com\//})).not.toBeInTheDocument();
    expect(screen.getByText(/no further changes are allowed/)).toBeInTheDocument();
  });

  it.each<[string, RegExp]>([
    ['GOOGLE_MEET_NOT_CONNECTED', /Connect Google Calendar in Integrations/],
    ['GOOGLE_MEET_RECONNECT_REQUIRED', /authorization has expired/],
    ['GOOGLE_MEET_PROVISIONING_UNAVAILABLE', /Google Meet is unavailable/],
  ])('maps the %s error to a safe, actionable message', async (code, pattern) => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'IN_REVIEW'});
    vi.spyOn(recruiterRepository, 'getGoogleConnection').mockResolvedValue(connected);
    vi.spyOn(recruiterRepository, 'createInterview')
      .mockRejectedValue(new AuthApiError(409, code, 'private detail'));
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByText('Application progress');
    fireEvent.click(screen.getByRole('button', {name: 'Schedule interview'}));
    await screen.findByRole('heading', {name: 'Schedule interview'});
    fireEvent.change(screen.getByLabelText('Date and time'), {target: {value: '2026-08-20T09:00'}});
    fireEvent.change(screen.getByLabelText('Duration minutes'), {target: {value: '45'}});
    fireEvent.click(screen.getByRole('button', {name: 'Confirm schedule'}));
    expect(await screen.findByText(pattern)).toBeInTheDocument();
    expect(screen.queryByText('private detail')).not.toBeInTheDocument();
  });

  it('maps the GOOGLE_MEET_SYNC_IN_PROGRESS error to a safe message on reschedule', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'INTERVIEW', interview: googleMeetReady});
    vi.spyOn(recruiterRepository, 'updateInterview')
      .mockRejectedValue(new AuthApiError(409, 'GOOGLE_MEET_SYNC_IN_PROGRESS', 'private detail'));
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByRole('heading', {name: 'Interview'});
    fireEvent.click(screen.getByRole('button', {name: 'Reschedule'}));
    await screen.findByRole('heading', {name: 'Reschedule interview'});
    fireEvent.click(screen.getByRole('button', {name: 'Save changes'}));
    expect(await screen.findByText(/sync is already in progress/)).toBeInTheDocument();
    expect(screen.queryByText('private detail')).not.toBeInTheDocument();
  });

  it.each<[ApplicationStatus, string[], string[]]>([
    ['APPLIED', ['Start review', 'Reject'], ['Schedule interview', 'Reject application', 'Make offer']],
    ['IN_REVIEW', ['Schedule interview', 'Reject'], ['Start review', 'Reject application', 'Make offer']],
    ['INTERVIEW', ['Make offer', 'Reject application'], ['Start review', 'Schedule interview', 'Reject']],
    ['OFFERED', [], ['Start review', 'Schedule interview', 'Reject', 'Reject application', 'Make offer']],
    ['REJECTED', [], ['Start review', 'Schedule interview', 'Reject', 'Reject application', 'Make offer']],
    ['WITHDRAWN', [], ['Start review', 'Schedule interview', 'Reject', 'Reject application', 'Make offer']],
  ])('renders %s progress and context actions', async (status, present, absent) => {
    const app = status === 'INTERVIEW' ? {...detail, status, interview} : {...detail, status};
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue(app);
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByText('Application progress');
    const rail = screen.getByText('Submitted').closest('ol')!;
    for (const label of ['Submitted', 'Review', 'Interview']) {
      expect(within(rail).getByText(label)).toBeInTheDocument();
    }
    expect(within(rail).getByText(outcomeLabel(status))).toBeInTheDocument();
    for (const name of present) expect(screen.getByRole('button', {name})).toBeInTheDocument();
    for (const name of absent) expect(screen.queryByRole('button', {name})).not.toBeInTheDocument();
    if (status === 'INTERVIEW') expect(screen.getByRole('heading', {name: 'Interview'})).toBeInTheDocument();
    if (status === 'OFFERED' || status === 'REJECTED' || status === 'WITHDRAWN') {
      expect(screen.getByText(/terminal stage/)).toBeInTheDocument();
    }
  });

  it('rejects an application only with a reason and disables while submitting', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue(detail);
    const transition = vi.spyOn(recruiterRepository, 'updateApplicationStatus')
      .mockResolvedValue({...detail, status: 'REJECTED' as const});
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByText('Application progress');
    fireEvent.click(screen.getByRole('button', {name: 'Reject'}));
    const confirm = screen.getByRole('button', {name: 'Confirm reject'});
    expect(confirm).toBeDisabled();
    fireEvent.change(screen.getByLabelText('DECISION REASON'), {target: {value: 'Not a match'}});
    expect(confirm).toBeEnabled();
    fireEvent.click(confirm);
    await waitFor(() => expect(transition).toHaveBeenCalledWith(detail.applicationId, 'REJECTED',
      'Not a match', detail.version));
  });

  it('makes an offer only from interview with a reason and disables while submitting', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue({...detail, status: 'INTERVIEW' as const});
    const transition = vi.spyOn(recruiterRepository, 'updateApplicationStatus')
      .mockResolvedValue({...detail, status: 'OFFERED' as const});
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByText('Application progress');
    fireEvent.click(screen.getByRole('button', {name: 'Make offer'}));
    const confirm = screen.getByRole('button', {name: 'Confirm offer'});
    expect(confirm).toBeDisabled();
    fireEvent.change(screen.getByLabelText('DECISION REASON'), {target: {value: 'Strong candidate'}});
    expect(confirm).toBeEnabled();
    fireEvent.click(confirm);
    await waitFor(() => expect(transition).toHaveBeenCalledWith(detail.applicationId, 'OFFERED',
      'Strong candidate', detail.version));
  });

  it('shows the rejection reason in the outcome stage and activity history', async () => {
    const rejected = {...detail, status: 'REJECTED' as const, timeline: [
      {eventId: 'e1', actorId: 'cand_001', companyId: 'company_001', fromStatus: null,
        toStatus: 'APPLIED' as const, occurredAt: '2026-08-09T01:42:00Z', reason: 'Application submitted', requestId: 'r1'},
      {eventId: 'e2', actorId: 'rec_001', companyId: 'company_001', fromStatus: 'APPLIED' as const,
        toStatus: 'REJECTED' as const, occurredAt: '2026-08-10T03:00:00Z', reason: 'Not a fit', requestId: 'r2'},
    ]};
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue(rejected);
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    await screen.findByText('Application progress');
    expect(screen.getByText(/terminal stage/)).toBeInTheDocument();
    expect(screen.getAllByText('Not a fit').length).toBeGreaterThan(0);
  });

  it('navigates to the exact conversation when one exists', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue(detail);
    vi.mocked(recruiterRepository.listConversations)
      .mockResolvedValue({data: [conversation], meta: {page: 1, pageSize: 100, total: 1, hasNext: false}});
    const {router} = renderRoute(`/recruiter/applications/${detail.applicationId}`, [
      {path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>},
      {path: '/recruiter/messages/:conversationId', element: <div>conversation view</div>},
    ]);
    fireEvent.click(await screen.findByRole('button', {name: 'Message candidate'}));
    await waitFor(() => expect(router.state.location.pathname).toBe('/recruiter/messages/conv-1'));
    expect(recruiterRepository.listConversations).toHaveBeenCalledWith(detail.applicationId);
  });

  it('shows a disabled neutral state when no conversation exists', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue(detail);
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    expect(await screen.findByRole('button', {name: 'Message candidate'})).toBeDisabled();
    expect(screen.getByText('No conversation with this candidate yet.')).toBeInTheDocument();
  });

  it('shows a disabled loading label while the lookup is pending', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue(detail);
    vi.mocked(recruiterRepository.listConversations).mockReturnValue(new Promise(() => {}));
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    expect(await screen.findByRole('button', {name: 'Message candidate…'})).toBeDisabled();
  });

  it('offers a safe retry when the lookup fails', async () => {
    vi.spyOn(recruiterRepository, 'getApplication').mockResolvedValue(detail);
    vi.mocked(recruiterRepository.listConversations)
      .mockRejectedValue(new AuthApiError(0, 'NETWORK_ERROR', 'private detail'));
    renderRoute(`/recruiter/applications/${detail.applicationId}`,
      [{path: '/recruiter/applications/:applicationId', element: <ApplicationDetailPage/>}]);
    const button = await screen.findByRole('button', {name: 'Message candidate'});
    expect(button).toBeEnabled();
    expect(screen.getByText(/Could not look up the conversation/)).toBeInTheDocument();
    expect(screen.queryByText('private detail')).not.toBeInTheDocument();
  });
});

const updated = {...detail, status: 'IN_REVIEW' as const, version: 2, updatedAt: '2026-08-12T02:00:00Z'};
const interview = {interviewId: 'interview-1', applicationId: detail.applicationId, scheduledAt: '2026-08-20T09:00:00Z',
  timezone: 'Asia/Singapore', durationMinutes: 45, mode: 'ONLINE' as const,
  locationOrMeetingUrl: 'https://meet.example.com/abc', note: 'Bring portfolio', status: 'SCHEDULED' as const,
  version: 1, meetingProvider: 'MANUAL' as const, meetingSyncStatus: 'NOT_APPLICABLE' as const,
  createdAt: '2026-08-14T00:00:00Z', updatedAt: '2026-08-14T00:00:00Z'};

const connected: GoogleConnection = {connected: true, status: 'CONNECTED', connectedAt: '2026-08-15T01:00:00Z'};
const disconnected: GoogleConnection = {connected: false, status: 'DISCONNECTED', connectedAt: null};
const revoked: GoogleConnection = {connected: false, status: 'REVOKED', connectedAt: '2026-08-14T01:00:00Z'};
const googleMeetReady = {...interview, meetingProvider: 'GOOGLE_MEET' as const, meetingSyncStatus: 'READY' as const,
  locationOrMeetingUrl: 'https://meet.google.com/abc-def'};
const googleMeetPending = {...interview, meetingProvider: 'GOOGLE_MEET' as const, meetingSyncStatus: 'PENDING' as const,
  locationOrMeetingUrl: null};
const googleMeetFailed = {...googleMeetReady, meetingSyncStatus: 'FAILED' as const};
const googleMeetFailedNoLink = {...googleMeetFailed, locationOrMeetingUrl: null};
const googleMeetCancelled = {...googleMeetReady, status: 'CANCELLED' as const, locationOrMeetingUrl: null};

const conversation = {conversationId: 'conv-1', applicationId: detail.applicationId, jobId: detail.jobId,
  createdAt: '2026-08-09T01:42:00Z', updatedAt: '2026-08-09T02:00:00Z',
  participant: {userId: 'cand_001', fullName: detail.candidate.fullName, avatarUrl: null, title: null, company: null, online: true},
  lastMessage: null, unreadCount: 0, jobTitle: detail.jobTitle};
