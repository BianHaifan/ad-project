import {authClient, type AuthClient} from './authClient';
import {apiPaths} from './contract';
import type {
  AdminUser,
  AuditEvent,
  BusinessRole,
  CompanyReview,
  CompanyVerificationStatus,
  DataResponse,
  ListResponse,
  UserStatus,
  UpdateAdminCompanyInput,
} from '../models/admin';

type RequestClient = Pick<AuthClient, 'requestWithAuth'>;

export interface UserListParams {
  q?: string;
  role?: BusinessRole | '';
  status?: UserStatus | '';
  adminAccess?: boolean;
  page?: number;
  pageSize?: number;
}

export interface CompanyListParams {
  q?: string;
  status?: CompanyVerificationStatus | '';
  page?: number;
  pageSize?: number;
}

export class AdminClient {
  constructor(private readonly client: RequestClient = authClient) {}

  async me(): Promise<AdminUser> {
    return (await this.client.requestWithAuth<DataResponse<AdminUser>>(apiPaths.adminMe)).data;
  }

  listUsers(params: UserListParams = {}): Promise<ListResponse<AdminUser>> {
    return this.client.requestWithAuth(withQuery(apiPaths.adminUsers, params));
  }

  async getUser(userId: string): Promise<AdminUser> {
    return (await this.client.requestWithAuth<DataResponse<AdminUser>>(apiPaths.adminUser(userId))).data;
  }

  async changeUserStatus(user: AdminUser, status: UserStatus, reason: string): Promise<AdminUser> {
    return (await this.client.requestWithAuth<DataResponse<AdminUser>>(apiPaths.adminUserStatus(user.userId), {
      method: 'POST',
      body: JSON.stringify({status, reason, expectedVersion: user.version}),
    })).data;
  }

  async changeAdminAccess(user: AdminUser, enabled: boolean, reason: string): Promise<AdminUser> {
    return (await this.client.requestWithAuth<DataResponse<AdminUser>>(apiPaths.adminAccess(user.userId), {
      method: 'POST',
      body: JSON.stringify({enabled, reason, expectedVersion: user.version}),
    })).data;
  }

  listCompanies(params: CompanyListParams = {}): Promise<ListResponse<CompanyReview>> {
    return this.client.requestWithAuth(withQuery(apiPaths.companyReviews, params));
  }

  async getCompany(companyId: string): Promise<CompanyReview> {
    return (await this.client.requestWithAuth<DataResponse<CompanyReview>>(apiPaths.companyReview(companyId))).data;
  }

  async reviewCompany(company: CompanyReview, decision: 'APPROVE' | 'REJECT', reason: string) {
    const path = decision === 'APPROVE' ? apiPaths.approveCompany(company.companyId)
      : apiPaths.rejectCompany(company.companyId);
    return (await this.client.requestWithAuth<DataResponse<CompanyReview>>(path, {
      method: 'POST', body: JSON.stringify({reason, expectedVersion: company.version}),
    })).data;
  }

  async updateCompany(company: CompanyReview, input: UpdateAdminCompanyInput): Promise<CompanyReview> {
    return (await this.client.requestWithAuth<DataResponse<CompanyReview>>(apiPaths.updateCompany(company.companyId), {
      method: 'PATCH', body: JSON.stringify({...input, expectedVersion: company.version}),
    })).data;
  }

  listAuditEvents(params: {actorId?: string; action?: string; targetType?: string; page?: number; pageSize?: number} = {}) {
    return this.client.requestWithAuth<ListResponse<AuditEvent>>(withQuery(apiPaths.auditEvents, params));
  }
}

function withQuery<T extends object>(path: string, values: T): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(values as Record<string, unknown>)) {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value));
  }
  const suffix = query.toString();
  return suffix ? `${path}?${suffix}` : path;
}

export const adminClient = new AdminClient();
