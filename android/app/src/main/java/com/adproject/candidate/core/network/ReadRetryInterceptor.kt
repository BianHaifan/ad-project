package com.adproject.candidate.core.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Retries a read request at most once; mutation requests are never replayed here. */
class ReadRetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)

        val first = try {
            chain.proceed(request)
        } catch (_: IOException) {
            return chain.proceed(request)
        }
        if (first.code !in RETRYABLE_STATUS_CODES) return first
        first.close()
        return chain.proceed(request)
    }

    private companion object {
        val RETRYABLE_STATUS_CODES = setOf(502, 503, 504)
    }
}
