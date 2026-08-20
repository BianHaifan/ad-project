package com.adproject.candidate.feature.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adproject.candidate.core.designsystem.*
import com.adproject.candidate.data.contract.AgentRun

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    state: AgentUiState,
    onTab: (MainTab) -> Unit,
    onInstruction: (String) -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onStartAnother: () -> Unit,
    onOpenConversation: (String) -> Unit = {},
    onDeleteConversation: (String) -> Unit = {},
) {
    var historyVisible by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val blockingRun = state.runs.lastOrNull {
        it.status in setOf("AWAITING_CONFIRMATION", "PROCESSING", "EXECUTING")
    }
    val controlsVisible = state.conversations.isNotEmpty() ||
        (state.runs.isNotEmpty() && blockingRun == null && !state.submitting)
    val listState = rememberLazyListState()
    val conversationItemCount =
        state.runs.size * 2 +
            (if (state.runs.isEmpty() && !state.submitting && !state.loadingHistory && state.message == null) 1 else 0) +
            (if (state.submitting && state.instruction.isNotBlank()) 1 else 0) +
            (if (state.submitting) 1 else 0) +
            (if (state.message != null) 1 else 0)
    LaunchedEffect(conversationItemCount) {
        if (conversationItemCount > 0) {
            listState.animateScrollToItem(conversationItemCount - 1)
        }
    }
    Scaffold(
        topBar = { AdTopBar("AI Agent") },
        bottomBar = { AdBottomBar(MainTab.Agent, onTab) },
        containerColor = AdBackground,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (controlsVisible) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.runs.isNotEmpty() && blockingRun == null && !state.submitting) {
                        OutlinedButton(onClick = onStartAnother, modifier = Modifier.weight(1f)) {
                            Text("＋ New")
                        }
                    }
                    if (state.conversations.isNotEmpty()) {
                        OutlinedButton(onClick = { historyVisible = true }, modifier = Modifier.weight(1f)) {
                            Text("History (${state.conversations.size})")
                        }
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.runs.isEmpty() && !state.submitting && !state.loadingHistory && state.message == null) {
                    item {
                        AgentBubble {
                            Text("Hi, I'm your HireX Agent.", color = AdText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "Tell me what you want to change. I will show the exact plan and ask for confirmation before writing anything.",
                                color = AdMuted,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                            )
                            SecondaryButton(
                                "Try: Change my resume age to 28",
                                { onInstruction("Change my default resume age to 28") },
                                Modifier.fillMaxWidth(),
                            )
                            SecondaryButton(
                                "Try: Show my resume skills",
                                { onInstruction("Show my resume skills") },
                                Modifier.fillMaxWidth(),
                            )
                            SecondaryButton(
                                "Try: Add Python to my skills",
                                { onInstruction("Add Python to my skills") },
                                Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                state.runs.forEach { run ->
                    item(key = "user-${run.runId}") { UserBubble(run.instruction) }
                    item {
                        AgentBubble {
                            AgentRunContent(
                                run = run,
                                submitting = state.submitting,
                                interactive = run.runId == blockingRun?.runId,
                                onRefresh = onRefresh,
                                onConfirm = onConfirm,
                                onCancel = onCancel,
                            )
                        }
                    }
                }

                if (state.submitting && state.instruction.isNotBlank()) {
                    item { UserBubble(state.instruction.trim()) }
                }

                if (state.submitting) {
                    item {
                        AgentBubble {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = AdTeal, strokeWidth = 2.dp)
                                Text("Thinking…", color = AdMuted)
                            }
                        }
                    }
                }

                state.message?.let { message ->
                    item {
                        AgentBubble(error = true) {
                            Text("I couldn't complete that request.", color = Color(0xFF9C2F2F), fontWeight = FontWeight.SemiBold)
                            Text(message, color = Color(0xFF7D3A3A), fontSize = 13.sp)
                        }
                    }
                }
            }

            AgentComposer(
                value = state.instruction,
                enabled = !state.submitting && blockingRun == null,
                onValueChange = onInstruction,
                onSend = onCreate,
            )
        }
    }

    if (historyVisible) {
        ModalBottomSheet(onDismissRequest = { historyVisible = false }) {
            Text(
                "Conversation history",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = AdText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.conversations.forEach { conversation ->
                    item(key = conversation.conversationId) {
                        Card(
                            onClick = {
                                historyVisible = false
                                onOpenConversation(conversation.conversationId)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (conversation.conversationId == state.conversationId)
                                    Color(0xFFE8F7F4) else Color.White,
                            ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        conversation.lastInstruction,
                                        color = AdText,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                    )
                                    conversation.lastMessage?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, color = AdMuted, fontSize = 12.sp, maxLines = 2)
                                    }
                                    Text(
                                        conversation.updatedAt.replace('T', ' ').take(16),
                                        color = AdMuted,
                                        fontSize = 10.sp,
                                    )
                                }
                                IconButton(onClick = { pendingDeleteId = conversation.conversationId }) {
                                    Text("✕", color = AdMuted, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    state.conversations.firstOrNull { it.conversationId == pendingDeleteId }?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete conversation?") },
            text = { Text("This removes the conversation and all of its messages from your history.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteConversation(conversation.conversationId)
                    pendingDeleteId = null
                }) { Text("Delete", color = Color(0xFFB42318)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AgentComposer(value: String, enabled: Boolean, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        HorizontalDivider(color = AdBorder)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text(if (enabled) "Message HireX Agent" else "Finish the current request first") },
            )
            Button(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AdTeal),
            ) { Text("↑", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        }
        Text(
            "Agent actions use your permissions and require confirmation.",
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            color = AdMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UserBubble(message: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Card(
            Modifier.fillMaxWidth(0.84f),
            shape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp),
            colors = CardDefaults.cardColors(containerColor = AdTeal),
        ) {
            Text(message, Modifier.padding(horizontal = 16.dp, vertical = 13.dp), color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun AgentBubble(error: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(34.dp).background(AdTeal, CircleShape), contentAlignment = Alignment.Center) {
            Text("HX", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Card(
            Modifier.weight(1f),
            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
            colors = CardDefaults.cardColors(containerColor = if (error) Color(0xFFFFEEEE) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun AgentRunContent(
    run: AgentRun,
    submitting: Boolean,
    interactive: Boolean,
    onRefresh: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val chatOnly = run.status == "COMPLETED" && run.target == null && run.preview == null && run.result == null
    if (chatOnly) {
        Text(run.message.orEmpty(), color = AdText, fontSize = 14.sp, lineHeight = 20.sp)
        return
    }
    val label = statusLabel(run.status, run.result?.queryResult != null)
    val message = run.message?.takeIf { it.isNotBlank() }
    if (message == null) {
        Text(label, color = AdText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    } else {
        Text(message, color = AdText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp)
        Text(label, color = AdMuted, fontSize = 12.sp)
    }

    run.preview?.let { preview ->
        AgentSection("Review exact changes", accent = true) {
            preview.changes.forEach { change ->
                Text(change.field.replaceFirstChar(Char::uppercase), color = AdMuted, fontSize = 12.sp)
                Text(formatAgentValue(change.field, change.oldValue), color = AdMuted, fontSize = 13.sp, lineHeight = 18.sp)
                Text("↓", color = AdTealDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(formatAgentValue(change.field, change.newValue), color = AdTealDark,
                    fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("Resume version ${preview.expectedVersion}", color = AdMuted, fontSize = 11.sp)
            Text("Preview expires ${preview.expiresAt}", color = AdMuted, fontSize = 11.sp)
        }
    }

    run.result?.let { result ->
        result.queryResult?.let { query ->
            AgentSection("Resume ${query.section}", accent = true) {
                query.summary?.let { Text(it.ifBlank { "No summary saved." }, color = AdText, lineHeight = 20.sp) }
                query.skills?.let { skills ->
                    Text(if (skills.isEmpty()) "No skills saved." else skills.joinToString(" · "), color = AdText, lineHeight = 20.sp)
                }
                query.experiences?.let { experiences ->
                    if (experiences.isEmpty()) Text("No experiences saved.", color = AdMuted)
                    experiences.forEachIndexed { index, experience ->
                        Text("${index + 1}. ${experience.title} · ${experience.company}", color = AdText,
                            fontWeight = FontWeight.SemiBold)
                        Text("${experience.startDate} — ${experience.endDate ?: "Present"}", color = AdMuted, fontSize = 12.sp)
                        Text(experience.description, color = AdText, fontSize = 13.sp, lineHeight = 18.sp)
                        experience.experienceId?.let { Text("ID: $it", color = AdMuted, fontSize = 10.sp) }
                    }
                }
            }
        } ?: AgentSection("Completed", accent = true) {
            result.appliedChanges.forEach { change ->
                Text(change.field.replaceFirstChar(Char::uppercase), color = AdMuted, fontSize = 12.sp)
                Text(formatAgentValue(change.field, change.newValue), color = AdTealDark,
                    fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
            }
            Text("Resume version ${result.previousVersion} → ${result.newVersion}", color = AdMuted, fontSize = 12.sp)
        }
    }

    if (interactive) when (run.status) {
        "AWAITING_CONFIRMATION" -> {
            PrimaryButton(if (submitting) "Applying…" else "Confirm and apply", onConfirm, Modifier.fillMaxWidth(), !submitting)
            SecondaryButton("Cancel", onCancel, Modifier.fillMaxWidth(), !submitting)
        }
        "PROCESSING", "EXECUTING" -> SecondaryButton("Refresh", onRefresh, Modifier.fillMaxWidth(), !submitting)
    }

    Text("Run ${run.runId.take(8)} · v${run.version}", color = AdMuted, fontSize = 10.sp)
}

@Composable
private fun AgentSection(title: String, accent: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (accent) AdTealSoft else AdBackground),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = if (accent) AdTealDark else AdText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            content()
        }
    }
}

private fun statusLabel(status: String, readOnlyResult: Boolean = false) = when (status) {
    "AWAITING_CONFIRMATION" -> "Ready for your confirmation"
    "NEEDS_CLARIFICATION" -> "I need a little more information"
    "NO_ACTION_REQUIRED" -> "No change needed"
    "COMPLETED" -> if (readOnlyResult) "Done — resume information loaded" else "Done — your change was applied"
    "CANCELLED" -> "This request was cancelled"
    "FAILED" -> "I couldn't complete this request"
    "EXECUTING" -> "Applying your confirmed change"
    else -> "Preparing your plan"
}

private fun formatAgentValue(field: String, value: Any?): String = when {
    value == null -> "None"
    field == "skills" && value is List<*> -> value.joinToString(" · ") { it.toString() }.ifBlank { "No skills" }
    field == "experiences" && value is List<*> -> value.mapIndexed { index, item ->
        val map = item as? Map<*, *>
        val title = map?.get("title")?.toString().orEmpty()
        val company = map?.get("company")?.toString().orEmpty()
        val start = map?.get("startDate")?.toString().orEmpty()
        val end = map?.get("endDate")?.toString()?.takeUnless { it == "null" } ?: "Present"
        "${index + 1}. $title · $company ($start — $end)"
    }.joinToString("\n").ifBlank { "No experiences" }
    else -> value.toString()
}
