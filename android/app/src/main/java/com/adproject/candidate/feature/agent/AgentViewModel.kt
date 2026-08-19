package com.adproject.candidate.feature.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.CandidateAgentRepository
import com.adproject.candidate.data.contract.AgentConversationSummary
import com.adproject.candidate.data.contract.AgentRun
import com.adproject.candidate.data.contract.ConfirmAgentRunRequest
import com.adproject.candidate.data.contract.CreateAgentRunRequest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgentUiState(
    val instruction: String = "",
    val conversationId: String? = null,
    val conversations: List<AgentConversationSummary> = emptyList(),
    val runs: List<AgentRun> = emptyList(),
    val loadingHistory: Boolean = false,
    val submitting: Boolean = false,
    val message: String? = null,
)

class AgentViewModel(private val repository: CandidateAgentRepository) : ViewModel() {
    private val mutable = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = mutable
    private var confirmationIdempotencyKey: String? = null
    private var historyLoaded = false

    fun loadHistory() {
        if (historyLoaded || mutable.value.loadingHistory) return
        historyLoaded = true
        mutable.update { it.copy(loadingHistory = true, message = null) }
        viewModelScope.launch {
            val conversations = repository.conversations()
            val summaries = when (conversations) {
                is ApiResult.Success -> conversations.value
                is ApiResult.Failure -> emptyList()
            }
            when (val result = repository.recentConversation()) {
                is ApiResult.Success -> mutable.update {
                    it.copy(
                        loadingHistory = false,
                        conversationId = result.value.conversationId,
                        conversations = summaries.ifEmpty { it.conversations },
                        runs = result.value.runs,
                    )
                }
                is ApiResult.Failure -> {
                    historyLoaded = false
                    mutable.update {
                        it.copy(
                            loadingHistory = false,
                            conversations = summaries.ifEmpty { it.conversations },
                            message = result.message,
                        )
                    }
                }
            }
        }
    }

    fun updateInstruction(value: String) {
        mutable.update { it.copy(instruction = value.take(2000), message = null) }
    }

    fun create() {
        val current = mutable.value
        val instruction = current.instruction.trim()
        if (current.submitting || current.runs.any { it.status in setOf("AWAITING_CONFIRMATION", "PROCESSING", "EXECUTING") }) return
        if (instruction.isEmpty()) {
            mutable.update { it.copy(message = "Enter an instruction for the Agent.") }
            return
        }
        confirmationIdempotencyKey = null
        mutable.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.create(CreateAgentRunRequest(
                instruction, current.conversationId,
            ))) {
                is ApiResult.Success -> mutable.update {
                    it.copy(
                        instruction = "",
                        submitting = false,
                        conversationId = result.value.conversationId,
                        conversations = upsertConversation(it.conversations, result.value),
                        runs = upsert(it.runs, result.value),
                        message = null,
                    )
                }
                is ApiResult.Failure -> mutable.update {
                    it.copy(submitting = false, message = result.message)
                }
            }
        }
    }

    fun refresh() {
        val runId = mutable.value.runs.lastOrNull()?.runId ?: return
        if (mutable.value.submitting) return
        mutable.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.get(runId)) {
                is ApiResult.Success -> mutable.update {
                    it.copy(submitting = false, runs = upsert(it.runs, result.value))
                }
                is ApiResult.Failure -> mutable.update { it.copy(submitting = false, message = result.message) }
            }
        }
    }

    fun confirm() {
        val current = mutable.value
        val run = current.runs.lastOrNull { it.status == "AWAITING_CONFIRMATION" } ?: return
        val preview = run.preview ?: return
        if (current.submitting || run.status != "AWAITING_CONFIRMATION") return
        val key = confirmationIdempotencyKey ?: UUID.randomUUID().toString().also {
            confirmationIdempotencyKey = it
        }
        mutable.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.confirm(run.runId, key,
                ConfirmAgentRunRequest(preview.confirmationId, run.version))) {
                is ApiResult.Success -> mutable.update {
                    it.copy(submitting = false, runs = upsert(it.runs, result.value), message = null)
                }
                is ApiResult.Failure -> {
                    val refreshed = repository.get(run.runId)
                    mutable.update {
                        it.copy(
                            submitting = false,
                            runs = (refreshed as? ApiResult.Success)?.value?.let { run ->
                                upsert(it.runs, run)
                            } ?: it.runs,
                            message = result.message,
                        )
                    }
                }
            }
        }
    }

    fun cancel() {
        val current = mutable.value
        val run = current.runs.lastOrNull {
            it.status == "AWAITING_CONFIRMATION" || it.status == "NEEDS_CLARIFICATION"
        } ?: return
        if (current.submitting || run.status !in setOf("AWAITING_CONFIRMATION", "NEEDS_CLARIFICATION")) return
        mutable.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.cancel(run.runId)) {
                is ApiResult.Success -> mutable.update {
                    it.copy(submitting = false, runs = upsert(it.runs, result.value), message = null)
                }
                is ApiResult.Failure -> mutable.update {
                    it.copy(submitting = false, message = result.message)
                }
            }
        }
    }

    fun startNewConversation() {
        confirmationIdempotencyKey = null
        mutable.update { AgentUiState(conversations = it.conversations) }
        historyLoaded = true
    }

    fun openConversation(conversationId: String) {
        val current = mutable.value
        if (current.submitting || current.loadingHistory || current.conversationId == conversationId) return
        confirmationIdempotencyKey = null
        mutable.update { it.copy(loadingHistory = true, instruction = "", message = null) }
        viewModelScope.launch {
            when (val result = repository.conversation(conversationId)) {
                is ApiResult.Success -> mutable.update {
                    it.copy(
                        loadingHistory = false,
                        conversationId = result.value.conversationId,
                        runs = result.value.runs,
                        message = null,
                    )
                }
                is ApiResult.Failure -> mutable.update {
                    it.copy(loadingHistory = false, message = result.message)
                }
            }
        }
    }

    companion object {
        private fun upsert(runs: List<AgentRun>, run: AgentRun): List<AgentRun> {
            val index = runs.indexOfFirst { it.runId == run.runId }
            if (index < 0) return runs + run
            return runs.toMutableList().also { it[index] = run }
        }

        private fun upsertConversation(
            conversations: List<AgentConversationSummary>,
            run: AgentRun,
        ): List<AgentConversationSummary> {
            val summary = AgentConversationSummary(
                run.conversationId, run.instruction, run.message, run.updatedAt,
            )
            return listOf(summary) + conversations.filterNot { it.conversationId == run.conversationId }
        }

        fun factory(repository: CandidateAgentRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AgentViewModel(repository) }
        }
    }
}
