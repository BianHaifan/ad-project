package com.adproject.candidate

import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.AttachmentUpload
import com.adproject.candidate.data.api.CandidateConversationRepository
import com.adproject.candidate.data.api.ConversationListResult
import com.adproject.candidate.data.api.DownloadedAttachment
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
import com.adproject.candidate.feature.messages.PollSchedule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesPollingTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun pollDelayEscalatesAndCaps() {
        assertEquals(3_000L, PollSchedule.delayAfter(0, 3_000L))
        assertEquals(3_000L, PollSchedule.delayAfter(1, 3_000L))
        assertEquals(10_000L, PollSchedule.delayAfter(2, 3_000L))
        assertEquals(30_000L, PollSchedule.delayAfter(3, 3_000L))
        assertEquals(30_000L, PollSchedule.delayAfter(99, 3_000L))
        assertEquals(1_000L, PollSchedule.delayAfter(0, 1_000L))
    }

    @Test fun listPollsEveryThreeSecondsWhileVisible() = runTest(main.dispatcher) {
        val repository = CountingConversationRepository(listResult = successList("c1"))
        val viewModel = MessagesViewModel(repository)
        runCurrent() // initial load (no polling loop yet)
        assertEquals(1, repository.listCalls)

        viewModel.onScreenStarted()
        try {
            runCurrent() // immediate refresh on becoming visible
            assertEquals(2, repository.listCalls)

            advanceTimeBy(3_000); runCurrent() // tick #1
            assertEquals(3, repository.listCalls)
            advanceTimeBy(3_000); runCurrent() // tick #2
            assertEquals(4, repository.listCalls)
        } finally {
            viewModel.onScreenStopped()
        }
        runCurrent()
    }

    @Test fun chatPollsEverySecondWhileVisible() = runTest(main.dispatcher) {
        val repository = CountingConversationRepository(detailResult = successDetail(), messageResult = successMessages("m1"))
        val viewModel = ChatViewModel("conv-1", repository)
        runCurrent() // initial load: detail + messages + markRead
        assertEquals(1, repository.detailCalls)
        assertEquals(1, repository.messageCalls)

        viewModel.onScreenStarted()
        try {
            runCurrent() // immediate refresh
            assertEquals(2, repository.detailCalls)
            assertEquals(2, repository.messageCalls)

            advanceTimeBy(1_000); runCurrent() // tick #1
            assertEquals(3, repository.detailCalls)
            assertEquals(3, repository.messageCalls)
        } finally {
            viewModel.onScreenStopped()
        }
        runCurrent()
    }

    @Test fun pollingStopsWhenHiddenAndResumesWithImmediateRefresh() = runTest(main.dispatcher) {
        val repository = CountingConversationRepository(listResult = successList("c1"))
        val viewModel = MessagesViewModel(repository)
        runCurrent()
        viewModel.onScreenStarted()
        runCurrent()
        val calls = repository.listCalls

        viewModel.onScreenStopped()
        try {
            advanceTimeBy(15_000) // no polling while hidden
            assertEquals(calls, repository.listCalls)

            viewModel.onScreenStarted()
            runCurrent() // immediate refresh on return
            assertEquals(calls + 1, repository.listCalls)
        } finally {
            viewModel.onScreenStopped()
        }
        runCurrent()
    }

    @Test fun pollSkipsTickWhileRequestInFlight() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = CountingConversationRepository(listResult = successList("c1"))
        val viewModel = MessagesViewModel(repository)
        runCurrent() // initial load
        viewModel.onScreenStarted()
        try {
            runCurrent() // first poll settles at delay(3s)
            assertEquals(2, repository.listCalls)

            repository.listGate = gate
            viewModel.refresh() // separate coroutine, now in-flight and blocked
            runCurrent()
            assertEquals(3, repository.listCalls)

            advanceTimeBy(3_000); runCurrent() // tick fires but skips (request in flight)
            assertEquals(3, repository.listCalls)

            gate.complete(Unit)
            runCurrent() // refresh completes, in-flight clears
            assertEquals(3, repository.listCalls)

            advanceTimeBy(3_000); runCurrent() // next tick proceeds normally
            assertEquals(4, repository.listCalls)
        } finally {
            viewModel.onScreenStopped()
        }
        runCurrent()
    }

    @Test fun consecutiveFailuresEscalateAndResetOnSuccess() = runTest(main.dispatcher) {
        val repository = CountingConversationRepository(detailResult = successDetail(), messageResult = successMessages("m1"))
        val viewModel = ChatViewModel("conv-1", repository)
        runCurrent() // initial load success
        viewModel.onScreenStarted()
        try {
            runCurrent() // first poll success
            assertEquals(2, repository.detailCalls)

            repository.detailResult = ApiResult.Failure("Network unavailable")
            advanceTimeBy(1_000); runCurrent() // base 1s -> fail (failures=1)
            assertEquals(3, repository.detailCalls)
            advanceTimeBy(3_000); runCurrent() // backoff 3s -> fail (failures=2)
            assertEquals(4, repository.detailCalls)
            advanceTimeBy(10_000); runCurrent() // backoff 10s -> fail (failures=3)
            assertEquals(5, repository.detailCalls)
            advanceTimeBy(30_000); runCurrent() // backoff 30s -> fail (failures=4)
            assertEquals(6, repository.detailCalls)

            repository.detailResult = successDetail()
            advanceTimeBy(30_000); runCurrent() // capped at 30s -> success, resets
            assertEquals(7, repository.detailCalls)
            advanceTimeBy(1_000); runCurrent() // reset to base 1s
            assertEquals(8, repository.detailCalls)
        } finally {
            viewModel.onScreenStopped()
        }
        runCurrent()
    }
}

private class CountingConversationRepository(
    var listResult: ApiResult<ConversationListResult> = ApiResult.Success(ConversationListResult(emptyList(), PageMeta(1, 20, 0, false))),
    var detailResult: ApiResult<ConversationDetail> = ApiResult.Failure("detail", statusCode = 404),
    var messageResult: ApiResult<MessageListResult> = ApiResult.Success(MessageListResult(emptyList(), CursorMeta(null, false))),
    var listGate: CompletableDeferred<Unit>? = null,
) : CandidateConversationRepository {
    var listCalls = 0; private set
    var detailCalls = 0; private set
    var messageCalls = 0; private set

    override suspend fun conversations(): ApiResult<ConversationListResult> {
        listCalls++
        listGate?.await()
        return listResult
    }

    override suspend fun conversation(conversationId: String): ApiResult<ConversationDetail> {
        detailCalls++
        return detailResult
    }

    override suspend fun messages(conversationId: String, before: String?): ApiResult<MessageListResult> {
        messageCalls++
        return messageResult
    }

    override suspend fun sendMessage(conversationId: String, idempotencyKey: String, request: SendMessageRequest): ApiResult<Message> =
        throw UnsupportedOperationException("not used by polling tests")

    override suspend fun sendMessageWithAttachment(conversationId: String, idempotencyKey: String, request: AttachmentUpload): ApiResult<Message> =
        throw UnsupportedOperationException("not used by polling tests")

    override suspend fun downloadAttachment(conversationId: String, messageId: String): ApiResult<DownloadedAttachment> =
        throw UnsupportedOperationException("not used by polling tests")

    override suspend fun markRead(conversationId: String, request: ReadStateRequest): ApiResult<Unit> =
        ApiResult.Success(Unit)
}

private const val NOW = "2026-08-11T08:00:00Z"
private fun participant() = ConversationParticipant("rec-1", "Mia Chen", null, "Hiring Manager", null, true)
private fun summary(id: String) = ConversationSummary(id, "app-1", "job-1", NOW, NOW, participant(), null, 0, "Backend Engineer")
private fun detail() = ConversationDetail("conv-1", "app-1", "job-1", NOW, NOW, participant(), null)
private fun message(id: String) = Message(id, "conv-1", "Hello", SenderType.CANDIDATE, NOW, null, DeliveryStatus.SENT)
private fun successList(id: String) = ApiResult.Success(ConversationListResult(listOf(summary(id)), PageMeta(1, 20, 1, false)))
private fun successDetail() = ApiResult.Success(detail())
private fun successMessages(id: String) = ApiResult.Success(MessageListResult(listOf(message(id)), CursorMeta(null, false)))
