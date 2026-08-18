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
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
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
import com.adproject.candidate.data.contract.MessageAttachment
import com.adproject.candidate.data.contract.SenderType
import com.adproject.candidate.feature.community.CommunityDirectConversation
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MessagesScreen(
    state: MessagesUiState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onTab: (MainTab) -> Unit,
    onConversation: (String) -> Unit,
    onCommunityConversation: (String) -> Unit = {},
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
                state.message != null && state.conversations.isEmpty() && state.communityConversations.isEmpty() -> Column(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(state.message, color = AdMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Try again", onRetry)
                }
                state.conversations.isEmpty() && state.communityConversations.isEmpty() -> Column(
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
                    if (state.conversations.isNotEmpty()) item {
                        Text("Hiring conversations", Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            color = AdMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    items(state.conversations, key = { it.conversationId }) { conversation ->
                        ConversationRow(conversation, onConversation)
                        HorizontalDivider(color = Color(0xFFE8EDF0))
                    }
                    if (state.communityConversations.isNotEmpty()) item {
                        Text("Community conversations", Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            color = AdMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    items(state.communityConversations, key = { "community-${it.conversationId}" }) { conversation ->
                        CommunityConversationRow(conversation, onCommunityConversation)
                        HorizontalDivider(color = Color(0xFFE8EDF0))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityConversationRow(
    conversation: CommunityDirectConversation,
    onConversation: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(88.dp).clickable { onConversation(conversation.conversationId) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(AdTealSoft), contentAlignment = Alignment.Center) {
            Text(conversation.participant.fullName.take(1).uppercase(), color = AdTeal, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(conversation.participant.fullName, color = AdText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Community direct message", color = AdMuted, fontSize = 12.sp)
        }
        Text(formatConversationTime(conversation.updatedAt), color = Color(0xFF89939D), fontSize = 10.sp)
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
    onSelectAttachment: (PendingAttachment) -> Unit,
    onRemoveAttachment: () -> Unit,
    onDownloadAttachment: (Message) -> Unit,
    onOpenImage: (Message) -> Unit,
    onConsumeDownload: () -> Unit,
    onCloseImagePreview: () -> Unit,
    onViewJob: (String) -> Unit,
    onViewRecruiter: (String) -> Unit,
) {
    val context = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingUri = uri
    }
    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        pendingUri = null
        readPendingAttachment(context, uri)?.let(onSelectAttachment)
    }
    LaunchedEffect(state.downloadEvent) {
        val event = state.downloadEvent ?: return@LaunchedEffect
        openDownloadedAttachment(context, event)
        onConsumeDownload()
    }
    state.imagePreview?.let { preview ->
        ImagePreviewDialog(preview, onCloseImagePreview)
    }
    Column(Modifier.fillMaxSize().background(AdBackground)) {
        ChatHeader(state.conversation, onBack, onViewRecruiter)
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
                    MessageList(
                        messages = state.messages,
                        downloadingMessageId = state.downloadingMessageId,
                        thumbnails = state.imageThumbnails,
                        loadingThumbnails = state.loadingThumbnails,
                        onOpenImage = onOpenImage,
                        onDownload = onDownloadAttachment,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
        MessageComposer(
            value = state.draft,
            attachment = state.attachment,
            sending = state.sending,
            onValueChange = onDraft,
            onSend = onSend,
            onPickAttachment = { filePicker.launch(ATTACHMENT_MIME_TYPES) },
            onRemoveAttachment = onRemoveAttachment,
        )
    }
}

@Composable
private fun ChatHeader(conversation: ConversationDetail?, onBack: () -> Unit, onViewRecruiter: (String) -> Unit) {
    val recruiterId = conversation?.participant?.userId
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
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(AdTealSoft).clickable(enabled = recruiterId != null) { onViewRecruiter(recruiterId.orEmpty()) },
            contentAlignment = Alignment.Center,
        ) {
            Text(conversation?.participant?.fullName?.take(1)?.uppercase() ?: "·", color = AdTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier.weight(1f).clickable(enabled = recruiterId != null) { onViewRecruiter(recruiterId.orEmpty()) },
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
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
private fun MessageList(
    messages: List<Message>,
    downloadingMessageId: String?,
    thumbnails: Map<String, ImagePreview>,
    loadingThumbnails: Set<String>,
    onOpenImage: (Message) -> Unit,
    onDownload: (Message) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        items(messages, key = { it.messageId }) { message ->
            MessageBubble(
                message = message,
                downloading = downloadingMessageId == message.messageId,
                thumbnail = thumbnails[message.messageId],
                thumbnailLoading = message.messageId in loadingThumbnails,
                onOpenImage = onOpenImage,
                onDownload = onDownload,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    downloading: Boolean,
    thumbnail: ImagePreview?,
    thumbnailLoading: Boolean,
    onOpenImage: (Message) -> Unit,
    onDownload: (Message) -> Unit,
) {
    val sent = message.senderType == SenderType.CANDIDATE
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (sent) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.width(if (sent) 260.dp else 306.dp)
                .clip(if (sent) RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp) else RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
                .background(if (sent) AdTeal else Color.White)
                .then(if (sent) Modifier else Modifier.border(1.dp, Color(0xFFE8EDF0), RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (message.body.isNotBlank()) {
                Text(message.body, color = if (sent) Color.White else AdText, fontSize = 13.sp, lineHeight = 18.sp)
            }
            message.attachment?.let { attachment ->
                if (isPreviewableImage(attachment.contentType)) {
                    InlineImageAttachment(
                        attachment = attachment,
                        sent = sent,
                        thumbnail = thumbnail,
                        loading = thumbnailLoading,
                        onOpen = { onOpenImage(message) },
                        onFallbackClick = { onDownload(message) },
                    )
                } else {
                    AttachmentChip(attachment, sent, downloading) { onDownload(message) }
                }
            }
            Text(formatMessageTime(message.sentAt), color = if (sent) Color.White else Color(0xFFD4F5F2), fontSize = 11.sp)
        }
    }
}

@Composable
fun DirectTextBubble(body: String, sentAt: String, sent: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (sent) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.width(if (sent) 260.dp else 306.dp)
                .clip(if (sent) RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp) else RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
                .background(if (sent) AdTeal else Color.White)
                .then(if (sent) Modifier else Modifier.border(1.dp, Color(0xFFE8EDF0), RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(body, color = if (sent) Color.White else AdText, fontSize = 13.sp, lineHeight = 18.sp)
            Text(formatMessageTime(sentAt), color = if (sent) Color.White else Color(0xFF6B7885), fontSize = 11.sp)
        }
    }
}

@Composable
private fun InlineImageAttachment(
    attachment: MessageAttachment,
    sent: Boolean,
    thumbnail: ImagePreview?,
    loading: Boolean,
    onOpen: () -> Unit,
    onFallbackClick: () -> Unit,
) {
    when {
        thumbnail != null -> {
            val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, thumbnail.bytes) {
                value = withContext(Dispatchers.Default) {
                    runCatching { BitmapFactory.decodeByteArray(thumbnail.bytes, 0, thumbnail.bytes.size)?.asImageBitmap() }
                        .getOrNull()
                }
            }
            val decoded = bitmap
            if (decoded != null) {
                Image(
                    bitmap = decoded,
                    contentDescription = attachment.fileName,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen),
                    contentScale = ContentScale.Crop,
                )
            } else {
                AttachmentChip(attachment, sent, downloading = false) { onFallbackClick() }
            }
        }
        loading -> Box(
            Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp))
                .background(if (sent) Color(0x33FFFFFF) else Color(0xFFF0F2F4)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), color = AdTeal, strokeWidth = 2.dp)
        }
        else -> AttachmentChip(attachment, sent, downloading = false) { onFallbackClick() }
    }
}

@Composable
private fun AttachmentChip(attachment: MessageAttachment, sent: Boolean, downloading: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (sent) Color(0x33FFFFFF) else Color(0xFFF0F2F4))
            .clickable(enabled = !downloading, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (downloading) {
            CircularProgressIndicator(Modifier.size(16.dp), color = AdTeal, strokeWidth = 2.dp)
        } else {
            Text("📎", fontSize = 14.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                attachment.fileName,
                color = if (sent) Color.White else AdText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatBytes(attachment.sizeBytes),
                color = if (sent) Color(0xFFD4F5F2) else AdMuted, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ImagePreviewDialog(preview: ImagePreview, onClose: () -> Unit) {
    val bitmap: Bitmap? by produceState<Bitmap?>(initialValue = null, preview) {
        value = withContext(Dispatchers.Default) {
            runCatching { BitmapFactory.decodeByteArray(preview.bytes, 0, preview.bytes.size) }.getOrNull()
        }
    }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        preview.fileName,
                        Modifier.weight(1f), color = AdText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Close",
                        Modifier.clickable(onClick = onClose),
                        color = AdTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                val decoded = bitmap
                if (decoded != null) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF0F2F4)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            decoded.asImageBitmap(),
                            contentDescription = preview.fileName,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("This image could not be displayed.", color = AdMuted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageComposer(
    value: String,
    attachment: PendingAttachment?,
    sending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickAttachment: () -> Unit,
    onRemoveAttachment: () -> Unit,
) {
    val canSend = value.isNotBlank() || attachment != null
    Column(
        Modifier.fillMaxWidth().background(Color.White).border(1.dp, Color(0xFFE8EDF0))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (attachment != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF5F7F9))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "📎 ${attachment.fileName} · ${formatBytes(attachment.sizeBytes)}",
                    Modifier.weight(1f), color = AdText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Remove",
                    Modifier.clickable(onClick = onRemoveAttachment),
                    color = AdTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().height(46.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onPickAttachment, modifier = Modifier.size(46.dp), enabled = !sending) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFF5F7F9)), contentAlignment = Alignment.Center) {
                    Text("+", color = AdText, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                }
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
            if (sending) {
                CircularProgressIndicator(Modifier.size(24.dp), color = AdTeal, strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onSend, modifier = Modifier.size(48.dp), enabled = canSend) {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape).background(if (canSend) AdTeal else Color(0xFF9AD7D6)),
                        contentAlignment = Alignment.Center,
                    ) { Text("↑", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium) }
                }
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

private val ATTACHMENT_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/plain",
    "image/png",
    "image/jpeg",
)

private fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(Locale.US, bytes / 1024.0)
    else -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
}

private suspend fun readPendingAttachment(context: Context, uri: Uri): PendingAttachment? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val meta = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else -1L
                    name to size
                } else null
            }
        val fileName = meta?.first.orEmpty().ifBlank { "attachment" }
        val sizeBytes = meta?.second ?: -1L
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        if (bytes.isEmpty()) null
        else PendingAttachment(fileName, resolver.getType(uri) ?: "application/octet-stream", sizeBytes, bytes)
    }.getOrNull()
}

private suspend fun openDownloadedAttachment(context: Context, event: DownloadEvent) {
    val uri = withContext(Dispatchers.IO) {
        val safeName = event.fileName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "attachment" }
        val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
        val file = File(dir, safeName)
        FileOutputStream(file).use { it.write(event.bytes) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, event.contentType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}
