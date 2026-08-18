import {useEffect, useState, type FormEvent} from 'react';
import {Link, useNavigate, useSearchParams} from 'react-router-dom';
import {AuthApiError, authClient, type AuthClient} from '../api/authClient';

type ResetClient = Pick<AuthClient, 'requestPasswordReset' | 'confirmPasswordReset'>;

export function PasswordResetPage({mode, client = authClient}: {
  mode: 'request' | 'confirm'; client?: ResetClient;
}) {
  const nav = useNavigate();
  const [params] = useSearchParams();
  const [email, setEmail] = useState(params.get('email') ?? '');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState('');
  const [seconds, setSeconds] = useState(mode === 'confirm' ? 60 : 0);

  useEffect(() => {
    if (seconds <= 0) return;
    const timer = window.setInterval(() => setSeconds(value => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [seconds]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (submitting) return;
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) { setMessage('Enter a valid email address.'); return; }
    if (mode === 'confirm' && (!/^\d{6}$/.test(code) || password.length < 8 || password !== confirm)) {
      setMessage(password !== confirm ? 'Passwords do not match.' : 'Enter the 6-digit code and a password of at least 8 characters.');
      return;
    }
    setSubmitting(true); setMessage('');
    try {
      if (mode === 'request') {
        await client.requestPasswordReset(email);
        nav(`/recruiter/reset-password?email=${encodeURIComponent(email.trim())}`);
      } else {
        await client.confirmPasswordReset({email, code, newPassword: password});
        setMessage('Password changed. You can now sign in.');
      }
    } catch (error) {
      setMessage(error instanceof AuthApiError && error.code === 'PASSWORD_RESET_EMAIL_NOT_CONFIGURED'
        ? 'Password reset email is not configured. Contact the administrator.'
        : error instanceof AuthApiError && error.code === 'PASSWORD_RESET_INVALID'
          ? 'The email, code, or reset request is invalid.' : 'Unable to reset the password. Please try again.');
    } finally { setSubmitting(false); }
  };

  const resend = async () => {
    if (seconds > 0 || submitting) return;
    setSubmitting(true); setMessage('');
    try { await client.requestPasswordReset(email); setSeconds(60); setMessage('If the account exists, a new code was sent.'); }
    catch { setMessage('Unable to request another code. Please try again.'); }
    finally { setSubmitting(false); }
  };

  return <main className="auth-layout">
    <aside className="auth-brand"><b>HIREX · RECRUITER</b><div><h1>Recover access.<br/>Keep hiring.</h1>
      <p>Use a one-time verification code to choose a new password securely.</p></div><small>Codes expire after 15 minutes.</small></aside>
    <section className="auth-area"><form className="auth-card" onSubmit={submit} noValidate aria-busy={submitting}>
      <span className="portal-badge">PASSWORD RESET</span>
      <h2>{mode === 'request' ? 'Forgot password' : 'Enter verification code'}</h2>
      <p>{mode === 'request' ? 'Enter your account email. The response is the same for every address.' : 'Check your email, then choose a new password.'}</p>
      <label>EMAIL<input type="email" value={email} disabled={mode === 'confirm'} onChange={e => {setEmail(e.target.value); setMessage('')}}/></label>
      {mode === 'confirm' && <>
        <label>6-DIGIT CODE<input inputMode="numeric" maxLength={6} value={code} onChange={e => {setCode(e.target.value.replace(/\D/g, '')); setMessage('')}}/></label>
        <label>NEW PASSWORD<input type="password" value={password} onChange={e => {setPassword(e.target.value); setMessage('')}}/></label>
        <label>CONFIRM PASSWORD<input type="password" value={confirm} onChange={e => {setConfirm(e.target.value); setMessage('')}}/></label>
      </>}
      {message && <p className={message.startsWith('Password changed') ? 'form-success' : 'form-error'} role="status">{message}</p>}
      <button className="button primary wide" disabled={submitting}>{submitting ? 'Please wait…' : mode === 'request' ? 'Send code' : 'Reset password'}</button>
      {mode === 'confirm' && <button type="button" className="text-button" disabled={seconds > 0 || submitting} onClick={resend}>
        {seconds > 0 ? `Resend in ${seconds}s` : 'Resend code'}</button>}
      <Link className="auth-link" to="/recruiter/sign-in">Back to sign in</Link>
    </form></section>
  </main>;
}
