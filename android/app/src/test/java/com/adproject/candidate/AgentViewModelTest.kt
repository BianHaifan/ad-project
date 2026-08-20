package com.adproject.candidate

import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.CandidateAgentRepository
import com.adproject.candidate.data.contract.AgentConversationSummary
import com.adproject.candidate.data.contract.AgentFieldChange
import com.adproject.candidate.data.contract.AgentPreview
import com.adproject.candidate.data.contract.AgentRun
import com.adproject.candidate.data.contract.AgentStep
import com.adproject.candidate.data.contract.AgentTarget
import com.adproject.candidate.data.contract.ConfirmAgentRunRequest
import com.adproject.candidate.data.contract.CreateAgentRunRequest
import com.adproject.candidate.feature.agent.AgentViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun blankInstructionDoesNotCreateRun() = runTest(main.dispatcher) {
        val repository = QueueAgentRepository()
        val viewModel = AgentViewModel(repository)

        viewModel.create()

        assertEquals(0, repository.createCalls)
        assertTrue(viewModel.state.value.message!!.isNotBlank())
    }

    @Test fun createsPreviewAndSendsExactConfirmationContract() = runTest(main.dispatcher) {
        val repository = QueueAgentRepository()
        val viewModel = AgentViewModel(repository)
        viewModel.updateInstruction("Change my age to 28")

        viewModel.create()
        advanceUntilIdle()
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals("AWAITING_CONFIRMATION", repository.createdRun?.status)
        assertEquals(1, repository.confirmCalls)
        assertEquals("confirmation-1", repository.confirmRequests.single().confirmationId)
        assertEquals(2, repository.confirmRequests.single().expectedRunVersion)
        assertEquals("COMPLETED", viewModel.state.value.runs.last().status)
        java.util.UUID.fromString(repository.keys.single())
    }

    @Test fun confirmationRetryReusesIdempotencyKeyAndNewRunGetsNewKey() = runTest(main.dispatcher) {
        val repository = QueueAgentRepository(
            mutableListOf(ApiResult.Failure("Network unavailable"), ApiResult.Success(completedRun())),
        )
        val viewModel = AgentViewModel(repository)
        viewModel.updateInstruction("Change my age to 28")
        viewModel.create(); advanceUntilIdle()

        viewModel.confirm(); advanceUntilIdle()
        assertEquals("Network unavailable", viewModel.state.value.message)
        viewModel.confirm(); advanceUntilIdle()

        assertEquals(repository.keys[0], repository.keys[1])
        assertEquals("COMPLETED", viewModel.state.value.runs.last().status)
        val firstKey = repository.keys.first()

        viewModel.startNewConversation()
        assertTrue(viewModel.state.value.runs.isEmpty())
        viewModel.updateInstruction("Change my age to 29")
        viewModel.create(); advanceUntilIdle()
        viewModel.confirm(); advanceUntilIdle()
        assertNotEquals(firstKey, repository.keys.last())
    }

    @Test fun cancelUsesServerResultAndPreventsConfirmation() = runTest(main.dispatcher) {
        val repository = QueueAgentRepository()
        val viewModel = AgentViewModel(repository)
        viewModel.updateInstruction("Change my age to 28")
        viewModel.create(); advanceUntilIdle()

        viewModel.cancel(); advanceUntilIdle()
        viewModel.confirm(); advanceUntilIdle()

        assertEquals(1, repository.cancelCalls)
        assertEquals(0, repository.confirmCalls)
        assertEquals("CANCELLED", viewModel.state.value.runs.last().status)

    }

    @Test fun clarificationAllowsFollowUpInSameConversation() = runTest(main.dispatcher) {
        val repository = QueueAgentRepository(createResults = mutableListOf(
            ApiResult.Success(clarificationRun()),
            ApiResult.Success(awaitingRun().copy(runId = "run-2", instruction = "28")),
        ))
        val viewModel = AgentViewModel(repository)
        viewModel.updateInstruction("修改年龄")
        viewModel.create(); advanceUntilIdle()
        viewModel.updateInstruction("28")
        viewModel.create(); advanceUntilIdle()

        assertEquals(2, viewModel.state.value.runs.size)
        assertEquals("conversation-1", repository.createRequests[1].conversationId)
        assertEquals("28", viewModel.state.value.runs.last().instruction)
    }

    @Test fun loadsPersistedRecentConversation() = runTest(main.dispatcher) {
        val repository = QueueAgentRepository()
        val viewModel = AgentViewModel(repository)
        viewModel.loadHistory(); advanceUntilIdle()

        assertEquals("conversation-1", viewModel.state.value.conversationId)
        assertEquals(1, viewModel.state.value.runs.size)
        assertEquals(1, viewModel.state.value.conversations.size)
    }

    @Test fun newConversationKeepsHistoryAndCanRestoreAnOlderConversation() = runTest(main.dispatcher) {
        val repository = QueueAgentRepository()
        val viewModel = AgentViewModel(repository)
        viewModel.loadHistory(); advanceUntilIdle()

        viewModel.startNewConversation()
        assertNull(viewModel.state.value.conversationId)
        assertTrue(viewModel.state.value.runs.isEmpty())
        assertEquals(1, viewModel.state.value.conversations.size)

        viewModel.openConversation("conversation-1")
        advanceUntilIdle()
        assertEquals("conversation-1", viewModel.state.value.conversationId)
        assertEquals(1, viewModel.state.value.runs.size)
    }
}

private class QueueAgentRepository(
    private val confirmResults: MutableList<ApiResult<AgentRun>> =
        mutableListOf(ApiResult.Success(completedRun()), ApiResult.Success(completedRun())),
    private val createResults: MutableList<ApiResult<AgentRun>> = mutableListOf(),
) : CandidateAgentRepository {
    var createCalls = 0
    var confirmCalls = 0
    var cancelCalls = 0
    var createdRun: AgentRun? = null
    val keys = mutableListOf<String>()
    val confirmRequests = mutableListOf<ConfirmAgentRunRequest>()
    val createRequests = mutableListOf<CreateAgentRunRequest>()

    override suspend fun create(request: CreateAgentRunRequest): ApiResult<AgentRun> {
        createCalls++
        createRequests += request
        val result = if (createResults.isEmpty()) ApiResult.Success(awaitingRun()) else createResults.removeFirst()
        if (result is ApiResult.Success) createdRun = result.value
        return result
    }

    override suspend fun get(runId: String): ApiResult<AgentRun> =
        ApiResult.Success(createdRun ?: awaitingRun())

    override suspend fun confirm(
        runId: String,
        idempotencyKey: String,
        request: ConfirmAgentRunRequest,
    ): ApiResult<AgentRun> {
        confirmCalls++
        keys += idempotencyKey
        confirmRequests += request
        return if (confirmResults.isEmpty()) ApiResult.Success(completedRun()) else confirmResults.removeFirst()
    }

    override suspend fun cancel(runId: String): ApiResult<AgentRun> {
        cancelCalls++
        return ApiResult.Success(awaitingRun().copy(status = "CANCELLED", confirmationStatus = "CANCELLED", version = 3))
    }

    override suspend fun recentConversation() = ApiResult.Success(
        com.adproject.candidate.data.contract.AgentConversation("conversation-1", listOf(awaitingRun())),
    )

    override suspend fun conversations() = ApiResult.Success(listOf(
        AgentConversationSummary(
            "conversation-1", "Change my age to 28", "Review the change", "2026-08-18T00:00:00Z",
        ),
    ))

    override suspend fun conversation(conversationId: String) = recentConversation()

    override suspend fun deleteConversation(conversationId: String): ApiResult<Unit> {
        deleteCalls++
        return ApiResult.Success(Unit)
    }

    var deleteCalls = 0
}

private fun awaitingRun() = AgentRun(
    runId = "run-1",
    conversationId = "conversation-1",
    instruction = "Change my age to 28",
    status = "AWAITING_CONFIRMATION",
    confirmationStatus = "PENDING",
    target = AgentTarget("resume-1"),
    steps = listOf(AgentStep(1, "TOOL", "get_my_resume", "SUCCEEDED", null, "2026-08-18T00:00:00Z")),
    preview = AgentPreview(
        "confirmation-1", "RESUME", "resume-1", 7, "2026-08-18T00:05:00Z",
        listOf(AgentFieldChange("age", 27, 28)),
    ),
    result = null,
    message = "Review the change",
    errorCode = null,
    version = 2,
    createdAt = "2026-08-18T00:00:00Z",
    updatedAt = "2026-08-18T00:00:00Z",
)

private fun clarificationRun() = awaitingRun().copy(
    instruction = "修改年龄",
    status = "NEEDS_CLARIFICATION",
    confirmationStatus = "NOT_REQUIRED",
    preview = null,
    message = "What age should be set?",
)

private fun completedRun() = awaitingRun().copy(
    status = "COMPLETED",
    confirmationStatus = "CONFIRMED",
    preview = null,
    version = 4,
)
