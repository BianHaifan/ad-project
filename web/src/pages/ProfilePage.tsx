import {useEffect, useRef, useState, type FormEvent} from 'react';
import {AuthApiError} from '../api/authClient';
import {useRecruiterProfile, useUpdateRecruiterProfile} from '../api/queries';
import {ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';


interface ProfileForm {
  fullName: string;
  title: string;
  bio: string;
  avatarUrl: string;
}

export function ProfilePage() {
  const query = useRecruiterProfile();
  const update = useUpdateRecruiterProfile();
  const submittingRef = useRef(false);
  const initialized = useRef(false);
  const [form, setForm] = useState<ProfileForm>({fullName: '', title: '', bio: '', avatarUrl: ''});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [pageError, setPageError] = useState('');
  const [saved, setSaved] = useState('');

  useEffect(() => {
    if (query.data && !initialized.current) {
      setForm({
        fullName: query.data.fullName,
        title: query.data.title,
        bio: query.data.bio ?? '',
        avatarUrl: query.data.avatarUrl ?? '',
      });
      initialized.current = true;
    }
  }, [query.data]);

  if (query.isLoading) return <LoadingState label="Loading your profile…"/>;
  if (query.isError || !query.data) return <ErrorState onRetry={() => query.refetch()}/>;
  const profile = query.data;

  const set = <K extends keyof ProfileForm>(key: K, value: ProfileForm[K]) => {
    setForm(current => ({...current, [key]: value}));
    setErrors(current => current[key] ? {...current, [key]: ''} : current);
  };

  const validate = () => {
    const next: Record<string, string> = {};
    if (!form.fullName.trim()) next.fullName = 'Full name is required.';
    else if (form.fullName.trim().length > 100) next.fullName = 'Full name must not exceed 100 characters.';
    if (!form.title.trim()) next.title = 'Title is required.';
    else if (form.title.trim().length > 100) next.title = 'Title must not exceed 100 characters.';
    if (form.bio.length > 1000) next.bio = 'Bio must not exceed 1000 characters.';
    if (form.avatarUrl.trim().length > 500) next.avatarUrl = 'Avatar URL must not exceed 500 characters.';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (submittingRef.current || !validate()) return;
    submittingRef.current = true;
    setPageError('');
    setSaved('');
    try {
      const updated = await update.mutateAsync({
        fullName: form.fullName.trim(),
        title: form.title.trim(),
        bio: form.bio.trim() ? form.bio.trim() : null,
        avatarUrl: form.avatarUrl.trim() ? form.avatarUrl.trim() : null,
      });
      setForm({
        fullName: updated.fullName,
        title: updated.title,
        bio: updated.bio ?? '',
        avatarUrl: updated.avatarUrl ?? '',
      });
      setSaved('Profile saved');
    } catch (caught) {
      const presented = presentProfileError(caught);
      setErrors(current => ({...current, ...presented.fieldErrors}));
      setPageError(presented.pageError);
    } finally {
      submittingRef.current = false;
    }
  };

  return <>
    <PageHeader title="Recruiter Profile" subtitle="Manage the profile shown to your hiring team."/>
    {saved && <div className="autosave" role="status">{saved}</div>}
    {pageError && <div className="state-card error" role="alert"><span>{pageError}</span></div>}
    <div className="profile-layout">
      <section className="panel profile-summary">
        {profile.avatarUrl
          ? <img className="avatar xl profile-avatar" src={profile.avatarUrl} alt=""/>
          : <span className="avatar xl">{initials(profile.fullName)}</span>}
        <div>
          <h2>{profile.fullName}</h2>
          <p>{profile.title || 'No title yet'}</p>
          <p className="muted">{profile.company.name}</p>
        </div>
        <dl className="profile-readonly">
          <dt>Email</dt><dd>{profile.email}</dd>
          <dt>Company</dt><dd>{profile.company.name}</dd>
          <dt>Registered</dt><dd>{formatDate(profile.createdAt)}</dd>
        </dl>
      </section>
      <form className="panel form-section profile-form" onSubmit={submit} noValidate aria-busy={update.isPending}>
        <h2>Edit profile</h2>
        <label>FULL NAME
          <input value={form.fullName} onChange={event => set('fullName', event.target.value)} maxLength={100}/>
          {errors.fullName && <em>{errors.fullName}</em>}
        </label>
        <label>TITLE
          <input value={form.title} onChange={event => set('title', event.target.value)} maxLength={100}/>
          {errors.title && <em>{errors.title}</em>}
        </label>
        <label>BIO
          <textarea rows={5} value={form.bio} onChange={event => set('bio', event.target.value)} maxLength={1000}/>
          {errors.bio && <em>{errors.bio}</em>}
        </label>
        <label>AVATAR URL
          <input value={form.avatarUrl} onChange={event => set('avatarUrl', event.target.value)} maxLength={500} placeholder="https://example.com/avatar.png"/>
          {errors.avatarUrl && <em>{errors.avatarUrl}</em>}
        </label>
        <div className="actions">
          <button type="submit" className="button primary" disabled={update.isPending}>
            {update.isPending ? 'Saving…' : 'Save profile'}
          </button>
        </div>
      </form>
    </div>
  </>;
}

function initials(fullName: string) {
  return fullName.split(' ').map(part => part[0]).join('').slice(0, 2).toUpperCase();
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString(undefined, {year: 'numeric', month: 'short', day: 'numeric'});
}

function presentProfileError(caught: unknown): {fieldErrors: Record<string, string>; pageError: string} {
  if (!(caught instanceof AuthApiError)) {
    return {fieldErrors: {}, pageError: 'Unable to save your profile. Please try again.'};
  }
  if (caught.code === 'VALIDATION_ERROR' || caught.code === 'INVALID_REQUEST') {
    return {fieldErrors: caught.fieldErrors, pageError: 'Please check the highlighted profile fields.'};
  }
  if (caught.code === 'NETWORK_ERROR') {
    return {fieldErrors: {}, pageError: 'Unable to reach the server. Check your connection and try again.'};
  }
  if (caught.status === 403) {
    return {fieldErrors: {}, pageError: 'You do not have permission to update this profile.'};
  }
  return {fieldErrors: {}, pageError: 'Unable to save your profile. Please try again.'};
}
