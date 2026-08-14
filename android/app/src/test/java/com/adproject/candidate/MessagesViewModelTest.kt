package com.adproject.candidate

import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.CandidateConversationRepository
import com.adproject.candidate.data.api.ConversationListResult
import com.adproject.candidate.data.api.MessageListResult
import com.adproject.candidate.data.contract.ConversationDetail
import com.adproject.candidate.data.contract.ConversationParticipant
import com.adproject.candidate.data.contract.ConversationSummary
import com.adproject.candidate.data.contract.CursorMeta
import com.adproject.candidate.data.contract.DeliveryStatus
import com.adproject.candidate.data.contract.Message
import com.adproject.candidate.data.contract.PageMeta
import com.adproject.candidate.data.contract.ReadStateRequest
import com.adproject.candidate.data.contract.SendMessageRequest
import com.adproject.candidate.data.contract.SenderType
import com.adproject.candidate.feature.messages.ChatViewModel
import com.adproject.candidate.feature.messages.MessagesViewModel
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun messagesListCoversContentEmptyErrorAndRetry() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(listResults = mutableListOf(
            ApiResult.Success(ConversationListResult(listOf(summary("c1")), PageMeta(1, 20, 1, false))),
            ApiResult.Success(ConversationListResult(emptyList(), PageMeta(1, 20, 0, false))),
            ApiResult.Failure("Network unavailable"),
        ))
        val viewModel = MessagesViewModel(repository)
        advanceUntilIdle()
        assertEquals(listOf("c1"), viewModel.state.value.conversations.map { it.conversationId })
        assertFalse(viewModel.state.value.loading)
        viewModel.refresh(); advanceUntilIdle()
        assertTrue(viewModel.state.value.conversations.isEmpty())
        viewModel.retry(); advanceUntilIdle()
        assertEquals("Network unavailable", viewModel.state.value.message)
    }

    @Test fun chatLoadsConversationMessagesAndMarksRead() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(
            detailResults = mutableListOf(ApiResult.Success(detail())),
            messageResults = mutableListOf(ApiResult.Success(MessageListResult(listOf(message("m1")), CursorMeta(null, false)))),
        )
        val viewModel = ChatViewModel("conv-1", repository)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.loading)
        assertEquals("Mia Chen", viewModel.state.value.conversation?.participant?.fullName)
        assertEquals(listOf("m1"), viewModel.state.value.messages.map { it.messageId })
        assertEquals(1, repository.markReadCalls.size)
        assertEquals("conv-1", repository.markReadCalls.single().first)
        assertEquals("m1", repository.markReadCalls.single().second.lastReadMessageId)
    }

    @Test fun chatDetail404IsExplicit() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(
            detailResults = mutableListOf(ApiResult.Failure("gone", statusCode = 404)),
        )
        val viewModel = ChatViewModel("missing", repository)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.notFound)
        assertNull(viewModel.state.value.conversation)
    }

    @Test fun sendSuccessAppendsAndClearsDraftWithDualUuids() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(
            detailResults = mutableListOf(ApiResult.Success(detail())),
            messageResults = mutableListOf(ApiResult.Success(MessageListResult(emptyList(), CursorMeta(null, false)))),
            sendResults = mutableListOf(ApiResult.Success(message("m-new"))),
        )
        val viewModel = ChatViewModel("conv-1", repository)
        advanceUntilIdle()
        viewModel.updateDraft("Hi there")
        viewModel.send(); advanceUntilIdle()
        assertEquals(listOf("m-new"), viewModel.state.value.messages.map { it.messageId })
        assertEquals("", viewModel.state.value.draft)
        assertFalse(viewModel.state.value.sending)
        val (_, key, request) = repository.sendCalls.single()
        assertNotNull(UUID.fromString(key))
        assertNotNull(UUID.fromString(request.clientMessageId))
        assertEquals("Hi there", request.body)
    }

    @Test fun sendFailurePreservesDraftAndRetryReusesSameKey() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(
            detailResults = mutableListOf(ApiResult.Success(detail())),
            messageResults = mutableListOf(ApiResult.Success(MessageListResult(emptyList(), CursorMeta(null, false)))),
            sendResults = mutableListOf(ApiResult.Failure("Network unavailable"), ApiResult.Success(message("m-new"))),
        )
        val viewModel = ChatViewModel("conv-1", repository)
        advanceUntilIdle()
        viewModel.updateDraft("Retry me")
        viewModel.send(); advanceUntilIdle()
        assertTrue(viewModel.state.value.messages.isEmpty())
        assertEquals("Retry me", viewModel.state.value.draft)
        assertEquals("Network unavailable", viewModel.state.value.message)
        viewModel.send(); advanceUntilIdle()
        assertEquals(2, repository.sendCalls.size)
        assertEquals(repository.sendCalls[0].second, repository.sendCalls[1].second)
        assertEquals(listOf("m-new"), viewModel.state.value.messages.map { it.messageId })
        assertEquals("", viewModel.state.value.draft)
    }
}

private class QueueConversationRepository(
    val listResults: MutableList<ApiResult<ConversationListResult>> = mutableListOf(),
    val detailResults: MutableList<ApiResult<ConversationDetail>> = mutableListOf(),
    val messageResults: MutableList<ApiResult<MessageListResult>> = mutableListOf(),
    val sendResults: MutableList<ApiResult<Message>> = mutableListOf(),
) : CandidateConversationRepository {
    val markReadCalls = mutableListOf<Pair<String, ReadStateRequest>>()
    val sendCalls = mutableListOf<Triple<String, String, SendMessageRequest>>()

    override suspend fun conversations() = listResults.removeFirst()
    override suspend fun conversation(conversationId: String) = detailResults.removeFirst()
    override suspend fun messages(conversationId: String, before: String?) = messageResults.removeFirst()
    override suspend fun sendMessage(conversationId: String, idempotencyKey: String, request: SendMessageRequest): ApiResult<Message> {
        sendCalls += Triple(conversationId, idempotencyKey, request)
        return sendResults.removeFirst()
    }
    override suspend fun markRead(conversationId: String, request: ReadStateRequest): ApiResult<Unit> {
        markReadCalls += conversationId to request
        return ApiResult.Success(Unit)
    }
}

private const val NOW = "2026-08-11T08:00:00Z"
private fun participant() = ConversationParticipant("rec-1", "Mia Chen", null, "Hiring Manager", null, true)
private fun summary(id: String) = ConversationSummary(id, "app-1", "job-1", NOW, NOW, participant(), null, 0, "Backend Engineer")
private fun detail() = ConversationDetail("conv-1", "app-1", "job-1", NOW, NOW, participant(), null)
private fun message(id: String) = Message(id, "conv-1", "Hello", SenderType.CANDIDATE, NOW, null, DeliveryStatus.SENT)
