import {describe, expect, it} from 'vitest';
import {sanitizePreviewUrl} from './safeUrl';

describe('sanitizePreviewUrl', () => {
  it('returns null for empty values', () => {
    expect(sanitizePreviewUrl(null)).toBeNull();
    expect(sanitizePreviewUrl(undefined)).toBeNull();
    expect(sanitizePreviewUrl('')).toBeNull();
  });

  it('passes through blob: URLs unchanged', () => {
    expect(sanitizePreviewUrl('blob:http://localhost:8080/abc-123')).toBe('blob:http://localhost:8080/abc-123');
  });

  it('passes through relative paths unchanged', () => {
    expect(sanitizePreviewUrl('/api/v1/avatars/rec-1')).toBe('/api/v1/avatars/rec-1');
  });

  it('passes through http and https URLs', () => {
    expect(sanitizePreviewUrl('http://cdn.example.com/a.png')).toBe('http://cdn.example.com/a.png');
    expect(sanitizePreviewUrl('https://cdn.example.com/a.png')).toBe('https://cdn.example.com/a.png');
  });

  it('rejects javascript and data URLs', () => {
    expect(sanitizePreviewUrl('javascript:alert(1)')).toBeNull();
    expect(sanitizePreviewUrl('data:text/html;base64,PHNjcmlwdD4=')).toBeNull();
  });

  it('rejects malformed URLs', () => {
    expect(sanitizePreviewUrl('not a url')).toBeNull();
  });
});