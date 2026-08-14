import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {act, cleanup, renderHook, waitFor} from '@testing-library/react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import type {ReactNode} from 'react';
import {DETAIL_INTERVAL_MS, LIST_INTERVAL_MS, nextPollDelay, useForeground, usePollingQuery} from './polling';

function wrapper(client: QueryClient) {
  return function Wrapper({children}: {children: ReactNode}) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

describe('nextPollDelay', () => {
  it('uses the base cadence when healthy and escalates failures to 3s/10s/30s', () => {
    expect(nextPollDelay(0, LIST_INTERVAL_MS)).toBe(LIST_INTERVAL_MS);
    expect(nextPollDelay(-1, DETAIL_INTERVAL_MS)).toBe(DETAIL_INTERVAL_MS);
    expect(nextPollDelay(1, DETAIL_INTERVAL_MS)).toBe(3000);
    expect(nextPollDelay(2, DETAIL_INTERVAL_MS)).toBe(10000);
    expect(nextPollDelay(3, DETAIL_INTERVAL_MS)).toBe(30000);
    expect(nextPollDelay(99, DETAIL_INTERVAL_MS)).toBe(30000);
  });
});

describe('usePollingQuery', () => {
  afterEach(() => {cleanup(); vi.restoreAllMocks();});

  it('polls repeatedly at the configured interval', async () => {
    const queryFn = vi.fn().mockResolvedValue('ok');
    const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
    renderHook(() => usePollingQuery({queryKey: ['poll'], queryFn, enabled: true, intervalMs: 10}),
      {wrapper: wrapper(client)});
    await waitFor(() => expect(queryFn.mock.calls.length).toBeGreaterThan(2), {timeout: 2000});
  });

  it('does not stack requests while one is already in flight', async () => {
    let resolve!: (value: string) => void;
    const queryFn = vi.fn().mockImplementation(() => new Promise<string>(r => {resolve = r;}));
    const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
    renderHook(() => usePollingQuery({queryKey: ['poll'], queryFn, enabled: true, intervalMs: 10}),
      {wrapper: wrapper(client)});
    await waitFor(() => expect(queryFn).toHaveBeenCalledTimes(1));
    await new Promise(r => setTimeout(r, 60));
    expect(queryFn).toHaveBeenCalledTimes(1);
    await act(async () => {resolve('ok');});
    await waitFor(() => expect(queryFn.mock.calls.length).toBeGreaterThan(1), {timeout: 2000});
  });

  it('counts consecutive failures and resets on a successful refetch', async () => {
    const queryFn = vi.fn().mockRejectedValueOnce(new Error('boom')).mockResolvedValue('ok');
    const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
    const {result} = renderHook(() => usePollingQuery({queryKey: ['poll'], queryFn, enabled: true, intervalMs: LIST_INTERVAL_MS}),
      {wrapper: wrapper(client)});
    await waitFor(() => expect(result.current.consecutiveFailures).toBe(1));
    await act(async () => {await result.current.refetch();});
    await waitFor(() => expect(result.current.consecutiveFailures).toBe(0));
    expect(queryFn).toHaveBeenCalledTimes(2);
  });

  it('stops polling when the window blurs and refreshes immediately on focus', async () => {
    const queryFn = vi.fn().mockResolvedValue('ok');
    const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
    const clearInterval = vi.spyOn(window, 'clearInterval');
    const {result} = renderHook(() => usePollingQuery({queryKey: ['poll'], queryFn, enabled: true, intervalMs: LIST_INTERVAL_MS}),
      {wrapper: wrapper(client)});
    await waitFor(() => expect(queryFn).toHaveBeenCalledTimes(1));
    expect(result.current.active).toBe(true);
    act(() => {window.dispatchEvent(new Event('blur'));});
    expect(result.current.active).toBe(false);
    expect(clearInterval).toHaveBeenCalled();
    act(() => {window.dispatchEvent(new Event('focus'));});
    expect(result.current.active).toBe(true);
    await waitFor(() => expect(queryFn.mock.calls.length).toBeGreaterThan(1));
  });

  it('does not poll while disabled', () => {
    const queryFn = vi.fn().mockResolvedValue('ok');
    const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
    const setInterval = vi.spyOn(window, 'setInterval');
    const {result} = renderHook(() => usePollingQuery({queryKey: ['poll'], queryFn, enabled: false, intervalMs: LIST_INTERVAL_MS}),
      {wrapper: wrapper(client)});
    expect(result.current.active).toBe(false);
    expect(setInterval).not.toHaveBeenCalled();
  });
});

describe('useForeground', () => {
  afterEach(() => {cleanup(); vi.restoreAllMocks();});

  it('tracks focus and visibility', () => {
    const {result} = renderHook(() => useForeground());
    expect(result.current).toBe(true);
    act(() => {window.dispatchEvent(new Event('blur'));});
    expect(result.current).toBe(false);
    act(() => {window.dispatchEvent(new Event('focus'));});
    expect(result.current).toBe(true);
  });
});
