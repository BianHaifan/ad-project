import {useState} from 'react';
import {AuthApiError} from '../api/authClient';
import {useBeginGoogleConnection, useDisconnectGoogle, useGoogleConnection} from '../api/queries';
import {isGoogleAuthorizationUrl} from '../lib/googleOAuth';
import type {GoogleConnection} from '../models/recruiter';
import {ErrorState, LoadingState} from './AsyncState';

type Redirect = (url: string) => void;
const defaultRedirect: Redirect = url => window.location.assign(url);

export function GoogleConnectionSection({redirect = defaultRedirect}: {redirect?: Redirect}) {
  const connection = useGoogleConnection();
  const begin = useBeginGoogleConnection();
  const disconnect = useDisconnectGoogle();
  const [actionError, setActionError] = useState<string | null>(null);

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

  return <section className="panel">
    <div className="section-title">
      <div><h2>Google Calendar</h2>
        <small>Connect Google Calendar to schedule Google Meet interviews automatically.</small></div>
    </div>
    {actionError && <div className="form-error" role="alert">{actionError}</div>}
    {connection.isLoading
      ? <LoadingState label="Loading Google connection…"/>
      : connection.isError || !connection.data
        ? <ErrorState onRetry={() => connection.refetch()}/>
        : <ConnectionPanel connection={connection.data} submitting={submitting}
            onConnect={connect} onDisconnect={disconnectAccount}/>}
  </section>;
}

function ConnectionPanel({connection, submitting, onConnect, onDisconnect}: {
  connection: GoogleConnection;
  submitting: boolean;
  onConnect: () => void;
  onDisconnect: () => void;
}) {
  if (connection.status === 'CONNECTED') {
    return <>
      <div className="section-title">
        <div><span className="badge connected">Connected</span>
          {connection.connectedAt &&
            <p>Connected since {new Date(connection.connectedAt).toLocaleString()}.</p>}</div>
      </div>
      <button className="button danger" onClick={onDisconnect} disabled={submitting}>
        {submitting ? 'Disconnecting…' : 'Disconnect'}
      </button>
    </>;
  }
  if (connection.status === 'REVOKED') {
    return <>
      <span className="badge revoked">Authorization expired</span>
      <p>Google no longer recognizes this connection. Reconnect to keep scheduling Google Meet interviews.</p>
      <button className="button primary" onClick={onConnect} disabled={submitting}>
        {submitting ? 'Connecting…' : 'Reconnect Google'}
      </button>
    </>;
  }
  return <>
    <span className="badge disconnected">Disconnected</span>
    <p>Connect your Google account to create Meet links when you schedule interviews.</p>
    <button className="button primary" onClick={onConnect} disabled={submitting}>
      {submitting ? 'Connecting…' : 'Connect Google Calendar'}
    </button>
  </>;
}

function describeActionError(error: unknown): string {
  if (error instanceof AuthApiError && error.code === 'GOOGLE_OAUTH_NOT_CONFIGURED') {
    return 'Google integration is not configured for this environment.';
  }
  return 'Something went wrong. Please try again.';
}
