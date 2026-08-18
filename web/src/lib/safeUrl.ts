export function sanitizePreviewUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  if (url.startsWith('blob:')) return url;
  if (url.startsWith('/')) return url;
  try {
    const parsed = new URL(url);
    if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
      return parsed.href;
    }
  } catch {
    return null;
  }
  return null;
}