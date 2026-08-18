export function sanitizePreviewUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  if (!isSafePreviewUrl(url)) return null;
  return encodeURI(url);
}

function isSafePreviewUrl(url: string): boolean {
  if (url.startsWith('blob:') || url.startsWith('/')) return true;
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch {
    return false;
  }
}