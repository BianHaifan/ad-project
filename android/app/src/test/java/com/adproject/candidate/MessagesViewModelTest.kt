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
import com.adproject.candidate.data.contract.MessageAttachment
import com.adproject.candidate.data.contract.PageMeta
import com.adproject.candidate.data.contract.ReadStateRequest
import com.adproject.candidate.data.contract.SendMessageRequest
import com.adproject.candidate.data.contract.SenderType
import com.adproject.candidate.feature.messages.ChatViewModel
import com.adproject.candidate.feature.messages.MessagesViewModel
import com.adproject.candidate.feature.messages.PendingAttachment
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

    @Test fun sendAttachmentOnlyClearsDraftAndAttachment() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(
            detailResults = mutableListOf(ApiResult.Success(detail())),
            messageResults = mutableListOf(ApiResult.Success(MessageListResult(emptyList(), CursorMeta(null, false)))),
            attachmentSendResults = mutableListOf(ApiResult.Success(message("m-att", attachment = attachmentMeta()))),
        )
        val viewModel = ChatViewModel("conv-1", repository)
        advanceUntilIdle()
        viewModel.selectAttachment(PendingAttachment("resume.pdf", "application/pdf", 1024L, byteArrayOf(1, 2, 3)))
        viewModel.send(); advanceUntilIdle()
        assertEquals(listOf("m-att"), viewModel.state.value.messages.map { it.messageId })
        assertEquals("", viewModel.state.value.draft)
        assertNull(viewModel.state.value.attachment)
        assertFalse(viewModel.state.value.sending)
        val (_, key, request) = repository.attachmentSendCalls.single()
        assertNotNull(UUID.fromString(key))
        assertNotNull(UUID.fromString(request.clientMessageId))
        assertNull(request.body)
        assertEquals("resume.pdf", request.fileName)
    }

    @Test fun sendAttachmentFailurePreservesAttachmentForRetry() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(
            detailResults = mutableListOf(ApiResult.Success(detail())),
            messageResults = mutableListOf(ApiResult.Success(MessageListResult(emptyList(), CursorMeta(null, false)))),
            attachmentSendResults = mutableListOf(ApiResult.Failure("File too large"), ApiResult.Success(message("m-att"))),
        )
        val viewModel = ChatViewModel("conv-1", repository)
        advanceUntilIdle()
        viewModel.selectAttachment(PendingAttachment("big.pdf", "application/pdf", 99L, byteArrayOf(1)))
        viewModel.send(); advanceUntilIdle()
        assertTrue(viewModel.state.value.messages.isEmpty())
        assertNotNull(viewModel.state.value.attachment)
        assertEquals("File too large", viewModel.state.value.message)
        viewModel.send(); advanceUntilIdle()
        assertEquals(listOf("m-att"), viewModel.state.value.messages.map { it.messageId })
        assertNull(viewModel.state.value.attachment)
    }

    @Test fun downloadEmitsEventWithFileNameAndContentType() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(
            detailResults = mutableListOf(ApiResult.Success(detail())),
            messageResults = mutableListOf(ApiResult.Success(MessageListResult(emptyList(), CursorMeta(null, false)))),
            downloadResults = mutableListOf(ApiResult.Success(DownloadedAttachment(byteArrayOf(9, 8, 7), "application/pdf"))),
        )
        val viewModel = ChatViewModel("conv-1", repository)
        advanceUntilIdle()
        viewModel.download(message("m-att", attachment = attachmentMeta("cover.pdf")))
        advanceUntilIdle()
        assertEquals("m-att", repository.downloadCalls.single().second)
        val event = viewModel.state.value.downloadEvent
        assertNotNull(event)
        assertEquals("cover.pdf", event!!.fileName)
        assertEquals("application/pdf", event.contentType)
        viewModel.consumeDownload()
        assertNull(viewModel.state.value.downloadEvent)
    }

    @Test fun downloadFailureShowsMessage() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(
            detailResults = mutableListOf(ApiResult.Success(detail())),
            messageResults = mutableListOf(ApiResult.Success(MessageListResult(emptyList(), CursorMeta(null, false)))),
            downloadResults = mutableListOf(ApiResult.Failure("Unable to download this attachment.")),
        )
        val viewModel = ChatViewModel("conv-1", repository)
        advanceUntilIdle()
        viewModel.download(message("m-att", attachment = attachmentMeta()))
        advanceUntilIdle()
        assertNull(viewModel.state.value.downloadEvent)
        assertNull(viewModel.state.value.downloadingMessageId)
        assertEquals("Unable to download this attachment.", viewModel.state.value.message)
    }

    @Test fun imageAttachmentDownloadEntersPreviewState() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(
            detailResults = mutableListOf(ApiResult.Success(detail())),
            messageResults = mutableListOf(ApiResult.Success(MessageListResult(emptyList(), CursorMeta(null, false)))),
            downloadResults = mutableListOf(ApiResult.Success(DownloadedAttachment(byteArrayOf(1, 2, 3), "image/png"))),
        )
        val viewModel = ChatViewModel("conv-1", repository)
        advanceUntilIdle()
        viewModel.download(message("m-img", attachment = attachmentMeta("photo.png", "image/png")))
        advanceUntilIdle()
        assertEquals("m-img", repository.downloadCalls.single().second)
        val preview = viewModel.state.value.imagePreview
        assertNotNull(preview)
        assertEquals("photo.png", preview!!.fileName)
        assertEquals("image/png", preview.contentType)
        assertTrue(preview.bytes.contentEquals(byteArrayOf(1, 2, 3)))
        assertNull(viewModel.state.value.downloadEvent)
    }

    @Test fun imageDownloadFailureShowsMessageWithoutPreview() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(
            detailResults = mutableListOf(ApiResult.Success(detail())),
            messageResults = mutableListOf(ApiResult.Success(MessageListResult(emptyList(), CursorMeta(null, false)))),
            downloadResults = mutableListOf(ApiResult.Failure("Unable to download this attachment.")),
        )
        val viewModel = ChatViewModel("conv-1", repository)
        advanceUntilIdle()
        viewModel.download(message("m-img", attachment = attachmentMeta("photo.png", "image/png")))
        advanceUntilIdle()
        assertNull(viewModel.state.value.imagePreview)
        assertNull(viewModel.state.value.downloadingMessageId)
        assertEquals("Unable to download this attachment.", viewModel.state.value.message)
    }

    @Test fun nonImageAttachmentStillEmitsExternalOpenEvent() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(
            detailResults = mutableListOf(ApiResult.Success(detail())),
            messageResults = mutableListOf(ApiResult.Success(MessageListResult(emptyList(), CursorMeta(null, false)))),
            downloadResults = mutableListOf(ApiResult.Success(DownloadedAttachment(byteArrayOf(9, 8, 7), "application/pdf"))),
        )
        val viewModel = ChatViewModel("conv-1", repository)
        advanceUntilIdle()
        viewModel.download(message("m-att", attachment = attachmentMeta("resume.pdf", "application/pdf")))
        advanceUntilIdle()
        assertNull(viewModel.state.value.imagePreview)
        val event = viewModel.state.value.downloadEvent
        assertNotNull(event)
        assertEquals("resume.pdf", event!!.fileName)
        assertEquals("application/pdf", event.contentType)
    }

    @Test fun closeImagePreviewClearsState() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(
            detailResults = mutableListOf(ApiResult.Success(detail())),
            messageResults = mutableListOf(ApiResult.Success(MessageListResult(emptyList(), CursorMeta(null, false)))),
            downloadResults = mutableListOf(ApiResult.Success(DownloadedAttachment(byteArrayOf(1, 2, 3), "image/png"))),
        )
        val viewModel = ChatViewModel("conv-1", repository)
        advanceUntilIdle()
        viewModel.download(message("m-img", attachment = attachmentMeta("photo.png", "image/png")))
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.imagePreview)
        viewModel.closeImagePreview()
        assertNull(viewModel.state.value.imagePreview)
    }

    @Test fun imageThumbnailsDownloadOnceAndOpenImageReusesCache() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(
            detailResults = mutableListOf(ApiResult.Success(detail()), ApiResult.Success(detail())),
            messageResults = mutableListOf(
                ApiResult.Success(MessageListResult(listOf(message("m-img", attachment = attachmentMeta("photo.png", "image/png"))), CursorMeta(null, false))),
                ApiResult.Success(MessageListResult(listOf(message("m-img", attachment = attachmentMeta("photo.png", "image/png"))), CursorMeta(null, false))),
            ),
            downloadResults = mutableListOf(ApiResult.Success(DownloadedAttachment(byteArrayOf(1, 2, 3), "image/png"))),
        )
        val viewModel = ChatViewModel("conv-1", repository)
        advanceUntilIdle()
        assertEquals(1, repository.downloadCalls.size)
        assertTrue(viewModel.state.value.imageThumbnails.containsKey("m-img"))
        assertTrue(viewModel.state.value.loadingThumbnails.isEmpty())

        // A subsequent poll (retry) must not re-download an already-loaded thumbnail.
        viewModel.retry(); advanceUntilIdle()
        assertEquals(1, repository.downloadCalls.size)

        // Opening the image reuses the cached thumbnail without a second download.
        viewModel.openImage(message("m-img", attachment = attachmentMeta("photo.png", "image/png")))
        advanceUntilIdle()
        assertEquals(1, repository.downloadCalls.size)
        assertNotNull(viewModel.state.value.imagePreview)
        assertEquals("photo.png", viewModel.state.value.imagePreview!!.fileName)
        assertTrue(viewModel.state.value.imagePreview!!.bytes.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test fun imageThumbnailFailureClearsLoadingStateForChipFallback() = runTest(main.dispatcher) {
        val repository = QueueConversationRepository(
            detailResults = mutableListOf(ApiResult.Success(detail())),
            messageResults = mutableListOf(
                ApiResult.Success(MessageListResult(listOf(message("m-img", attachment = attachmentMeta("photo.png", "image/png"))), CursorMeta(null, false))),
            ),
            downloadResults = mutableListOf(ApiResult.Failure("Unable to download this attachment.")),
        )
        val viewModel = ChatViewModel("conv-1", repository)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.imageThumbnails.isEmpty())
        assertTrue(viewModel.state.value.loadingThumbnails.isEmpty())
        assertNull(viewModel.state.value.imagePreview)
    }
}

private class QueueConversationRepository(
    val listResults: MutableList<ApiResult<ConversationListResult>> = mutableListOf(),
    val detailResults: MutableList<ApiResult<ConversationDetail>> = mutableListOf(),
    val messageResults: MutableList<ApiResult<MessageListResult>> = mutableListOf(),
    val sendResults: MutableList<ApiResult<Message>> = mutableListOf(),
    val attachmentSendResults: MutableList<ApiResult<Message>> = mutableListOf(),
    val downloadResults: MutableList<ApiResult<DownloadedAttachment>> = mutableListOf(),
) : CandidateConversationRepository {
    val markReadCalls = mutableListOf<Pair<String, ReadStateRequest>>()
    val sendCalls = mutableListOf<Triple<String, String, SendMessageRequest>>()
    val attachmentSendCalls = mutableListOf<Triple<String, String, AttachmentUpload>>()
    val downloadCalls = mutableListOf<Pair<String, String>>()

    override suspend fun conversations() = listResults.removeFirst()
    override suspend fun conversation(conversationId: String) = detailResults.removeFirst()
    override suspend fun messages(conversationId: String, before: String?) = messageResults.removeFirst()
    override suspend fun sendMessage(conversationId: String, idempotencyKey: String, request: SendMessageRequest): ApiResult<Message> {
        sendCalls += Triple(conversationId, idempotencyKey, request)
        return sendResults.removeFirst()
    }
    override suspend fun sendMessageWithAttachment(conversationId: String, idempotencyKey: String, request: AttachmentUpload): ApiResult<Message> {
        attachmentSendCalls += Triple(conversationId, idempotencyKey, request)
        return attachmentSendResults.removeFirst()
    }
    override suspend fun downloadAttachment(conversationId: String, messageId: String): ApiResult<DownloadedAttachment> {
        downloadCalls += conversationId to messageId
        return downloadResults.removeFirst()
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
private fun message(id: String, attachment: MessageAttachment? = null) =
    Message(id, "conv-1", "Hello", SenderType.CANDIDATE, NOW, null, DeliveryStatus.SENT, attachment)

private fun attachmentMeta(fileName: String = "resume.pdf", contentType: String = "application/pdf") =
    MessageAttachment("att-1", fileName, 1024L, contentType)
