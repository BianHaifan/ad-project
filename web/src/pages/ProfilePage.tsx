import {useEffect, useRef, useState, type ChangeEvent, type FormEvent} from 'react';
import {useSearchParams} from 'react-router-dom';
import {AuthApiError} from '../api/authClient';
import {authSession, type AuthSessionStore} from '../api/authSession';
import {useDeleteAvatar, useRecruiterProfile, useUpdateRecruiterProfile, useUploadAvatar} from '../api/queries';
import {ErrorState, LoadingState} from '../components/AsyncState';
import {GoogleConnectionSection} from '../components/GoogleConnectionSection';
import {PageHeader} from '../components/PageHeader';
import {parseOAuthCallbackResult, type GoogleOAuthCallbackResult} from '../lib/googleOAuth';
import type {RecruiterProfileDetail} from '../models/recruiter';

type Redirect = (url: string) => void;
const defaultRedirect: Redirect = url => window.location.assign(url);

const MAX_AVATAR_BYTES = 5 * 1024 * 1024;

const RESULT_COPY: Record<GoogleOAuthCallbackResult, {tone: 'success' | 'info' | 'warn'; message: string}> = {
  connected: {tone: 'success', message: 'Successfully connected to Google.'},
  denied: {tone: 'info', message: 'You cancelled the Google authorization.'},
  failed: {tone: 'warn', message: "The connection wasn't completed. You can try again."},
};

interface ProfileForm {
  fullName: string;
  title: string;
  bio: string;
}

export function ProfilePage({redirect = defaultRedirect, sessions = authSession}: {
  redirect?: Redirect;
  sessions?: AuthSessionStore;
}) {
  const query = useRecruiterProfile();
  const update = useUpdateRecruiterProfile();
  const [searchParams, setSearchParams] = useSearchParams();
  // Capture the callback result once, before the URL is cleared, so it stays visible for this
  // render while a refresh or back/forward no longer replays it.
  const [callbackResult] = useState<GoogleOAuthCallbackResult | null>(() =>
    parseOAuthCallbackResult(searchParams.get('googleOAuth')));
  const submittingRef = useRef(false);
  const initialized = useRef(false);
  const [form, setForm] = useState<ProfileForm>({fullName: '', title: '', bio: ''});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [pageError, setPageError] = useState('');
  const [saved, setSaved] = useState('');

  // The page carries no legitimate query parameters; drop whatever arrived so the OAuth
  // notice cannot be re-read on refresh. Unknown params are never rendered or forwarded.
  useEffect(() => {
    if (searchParams.toString()) setSearchParams({}, {replace: true});
  }, [searchParams, setSearchParams]);

  useEffect(() => {
    if (query.data && !initialized.current) {
      setForm({fullName: query.data.fullName, title: query.data.title, bio: query.data.bio ?? ''});
      initialized.current = true;
    }
  }, [query.data]);

  const header = <>
    <PageHeader title="Recruiter Profile" subtitle="Manage the profile shown to your hiring team."/>
    {callbackResult && (
      <div className={`oauth-banner ${RESULT_COPY[callbackResult].tone}`} role="status">
        {RESULT_COPY[callbackResult].message}
      </div>
    )}
  </>;

  if (query.isLoading) return <>{header}<LoadingState label="Loading your profile…"/></>;
  if (query.isError || !query.data) return <>{header}<ErrorState onRetry={() => query.refetch()}/></>;
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
      });
      setForm({fullName: updated.fullName, title: updated.title, bio: updated.bio ?? ''});
      setSaved('Profile saved');
    } catch (caught) {
      const presented = presentProfileError(caught);
      setErrors(current => ({...current, ...presented.fieldErrors}));
      setPageError(presented.pageError);
    } finally {
      submittingRef.current = false;
    }
  };

  const onAvatarChanged = (avatarUrl: string | null) => sessions.updateAvatarUrl(avatarUrl);

  return <>
    {header}
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
        <AvatarSection profile={profile} onAvatarChanged={onAvatarChanged}/>
      </section>
      <div className="profile-main">
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
          <div className="actions">
            <button type="submit" className="button primary" disabled={update.isPending}>
              {update.isPending ? 'Saving…' : 'Save profile'}
            </button>
          </div>
        </form>
        <GoogleConnectionSection redirect={redirect}/>
      </div>
    </div>
  </>;
}

function AvatarSection({profile, onAvatarChanged}: {
  profile: RecruiterProfileDetail;
  onAvatarChanged: (avatarUrl: string | null) => void;
}) {
  const upload = useUploadAvatar();
  const remove = useDeleteAvatar();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [message, setMessage] = useState<{tone: 'success' | 'error'; text: string} | null>(null);

  // Release the browser object URL whenever the preview changes or the section unmounts,
  // so a selected image never leaks an object URL.
  useEffect(() => {
    return () => { if (previewUrl) URL.revokeObjectURL(previewUrl); };
  }, [previewUrl]);

  const choose = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null;
    event.target.value = '';
    setMessage(null);
    if (!file) return;
    const problem = validateAvatarFile(file);
    if (problem) {
      setMessage({tone: 'error', text: problem});
      return;
    }
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setSelectedFile(file);
    setPreviewUrl(URL.createObjectURL(file));
  };

  const clearSelection = () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setPreviewUrl(null);
    setSelectedFile(null);
  };

  const doUpload = async () => {
    if (!selectedFile) return;
    setMessage(null);
    try {
      const metadata = await upload.mutateAsync(selectedFile);
      onAvatarChanged(metadata.avatarUrl);
      clearSelection();
      setMessage({tone: 'success', text: 'Avatar updated'});
    } catch (caught) {
      setMessage({tone: 'error', text: presentAvatarError(caught)});
    }
  };

  const doRemove = async () => {
    setMessage(null);
    try {
      await remove.mutateAsync();
      onAvatarChanged(null);
      clearSelection();
      setMessage({tone: 'success', text: 'Avatar removed'});
    } catch (caught) {
      setMessage({tone: 'error', text: presentAvatarError(caught)});
    }
  };

  const busy = upload.isPending || remove.isPending;

  return <div className="avatar-editor">
    <div className="section-title">
      <div><h3>Avatar</h3><small>PNG or JPEG, up to 5 MB.</small></div>
    </div>
    {previewUrl && <img className="avatar xl profile-avatar" src={previewUrl} alt="Avatar preview"/>}
    {message?.tone === 'error'
      ? <div className="form-error" role="alert">{message.text}</div>
      : message ? <div className="autosave" role="status">{message.text}</div> : null}
    <div className="actions">
      <label className="button secondary">
        Choose image
        <input type="file" accept="image/png,image/jpeg" onChange={choose} className="sr-only"/>
      </label>
      <button type="button" className="button primary" disabled={busy || !selectedFile} onClick={doUpload}>
        {upload.isPending ? 'Uploading…' : 'Upload'}
      </button>
      {profile.avatarUrl && (
        <button type="button" className="button danger" disabled={busy} onClick={doRemove}>
          {remove.isPending ? 'Removing…' : 'Remove'}
        </button>
      )}
    </div>
  </div>;
}

function initials(fullName: string) {
  return fullName.split(' ').map(part => part[0]).join('').slice(0, 2).toUpperCase();
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString(undefined, {year: 'numeric', month: 'short', day: 'numeric'});
}

function validateAvatarFile(file: File): string {
  if (file.type !== 'image/png' && file.type !== 'image/jpeg') {
    return 'Please choose a PNG or JPEG image.';
  }
  if (file.size > MAX_AVATAR_BYTES) {
    return 'The image must be 5 MB or smaller.';
  }
  return '';
}

function presentAvatarError(caught: unknown): string {
  if (!(caught instanceof AuthApiError)) return 'Unable to update your avatar. Please try again.';
  if (caught.code === 'FILE_TOO_LARGE') return 'The image must be 5 MB or smaller.';
  if (caught.code === 'VALIDATION_ERROR') {
    return 'That image could not be used. Please choose a PNG or JPEG under 5 MB.';
  }
  if (caught.code === 'NETWORK_ERROR') {
    return 'Unable to reach the server. Check your connection and try again.';
  }
  return 'Unable to update your avatar. Please try again.';
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
