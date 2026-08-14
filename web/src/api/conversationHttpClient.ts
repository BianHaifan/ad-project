import type {AuthClient} from './authClient';
import {authClient, AuthApiError} from './authClient';
import {apiPaths} from './contract';
import type {
  ConversationDetail, ConversationListResult, ConversationParticipant, ConversationSummary, Message,
  MessageListResult, PageMeta,
} from '../models/recruiter';

const randomUuid = () => globalThis.crypto.randomUUID();

export class ConversationHttpClient {
  constructor(
    private readonly client: Pick<AuthClient, 'requestWithAuth'> = authClient,
    private readonly uuid: () => string = randomUuid,
  ) {}

  async listConversations(): Promise<ConversationListResult> {
    const search = new URLSearchParams();
    search.set('page', '1');
    search.set('pageSize', '100');
    const payload = await this.client.requestWithAuth<unknown>(`${apiPaths.conversations}?${search}`);
    return parseConversationList(payload);
  }

  async getConversation(conversationId: string): Promise<ConversationDetail> {
    const payload = await this.client.requestWithAuth<unknown>(
      apiPaths.conversation(encodeURIComponent(conversationId)));
    return parseConversationDetailEnvelope(payload);
  }

  async listMessages(conversationId: string): Promise<MessageListResult> {
    const payload = await this.client.requestWithAuth<unknown>(
      apiPaths.messages(encodeURIComponent(conversationId)));
    return parseMessageList(payload);
  }

  async sendMessage(conversationId: string, body: string): Promise<Message> {
    const clientMessageId = this.uuid();
    const idempotencyKey = this.uuid();
    const payload = await this.client.requestWithAuth<unknown>(
      apiPaths.messages(encodeURIComponent(conversationId)), {
        method: 'POST',
        headers: {'Idempotency-Key': idempotencyKey},
        body: JSON.stringify({body, clientMessageId}),
      });
    return parseMessageEnvelope(payload);
  }

  async markRead(conversationId: string, lastReadMessageId: string): Promise<void> {
    await this.client.requestWithAuth<unknown>(
      apiPaths.readState(encodeURIComponent(conversationId)), {
        method: 'PUT',
        body: JSON.stringify({lastReadMessageId}),
      });
  }
}

function parseConversationList(payload: unknown): ConversationListResult {
  if (!isRecord(payload) || !Array.isArray(payload.data) || !isPageMeta(payload.meta)) throw unexpectedResponse();
  return {data: payload.data.map(parseSummary), meta: payload.meta};
}

function parseConversationDetailEnvelope(payload: unknown): ConversationDetail {
  if (!isRecord(payload) || !isRecord(payload.data)) throw unexpectedResponse();
  return parseDetail(payload.data);
}

function parseMessageList(payload: unknown): MessageListResult {
  if (!isRecord(payload) || !Array.isArray(payload.data) || !isRecord(payload.meta) ||
      !(payload.meta.nextCursor === null || typeof payload.meta.nextCursor === 'string') ||
      typeof payload.meta.hasMore !== 'boolean') throw unexpectedResponse();
  return {data: payload.data.map(parseMessage), meta: {nextCursor: payload.meta.nextCursor, hasMore: payload.meta.hasMore}};
}

function parseMessageEnvelope(payload: unknown): Message {
  if (!isRecord(payload) || !isRecord(payload.data)) throw unexpectedResponse();
  return parseMessage(payload.data);
}

export function parseSummary(value: unknown): ConversationSummary {
  if (!isRecord(value) || typeof value.conversationId !== 'string' || typeof value.applicationId !== 'string' ||
      typeof value.jobId !== 'string' || typeof value.createdAt !== 'string' || typeof value.updatedAt !== 'string' ||
      !(value.lastMessage === null || isRecord(value.lastMessage)) ||
      typeof value.unreadCount !== 'number' || typeof value.jobTitle !== 'string') throw unexpectedResponse();
  parseParticipant(value.participant);
  if (value.lastMessage !== null) parseMessage(value.lastMessage);
  return value as unknown as ConversationSummary;
}

function parseDetail(value: unknown): ConversationDetail {
  if (!isRecord(value) || typeof value.conversationId !== 'string' || typeof value.applicationId !== 'string' ||
      typeof value.jobId !== 'string' || typeof value.createdAt !== 'string' || typeof value.updatedAt !== 'string' ||
      !(value.context === null || isInterviewContext(value.context))) throw unexpectedResponse();
  parseParticipant(value.participant);
  return value as unknown as ConversationDetail;
}

export function parseMessage(value: unknown): Message {
  if (!isRecord(value) || typeof value.messageId !== 'string' || typeof value.conversationId !== 'string' ||
      typeof value.body !== 'string' || !isSenderType(value.senderType) || typeof value.sentAt !== 'string' ||
      !(value.clientMessageId === null || typeof value.clientMessageId === 'string') ||
      !isDeliveryStatus(value.deliveryStatus)) throw unexpectedResponse();
  return value as unknown as Message;
}

function parseParticipant(value: unknown): ConversationParticipant {
  if (!isRecord(value) || typeof value.userId !== 'string' || typeof value.fullName !== 'string' ||
      typeof value.online !== 'boolean' ||
      !(value.avatarUrl === null || typeof value.avatarUrl === 'string') ||
      !(value.title === null || typeof value.title === 'string') ||
      !(value.company === null || isRecord(value.company))) throw unexpectedResponse();
  return value as unknown as ConversationParticipant;
}

function isInterviewContext(value: unknown): boolean {
  return isRecord(value) && value.type === 'INTERVIEW_INVITATION' && typeof value.interviewId === 'string';
}

function isSenderType(value: unknown): boolean {
  return value === 'CANDIDATE' || value === 'RECRUITER' || value === 'SYSTEM';
}

function isDeliveryStatus(value: unknown): boolean {
  return value === 'SENDING' || value === 'SENT' || value === 'DELIVERED' || value === 'READ' || value === 'FAILED';
}

function isPageMeta(value: unknown): value is PageMeta {
  return isRecord(value) && typeof value.page === 'number' && typeof value.pageSize === 'number' &&
    typeof value.total === 'number' && typeof value.hasNext === 'boolean';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function unexpectedResponse(): AuthApiError {
  return new AuthApiError(0, 'UNEXPECTED_RESPONSE', 'The server returned an unexpected response.');
}

export const conversationHttpClient = new ConversationHttpClient();
