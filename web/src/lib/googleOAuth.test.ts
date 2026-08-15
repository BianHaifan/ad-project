import {describe, expect, it} from 'vitest';
import {isGoogleAuthorizationUrl, parseOAuthCallbackResult} from './googleOAuth';

describe('parseOAuthCallbackResult', () => {
  it('accepts the three safe callback results', () => {
    expect(parseOAuthCallbackResult('connected')).toBe('connected');
    expect(parseOAuthCallbackResult('denied')).toBe('denied');
    expect(parseOAuthCallbackResult('failed')).toBe('failed');
  });

  it('ignores unknown, empty, and missing values', () => {
    expect(parseOAuthCallbackResult('success')).toBeNull();
    expect(parseOAuthCallbackResult('CONNECTED')).toBeNull();
    expect(parseOAuthCallbackResult('')).toBeNull();
    expect(parseOAuthCallbackResult(null)).toBeNull();
    expect(parseOAuthCallbackResult(undefined)).toBeNull();
  });
});

describe('isGoogleAuthorizationUrl', () => {
  it('accepts the fixed Google authorization endpoint', () => {
    expect(isGoogleAuthorizationUrl('https://accounts.google.com/o/oauth2/v2/auth')).toBe(true);
    expect(isGoogleAuthorizationUrl('https://accounts.google.com/o/oauth2/v2/auth?client_id=abc&scope=calendar')).toBe(true);
  });

  it('rejects a non-HTTPS scheme, wrong host, and wrong path', () => {
    expect(isGoogleAuthorizationUrl('http://accounts.google.com/o/oauth2/v2/auth')).toBe(false);
    expect(isGoogleAuthorizationUrl('https://evil.example.com/o/oauth2/v2/auth')).toBe(false);
    expect(isGoogleAuthorizationUrl('https://accounts.google.com.evil.com/o/oauth2/v2/auth')).toBe(false);
    expect(isGoogleAuthorizationUrl('https://accounts.google.com/other/path')).toBe(false);
    expect(isGoogleAuthorizationUrl('https://accounts.google.com/o/oauth2/v2/auth/extra')).toBe(false);
  });

  it('rejects a non-default port while accepting the default HTTPS port', () => {
    expect(isGoogleAuthorizationUrl('https://accounts.google.com:444/o/oauth2/v2/auth')).toBe(false);
    expect(isGoogleAuthorizationUrl('https://accounts.google.com:443/o/oauth2/v2/auth')).toBe(true);
  });

  it('rejects malformed and empty input', () => {
    expect(isGoogleAuthorizationUrl('')).toBe(false);
    expect(isGoogleAuthorizationUrl('not a url')).toBe(false);
    expect(isGoogleAuthorizationUrl('javascript:alert(1)')).toBe(false);
  });
});
