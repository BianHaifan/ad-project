import {NavLink, Outlet, useNavigate} from 'react-router-dom';
import {useMe} from '../api/queries';

export function AppShell() {
  const nav = useNavigate();
  const {data} = useMe();
  return <main className="app-shell"><header className="topnav"><NavLink className="brand" to="/recruiter/dashboard">AD Recruiter</NavLink><nav><NavLink to="/recruiter/dashboard">Dashboard</NavLink><NavLink to="/recruiter/jobs">Jobs</NavLink><NavLink to="/recruiter/applications">Applications</NavLink><NavLink to="/recruiter/messages">Messages</NavLink></nav><div className="account"><span className="avatar">{data?.fullName.slice(0, 1) ?? 'M'}</span><span><b>{data?.fullName ?? 'Mia Chen'}</b><small>{data?.company.name ?? 'Moonshot AI'}</small></span><button className="text-button" onClick={() => {localStorage.removeItem('ad_session'); nav('/recruiter/sign-in');}}>Sign out</button></div></header><Outlet/></main>;
}
