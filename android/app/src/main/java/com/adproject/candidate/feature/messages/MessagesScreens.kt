package com.adproject.candidate.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adproject.candidate.R
import com.adproject.candidate.core.designsystem.AdBackground
import com.adproject.candidate.core.designsystem.AdBottomBar
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdTealSoft
import com.adproject.candidate.core.designsystem.AdText
import com.adproject.candidate.core.designsystem.FigmaSvg
import com.adproject.candidate.core.designsystem.MainTab
import com.adproject.candidate.core.designsystem.PrimaryButton
import com.adproject.candidate.data.contract.ConversationDetail
import com.adproject.candidate.data.contract.ConversationSummary
import com.adproject.candidate.data.contract.InterviewContext
import com.adproject.candidate.data.contract.Message
import com.adproject.candidate.data.contract.SenderType

@Composable
fun MessagesScreen(
    state: MessagesUiState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onTab: (MainTab) -> Unit,
    onConversation: (String) -> Unit,
) {
    Scaffold(bottomBar = { AdBottomBar(MainTab.Messages, onTab) }, containerColor = AdBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Messages", color = Color(0xFF0E1114), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Recruiters and hiring teams", color = Color(0xFF6B7885), fontSize = 12.sp)
                }
                Text(
                    if (state.refreshing) "Refreshing…" else "Refresh",
                    Modifier.clickable(enabled = !state.refreshing, onClick = onRefresh),
                    color = AdTeal, fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            when {
                state.loading -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = AdTeal) }
                state.message != null && state.conversations.isEmpty() -> Column(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(state.message, color = AdMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Try again", onRetry)
                }
                state.conversations.isEmpty() -> Column(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("No conversations yet", color = AdText, fontWeight = FontWeight.SemiBold)
                    Text("Messages from recruiters will appear here.", color = AdMuted, fontSize = 12.sp)
                }
                else -> LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White),
                ) {
                    items(state.conversations, key = { it.conversationId }) { conversation ->
                        ConversationRow(conversation, onConversation)
                        HorizontalDivider(color = Color(0xFFE8EDF0))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: ConversationSummary, onConversation: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(104.dp).clickable { onConversation(conversation.conversationId) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(AdTealSoft), contentAlignment = Alignment.Center) {
            Text(conversation.participant.fullName.take(1).uppercase(), color = AdTeal, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(conversation.participant.fullName, color = AdText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                conversation.lastMessage?.body ?: "No messages yet",
                color = AdMuted, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(formatConversationTime(conversation.updatedAt), color = Color(0xFF89939D), fontSize = 10.sp)
            if (conversation.unreadCount > 0) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(AdTeal), contentAlignment = Alignment.Center) {
                    Text(conversation.unreadCount.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onViewJob: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(AdBackground)) {
        ChatHeader(state.conversation, onBack)
        state.conversation?.context?.let { context ->
            JobContextCard(context, state.conversation.jobId, onViewJob)
        }
        when {
            state.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AdTeal)
            }
            state.notFound || (state.message != null && state.conversation == null) -> Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.message ?: "This conversation is no longer available.", color = AdMuted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Try again", onRetry)
            }
            else -> {
                state.message?.let { message ->
                    Text(
                        message,
                        Modifier.fillMaxWidth().background(Color(0xFFFFF4EC)).padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color(0xFFB42318), fontSize = 12.sp,
                    )
                }
                if (state.messages.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No messages yet", color = AdMuted, fontSize = 13.sp)
                    }
                } else {
                    MessageList(state.messages, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
        MessageComposer(state.draft, state.sending, onDraft, onSend)
    }
}

@Composable
private fun ChatHeader(conversation: ConversationDetail?, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(80.dp).background(Color.White).border(1.dp, Color(0xFFE8EDF0))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF5F7F9)), contentAlignment = Alignment.Center) {
                FigmaSvg(R.raw.icon_back, "Back to messages", Modifier.size(22.dp))
            }
        }
        Box(Modifier.size(42.dp).clip(CircleShape).background(AdTealSoft), contentAlignment = Alignment.Center) {
            Text(conversation?.participant?.fullName?.take(1)?.uppercase() ?: "·", color = AdTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(conversation?.participant?.fullName ?: "…", color = AdText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                conversation?.participant?.company?.name ?: conversation?.participant?.title ?: "Recruiter",
                color = Color(0xFF6B7885), fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun JobContextCard(context: InterviewContext, jobId: String, onViewJob: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().height(98.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color.White)
                .border(1.dp, Color(0xFFE8EDF0), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("INTERVIEW", color = AdTeal, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Text(context.jobTitle, color = AdText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatInterviewTime(context.scheduledAt)} · ${context.mode.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    color = Color(0xFF6B7885), fontSize = 12.sp,
                )
            }
            Box(
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(AdTealSoft).clickable { onViewJob(jobId) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("View job", color = AdTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun MessageList(messages: List<Message>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages, key = { it.messageId }) { message -> MessageBubble(message) }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val sent = message.senderType == SenderType.CANDIDATE
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (sent) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.width(if (sent) 260.dp else 306.dp)
                .clip(if (sent) RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp) else RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
                .background(if (sent) AdTeal else Color.White)
                .then(if (sent) Modifier else Modifier.border(1.dp, Color(0xFFE8EDF0), RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(message.body, color = if (sent) Color.White else AdText, fontSize = 13.sp, lineHeight = 18.sp)
            Text(formatMessageTime(message.sentAt), color = if (sent) Color(0xFFD4F5F2) else Color(0xFF6B7885), fontSize = 11.sp)
        }
    }
}

@Composable
private fun MessageComposer(
    value: String,
    sending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(74.dp).background(Color.White).border(1.dp, Color(0xFFE8EDF0))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).height(46.dp),
            singleLine = true,
            textStyle = TextStyle(color = AdText, fontSize = 13.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            decorationBox = { input ->
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(23.dp)).background(Color(0xFFF5F7F9)).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) Text("Write a message…", color = Color(0xFF6B7885), fontSize = 13.sp)
                    input()
                }
            },
        )
        if (sending) {
            CircularProgressIndicator(Modifier.size(24.dp), color = AdTeal, strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onSend, modifier = Modifier.size(48.dp), enabled = value.isNotBlank()) {
                Box(
                    Modifier.fillMaxSize().clip(CircleShape).background(if (value.isNotBlank()) AdTeal else Color(0xFF9AD7D6)),
                    contentAlignment = Alignment.Center,
                ) { Text("↑", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

private fun formatConversationTime(value: String): String = runCatching {
    java.time.OffsetDateTime.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm"))
}.getOrDefault(value)

private fun formatInterviewTime(value: String): String = runCatching {
    java.time.OffsetDateTime.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d · h:mm a"))
}.getOrDefault(value)

private fun formatMessageTime(value: String): String = runCatching {
    java.time.OffsetDateTime.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault(value)
