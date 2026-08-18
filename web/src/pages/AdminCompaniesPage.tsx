import {useState, type FormEvent} from 'react';
import {AuthApiError} from '../api/authClient';
import {useAdminCompanies, useReviewCompany, useUpdateAdminCompany} from '../api/adminQueries';
import {AdminActionDialog} from '../components/AdminActionDialog';
import {AdminPagination} from '../components/AdminPagination';
import {AdminStatusBadge} from '../components/AdminStatusBadge';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';
import type {CompanyReview, CompanyVerificationStatus, UpdateAdminCompanyInput} from '../models/admin';

type Decision = 'APPROVE' | 'REJECT';

export function AdminCompaniesPage() {
  const [searchInput, setSearchInput] = useState('');
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<CompanyVerificationStatus | ''>('PENDING');
  const [page, setPage] = useState(1);
  const [selectedId, setSelectedId] = useState('');
  const [decision, setDecision] = useState<Decision | null>(null);
  const [notice, setNotice] = useState('');
  const [editing, setEditing] = useState(false);
  const companies = useAdminCompanies({q, status, page, pageSize: 20});
  const review = useReviewCompany();
  const update = useUpdateAdminCompany();
  const data = companies.data?.data ?? [];
  const selected = data.find(company => company.companyId === selectedId) ?? data[0] ?? null;
  const error = review.error instanceof AuthApiError
    ? `${review.error.message}${review.error.requestId ? ` Request ID: ${review.error.requestId}` : ''}` : undefined;

  const search = (event: FormEvent) => { event.preventDefault(); setQ(searchInput.trim()); setPage(1) };
  const confirm = (reason: string) => {
    if (!selected || !decision) return;
    review.mutate({company: selected, decision, reason}, {onSuccess: () => {setDecision(null); setNotice(`${selected.name} review decision was saved.`)}, onError: caught => {
      if (caught instanceof AuthApiError && caught.code === 'VERSION_CONFLICT') {
        setDecision(null);
        setNotice('This company changed while the dialog was open. The latest profile is loading; review it and confirm the decision again.');
      }
    }});
  };
  return <section className="admin-page">
    <header className="admin-page-header"><div><span className="admin-eyebrow">TRUST & VERIFICATION</span><h1>Company reviews</h1>
      <p>Review company profile evidence before recruiter publishing access is unlocked.</p></div></header>
    {notice && <div className="admin-page-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="Dismiss notification">×</button></div>}
    <div className="admin-directory-layout">
      <div className="admin-table-card">
        <div className="admin-card-title"><div><h2>Verification queue</h2><p>Newest requests are reviewed first.</p></div>
          <span className="admin-count">{companies.data?.meta.total ?? '—'} reviews</span></div>
        <form className="admin-filters" onSubmit={search}>
          <input value={searchInput} onChange={event => setSearchInput(event.target.value)} placeholder="Search company"/>
          <select value={status} onChange={event => {setStatus(event.target.value as CompanyVerificationStatus | ''); setPage(1)}}>
            <option value="">All statuses</option><option value="PENDING">Pending</option>
            <option value="APPROVED">Approved</option><option value="REJECTED">Rejected</option>
          </select><button className="button secondary">Search</button>
        </form>
        {companies.isPending ? <LoadingState label="Loading company reviews…"/> : companies.isError ?
          <ErrorState error={companies.error} onRetry={() => companies.refetch()}/> : data.length === 0 ?
            <EmptyState title="Review queue is clear" description="There are no companies matching these filters."/> : <>
              <div className="admin-table company-admin-table">
                <div className="admin-table-head"><span>Company</span><span>Location</span><span>Submitted by</span><span>Status</span></div>
                {data.map(company => <button key={company.companyId} className={`admin-table-row ${selected?.companyId === company.companyId ? 'selected' : ''}`}
                  onClick={() => setSelectedId(company.companyId)}><span className="admin-person"><i className="company-mark">{company.name.slice(0, 2).toUpperCase()}</i>
                    <span><b>{company.name}</b><small>{company.website ?? 'No website supplied'}</small></span></span>
                  <span>{company.location ?? '—'}</span><span>{company.createdByName}</span><span><AdminStatusBadge status={company.verificationStatus}/></span></button>)}
              </div>
              <AdminPagination page={page} total={companies.data.meta.total} pageSize={companies.data.meta.pageSize}
                hasNext={companies.data.meta.hasNext} onPage={setPage}/>
            </>}
      </div>
      <aside className="admin-detail-card">
        {selected ? editing
          ? <CompanyEdit company={selected} submitting={update.isPending} error={update.error}
              onCancel={() => {update.reset(); setEditing(false)}} onSave={input => update.mutate({company: selected, input}, {
                onSuccess: () => {setEditing(false); setNotice(`${selected.name} company profile was updated.`)},
                onError: caught => {if(caught instanceof AuthApiError && caught.code === 'VERSION_CONFLICT') {
                  setEditing(false); setNotice('This company changed while you were editing. The latest profile is loading; review it before trying again.');
                }},
              })}/>
          : <CompanyDetail company={selected} onEdit={() => setEditing(true)} onDecision={value => {review.reset(); setDecision(value)}}/> :
          <EmptyState title="Select a company" description="Company evidence and decisions will appear here."/>}
      </aside>
    </div>
    <AdminActionDialog open={Boolean(decision)} title={decisionTitle(decision, selected?.name)}
      description="Add a clear explanation. Recruiters will use this outcome to understand the verification state."
      confirmLabel={decision === 'APPROVE' ? 'Approve company' : 'Reject company'}
      danger={decision === 'REJECT'} submitting={review.isPending} error={error} onCancel={() => {review.reset(); setDecision(null)}} onConfirm={confirm}/>
  </section>;
}

function CompanyDetail({company, onEdit, onDecision}: {company: CompanyReview; onEdit: () => void; onDecision: (decision: Decision) => void}) {
  const reviewable = company.verificationStatus === 'PENDING';
  return <><div className="admin-detail-heading"><span className="company-mark large">{company.name.slice(0, 2).toUpperCase()}</span>
    <div><span className="admin-eyebrow">COMPANY PROFILE</span><h2>{company.name}</h2><p>{company.location ?? 'Location not supplied'}</p></div></div>
    <div className="admin-signal-list">
      <div><span>Business website</span><b>{company.website ?? 'Not supplied'}</b></div>
      <div><span>Company stage</span><b>{company.stage?.replaceAll('_', ' ') ?? 'Not supplied'}</b></div>
      <div><span>Team size</span><b>{company.employeeRange ?? 'Not supplied'}</b></div>
      <div><span>Submitted by</span><b>{company.createdByName}</b><small>{company.createdByEmail}</small></div>
    </div>
    <div className="admin-description"><span>Company description</span><p>{company.description ?? 'No company description has been supplied.'}</p></div>
    <div className="admin-signal"><span>Verification state</span><AdminStatusBadge status={company.verificationStatus}/></div>
    <div className="admin-detail-actions"><button className="button secondary" onClick={onEdit}>Edit company profile</button></div>
    {reviewable ? <div className="admin-detail-actions stacked">
      <button className="button primary" onClick={() => onDecision('APPROVE')}>Approve company</button>
      <button className="button danger" onClick={() => onDecision('REJECT')}>Reject company</button>
    </div> : <div className="admin-notice"><b>Decision recorded</b><p>This company is no longer awaiting review.</p></div>}
  </>;
}

function CompanyEdit({company, submitting, error, onCancel, onSave}: {
  company: CompanyReview; submitting: boolean; error: Error | null; onCancel: () => void;
  onSave: (input: UpdateAdminCompanyInput) => void;
}) {
  const [form, setForm] = useState<UpdateAdminCompanyInput>({
    name: company.name, logoUrl: company.logoUrl, website: company.website, stage: company.stage,
    employeeRange: company.employeeRange, location: company.location, description: company.description, reason: '',
  });
  const set = (key: keyof UpdateAdminCompanyInput, value: string) => setForm(current => ({...current, [key]: value || null}));
  const submit = (event: FormEvent) => {event.preventDefault(); if(form.name.trim() && form.reason.trim()) onSave(form)};
  return <form className="form-section admin-company-edit" onSubmit={submit}>
    <div className="section-title"><div><span className="admin-eyebrow">ADMIN EDIT</span><h2>Company profile</h2></div></div>
    <label>NAME<input value={form.name} maxLength={200} onChange={event => setForm(current => ({...current, name: event.target.value}))}/></label>
    <label>LOGO URL<input value={form.logoUrl ?? ''} maxLength={500} onChange={event => set('logoUrl', event.target.value)}/></label>
    <label>WEBSITE<input value={form.website ?? ''} maxLength={500} onChange={event => set('website', event.target.value)}/></label>
    <div className="form-grid"><label>STAGE<input value={form.stage ?? ''} maxLength={32} onChange={event => set('stage', event.target.value)}/></label>
      <label>TEAM SIZE<input value={form.employeeRange ?? ''} maxLength={50} onChange={event => set('employeeRange', event.target.value)}/></label></div>
    <label>LOCATION<input value={form.location ?? ''} maxLength={100} onChange={event => set('location', event.target.value)}/></label>
    <label>DESCRIPTION<textarea value={form.description ?? ''} maxLength={5000} rows={4} onChange={event => set('description', event.target.value)}/></label>
    <label>REASON FOR CHANGE<textarea value={form.reason} maxLength={500} rows={3}
      onChange={event => setForm(current => ({...current, reason: event.target.value}))}/></label>
    {error && <div className="form-error" role="alert">Unable to update the company. Review the fields and try again.</div>}
    <div className="actions"><button type="button" className="button secondary" disabled={submitting} onClick={onCancel}>Cancel</button>
      <button className="button primary" disabled={submitting || !form.name.trim() || !form.reason.trim()}>{submitting ? 'Saving…' : 'Save company'}</button></div>
  </form>;
}
function decisionTitle(decision: Decision | null, company?: string) {
  if (!decision) return '';
  const verb = decision === 'APPROVE' ? 'Approve' : 'Reject';
  return `${verb} ${company ?? 'company'}?`;
}
