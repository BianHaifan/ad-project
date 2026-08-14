package com.adproject.candidate.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.CandidateConversationRepository
import com.adproject.candidate.data.contract.ConversationDetail
import com.adproject.candidate.data.contract.ConversationSummary
import com.adproject.candidate.data.contract.Message
import com.adproject.candidate.data.contract.ReadStateRequest
import com.adproject.candidate.data.contract.SendMessageRequest
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
    val message: String? = null,
)

class MessagesViewModel(private val repository: CandidateConversationRepository) : ViewModel() {
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
                    it.copy(loading = it.conversations.isEmpty() && !refreshing, refreshing = refreshing, message = null)
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
        when (val result = repository.conversations()) {
            is ApiResult.Success -> {
                consecutiveFailures = 0
                mutableState.update {
                    it.copy(loading = false, refreshing = false, conversations = result.value.conversations, message = null)
                }
            }
            is ApiResult.Failure -> {
                consecutiveFailures++
                mutableState.update { it.copy(loading = false, refreshing = false, message = result.message) }
            }
        }
    }

    companion object {
        fun factory(repository: CandidateConversationRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MessagesViewModel(repository) as T
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

    fun send() {
        val body = mutableState.value.draft.trim()
        if (body.isEmpty() || mutableState.value.sending) return
        val key = sendKey ?: UUID.randomUUID().toString().also { sendKey = it }
        val clientId = clientMessageId ?: UUID.randomUUID().toString().also { clientMessageId = it }
        mutableState.update { it.copy(sending = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.sendMessage(conversationId, key, SendMessageRequest(body, clientId))) {
                is ApiResult.Success -> {
                    sendKey = null
                    clientMessageId = null
                    mutableState.update {
                        it.copy(sending = false, draft = "", messages = it.messages + result.value, message = null)
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
