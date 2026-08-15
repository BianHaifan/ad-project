package com.adproject.integration.google.application;

/**
 * Port for the single real Google HTTP exchange needed by this package: the
 * authorization-code-for-token exchange. Tests substitute a fake so no test
 * ever contacts Google.
 */
public interface GoogleOAuthClient {

    /**
     * Exchanges an authorization code for tokens using the fixed Google token
     * endpoint. The client secret is supplied from local configuration only and
     * is never logged.
     *
     * @throws GoogleOAuthTokenExchangeException when Google rejects the exchange
     *         or the response is missing the access or refresh token
     */
    TokenExchangeResult exchangeAuthorizationCode(String code, String redirectUri, String codeVerifier);
}
