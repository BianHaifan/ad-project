package com.adproject.candidate.feature.jobs

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.adproject.candidate.R
import com.adproject.candidate.core.designsystem.AdBackground
import com.adproject.candidate.core.designsystem.AdBorder
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdTealSoft
import com.adproject.candidate.core.designsystem.AdText
import com.adproject.candidate.core.designsystem.FigmaSvg
import com.adproject.candidate.data.model.ChatMessage
import com.adproject.candidate.data.model.ChatThread

@Composable
fun ChatDetailScreen(
    thread: ChatThread,
    onBack: () -> Unit,
    onViewJob: (String) -> Unit,
    onSendMessage: (String) -> ChatMessage,
) {
    val messages = remember(thread.id) { mutableStateListOf<ChatMessage>().apply { addAll(thread.messages) } }
    var draft by remember(thread.id) { mutableStateOf("") }

    fun sendMessage() {
        val body = draft.trim()
        if (body.isNotEmpty()) {
            messages += onSendMessage(body)
            draft = ""
        }
    }

    Column(Modifier.fillMaxSize().background(AdBackground)) {
        ChatHeader(thread, onBack)
        JobContext(thread) { onViewJob(thread.jobId) }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
        ) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        thread.dayLabel,
                        Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFE5EDF0)).padding(horizontal = 10.dp, vertical = 5.dp),
                        color = Color(0xFF6B7885),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            items(messages) { message -> MessageBubble(message) }
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        thread.status,
                        Modifier.clip(RoundedCornerShape(13.dp)).background(AdTealSoft).padding(horizontal = 10.dp, vertical = 6.dp),
                        color = AdTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        MessageComposer(draft, { draft = it }, ::sendMessage)
    }
}

@Composable
private fun ChatHeader(thread: ChatThread, onBack: () -> Unit) {
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
            Text(thread.participantInitial, color = AdTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(thread.participantName, color = AdText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(thread.participantSubtitle, color = Color(0xFF6B7885), fontSize = 12.sp)
        }
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF5F7F9)), contentAlignment = Alignment.Center) {
            Text("•••", color = Color(0xFF6B7885), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun JobContext(thread: ChatThread, onViewJob: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(98.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color.White)
                .border(1.dp, Color(0xFFE8EDF0), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(thread.invitationLabel, color = AdTeal, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Text(thread.jobTitle, color = AdText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(thread.schedule, color = Color(0xFF6B7885), fontSize = 12.sp)
            }
            Box(
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(AdTealSoft).clickable(onClick = onViewJob)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("View job", color = AdTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.sent) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.width(if (message.sent) 260.dp else 306.dp)
                .clip(if (message.sent) RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp) else RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
                .background(if (message.sent) AdTeal else Color.White)
                .then(if (message.sent) Modifier else Modifier.border(1.dp, Color(0xFFE8EDF0), RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(message.body, color = if (message.sent) Color.White else AdText, fontSize = 13.sp, lineHeight = 18.sp)
            Text(message.time, color = if (message.sent) Color(0xFFD4F5F2) else Color(0xFF6B7885), fontSize = 11.sp)
        }
    }
}

@Composable
private fun MessageComposer(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(74.dp).background(Color.White).border(1.dp, Color(0xFFE8EDF0))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFF0F5F6)), contentAlignment = Alignment.Center) {
            Text("＋", color = Color(0xFF6B7885), fontSize = 18.sp)
        }
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
        IconButton(onClick = onSend, modifier = Modifier.size(48.dp), enabled = value.isNotBlank()) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(if (value.isNotBlank()) AdTeal else Color(0xFF9AD7D6)),
                contentAlignment = Alignment.Center,
            ) { Text("↑", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium) }
        }
    }
}
