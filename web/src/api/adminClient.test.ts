import {describe, expect, it, vi} from 'vitest';
import type {AuthClient} from './authClient';
import {AdminClient} from './adminClient';
import type {AdminUser, CompanyReview} from '../models/admin';

const user: AdminUser = {
  userId: '11111111-1111-1111-1111-111111111111', fullName: 'Ada Admin', email: 'ada@example.com',
  role: 'CANDIDATE', status: 'ACTIVE', adminAccess: false, company: null, version: 7,
  createdAt: '2026-08-11T08:00:00Z', updatedAt: '2026-08-11T08:00:00Z',
};

describe('AdminClient', () => {
  it('serializes user filters and optimistic versions exactly once', async () => {
    const requestWithAuth = vi.fn()
      .mockResolvedValueOnce({data: [], meta: {page: 2, pageSize: 20, total: 0, hasNext: false}})
      .mockResolvedValueOnce({data: {...user, status: 'DISABLED', version: 8}})
      .mockResolvedValueOnce({data: {...user, adminAccess: true, version: 8}});
    const client = new AdminClient({requestWithAuth} as unknown as Pick<AuthClient, 'requestWithAuth'>);

    await client.listUsers({q: 'Ada Admin', role: 'CANDIDATE', adminAccess: false, page: 2, pageSize: 20});
    await client.changeUserStatus(user, 'DISABLED', 'Security review');
    await client.changeAdminAccess(user, true, 'Operations coverage');

    expect(requestWithAuth.mock.calls[0][0]).toBe('/admin/users?q=Ada+Admin&role=CANDIDATE&adminAccess=false&page=2&pageSize=20');
    expect(requestWithAuth.mock.calls[1]).toEqual(['/admin/users/11111111-1111-1111-1111-111111111111/status', {
      method: 'POST', body: JSON.stringify({status: 'DISABLED', reason: 'Security review', expectedVersion: 7}),
    }]);
    expect(requestWithAuth.mock.calls[2][1]).toEqual({
      method: 'POST', body: JSON.stringify({enabled: true, reason: 'Operations coverage', expectedVersion: 7}),
    });
  });

  it('uses the declared company review contract', async () => {
    const company = {companyId: 'company-1', version: 3} as CompanyReview;
    const requestWithAuth = vi.fn().mockResolvedValue({data: {}});
    const client = new AdminClient({requestWithAuth} as unknown as Pick<AuthClient, 'requestWithAuth'>);

    await client.reviewCompany(company, 'APPROVE', 'Website evidence verified');

    expect(requestWithAuth.mock.calls[0]).toEqual(['/admin/companies/company-1/approve', {
      method: 'POST', body: JSON.stringify({reason: 'Website evidence verified', expectedVersion: 3}),
    }]);
  });

  it('updates a company with the current version and an audit reason', async () => {
    const company = {companyId: 'company-1', version: 3} as CompanyReview;
    const requestWithAuth = vi.fn().mockResolvedValue({data: {...company, name: 'Edited', version: 4}});
    const client = new AdminClient({requestWithAuth} as unknown as Pick<AuthClient, 'requestWithAuth'>);
    await client.updateCompany(company, {name: 'Edited', logoUrl: null, website: 'https://example.test',
      stage: 'SERIES_A', employeeRange: '11-50', location: 'Singapore', description: 'Public', reason: 'Verified'});
    expect(requestWithAuth).toHaveBeenCalledWith('/admin/companies/company-1', {
      method: 'PATCH', body: JSON.stringify({name: 'Edited', logoUrl: null, website: 'https://example.test',
        stage: 'SERIES_A', employeeRange: '11-50', location: 'Singapore', description: 'Public', reason: 'Verified',
        expectedVersion: 3}),
    });
  });
});
