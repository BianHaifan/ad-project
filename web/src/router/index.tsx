import {createBrowserRouter, Navigate} from 'react-router-dom';
import {AdminShell} from '../components/AdminShell';
import {AppShell} from '../components/AppShell';
import {AdminAuditPage} from '../pages/AdminAuditPage';
import {AdminAuthPage} from '../pages/AdminAuthPage';
import {AdminCompaniesPage} from '../pages/AdminCompaniesPage';
import {AdminUsersPage} from '../pages/AdminUsersPage';
import {ApplicationDetailPage} from '../pages/ApplicationDetailPage';
import {ApplicationsPage} from '../pages/ApplicationsPage';
import {AuthPage} from '../pages/AuthPage';
import {CommunityDetailPage} from '../pages/CommunityDetailPage';
import {CommunityPage} from '../pages/CommunityPage';
import {CommunityCreatePage} from '../pages/CommunityCreatePage';
import {CommunityDirectMessagePage} from '../pages/CommunityDirectMessagePage';
import {DashboardPage} from '../pages/DashboardPage';
import {GoogleOAuthPage} from '../pages/GoogleOAuthPage';
import {JobDetailPage} from '../pages/JobDetailPage';
import {JobFormPage} from '../pages/JobFormPage';
import {JobsPage} from '../pages/JobsPage';
import {MessagesPage} from '../pages/MessagesPage';
import {NotFoundPage} from '../pages/NotFoundPage';
import {ProfilePage} from '../pages/ProfilePage';
import {PasswordResetPage} from '../pages/PasswordResetPage';
import {ResumeReviewPage} from '../pages/ResumeReviewPage';
import {RouteTitle} from './RouteTitle';

export const router = createBrowserRouter([
  {path: '/', element: <Navigate to="/recruiter/sign-in" replace/>},
  {path: '/recruiter', element: <RouteTitle title="HireX Recruiter"/>, children: [
    {path: 'sign-in', element: <AuthPage mode="signin"/>},
    {path: 'create-account', element: <AuthPage mode="register"/>},
    {path: 'forgot-password', element: <PasswordResetPage mode="request"/>},
    {path: 'reset-password', element: <PasswordResetPage mode="confirm"/>},
    {element: <AppShell/>, children: [
    {path: 'dashboard', element: <DashboardPage/>},
    {path: 'jobs', element: <JobsPage/>},
    {path: 'jobs/new', element: <JobFormPage/>},
    {path: 'jobs/:jobId/edit', element: <JobFormPage/>},
    {path: 'jobs/:jobId', element: <JobDetailPage/>},
    {path: 'applications', element: <ApplicationsPage/>},
    {path: 'applications/:applicationId', element: <ApplicationDetailPage/>},
    {path: 'applications/:applicationId/review', element: <ResumeReviewPage/>},
    {path: 'messages', element: <MessagesPage/>},
    {path: 'messages/:conversationId', element: <MessagesPage/>},
    {path: 'community', element: <CommunityPage/>},
    {path: 'community/new', element: <CommunityCreatePage/>},
    {path: 'community/messages/:conversationId', element: <CommunityDirectMessagePage/>},
    {path: 'community/:postId', element: <CommunityDetailPage/>},
    {path: 'google-oauth', element: <GoogleOAuthPage/>},
    {path: 'profile', element: <ProfilePage/>},
    ]},
  ]},
  {path: '/admin', element: <RouteTitle title="HireX Administrator"/>, children: [
    {path: 'sign-in', element: <AdminAuthPage/>},
    {element: <AdminShell/>, children: [
    {index: true, element: <Navigate to="/admin/users" replace/>},
    {path: 'users', element: <AdminUsersPage/>},
    {path: 'company-reviews', element: <AdminCompaniesPage/>},
    {path: 'audit-log', element: <AdminAuditPage/>},
    ]},
  ]},
  {path: '*', element: <NotFoundPage/>},
]);
