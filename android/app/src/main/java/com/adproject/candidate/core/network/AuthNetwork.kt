package com.adproject.candidate.core.network

import com.adproject.candidate.core.auth.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class AccessTokenInterceptor(private val sessionManager: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val tokens = runBlocking { sessionManager.tokens() }
        val request = if (tokens == null) chain.request() else chain.request().newBuilder()
            .header("Authorization", "Bearer ${tokens.accessToken}")
            .build()
        return chain.proceed(request)
    }
}

class RefreshAuthenticator(private val sessionManager: SessionManager) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val failed = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null
        val replacement = runBlocking { sessionManager.refreshAfterUnauthorized(failed) } ?: return null
        return response.request.newBuilder().header("Authorization", "Bearer $replacement").build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count += 1
            prior = prior.priorResponse
        }
        return count
    }
}
