package com.adproject.candidate

import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.AttachmentUpload
import com.adproject.candidate.data.api.CandidateConversationHttpApi
import com.adproject.candidate.data.api.RealCandidateConversationRepository
import com.adproject.candidate.data.contract.ReadStateRequest
import com.adproject.candidate.data.contract.SendMessageRequest
import com.adproject.candidate.data.contract.SenderType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class CandidateConversationRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var moshi: Moshi
    private lateinit var repository: RealCandidateConversationRepository

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        repository = RealCandidateConversationRepository(
            retrofit().create(CandidateConversationHttpApi::class.java), moshi,
        )
    }

    @After fun tearDown() = server.shutdown()

    @Test fun debugDefaultApiUrlPointsAtLocalEmulator() {
        if (BuildConfig.DEBUG) {
            assertEquals("http://10.0.2.2:8081/api/v1/", BuildConfig.API_BASE_URL)
        }
    }

    @Test fun conversationsListUsesEnvelopeAndExposesSummaries() = runTest {
        server.enqueue(jsonResponse("""{"data":[${conversationSummary()}],"meta":{"page":1,"pageSize":20,"total":1,"hasNext":false}}"""))
        val result = repository.conversations() as ApiResult.Success
        assertEquals("conv-1", result.value.conversations.single().conversationId)
        assertEquals(2, result.value.conversations.single().unreadCount)
        assertEquals("Backend Engineer", result.value.conversations.single().jobTitle)
        assertEquals("/api/v1/candidate/conversations", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test fun conversationDetailAndMessagesUseCursorMeta() = runTest {
        server.enqueue(jsonResponse("""{"data":${conversationDetail()}}"""))
        val detail = repository.conversation("conv-1") as ApiResult.Success
        assertEquals("Mia Chen", detail.value.participant.fullName)
        assertNull(detail.value.context)
        assertEquals("/api/v1/candidate/conversations/conv-1", server.takeRequest().requestUrl!!.encodedPath)

        server.enqueue(jsonResponse("""{"data":[${message("msg-1")},${message("msg-2")}],"meta":{"nextCursor":"cursor-2","hasMore":false}}"""))
        val messages = repository.messages("conv-1", "cursor-0") as ApiResult.Success
        assertEquals(listOf("msg-1", "msg-2"), messages.value.messages.map { it.messageId })
        assertFalse(messages.value.meta.hasMore)
        val request = server.takeRequest()
        assertEquals("/api/v1/candidate/conversations/conv-1/messages", request.requestUrl!!.encodedPath)
        assertEquals("cursor-0", request.requestUrl!!.queryParameter("before"))
    }

    @Test fun sendMessageUsesHeaderAndBodyWithDualIdempotencyIds() = runTest {
        server.enqueue(jsonResponse("""{"data":${message("msg-3", clientMessageId = "client-id-1")}}"""))
        val key = "550e8400-e29b-41d4-a716-446655440000"
        val result = repository.sendMessage(
            "conv-1", key, SendMessageRequest("Hello there", "client-id-1"),
        ) as ApiResult.Success
        assertEquals("msg-3", result.value.messageId)
        assertEquals("client-id-1", result.value.clientMessageId)
        assertEquals(SenderType.CANDIDATE, result.value.senderType)
        val request = server.takeRequest()
        assertEquals("/api/v1/candidate/conversations/conv-1/messages", request.requestUrl!!.encodedPath)
        assertEquals(key, request.getHeader("Idempotency-Key"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"body\":\"Hello there\""))
        assertTrue(body.contains("\"clientMessageId\":\"client-id-1\""))
    }

    @Test fun sendFailureMapsIdempotencyReuseAndClosedConversation() = runTest {
        server.enqueue(jsonResponse(errorBody("IDEMPOTENCY_KEY_REUSED", "internal idempotency detail"), 409))
        val reused = repository.sendMessage("conv-1", "k", SendMessageRequest("Hi", "c1")) as ApiResult.Failure
        assertEquals("This message could not be retried safely. Please send a new message.", reused.message)
        assertFalse(reused.message.contains("internal"))
        server.takeRequest()

        server.enqueue(jsonResponse(errorBody("CONVERSATION_CLOSED", "internal closed detail"), 409))
        val closed = repository.sendMessage("conv-1", "k", SendMessageRequest("Hi", "c2")) as ApiResult.Failure
        assertEquals("This conversation is read-only.", closed.message)
        assertFalse(closed.message.contains("internal"))
    }

    @Test fun sendMessageWithAttachmentUsesMultipartForm() = runTest {
        server.enqueue(jsonResponse("""{"data":${message("msg-4", attachment = true)}}"""))
        val key = "550e8400-e29b-41d4-a716-446655440000"
        val result = repository.sendMessageWithAttachment(
            "conv-1", key,
            AttachmentUpload("client-id-2", "Please review", "resume.pdf", "application/pdf", "%PDF-1.4".toByteArray()),
        ) as ApiResult.Success
        assertEquals("msg-4", result.value.messageId)
        assertEquals("resume.pdf", result.value.attachment?.fileName)
        val request = server.takeRequest()
        assertEquals("/api/v1/candidate/conversations/conv-1/messages/attachment", request.requestUrl!!.encodedPath)
        assertEquals(key, request.getHeader("Idempotency-Key"))
        val body = request.body.readUtf8().lowercase()
        assertTrue(body.contains("name=\"clientmessageid\""))
        assertTrue(body.contains("client-id-2"))
        assertTrue(body.contains("name=\"body\""))
        assertTrue(body.contains("please review"))
        assertTrue(body.contains("filename=\"resume.pdf\""))
    }

    @Test fun downloadAttachmentReturnsBytesAndContentType() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/pdf").setBody("%PDF-1.4"))
        val result = repository.downloadAttachment("conv-1", "msg-1") as ApiResult.Success
        assertEquals("application/pdf", result.value.contentType)
        assertEquals("%PDF-1.4", result.value.bytes.toString(Charsets.UTF_8))
        val request = server.takeRequest()
        assertEquals("/api/v1/candidate/conversations/conv-1/messages/msg-1/attachment", request.requestUrl!!.encodedPath)
        assertEquals("GET", request.method)
    }

    @Test fun markReadPutsLastReadMessageId() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repository.markRead("conv-1", ReadStateRequest("msg-9")) is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("/api/v1/candidate/conversations/conv-1/read-state", request.requestUrl!!.encodedPath)
        assertEquals("PUT", request.method)
        assertTrue(request.body.readUtf8().contains("\"lastReadMessageId\":\"msg-9\""))
    }

    @Test fun conversation404UsesConversationSpecificMessage() = runTest {
        server.enqueue(jsonResponse(errorBody("NOT_FOUND", "Conversation not found"), 404))
        val missing = repository.conversation("missing") as ApiResult.Failure
        assertEquals(404, missing.statusCode)
        assertEquals("This conversation is no longer available.", missing.message)
    }

    private fun retrofit(): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/api/v1/"))
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private fun jsonResponse(body: String, code: Int = 200) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body)

    private fun errorBody(code: String, message: String): String =
        """{"error":{"code":"$code","message":"$message","fieldErrors":{},"requestId":"req-test"}}"""

    private fun conversationSummary() = """
        {"conversationId":"conv-1","applicationId":"app-1","jobId":"job-1",
        "createdAt":"2026-08-11T08:00:00Z","updatedAt":"2026-08-11T09:00:00Z",
        "participant":${participant()},
        "lastMessage":${message("msg-2")},
        "unreadCount":2,"jobTitle":"Backend Engineer"}
    """.trimIndent()

    private fun conversationDetail() = """
        {"conversationId":"conv-1","applicationId":"app-1","jobId":"job-1",
        "createdAt":"2026-08-11T08:00:00Z","updatedAt":"2026-08-11T09:00:00Z",
        "participant":${participant()},"context":null}
    """.trimIndent()

    private fun participant() = """
        {"userId":"rec-1","fullName":"Mia Chen","avatarUrl":null,"title":"Hiring Manager",
        "company":null,"online":true}
    """.trimIndent()

    private fun message(id: String, clientMessageId: String? = null, attachment: Boolean = false) = """
        {"messageId":"$id","conversationId":"conv-1","body":"Hello","senderType":"CANDIDATE",
        "sentAt":"2026-08-11T08:00:00Z","clientMessageId":${if (clientMessageId == null) "null" else "\"$clientMessageId\""},
        "deliveryStatus":"SENT","attachment":${if (attachment) attachmentMeta() else "null"}}
    """.trimIndent()

    private fun attachmentMeta() =
        """{"attachmentId":"att-1","fileName":"resume.pdf","sizeBytes":1024,"contentType":"application/pdf"}"""
}
