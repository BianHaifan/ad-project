import {useState, type FormEvent} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import {recruiterRepository} from '../api/repository';

export function AuthPage({mode}: {mode: 'signin' | 'register'}) {
  const nav = useNavigate();
  const [fullName, setFullName] = useState('');
  const [company, setCompany] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError('');
    if (!email.includes('@')) return setError('Enter a valid work email.');
    if (password.length < 8) return setError('Password must contain at least 8 characters.');
    if (mode === 'register' && !fullName.trim()) return setError('Full name is required.');
    if (mode === 'register' && !company.trim()) return setError('Company name is required.');
    setLoading(true);
    try {
      if (mode === 'signin') await recruiterRepository.signIn(email, password);
      else await recruiterRepository.register(fullName, company, email, password);
      nav('/recruiter/dashboard');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to continue.');
    } finally { setLoading(false); }
  };
  return <main className="auth-layout"><aside className="auth-brand"><b>AD PROJECT · RECRUITER</b><div><h1>Hire smarter.<br/>Move faster.</h1><p>Publish roles, review applicants, and discover AI-matched talent from one workspace.</p><ul><li>✓ AI-ranked candidate recommendations</li><li>✓ Application pipeline management</li><li>✓ Interview and messaging workflow</li></ul></div><small>Recruiter Workspace · Secure access</small></aside><section className="auth-area"><form className="auth-card" onSubmit={submit} noValidate><span className="portal-badge">RECRUITER PORTAL</span><h2>{mode === 'signin' ? 'Welcome back' : 'Create recruiter account'}</h2><p>{mode === 'signin' ? 'Sign in to manage roles and candidates.' : 'Set up your company hiring workspace.'}</p>{mode === 'register' && <><label>FULL NAME<input value={fullName} onChange={event => setFullName(event.target.value)} placeholder="Your full name"/></label><label>COMPANY NAME<input value={company} onChange={event => setCompany(event.target.value)} placeholder="Your company"/></label></>}<label>WORK EMAIL<input value={email} onChange={event => setEmail(event.target.value)} type="email" placeholder="recruiter@company.com"/></label><label>PASSWORD<input value={password} onChange={event => setPassword(event.target.value)} type="password" placeholder="••••••••••"/></label>{mode === 'signin' && <div className="form-options"><label className="check"><input type="checkbox" checked={remember} onChange={event => setRemember(event.target.checked)}/>Remember me</label><button type="button" className="text-button">Forgot password?</button></div>}{error && <p className="form-error" role="alert">{error}</p>}<button className="button primary wide" disabled={loading}>{loading ? 'Please wait…' : mode === 'signin' ? 'Sign in' : 'Create account'}</button><Link className="auth-link" to={mode === 'signin' ? '/recruiter/create-account' : '/recruiter/sign-in'}>{mode === 'signin' ? 'New to AD Project? Create recruiter account' : 'Already have an account? Sign in'}</Link></form></section></main>;
}
