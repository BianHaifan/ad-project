import {describe, expect, it} from 'vitest';
import {isValidTimeZone, localToUtcIso, utcToLocalInput} from './interviewTime';

describe('interview time conversion', () => {
  it('converts a local create value to the correct UTC instant', () => {
    expect(localToUtcIso('2026-08-20T09:00', 'Asia/Singapore')).toBe('2026-08-20T01:00:00Z');
  });

  it('handles daylight-saving offsets for a non-UTC zone', () => {
    expect(localToUtcIso('2026-07-04T09:00', 'America/New_York')).toBe('2026-07-04T13:00:00Z');
    expect(localToUtcIso('2026-01-04T09:00', 'America/New_York')).toBe('2026-01-04T14:00:00Z');
  });

  it('backfills a stored UTC instant into the saved timezone for rescheduling', () => {
    expect(utcToLocalInput('2026-08-20T01:00:00Z', 'Asia/Singapore')).toBe('2026-08-20T09:00');
    expect(utcToLocalInput('2026-07-04T13:00:00Z', 'America/New_York')).toBe('2026-07-04T09:00');
  });

  it('round-trips a local value through UTC and back', () => {
    const utc = localToUtcIso('2026-08-20T09:00', 'Asia/Singapore');
    expect(utcToLocalInput(utc!, 'Asia/Singapore')).toBe('2026-08-20T09:00');
  });

  it('rejects malformed or empty local values', () => {
    expect(localToUtcIso('', 'Asia/Singapore')).toBeNull();
    expect(localToUtcIso('not-a-date', 'Asia/Singapore')).toBeNull();
  });

  it('returns an empty string for an invalid stored instant', () => {
    expect(utcToLocalInput('garbage', 'Asia/Singapore')).toBe('');
  });

  it('returns null for an invalid timezone instead of throwing', () => {
    expect(localToUtcIso('2026-08-20T09:00', 'Not/AZone')).toBeNull();
    expect(localToUtcIso('2026-08-20T09:00', '')).toBeNull();
  });

  it('returns an empty string for an invalid timezone when backfilling', () => {
    expect(utcToLocalInput('2026-08-20T01:00:00Z', 'Not/AZone')).toBe('');
  });

  it('validates IANA timezone strings', () => {
    expect(isValidTimeZone('Asia/Singapore')).toBe(true);
    expect(isValidTimeZone('America/New_York')).toBe(true);
    expect(isValidTimeZone('Not/AZone')).toBe(false);
    expect(isValidTimeZone('')).toBe(false);
  });
});
