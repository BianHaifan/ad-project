import type {CompanyVerificationStatus, UserStatus} from '../models/admin';

type Status = CompanyVerificationStatus | UserStatus;
const labels: Record<Status, string> = {
  ACTIVE: 'Active',
  DISABLED: 'Disabled',
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
};

export function AdminStatusBadge({status}: {status: Status}) {
  return <span className={`admin-badge ${status.toLowerCase()}`}>{labels[status]}</span>;
}
