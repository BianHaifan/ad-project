package com.adproject.candidate.feature.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adproject.candidate.core.designsystem.AdBackground
import com.adproject.candidate.core.designsystem.AdCard
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdText
import com.adproject.candidate.core.designsystem.AdTopBar
import com.adproject.candidate.core.designsystem.PrimaryButton
import com.adproject.candidate.core.designsystem.SecondaryButton
import coil3.compose.AsyncImage

@Composable
fun CommunityDetailScreen(
    state: CommunityDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleLike: () -> Unit,
    onComment: (String) -> Unit,
    onPublishComment: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryComments: () -> Unit,
    onMessageAuthor: () -> Unit,
) {
    val normalized = normalizeCommunityText(state.commentDraft)
    val count = normalized.codePointCount(0, normalized.length)
    Scaffold(containerColor = AdBackground, topBar = { AdTopBar("Post", onBack) }) { padding ->
        when {
            state.loading && state.post == null -> StateBox { CircularProgressIndicator(color = AdTeal) }
            state.post == null -> StateBox {
                Text(state.error ?: "Post is unavailable", color = AdMuted)
                SecondaryButton("Try again", onRetry)
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { CommunityPostCard(state.post) }
                if (state.post.images.isNotEmpty()) item { Column(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    state.post.images.forEach { image -> AsyncImage(
                        model = image.url,
                        contentDescription = "Post attachment",
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    ) }
                } }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.error?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SecondaryButton("Message author", onMessageAuthor)
                            SecondaryButton(
                                if (state.liking) "Updating…"
                                else if (state.post.likedByCurrentUser) "Unlike (${state.post.likeCount})"
                                else "Like (${state.post.likeCount})",
                                onToggleLike,
                                enabled = !state.liking,
                            )
                        }
                    }
                }
                item { Text("Comments (${state.post.commentCount})", Modifier.padding(horizontal = 18.dp), color = AdText, fontWeight = FontWeight.Bold) }
                item {
                    AdCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(state.commentDraft, onComment, Modifier.fillMaxWidth(), minLines = 2,
                                placeholder = { Text("Write a comment") }, isError = count !in 1..500 && state.commentDraft.isNotEmpty(),
                                supportingText = { Text("$count / 500") })
                            state.commentError?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
                            PrimaryButton(if (state.submitting) "Posting…" else "Post comment", onPublishComment, Modifier.fillMaxWidth(),
                                enabled = !state.submitting && count in 1..500)
                        }
                    }
                }
                when {
                    state.loadingComments && state.comments.isEmpty() -> item { StateBox { CircularProgressIndicator(color = AdTeal) } }
                    state.commentError != null && state.comments.isEmpty() -> item { StateBox {
                        Text(state.commentError, color = Color(0xFFB42318), fontSize = 12.sp)
                        SecondaryButton("Retry comments", onRetryComments)
                    } }
                    state.comments.isEmpty() -> item { StateBox { Text("No comments yet", color = AdMuted) } }
                    else -> items(state.comments, key = CommunityComment::id) { comment ->
                        AdCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CommunityAvatar(comment.author)
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(comment.author.fullName, color = AdText, fontWeight = FontWeight.SemiBold)
                                    Text(comment.author.companyName ?: comment.author.role.lowercase().replaceFirstChar(Char::uppercase), color = AdMuted, fontSize = 11.sp)
                                    Text(comment.body, color = AdText)
                                    Text(localTime(comment.createdAt), color = AdMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                if (state.commentError != null && state.comments.isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(state.commentError, color = Color(0xFFB42318), fontSize = 12.sp)
                        SecondaryButton("Retry comments", onRetryComments, Modifier.fillMaxWidth())
                    }
                } else state.hasNext.let { hasNext -> if (hasNext) item {
                    SecondaryButton(if (state.loadingMore) "Loading…" else "Load more", onLoadMore,
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp), enabled = !state.loadingMore)
                } }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}
