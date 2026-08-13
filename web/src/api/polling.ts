import {useEffect, useRef, useState} from 'react';
import {useQuery, type QueryFunction, type QueryKey, type UseQueryResult} from '@tanstack/react-query';

export const LIST_INTERVAL_MS = 3000;
export const DETAIL_INTERVAL_MS = 1000;

const BACKOFF_SEQUENCE_MS = [3000, 10000, 30000];

/**
 * Returns the delay before the next poll. A healthy poll (zero consecutive failures)
 * uses the base cadence; consecutive failures escalate to 3s, 10s, then 30s.
 */
export function nextPollDelay(consecutiveFailures: number, baseMs: number): number {
  if (consecutiveFailures <= 0) return baseMs;
  return BACKOFF_SEQUENCE_MS[Math.min(consecutiveFailures - 1, BACKOFF_SEQUENCE_MS.length - 1)];
}

/**
 * Tracks whether the page is both visible and focused. Polling must pause when the
 * browser tab is hidden or the window loses focus, and resume on return.
 */
export function useForeground(): boolean {
  const [foreground, setForeground] = useState(true);
  useEffect(() => {
    const onVisibility = () => setForeground(document.visibilityState !== 'hidden');
    const onBlur = () => setForeground(false);
    const onFocus = () => setForeground(document.visibilityState !== 'hidden');
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('blur', onBlur);
    window.addEventListener('focus', onFocus);
    return () => {
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('blur', onBlur);
      window.removeEventListener('focus', onFocus);
    };
  }, []);
  return foreground;
}

export type PollingQueryResult<TData> = UseQueryResult<TData, Error> & {
  active: boolean;
  consecutiveFailures: number;
};

/**
 * A TanStack Query wrapper that drives foreground-only polling with failure backoff.
 * - The query is enabled while {@link options.enabled} is true; polling also requires focus.
 * - Ticks are skipped while a fetch is already in flight, so requests never stack.
 * - Consecutive failures escalate the interval (3s/10s/30s) and reset on success.
 * - Returning to the foreground triggers an immediate refresh.
 */
export function usePollingQuery<TData>(options: {
  queryKey: QueryKey;
  queryFn: QueryFunction<TData, QueryKey>;
  enabled: boolean;
  intervalMs: number;
}): PollingQueryResult<TData> {
  const foreground = useForeground();
  const [consecutiveFailures, setConsecutiveFailures] = useState(0);

  // Count failures per fetch, resetting on success. Wrapping the queryFn here (rather
  // than watching fetchStatus transitions) keeps the count correct even when React
  // batches the fetching/idle states into a single render.
  const queryFn: QueryFunction<TData, QueryKey> = async context => {
    try {
      const data = await options.queryFn(context);
      setConsecutiveFailures(0);
      return data;
    } catch (error) {
      setConsecutiveFailures(count => count + 1);
      throw error;
    }
  };

  const query = useQuery<TData, Error>({
    queryKey: options.queryKey,
    queryFn,
    enabled: options.enabled,
    retry: false,
    refetchOnWindowFocus: false,
  });

  const active = options.enabled && foreground;

  const isFetchingRef = useRef(false);
  isFetchingRef.current = query.isFetching;
  const refetchRef = useRef(query.refetch);
  refetchRef.current = query.refetch;

  // Poll on an interval that adapts to the current failure count and active state.
  useEffect(() => {
    if (!active) return;
    const delay = nextPollDelay(consecutiveFailures, options.intervalMs);
    const id = window.setInterval(() => {
      if (isFetchingRef.current) return;
      void refetchRef.current();
    }, delay);
    return () => window.clearInterval(id);
  }, [active, consecutiveFailures, options.intervalMs]);

  // Refresh immediately when returning to the foreground.
  const prevForegroundRef = useRef(foreground);
  useEffect(() => {
    if (foreground && !prevForegroundRef.current && options.enabled) {
      void refetchRef.current();
    }
    prevForegroundRef.current = foreground;
  }, [foreground, options.enabled]);

  return {...query, active, consecutiveFailures};
}
