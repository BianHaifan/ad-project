package com.adproject.candidate.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.AttachmentUpload
import com.adproject.candidate.data.api.CandidateConversationRepository
import com.adproject.candidate.data.contract.ConversationDetail
import com.adproject.candidate.data.contract.ConversationSummary
import com.adproject.candidate.data.contract.Message
import com.adproject.candidate.data.contract.ReadStateRequest
import com.adproject.candidate.data.contract.SendMessageRequest
import com.adproject.candidate.feature.community.CommunityDirectConversation
import com.adproject.candidate.feature.community.CommunityRepository
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MessagesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val conversations: List<ConversationSummary> = emptyList(),
    val communityConversations: List<CommunityDirectConversation> = emptyList(),
    val message: String? = null,
)

class MessagesViewModel(
    private val repository: CandidateConversationRepository,
    private val communityRepository: CommunityRepository? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MessagesUiState())
    val state: StateFlow<MessagesUiState> = mutableState.asStateFlow()

    private var pollingJob: Job? = null
    private var inFlight = false
    private var consecutiveFailures = 0

    init { load() }

    fun retry() = load()
    fun refresh() = load(refreshing = true)

    /**
     * Starts foreground polling. Called from the Compose layer only when the app is in the
     * foreground AND the Messages screen is visible (lifecycle at least STARTED). Idempotent.
     */
    fun onScreenStarted() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch { pollLoop() }
    }

    /** Stops polling when the screen is no longer visible or the app is backgrounded. */
    fun onScreenStopped() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun load(refreshing: Boolean = false) {
        viewModelScope.launch {
            if (inFlight) return@launch
            inFlight = true
            try {
                mutableState.update {
                    it.copy(loading = it.conversations.isEmpty() && it.communityConversations.isEmpty() && !refreshing, refreshing = refreshing, message = null)
                }
                fetchSilently()
            } finally {
                inFlight = false
            }
        }
    }

    private suspend fun pollLoop() {
        while (true) {
            if (!inFlight) {
                inFlight = true
                try { fetchSilently() } finally { inFlight = false }
            }
            delay(PollSchedule.delayAfter(consecutiveFailures, PollSchedule.LIST_INTERVAL_MS))
        }
    }

    private suspend fun fetchSilently() {
        val communityResult = communityRepository?.directConversations()
        when (val result = repository.conversations()) {
            is ApiResult.Success -> {
                consecutiveFailures = 0
                mutableState.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        conversations = result.value.conversations,
                        communityConversations = (communityResult as? ApiResult.Success)?.value ?: it.communityConversations,
                        message = null,
                    )
                }
            }
            is ApiResult.Failure -> {
                consecutiveFailures++
                mutableState.update {
                    val community = (communityResult as? ApiResult.Success)?.value ?: it.communityConversations
                    it.copy(loading = false, refreshing = false, communityConversations = community,
                        message = if (community.isEmpty()) result.message else null)
                }
            }
        }
    }

    companion object {
        fun factory(repository: CandidateConversationRepository, communityRepository: CommunityRepository? = null): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MessagesViewModel(repository, communityRepository) as T
            }
    }
}

data class ChatUiState(
    val loading: Boolean = true,
    val conversation: ConversationDetail? = null,
    val messages: List<Message> = emptyList(),
    val message: String? = null,
    val notFound: Boolean = false,
    val sending: Boolean = false,
    val draft: String = "",
    val attachment: PendingAttachment? = null,
    val downloadingMessageId: String? = null,
    val downloadEvent: DownloadEvent? = null,
    val imagePreview: ImagePreview? = null,
    // Inline image thumbnails, keyed by message id. Populated once per message and never
    // re-downloaded on polling; a message whose thumbnail download failed simply keeps the
    // fallback file chip and can be re-tapped to retry via the full download path.
    val imageThumbnails: Map<String, ImagePreview> = emptyMap(),
    val loadingThumbnails: Set<String> = emptySet(),
)

data class PendingAttachment(
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val bytes: ByteArray,
)

data class DownloadEvent(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)

data class ImagePreview(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)

class ChatViewModel(
    private val conversationId: String,
    private val repository: CandidateConversationRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = mutableState.asStateFlow()

    private var sendKey: String? = null
    private var clientMessageId: String? = null
    private var pollingJob: Job? = null
    private var inFlight = false
    private var consecutiveFailures = 0

    init { load() }

    fun retry() = load()
    fun updateDraft(value: String) = mutableState.update { it.copy(draft = value) }

    /** Starts foreground polling while the chat screen is visible (list = 1s). Idempotent. */
    fun onScreenStarted() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch { pollLoop() }
    }

    fun onScreenStopped() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun load() {
        viewModelScope.launch {
            if (inFlight) return@launch
            inFlight = true
            try {
                mutableState.update { it.copy(loading = true, message = null, notFound = false) }
                fetchDetailAndMessages(markRead = true)
            } finally {
                inFlight = false
            }
        }
    }

    private suspend fun pollLoop() {
        while (true) {
            if (!inFlight) {
                inFlight = true
                try { fetchDetailAndMessages(markRead = false) } finally { inFlight = false }
            }
            delay(PollSchedule.delayAfter(consecutiveFailures, PollSchedule.DETAIL_INTERVAL_MS))
        }
    }

    private suspend fun fetchDetailAndMessages(markRead: Boolean) {
        when (val detail = repository.conversation(conversationId)) {
            is ApiResult.Failure -> {
                consecutiveFailures++
                mutableState.update {
                    it.copy(loading = false, message = detail.message, notFound = detail.statusCode == 404)
                }
            }
            is ApiResult.Success -> {
                val conversation = detail.value
                mutableState.update { it.copy(conversation = conversation) }
                when (val result = repository.messages(conversationId)) {
                    is ApiResult.Failure -> {
                        consecutiveFailures++
                        mutableState.update { it.copy(loading = false, message = result.message) }
                    }
                    is ApiResult.Success -> {
                        consecutiveFailures = 0
                        val messages = result.value.messages
                        mutableState.update { it.copy(loading = false, messages = messages, message = null) }
                        ensureImageThumbnails(messages)
                        if (markRead) {
                            messages.lastOrNull()?.messageId?.let { lastId ->
                                repository.markRead(conversationId, ReadStateRequest(lastId))
                            }
                        }
                    }
                }
            }
        }
    }

    fun selectAttachment(value: PendingAttachment) = mutableState.update { it.copy(attachment = value, message = null) }

    fun removeAttachment() = mutableState.update { it.copy(attachment = null) }

    fun consumeDownload() = mutableState.update { it.copy(downloadEvent = null) }

    fun closeImagePreview() = mutableState.update { it.copy(imagePreview = null) }

    /** Opens an image attachment at full size, reusing an already-loaded thumbnail when present. */
    fun openImage(message: Message) {
        val existing = mutableState.value.imageThumbnails[message.messageId]
        if (existing != null) {
            mutableState.update { it.copy(imagePreview = existing) }
        } else {
            download(message)
        }
    }

    /** Starts a background thumbnail download for every image message that isn't already cached. */
    private fun ensureImageThumbnails(messages: List<Message>) {
        val current = mutableState.value
        val toLoad = messages.filter { message ->
            val attachment = message.attachment
            attachment != null && isPreviewableImage(attachment.contentType) &&
                message.messageId !in current.imageThumbnails &&
                message.messageId !in current.loadingThumbnails
        }
        if (toLoad.isEmpty()) return
        mutableState.update { it.copy(loadingThumbnails = it.loadingThumbnails + toLoad.map { m -> m.messageId }) }
        toLoad.forEach(::downloadThumbnail)
    }

    private fun downloadThumbnail(message: Message) {
        val attachment = message.attachment ?: return
        viewModelScope.launch {
            when (val result = repository.downloadAttachment(conversationId, message.messageId)) {
                is ApiResult.Success -> mutableState.update {
                    it.copy(
                        loadingThumbnails = it.loadingThumbnails - message.messageId,
                        imageThumbnails = it.imageThumbnails + (message.messageId to
                            ImagePreview(attachment.fileName, result.value.contentType, result.value.bytes)),
                    )
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(loadingThumbnails = it.loadingThumbnails - message.messageId)
                }
            }
        }
    }

    fun download(message: Message) {
        val attachment = message.attachment ?: return
        if (mutableState.value.downloadingMessageId != null) return
        mutableState.update { it.copy(downloadingMessageId = message.messageId, message = null) }
        viewModelScope.launch {
            when (val result = repository.downloadAttachment(conversationId, message.messageId)) {
                is ApiResult.Success -> mutableState.update {
                    if (isPreviewableImage(attachment.contentType)) {
                        it.copy(
                            downloadingMessageId = null,
                            imagePreview = ImagePreview(attachment.fileName, result.value.contentType, result.value.bytes),
                        )
                    } else {
                        it.copy(
                            downloadingMessageId = null,
                            downloadEvent = DownloadEvent(attachment.fileName, result.value.contentType, result.value.bytes),
                        )
                    }
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(downloadingMessageId = null, message = result.message)
                }
            }
        }
    }

    fun send() {
        val current = mutableState.value
        val body = current.draft.trim()
        val attachment = current.attachment
        if (current.sending || (body.isEmpty() && attachment == null)) return
        val key = sendKey ?: UUID.randomUUID().toString().also { sendKey = it }
        val clientId = clientMessageId ?: UUID.randomUUID().toString().also { clientMessageId = it }
        mutableState.update { it.copy(sending = true, message = null) }
        viewModelScope.launch {
            val result = if (attachment != null) {
                repository.sendMessageWithAttachment(
                    conversationId, key,
                    AttachmentUpload(clientId, body.ifBlank { null }, attachment.fileName, attachment.contentType, attachment.bytes),
                )
            } else {
                repository.sendMessage(conversationId, key, SendMessageRequest(body, clientId))
            }
            when (result) {
                is ApiResult.Success -> {
                    sendKey = null
                    clientMessageId = null
                    mutableState.update {
                        it.copy(sending = false, draft = "", attachment = null, messages = it.messages + result.value, message = null)
                    }
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(sending = false, message = result.message)
                }
            }
        }
    }

    companion object {
        fun factory(conversationId: String, repository: CandidateConversationRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatViewModel(conversationId, repository) as T
            }
    }
}

internal fun isPreviewableImage(contentType: String): Boolean =
    contentType == "image/png" || contentType == "image/jpeg"
