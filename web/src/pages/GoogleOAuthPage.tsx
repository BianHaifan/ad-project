import {useEffect, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {AuthApiError} from '../api/authClient';
import {useBeginGoogleConnection, useDisconnectGoogle, useGoogleConnection} from '../api/queries';
import {ErrorState, LoadingState} from '../components/AsyncState';
import {PageHeader} from '../components/PageHeader';
import {isGoogleAuthorizationUrl, parseOAuthCallbackResult, type GoogleOAuthCallbackResult} from '../lib/googleOAuth';
import type {GoogleConnection} from '../models/recruiter';

type Redirect = (url: string) => void;
const defaultRedirect: Redirect = url => window.location.assign(url);

const RESULT_COPY: Record<GoogleOAuthCallbackResult, {tone: 'success' | 'info' | 'warn'; message: string}> = {
  connected: {tone: 'success', message: 'Successfully connected to Google.'},
  denied: {tone: 'info', message: 'You cancelled the Google authorization.'},
  failed: {tone: 'warn', message: "The connection wasn't completed. You can try again."},
};

export function GoogleOAuthPage({redirect = defaultRedirect}: {redirect?: Redirect}) {
  const [searchParams, setSearchParams] = useSearchParams();
  // Capture the callback result once, before the URL is cleared, so it stays visible
  // for this render while a refresh or back/forward no longer replays it.
  const [callbackResult] = useState<GoogleOAuthCallbackResult | null>(() =>
    parseOAuthCallbackResult(searchParams.get('googleOAuth')));
  const [actionError, setActionError] = useState<string | null>(null);

  const connection = useGoogleConnection();
  const begin = useBeginGoogleConnection();
  const disconnect = useDisconnectGoogle();

  // The page has no legitimate query parameters; drop whatever arrived so it cannot be
  // re-read on refresh. Unknown params are never rendered, logged, or forwarded.
  useEffect(() => {
    if (searchParams.toString()) setSearchParams({}, {replace: true});
  }, [searchParams, setSearchParams]);

  const submitting = begin.isPending || disconnect.isPending;

  const connect = async () => {
    setActionError(null);
    try {
      const authorization = await begin.mutateAsync();
      if (!isGoogleAuthorizationUrl(authorization.authorizationUrl)) {
        setActionError('Unable to start the Google connection. Please try again.');
        return;
      }
      redirect(authorization.authorizationUrl);
    } catch (error) {
      setActionError(describeActionError(error));
    }
  };

  const disconnectAccount = async () => {
    setActionError(null);
    try {
      await disconnect.mutateAsync();
    } catch (error) {
      setActionError(describeActionError(error));
    }
  };

  return <>
    <PageHeader title="Integrations"
      subtitle="Connect Google Calendar to schedule Google Meet interviews automatically."/>
    {callbackResult && (
      <div className={`oauth-banner ${RESULT_COPY[callbackResult].tone}`} role="status">
        {RESULT_COPY[callbackResult].message}
      </div>
    )}
    {actionError && <div className="form-error" role="alert">{actionError}</div>}
    {connection.isLoading
      ? <LoadingState label="Loading Google connection…"/>
      : connection.isError || !connection.data
        ? <ErrorState onRetry={() => connection.refetch()}/>
        : <ConnectionPanel connection={connection.data} submitting={submitting}
            onConnect={connect} onDisconnect={disconnectAccount}/>}
  </>;
}

function ConnectionPanel({connection, submitting, onConnect, onDisconnect}: {
  connection: GoogleConnection;
  submitting: boolean;
  onConnect: () => void;
  onDisconnect: () => void;
}) {
  if (connection.status === 'CONNECTED') {
    return (
      <section className="panel">
        <div className="section-title">
          <div><h2>Google Calendar</h2><small>Google Meet interviews are available for scheduling.</small></div>
          <span className="badge connected">Connected</span>
        </div>
        {connection.connectedAt &&
          <p>Connected since {new Date(connection.connectedAt).toLocaleString()}.</p>}
        <button className="button danger" onClick={onDisconnect} disabled={submitting}>
          {submitting ? 'Disconnecting…' : 'Disconnect'}
        </button>
      </section>
    );
  }
  if (connection.status === 'REVOKED') {
    return (
      <section className="panel">
        <div className="section-title">
          <div><h2>Google Calendar</h2><small>Your Google authorization is no longer valid.</small></div>
          <span className="badge revoked">Authorization expired</span>
        </div>
        <p>Google no longer recognizes this connection. Reconnect to keep scheduling Google Meet interviews.</p>
        <button className="button primary" onClick={onConnect} disabled={submitting}>
          {submitting ? 'Connecting…' : 'Reconnect Google'}
        </button>
      </section>
    );
  }
  return (
    <section className="panel">
      <div className="section-title">
        <div><h2>Google Calendar</h2><small>Schedule Google Meet interviews automatically.</small></div>
        <span className="badge disconnected">Disconnected</span>
      </div>
      <p>Connect your Google account to create Meet links when you schedule interviews.</p>
      <button className="button primary" onClick={onConnect} disabled={submitting}>
        {submitting ? 'Connecting…' : 'Connect Google Calendar'}
      </button>
    </section>
  );
}

function describeActionError(error: unknown): string {
  if (error instanceof AuthApiError && error.code === 'GOOGLE_OAUTH_NOT_CONFIGURED') {
    return 'Google integration is not configured in this demo environment yet.';
  }
  return 'Something went wrong. Please try again.';
}
