import {useRef, useState, type FormEvent} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import {
  AuthApiError,
  CURRENT_TERMS_VERSION,
  authClient,
  type AuthClient,
} from '../api/authClient';

type AuthClientPort = Pick<AuthClient, 'signIn' | 'register'>;
type FormField = 'fullName' | 'companyName' | 'email' | 'password' | 'acceptedTermsVersion';
type FieldErrors = Partial<Record<FormField, string>>;

export function AuthPage({mode, client = authClient}: {
  mode: 'signin' | 'register';
  client?: AuthClientPort;
}) {
  const nav = useNavigate();
  const submittingRef = useRef(false);
  const [fullName, setFullName] = useState('');
  const [companyName, setCompanyName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(false);
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [pageError, setPageError] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (submittingRef.current) return;

    const validationErrors = validateForm({mode, fullName, companyName, email, password, acceptedTerms});
    setFieldErrors(validationErrors);
    setPageError('');
    if (Object.keys(validationErrors).length > 0) return;

    submittingRef.current = true;
    setLoading(true);
    try {
      if (mode === 'signin') {
        await client.signIn({email: email.trim(), password, remember});
      } else {
        await client.register({
          fullName: fullName.trim(),
          companyName: companyName.trim(),
          email: email.trim(),
          password,
          acceptedTermsVersion: CURRENT_TERMS_VERSION,
        });
      }
      nav('/recruiter/dashboard');
    } catch (caught) {
      const presented = presentError(caught);
      setFieldErrors(presented.fieldErrors);
      setPageError(presented.pageError);
    } finally {
      submittingRef.current = false;
      setLoading(false);
    }
  };

  const clearFieldError = (field: FormField) => {
    setFieldErrors(current => current[field] ? {...current, [field]: undefined} : current);
  };

  return <main className="auth-layout">
    <aside className="auth-brand">
      <b>AD PROJECT · RECRUITER</b>
      <div>
        <h1>Hire smarter.<br/>Move faster.</h1>
        <p>Publish roles, review applicants, and discover AI-matched talent from one workspace.</p>
        <ul><li>✓ AI-ranked candidate recommendations</li><li>✓ Application pipeline management</li><li>✓ Interview and messaging workflow</li></ul>
      </div>
      <small>Recruiter Workspace · Secure access</small>
    </aside>
    <section className="auth-area">
      <form className="auth-card" onSubmit={submit} noValidate aria-busy={loading}>
        <span className="portal-badge">RECRUITER PORTAL</span>
        <h2>{mode === 'signin' ? 'Welcome back' : 'Create recruiter account'}</h2>
        <p>{mode === 'signin' ? 'Sign in to manage roles and candidates.' : 'Set up your company hiring workspace.'}</p>
        {mode === 'register' && <>
          <label>FULL NAME
            <input value={fullName} onChange={event => {setFullName(event.target.value); clearFieldError('fullName')}}
              autoComplete="name" placeholder="Your full name" aria-invalid={Boolean(fieldErrors.fullName)}/>
            {fieldErrors.fullName && <span className="form-error">{fieldErrors.fullName}</span>}
          </label>
          <label>COMPANY NAME
            <input value={companyName} onChange={event => {setCompanyName(event.target.value); clearFieldError('companyName')}}
              autoComplete="organization" placeholder="Your company" aria-invalid={Boolean(fieldErrors.companyName)}/>
            {fieldErrors.companyName && <span className="form-error">{fieldErrors.companyName}</span>}
          </label>
        </>}
        <label>WORK EMAIL
          <input value={email} onChange={event => {setEmail(event.target.value); clearFieldError('email')}}
            type="email" autoComplete="email" placeholder="recruiter@company.com" aria-invalid={Boolean(fieldErrors.email)}/>
          {fieldErrors.email && <span className="form-error">{fieldErrors.email}</span>}
        </label>
        <label>PASSWORD
          <input value={password} onChange={event => {setPassword(event.target.value); clearFieldError('password')}}
            type="password" autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
            placeholder="••••••••••" aria-invalid={Boolean(fieldErrors.password)}/>
          {fieldErrors.password && <span className="form-error">{fieldErrors.password}</span>}
        </label>
        {mode === 'signin' && <div className="form-options">
          <label className="check"><input type="checkbox" checked={remember}
            onChange={event => setRemember(event.target.checked)}/>Remember me</label>
          <button type="button" className="text-button">Forgot password?</button>
        </div>}
        {mode === 'register' && <div className="form-options">
          <label className="check"><input type="checkbox" checked={acceptedTerms}
            onChange={event => {setAcceptedTerms(event.target.checked); clearFieldError('acceptedTermsVersion')}}/>
            I agree to the current terms</label>
        </div>}
        {fieldErrors.acceptedTermsVersion && <p className="form-error" role="alert">{fieldErrors.acceptedTermsVersion}</p>}
        {pageError && <p className="form-error" role="alert">{pageError}</p>}
        <button type="submit" className="button primary wide" disabled={loading}>
          {loading ? 'Please wait…' : mode === 'signin' ? 'Sign in' : 'Create account'}
        </button>
        <Link className="auth-link" to={mode === 'signin' ? '/recruiter/create-account' : '/recruiter/sign-in'}>
          {mode === 'signin' ? 'New to AD Project? Create recruiter account' : 'Already have an account? Sign in'}
        </Link>
      </form>
    </section>
  </main>;
}

function validateForm(input: {
  mode: 'signin' | 'register';
  fullName: string;
  companyName: string;
  email: string;
  password: string;
  acceptedTerms: boolean;
}): FieldErrors {
  const errors: FieldErrors = {};
  if (!/^\S+@\S+\.\S+$/.test(input.email.trim())) errors.email = 'Enter a valid work email.';
  if (input.password.length < 8) errors.password = 'Password must contain at least 8 characters.';
  if (input.mode === 'register' && !input.fullName.trim()) errors.fullName = 'Full name is required.';
  if (input.mode === 'register' && !input.companyName.trim()) errors.companyName = 'Company name is required.';
  if (input.mode === 'register' && !input.acceptedTerms) errors.acceptedTermsVersion = 'You must agree to the current terms.';
  return errors;
}

function presentError(caught: unknown): {fieldErrors: FieldErrors; pageError: string} {
  if (!(caught instanceof AuthApiError)) {
    return {fieldErrors: {}, pageError: 'Unable to continue. Please try again.'};
  }
  const fieldErrors: FieldErrors = {};
  for (const field of ['fullName', 'companyName', 'email', 'password', 'acceptedTermsVersion'] as const) {
    if (caught.fieldErrors[field]) fieldErrors[field] = caught.fieldErrors[field];
  }
  const knownMessages: Record<string, string> = {
    UNAUTHORIZED: 'Email or password is incorrect.',
    EMAIL_ALREADY_REGISTERED: 'An account already exists for this email.',
    VALIDATION_ERROR: 'Please check the highlighted fields.',
    WRONG_ROLE: 'This account is not a recruiter account and cannot access the recruiter workspace.',
    NETWORK_ERROR: 'Unable to reach the server. Check your connection and try again.',
    UNEXPECTED_RESPONSE: 'The server returned an unexpected response. Please try again later.',
  };
  return {fieldErrors, pageError: knownMessages[caught.code] ?? 'Unable to complete the request. Please try again.'};
}
