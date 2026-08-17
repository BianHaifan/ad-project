import '@testing-library/jest-dom/vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {AuthApiError} from '../api/authClient';
import {recruiterRepository} from '../api/repository';
import type {ConversationDetail, ConversationSummary, Message} from '../models/recruiter';
import {MessagesPage} from './MessagesPage';

const participant = {
  userId: 'cand-1', fullName: 'Yan Bohao', avatarUrl: null, title: 'CS Student', company: null, online: true,
};

const candidateMessage: Message = {
  messageId: 'msg-1', conversationId: 'conv-1', body: 'Hi recruiter', senderType: 'CANDIDATE',
  sentAt: '2026-08-12T03:00:00Z', clientMessageId: 'client-9', deliveryStatus: 'DELIVERED', attachment: null,
};

const recruiterMessage: Message = {
  ...candidateMessage, messageId: 'msg-2', body: 'Thanks for applying', senderType: 'RECRUITER', clientMessageId: 'client-10',
};

const imageMessage: Message = {
  ...candidateMessage, messageId: 'msg-img', body: '',
  attachment: {attachmentId: 'att-img', fileName: 'photo.png', sizeBytes: 42, contentType: 'image/png'},
};

const summary: ConversationSummary = {
  conversationId: 'conv-1', applicationId: 'app-1', jobId: 'job-1', createdAt: '2026-08-12T01:00:00Z',
  updatedAt: '2026-08-12T03:00:00Z', participant, lastMessage: candidateMessage, unreadCount: 1,
  jobTitle: 'AI Backend Engineer',
};

const detail: ConversationDetail = {
  conversationId: 'conv-1', applicationId: 'app-1', jobId: 'job-1', createdAt: '2026-08-12T01:00:00Z',
  updatedAt: '2026-08-12T03:00:00Z', participant, context: null,
};

const listMeta = {page: 1, pageSize: 100, total: 1, hasNext: false};
const messageMeta = {nextCursor: null, hasMore: false};

function renderMessages(path: string) {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
  const router = createMemoryRouter([
    {path: '/recruiter/messages', element: <MessagesPage/>},
    {path: '/recruiter/messages/:conversationId', element: <MessagesPage/>},
    {path: '/recruiter/applications/:applicationId', element: <div>Application detail page</div>},
  ], {initialEntries: [path]});
  const view = render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>);
  return {router, client, ...view};
}

function mockConversation() {
  vi.spyOn(recruiterRepository, 'listConversations').mockResolvedValue({data: [summary], meta: listMeta});
  vi.spyOn(recruiterRepository, 'getConversation').mockResolvedValue(detail);
  vi.spyOn(recruiterRepository, 'listMessages').mockResolvedValue({data: [candidateMessage], meta: messageMeta});
}

describe('MessagesPage', () => {
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:mock-url') as typeof URL.createObjectURL;
    URL.revokeObjectURL = vi.fn(() => {}) as typeof URL.revokeObjectURL;
  });
  afterEach(() => {cleanup(); vi.restoreAllMocks();});

  it('renders list, header and messages using only backend unread data', async () => {
    mockConversation();
    const {container} = renderMessages('/recruiter/messages/conv-1');
    expect(await screen.findAllByText('Yan Bohao')).not.toHaveLength(0);
    expect(screen.getByText('Applied to AI Backend Engineer')).toBeInTheDocument();
    expect(screen.getByText('Hi recruiter')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(container.querySelector('.circle-button')).not.toBeInTheDocument();
    expect(screen.queryByText(/match/i)).not.toBeInTheDocument();
  });

  it('truncates the candidate name on a single line', async () => {
    mockConversation();
    renderMessages('/recruiter/messages/conv-1');
    const names = await screen.findAllByText('Yan Bohao');
    expect(names.some(node => node.classList.contains('truncate'))).toBe(true);
  });

  it('shows an empty state when there are no conversations', async () => {
    vi.spyOn(recruiterRepository, 'listConversations').mockResolvedValue({data: [], meta: {...listMeta, total: 0}});
    renderMessages('/recruiter/messages');
    expect(await screen.findByText('No conversations')).toBeInTheDocument();
  });

  it('shows a safe error state when the list fails', async () => {
    vi.spyOn(recruiterRepository, 'listConversations').mockRejectedValue(new AuthApiError(0, 'NETWORK_ERROR', 'private detail'));
    renderMessages('/recruiter/messages');
    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.queryByText('private detail')).not.toBeInTheDocument();
  });

  it('marks read when the last message is from the candidate and unread', async () => {
    mockConversation();
    const markRead = vi.spyOn(recruiterRepository, 'markRead').mockResolvedValue(undefined);
    renderMessages('/recruiter/messages/conv-1');
    await screen.findAllByText('Yan Bohao');
    await waitFor(() => expect(markRead).toHaveBeenCalledWith('conv-1', 'msg-1'));
  });

  it('does not mark read when the conversation has no unread messages', async () => {
    vi.spyOn(recruiterRepository, 'listConversations')
      .mockResolvedValue({data: [{...summary, unreadCount: 0, lastMessage: recruiterMessage}], meta: listMeta});
    vi.spyOn(recruiterRepository, 'getConversation').mockResolvedValue(detail);
    vi.spyOn(recruiterRepository, 'listMessages').mockResolvedValue({data: [recruiterMessage], meta: messageMeta});
    const markRead = vi.spyOn(recruiterRepository, 'markRead').mockResolvedValue(undefined);
    renderMessages('/recruiter/messages/conv-1');
    await screen.findAllByText('Yan Bohao');
    expect(markRead).not.toHaveBeenCalled();
  });

  it('sends a message, clears the input on success and refreshes messages', async () => {
    vi.spyOn(recruiterRepository, 'listConversations')
      .mockResolvedValue({data: [{...summary, unreadCount: 0, lastMessage: recruiterMessage}], meta: listMeta});
    vi.spyOn(recruiterRepository, 'getConversation').mockResolvedValue(detail);
    const listMessages = vi.spyOn(recruiterRepository, 'listMessages')
      .mockResolvedValue({data: [recruiterMessage], meta: messageMeta});
    const sendMessage = vi.spyOn(recruiterRepository, 'sendMessage').mockResolvedValue(recruiterMessage);
    renderMessages('/recruiter/messages/conv-1');
    await screen.findAllByText('Yan Bohao');
    const input = screen.getByPlaceholderText('Write a message…');
    fireEvent.change(input, {target: {value: 'hello'}});
    fireEvent.click(screen.getByRole('button', {name: 'Send'}));
    await waitFor(() => expect(sendMessage).toHaveBeenCalledWith('conv-1', 'hello'));
    await waitFor(() => expect(screen.getByPlaceholderText('Write a message…')).toHaveValue(''));
    await waitFor(() => expect(listMessages.mock.calls.length).toBeGreaterThan(1));
  });

  it('preserves the input when sending fails', async () => {
    vi.spyOn(recruiterRepository, 'listConversations')
      .mockResolvedValue({data: [{...summary, unreadCount: 0, lastMessage: recruiterMessage}], meta: listMeta});
    vi.spyOn(recruiterRepository, 'getConversation').mockResolvedValue(detail);
    vi.spyOn(recruiterRepository, 'listMessages').mockResolvedValue({data: [recruiterMessage], meta: messageMeta});
    const sendMessage = vi.spyOn(recruiterRepository, 'sendMessage')
      .mockRejectedValue(new AuthApiError(409, 'IDEMPOTENCY_CONFLICT', 'duplicate'));
    renderMessages('/recruiter/messages/conv-1');
    await screen.findAllByText('Yan Bohao');
    fireEvent.change(screen.getByPlaceholderText('Write a message…'), {target: {value: 'hello'}});
    fireEvent.click(screen.getByRole('button', {name: 'Send'}));
    await waitFor(() => expect(sendMessage).toHaveBeenCalledWith('conv-1', 'hello'));
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be sent/i);
    expect(screen.getByPlaceholderText('Write a message…')).toHaveValue('hello');
  });

  it('downloads an image attachment via the repository and renders a preview', async () => {
    vi.spyOn(recruiterRepository, 'listConversations').mockResolvedValue({data: [summary], meta: listMeta});
    vi.spyOn(recruiterRepository, 'getConversation').mockResolvedValue(detail);
    vi.spyOn(recruiterRepository, 'listMessages').mockResolvedValue({data: [imageMessage], meta: messageMeta});
    const download = vi.spyOn(recruiterRepository, 'downloadAttachment')
      .mockResolvedValue(new Blob(['abc'], {type: 'image/png'}));
    renderMessages('/recruiter/messages/conv-1');
    await waitFor(() => expect(download).toHaveBeenCalledWith('conv-1', 'msg-img'));
    const img = await screen.findByRole('img', {name: 'photo.png'});
    expect(img).toHaveAttribute('src', 'blob:mock-url');
  });

  it('keeps the original download flow for non-image attachments', async () => {
    vi.spyOn(recruiterRepository, 'listConversations').mockResolvedValue({data: [summary], meta: listMeta});
    vi.spyOn(recruiterRepository, 'getConversation').mockResolvedValue(detail);
    const pdfMessage: Message = {
      ...candidateMessage, messageId: 'msg-pdf', body: '',
      attachment: {attachmentId: 'att-pdf', fileName: 'resume.pdf', sizeBytes: 1024, contentType: 'application/pdf'},
    };
    vi.spyOn(recruiterRepository, 'listMessages').mockResolvedValue({data: [pdfMessage], meta: messageMeta});
    const download = vi.spyOn(recruiterRepository, 'downloadAttachment')
      .mockResolvedValue(new Blob(['pdf'], {type: 'application/pdf'}));
    renderMessages('/recruiter/messages/conv-1');
    const button = await screen.findByRole('button', {name: /resume\.pdf/});
    expect(screen.queryByRole('img', {name: 'resume.pdf'})).not.toBeInTheDocument();
    expect(download).not.toHaveBeenCalled();
    fireEvent.click(button);
    await waitFor(() => expect(download).toHaveBeenCalledWith('conv-1', 'msg-pdf'));
  });

  it('shows a safe fallback and keeps the download entry when an image fails to load', async () => {
    vi.spyOn(recruiterRepository, 'listConversations').mockResolvedValue({data: [summary], meta: listMeta});
    vi.spyOn(recruiterRepository, 'getConversation').mockResolvedValue(detail);
    vi.spyOn(recruiterRepository, 'listMessages').mockResolvedValue({data: [imageMessage], meta: messageMeta});
    vi.spyOn(recruiterRepository, 'downloadAttachment')
      .mockRejectedValue(new AuthApiError(0, 'NETWORK_ERROR', 'private detail'));
    renderMessages('/recruiter/messages/conv-1');
    expect(await screen.findByText('Image preview unavailable.')).toBeInTheDocument();
    expect(screen.queryByText('private detail')).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: /photo\.png/})).toBeInTheDocument();
  });

  it('revokes the object URL when the image preview unmounts', async () => {
    vi.spyOn(recruiterRepository, 'listConversations').mockResolvedValue({data: [summary], meta: listMeta});
    vi.spyOn(recruiterRepository, 'getConversation').mockResolvedValue(detail);
    vi.spyOn(recruiterRepository, 'listMessages').mockResolvedValue({data: [imageMessage], meta: messageMeta});
    vi.spyOn(recruiterRepository, 'downloadAttachment')
      .mockResolvedValue(new Blob(['abc'], {type: 'image/png'}));
    const {unmount} = renderMessages('/recruiter/messages/conv-1');
    await screen.findByRole('img', {name: 'photo.png'});
    expect(URL.revokeObjectURL).not.toHaveBeenCalled();
    unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
  });

  it('shows a local thumbnail for a selected image and revokes it on remove', async () => {
    vi.spyOn(recruiterRepository, 'listConversations')
      .mockResolvedValue({data: [{...summary, unreadCount: 0, lastMessage: recruiterMessage}], meta: listMeta});
    vi.spyOn(recruiterRepository, 'getConversation').mockResolvedValue(detail);
    vi.spyOn(recruiterRepository, 'listMessages').mockResolvedValue({data: [recruiterMessage], meta: messageMeta});
    renderMessages('/recruiter/messages/conv-1');
    await screen.findAllByText('Yan Bohao');
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(input, {target: {files: [new File(['x'], 'photo.png', {type: 'image/png'})]}});
    expect(await screen.findByAltText('photo.png')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Remove attachment'}));
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url'));
  });
});
