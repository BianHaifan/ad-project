import {useState, useSyncExternalStore} from 'react';
import {Navigate, NavLink, Outlet, useNavigate} from 'react-router-dom';
import {authClient, type AuthClient} from '../api/authClient';
import {authSession, type AuthSessionStore} from '../api/authSession';

type LogoutClient = Pick<AuthClient, 'logout'>;

export function AppShell({client = authClient, sessions = authSession}: {
  client?: LogoutClient;
  sessions?: AuthSessionStore;
}) {
  const nav = useNavigate();
  const session = useSyncExternalStore(sessions.subscribe, sessions.getSnapshot);
  const [loggingOut, setLoggingOut] = useState(false);

  if (!session) return <Navigate to="/recruiter/sign-in" replace/>;

  const logout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await client.logout();
    } catch {
      // AuthClient always clears local state; the server error is intentionally not exposed raw.
    } finally {
      sessions.clear();
      nav('/recruiter/sign-in', {replace: true});
    }
  };

  const recruiter = session.user;
  return <main className="app-shell">
    <header className="topnav">
      <NavLink className="brand" to="/recruiter/dashboard">AD Recruiter</NavLink>
      <nav>
        <NavLink to="/recruiter/dashboard">Dashboard</NavLink>
        <NavLink to="/recruiter/jobs">Jobs</NavLink>
        <NavLink to="/recruiter/applications">Applications</NavLink>
        <NavLink to="/recruiter/messages">Messages</NavLink>
      </nav>
      <div className="account"><NavLink className="account-profile" to="/recruiter/profile">
        {recruiter.avatarUrl
          ? <img className="avatar" src={recruiter.avatarUrl} alt=""/>
          : <span className="avatar">{recruiter.fullName.slice(0, 1).toUpperCase()}</span>}
        <span><b>{recruiter.fullName}</b><small>{recruiter.company.name}</small></span>
        </NavLink>
        <button className="text-button" disabled={loggingOut} onClick={logout}>
          {loggingOut ? 'Signing out…' : 'Sign out'}
        </button>
      </div>
    </header>
    <Outlet/>
  </main>;
}
