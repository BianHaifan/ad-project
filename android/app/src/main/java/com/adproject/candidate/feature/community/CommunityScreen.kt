package com.adproject.candidate.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.adproject.candidate.core.designsystem.AdBackground
import com.adproject.candidate.core.designsystem.AdBottomBar
import com.adproject.candidate.core.designsystem.AdCard
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdTealSoft
import com.adproject.candidate.core.designsystem.AdText
import com.adproject.candidate.core.designsystem.AdTopBar
import com.adproject.candidate.core.designsystem.MainTab
import com.adproject.candidate.core.designsystem.PrimaryButton
import com.adproject.candidate.core.designsystem.SecondaryButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class CommunityScreenMode { LOADING, ERROR, EMPTY, CONTENT }
fun communityScreenMode(state: CommunityUiState) = when {
    state.loading && state.posts.isEmpty() -> CommunityScreenMode.LOADING
    state.loadError != null && state.posts.isEmpty() -> CommunityScreenMode.ERROR
    state.posts.isEmpty() -> CommunityScreenMode.EMPTY
    else -> CommunityScreenMode.CONTENT
}

@Composable
fun CommunityScreen(
    state: CommunityUiState,
    onTab: (MainTab) -> Unit,
    onDraft: (String) -> Unit,
    onPublish: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPost: (String) -> Unit,
) {
    val length = state.draft.codePointCount(0, state.draft.length)
    Scaffold(
        containerColor = AdBackground,
        topBar = { AdTopBar("Community") },
        bottomBar = { AdBottomBar(MainTab.Community, onTab) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AdCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Share an update", color = AdText, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = state.draft, onValueChange = onDraft, modifier = Modifier.fillMaxWidth(),
                            minLines = 3, placeholder = { Text("What would you like to share?") },
                            supportingText = { Text("$length / 2000") }, isError = length > 2000,
                        )
                        state.publishError?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
                        PrimaryButton(
                            if (state.submitting) "Publishing…" else "Publish", onPublish, Modifier.fillMaxWidth(),
                            enabled = !state.submitting && state.draft.isNotBlank() && length <= 2000,
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.End) {
                    SecondaryButton(if (state.refreshing) "Refreshing…" else "Refresh", onRefresh, enabled = !state.refreshing)
                }
            }
            when (communityScreenMode(state)) {
                CommunityScreenMode.LOADING -> item { StateBox { CircularProgressIndicator(color = AdTeal) } }
                CommunityScreenMode.ERROR -> item { StateBox {
                    Text(state.loadError ?: "Unable to load Community", color = AdMuted)
                    SecondaryButton("Try again", onRetry)
                } }
                CommunityScreenMode.EMPTY -> item { StateBox {
                    Text("No posts yet", color = AdText, fontWeight = FontWeight.Bold)
                    Text("Start the Community conversation.", color = AdMuted, fontSize = 12.sp)
                } }
                CommunityScreenMode.CONTENT -> {
                    state.loadError?.let { item { Text(it, Modifier.padding(horizontal = 18.dp), color = Color(0xFFB42318), fontSize = 12.sp) } }
                    items(state.posts, key = CommunityPost::id) { post -> CommunityPostCard(post) { onPost(post.id) } }
                    if (state.hasNext) item {
                        SecondaryButton(
                            if (state.loadingMore) "Loading…" else "Load more", onLoadMore,
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp), enabled = !state.loadingMore,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable internal fun StateBox(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable internal fun CommunityPostCard(post: CommunityPost, onClick: (() -> Unit)? = null) {
    AdCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CommunityAvatar(post.author)
                Column {
                    Text(post.author.fullName, color = AdText, fontWeight = FontWeight.SemiBold)
                    Text(post.author.companyName ?: post.author.role.lowercase().replaceFirstChar(Char::uppercase), color = AdMuted, fontSize = 11.sp)
                }
            }
            Text(post.body, color = AdText, lineHeight = 21.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(localTime(post.createdAt), color = AdMuted, fontSize = 11.sp)
                Text("♡ ${post.likeCount}   Comments ${post.commentCount}", color = AdMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable internal fun CommunityAvatar(author: CommunityAuthor) {
    if (author.avatarUrl != null) {
        AsyncImage(author.avatarUrl, author.fullName, Modifier.size(42.dp).clip(CircleShape))
    } else {
        Box(Modifier.size(42.dp).clip(CircleShape).background(AdTealSoft), contentAlignment = Alignment.Center) {
            Text(author.fullName.take(1).uppercase().ifBlank { "?" }, color = AdTeal, fontWeight = FontWeight.Bold)
        }
    }
}

internal fun localTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value))
}.getOrDefault(value)
