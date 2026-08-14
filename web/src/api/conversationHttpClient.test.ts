import {describe, expect, it, vi} from 'vitest';
import {AuthApiError, type AuthClient} from './authClient';
import {ConversationHttpClient} from './conversationHttpClient';
import type {ConversationDetail, ConversationSummary, Message} from '../models/recruiter';

const participant = {
  userId: 'cand-1', fullName: 'Yan Bohao', avatarUrl: null, title: 'CS Student', company: null, online: true,
};

const message: Message = {
  messageId: 'msg-1', conversationId: 'conv-1', body: 'Hi recruiter', senderType: 'CANDIDATE',
  sentAt: '2026-08-12T03:00:00Z', clientMessageId: 'client-9', deliveryStatus: 'DELIVERED',
};

const summary: ConversationSummary = {
  conversationId: 'conv-1', applicationId: 'app-1', jobId: 'job-1', createdAt: '2026-08-12T01:00:00Z',
  updatedAt: '2026-08-12T03:00:00Z', participant, lastMessage: message, unreadCount: 2,
  jobTitle: 'AI Backend Engineer',
};

const detail: ConversationDetail = {
  conversationId: 'conv-1', applicationId: 'app-1', jobId: 'job-1', createdAt: '2026-08-12T01:00:00Z',
  updatedAt: '2026-08-12T03:00:00Z', participant, context: null,
};

function setup(result: unknown) {
  const requestWithAuth = vi.fn().mockResolvedValue(result);
  const client = new ConversationHttpClient({requestWithAuth} as Pick<AuthClient, 'requestWithAuth'>);
  return {requestWithAuth, client};
}

function uuidSeq(...values: string[]) {
  let index = 0;
  return () => values[index++] ?? `uuid-${index}`;
}

describe('ConversationHttpClient', () => {
  it('loads and parses a paginated conversation list', async () => {
    const {client, requestWithAuth} = setup({data: [summary], meta: {page: 1, pageSize: 100, total: 1, hasNext: false}});
    await expect(client.listConversations()).resolves.toEqual({
      data: [summary], meta: {page: 1, pageSize: 100, total: 1, hasNext: false},
    });
    expect(requestWithAuth).toHaveBeenCalledWith('/recruiter/conversations?page=1&pageSize=100');
  });

  it('accepts an empty list with a null last message', async () => {
    const {client} = setup({data: [{...summary, lastMessage: null, unreadCount: 0}],
      meta: {page: 1, pageSize: 100, total: 1, hasNext: false}});
    await expect(client.listConversations()).resolves.toMatchObject({data: [{lastMessage: null}], meta: {total: 1}});
  });

  it('loads a detail envelope and a message list', async () => {
    const detailClient = setup({data: detail});
    await expect(detailClient.client.getConversation('conv-1')).resolves.toEqual(detail);
    expect(detailClient.requestWithAuth).toHaveBeenCalledWith('/recruiter/conversations/conv-1');
    const messageClient = setup({data: [message], meta: {nextCursor: null, hasMore: false}});
    await expect(messageClient.client.listMessages('conv-1')).resolves.toEqual({
      data: [message], meta: {nextCursor: null, hasMore: false},
    });
    expect(messageClient.requestWithAuth).toHaveBeenCalledWith('/recruiter/conversations/conv-1/messages');
  });

  it('sends a UUID clientMessageId and Idempotency-Key header', async () => {
    const sent: Message = {...message, senderType: 'RECRUITER', body: 'Thanks for applying', clientMessageId: 'client-1'};
    const requestWithAuth = vi.fn().mockResolvedValue({data: sent});
    const client = new ConversationHttpClient(
      {requestWithAuth} as Pick<AuthClient, 'requestWithAuth'>, uuidSeq('client-1', 'idem-1'));
    await expect(client.sendMessage('conv-1', 'Thanks for applying')).resolves.toEqual(sent);
    const [path, init] = requestWithAuth.mock.calls[0] as [string, RequestInit];
    expect(path).toBe('/recruiter/conversations/conv-1/messages');
    expect(init.method).toBe('POST');
    expect((init.headers as Record<string, string>)['Idempotency-Key']).toBe('idem-1');
    expect(JSON.parse(String(init.body))).toEqual({body: 'Thanks for applying', clientMessageId: 'client-1'});
  });

  it('marks read with only the last read message id', async () => {
    const {client, requestWithAuth} = setup(undefined);
    await expect(client.markRead('conv-1', 'msg-1')).resolves.toBeUndefined();
    const [path, init] = requestWithAuth.mock.calls[0] as [string, RequestInit];
    expect(path).toBe('/recruiter/conversations/conv-1/read-state');
    expect(init.method).toBe('PUT');
    expect(JSON.parse(String(init.body))).toEqual({lastReadMessageId: 'msg-1'});
  });

  it('rejects malformed success payloads with UNEXPECTED_RESPONSE', async () => {
    await expect(setup({data: [{conversationId: 'broken'}]}).client.listConversations())
      .rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
    await expect(setup({data: {messageId: 'x'}}).client.getConversation('conv-1'))
      .rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
    await expect(setup({data: [{body: 'missing fields'}]}).client.listMessages('conv-1'))
      .rejects.toMatchObject({code: 'UNEXPECTED_RESPONSE'});
  });

  it('preserves safe network and server failures from the authenticated client', async () => {
    const requestWithAuth = vi.fn().mockRejectedValue(new AuthApiError(409, 'IDEMPOTENCY_CONFLICT', 'Duplicate send'));
    const client = new ConversationHttpClient({requestWithAuth} as Pick<AuthClient, 'requestWithAuth'>);
    await expect(client.sendMessage('conv-1', 'hi')).rejects.toMatchObject({status: 409, code: 'IDEMPOTENCY_CONFLICT'});
  });
});
