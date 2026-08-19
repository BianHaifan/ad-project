package com.adproject.candidate.feature.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adproject.candidate.core.designsystem.MainTab
import com.adproject.candidate.data.contract.AgentFieldChange
import com.adproject.candidate.data.contract.AgentConversationSummary
import com.adproject.candidate.data.contract.AgentExecutionResult
import com.adproject.candidate.data.contract.AgentPreview
import com.adproject.candidate.data.contract.AgentQueryResult
import com.adproject.candidate.data.contract.AgentRun
import com.adproject.candidate.data.contract.AgentStep
import com.adproject.candidate.data.contract.AgentTarget
import com.adproject.candidate.data.contract.Experience
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class AgentScreenUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun welcomeUsesChatComposerAndFiveTabNavigation() {
        var instruction = ""
        var selectedTab: MainTab? = null
        composeRule.setContent {
            AgentScreen(AgentUiState(), { selectedTab = it }, { instruction = it }, {}, {}, {}, {}, {})
        }

        composeRule.onNodeWithText("Hi, I'm your HireX Agent.").assertIsDisplayed()
        composeRule.onNodeWithText("Message HireX Agent").assertIsDisplayed()
        listOf("Jobs", "Community", "Agent", "Messages", "Me").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onNodeWithText("Try: Change my resume age to 28").performClick()
        assertEquals("Change my default resume age to 28", instruction)
        composeRule.onNodeWithText("Jobs").performClick()
        assertEquals(MainTab.Jobs, selectedTab)
    }

    @Test
    fun awaitingConfirmationRendersInstructionPlanPreviewAndActionsInConversation() {
        var confirmations = 0
        var cancellations = 0
        val instruction = "Change my default resume age to 28"
        composeRule.setContent {
            AgentScreen(
                state = AgentUiState(runs = listOf(awaitingRun(instruction))),
                onTab = {}, onInstruction = {}, onCreate = {}, onRefresh = {},
                onConfirm = { confirmations++ }, onCancel = { cancellations++ }, onStartAnother = {},
            )
        }

        composeRule.onNodeWithText(instruction).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ready for your confirmation").assertIsDisplayed()
        composeRule.onNodeWithText("Plan").assertDoesNotExist()
        composeRule.onNodeWithText("27").assertIsDisplayed()
        composeRule.onNodeWithText("28").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm and apply").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performScrollTo().performClick()
        assertEquals(1, confirmations)
        assertEquals(1, cancellations)
    }

    @Test
    fun matchingAgeShowsNoActionAnswerWithoutPlanOrConfirmationControls() {
        val instruction = "Change my default resume age to 28"
        composeRule.setContent {
            AgentScreen(
                state = AgentUiState(instruction = instruction, runs = listOf(noActionRun(instruction))),
                onTab = {}, onInstruction = {}, onCreate = {}, onRefresh = {},
                onConfirm = {}, onCancel = {}, onStartAnother = {},
            )
        }

        composeRule.onNodeWithText("No change needed").assertIsDisplayed()
        composeRule.onNodeWithText("Your default resume age is already 28, so no change is needed.").assertIsDisplayed()
        composeRule.onNodeWithText("Plan").assertDoesNotExist()
        composeRule.onNodeWithText("Confirm and apply").assertDoesNotExist()
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        composeRule.onNodeWithText("＋ New").assertIsDisplayed()
    }

    @Test
    fun readOnlyExperienceResultRendersWithoutConfirmationControls() {
        val instruction = "查看我的工作经历"
        val run = awaitingRun(instruction).copy(
            status = "COMPLETED",
            confirmationStatus = "NOT_REQUIRED",
            preview = null,
            message = "Here are the experiences from your default resume.",
            result = AgentExecutionResult(
                operation = "READ_RESUME", targetType = "RESUME", targetId = "resume-1",
                previousVersion = 7, newVersion = 7, completedAt = "2026-08-18T00:00:01Z",
                appliedChanges = emptyList(),
                queryResult = AgentQueryResult(
                    section = "experiences",
                    experiences = listOf(Experience("exp-1", "Engineer", "Acme", "Built APIs", "2024-01", null)),
                ),
            ),
        )
        composeRule.setContent {
            AgentScreen(
                state = AgentUiState(instruction = instruction, runs = listOf(run)),
                onTab = {}, onInstruction = {}, onCreate = {}, onRefresh = {},
                onConfirm = {}, onCancel = {}, onStartAnother = {},
            )
        }

        composeRule.onNodeWithText("Done — resume information loaded").assertIsDisplayed()
        composeRule.onNodeWithText("1. Engineer · Acme").assertIsDisplayed()
        composeRule.onNodeWithText("Built APIs").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm and apply").assertDoesNotExist()
    }

    @Test
    fun rendersMultiplePersistedChatTurnsAndKeepsComposerEnabled() {
        val greeting = awaitingRun("hello").copy(
            runId = "run-chat-1",
            status = "COMPLETED",
            confirmationStatus = "NOT_REQUIRED",
            target = null,
            preview = null,
            result = null,
            message = "Hello! How can I help with your resume?",
        )
        val clarification = awaitingRun("帮我改年龄").copy(
            runId = "run-chat-2",
            status = "NEEDS_CLARIFICATION",
            confirmationStatus = "NOT_REQUIRED",
            target = null,
            preview = null,
            message = "What age should be set?",
        )
        composeRule.setContent {
            AgentScreen(
                state = AgentUiState(conversationId = "conversation-1", runs = listOf(greeting, clarification)),
                onTab = {}, onInstruction = {}, onCreate = {}, onRefresh = {},
                onConfirm = {}, onCancel = {}, onStartAnother = {},
            )
        }

        composeRule.onNodeWithText("hello").assertIsDisplayed()
        composeRule.onNodeWithText("Hello! How can I help with your resume?").assertIsDisplayed()
        composeRule.onNodeWithText("帮我改年龄").assertIsDisplayed()
        composeRule.onNodeWithText("What age should be set?").assertIsDisplayed()
        composeRule.onNodeWithText("Message HireX Agent").assertIsDisplayed()
    }

    @Test
    fun historySheetLetsUserRestoreAConversationAfterStartingANewOne() {
        var openedConversation: String? = null
        val summary = AgentConversationSummary(
            "conversation-old", "My previous question", "Previous answer", "2026-08-18T12:30:00Z",
        )
        composeRule.setContent {
            AgentScreen(
                state = AgentUiState(conversations = listOf(summary)),
                onTab = {}, onInstruction = {}, onCreate = {}, onRefresh = {},
                onConfirm = {}, onCancel = {}, onStartAnother = {},
                onOpenConversation = { openedConversation = it },
            )
        }

        composeRule.onNodeWithText("History (1)").performClick()
        composeRule.onNodeWithText("Conversation history").assertIsDisplayed()
        composeRule.onNodeWithText("My previous question").performClick()
        assertEquals("conversation-old", openedConversation)
    }
}

private fun noActionRun(instruction: String) = awaitingRun(instruction).copy(
    status = "NO_ACTION_REQUIRED",
    confirmationStatus = "NOT_REQUIRED",
    preview = null,
    message = "Your default resume age is already 28, so no change is needed.",
)

private fun awaitingRun(instruction: String) = AgentRun(
    runId = "run-12345678",
    conversationId = "conversation-1",
    instruction = instruction,
    status = "AWAITING_CONFIRMATION",
    confirmationStatus = "PENDING",
    target = AgentTarget("resume-1"),
    steps = listOf(
        AgentStep(1, "TOOL", "get_my_resume", "SUCCEEDED", null, "2026-08-18T00:00:00Z"),
        AgentStep(2, "TOOL", "preview_resume_patch", "SUCCEEDED", null, "2026-08-18T00:00:01Z"),
    ),
    preview = AgentPreview(
        confirmationId = "confirmation-1",
        targetType = "RESUME",
        targetId = "resume-1",
        expectedVersion = 7,
        expiresAt = "2026-08-18T00:05:00Z",
        changes = listOf(AgentFieldChange("age", 27, 28)),
    ),
    result = null,
    message = "Review the exact change before applying it.",
    errorCode = null,
    version = 2,
    createdAt = "2026-08-18T00:00:00Z",
    updatedAt = "2026-08-18T00:00:01Z",
)
