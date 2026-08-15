// Converts between a recruiter's local wall-clock `datetime-local` value and the
// UTC ISO instant the API stores. The given `timeZone` is a browser IANA zone
// such as "Asia/Singapore"; the conversion matches `Intl.DateTimeFormat`, so the
// submitted time is always the instant the recruiter actually picked.

const LOCAL_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/;

export function resolvedTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

// True when `timeZone` is a zone `Intl.DateTimeFormat` accepts. Constructing a formatter
// with an unknown zone throws a RangeError, so this is the guard every conversion uses to
// fail soft instead of crashing the page.
export function isValidTimeZone(timeZone: string): boolean {
  if (!timeZone) return false;
  try {
    new Intl.DateTimeFormat('en-US', {timeZone});
    return true;
  } catch {
    return false;
  }
}

// "2026-08-20T09:00" in `timeZone` -> "2026-08-20T01:00:00Z" (Asia/Singapore is UTC+8).
// Returns null when the input is not a valid `datetime-local` value or the zone is invalid.
export function localToUtcIso(localValue: string, timeZone: string): string | null {
  if (!isValidTimeZone(timeZone)) return null;
  const match = LOCAL_PATTERN.exec(localValue.trim());
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const hour = Number(match[4]);
  const minute = Number(match[5]);
  const second = Number(match[6] ?? '0');
  const guess = Date.UTC(year, month - 1, day, hour, minute, second);
  // Resolve the offset twice so ambiguous or nonexistent DST wall times settle correctly.
  const offset = timeZoneOffsetMs(guess, timeZone);
  const offset2 = timeZoneOffsetMs(guess - offset, timeZone);
  return new Date(guess - offset2).toISOString().replace('.000Z', 'Z');
}

// A stored UTC ISO instant -> the local `datetime-local` value in `timeZone`.
// Returns '' when the instant is invalid or the zone is invalid, so callers can fall back.
export function utcToLocalInput(iso: string, timeZone: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  if (!isValidTimeZone(timeZone)) return '';
  const parts = formatParts(date, timeZone);
  const hour = String(Number(parts.hour) % 24).padStart(2, '0');
  return `${parts.year}-${parts.month}-${parts.day}T${hour}:${parts.minute}`;
}

function timeZoneOffsetMs(utcMs: number, timeZone: string): number {
  const parts = formatParts(new Date(utcMs), timeZone);
  const asUtc = Date.UTC(
    Number(parts.year),
    Number(parts.month) - 1,
    Number(parts.day),
    Number(parts.hour) % 24,
    Number(parts.minute),
    Number(parts.second),
  );
  return asUtc - utcMs;
}

function formatParts(date: Date, timeZone: string): {year: string; month: string; day: string;
  hour: string; minute: string; second: string} {
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
  const values: Record<string, string> = {};
  for (const part of formatter.formatToParts(date)) values[part.type] = part.value;
  return {
    year: values.year,
    month: values.month,
    day: values.day,
    hour: values.hour,
    minute: values.minute,
    second: values.second,
  };
}
