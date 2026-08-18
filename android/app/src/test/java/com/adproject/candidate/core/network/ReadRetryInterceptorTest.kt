package com.adproject.candidate.core.network

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadRetryInterceptorTest {
    @Test fun `retries a transient get response once`() {
        val chain = FakeChain("GET", mutableListOf(response(503), response(200)))
        val result = ReadRetryInterceptor().intercept(chain)
        assertEquals(200, result.code)
        assertEquals(2, chain.calls)
    }

    @Test fun `does not retry mutation requests`() {
        val chain = FakeChain("POST", mutableListOf(response(503), response(200)))
        val result = ReadRetryInterceptor().intercept(chain)
        assertEquals(503, result.code)
        assertEquals(1, chain.calls)
    }

    @Test fun `never exceeds one retry`() {
        val chain = FakeChain("GET", mutableListOf(response(503), response(503), response(200)))
        val result = ReadRetryInterceptor().intercept(chain)
        assertEquals(503, result.code)
        assertEquals(2, chain.calls)
    }

    private fun response(code: Int) = code

    private class FakeChain(method: String, private val responses: MutableList<Int>) : okhttp3.Interceptor.Chain {
        private val value = Request.Builder().url("https://example.test/read")
            .method(method, if (method == "GET") null else "".toRequestBody()).build()
        var calls = 0
        override fun request() = value
        override fun proceed(request: Request): Response {
            calls++
            val code = responses.removeFirstOrNull() ?: throw IOException("no response")
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code)
                .message(code.toString()).body("".toResponseBody("text/plain".toMediaType())).build()
        }
        override fun connection() = null
        override fun call(): okhttp3.Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis() = 1_000
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun readTimeoutMillis() = 1_000
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun writeTimeoutMillis() = 1_000
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    }
}
