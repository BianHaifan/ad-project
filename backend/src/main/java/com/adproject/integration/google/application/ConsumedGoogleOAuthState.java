package com.adproject.integration.google.application;

/**
 * The outcome of consuming an OAuth state exactly once: the recruiter the state
 * was bound to, plus the still-encrypted PKCE verifier. The verifier is
 * decrypted by the caller only after the consumption has been committed.
 */
public record ConsumedGoogleOAuthState(String recruiterId, String pkceVerifierEncrypted) {}
