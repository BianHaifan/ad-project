package com.adproject.candidate.feature.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adproject.candidate.core.designsystem.AdBackground
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdTealDark
import com.adproject.candidate.core.designsystem.AdText
import com.adproject.candidate.core.designsystem.AdTopBar
import com.adproject.candidate.core.designsystem.PrimaryButton
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SavedJobsScreen(
    state: SavedJobsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onJob: (String) -> Unit,
    onUnsave: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to listState.layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 3) onLoadMore()
            }
    }
    Column(Modifier.fillMaxSize().background(AdBackground)) {
        AdTopBar("Saved jobs", onBack)
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AdTeal)
            }
            state.message != null && state.jobs.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.message, color = AdMuted, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Try again", onRetry)
            }
            state.jobs.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No saved jobs yet", color = AdText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Tap Save on a job to keep it here.", color = AdMuted, fontSize = 12.sp)
            }
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${state.total} saved", color = AdMuted, fontSize = 12.sp)
                        Text(
                            if (state.refreshing) "Refreshing…" else "Refresh",
                            Modifier.clickable(enabled = !state.refreshing, onClick = onRefresh),
                            color = AdTealDark, fontSize = 13.sp,
                        )
                    }
                }
                state.saveError?.let { message -> item {
                    Text(message, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        color = Color(0xFFB42318), fontSize = 12.sp)
                } }
                items(state.jobs, key = { it.jobId }) { job -> JobCard(job, onJob, onUnsave) }
                when {
                    state.loadingMore -> item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AdTeal, modifier = Modifier.size(28.dp))
                        }
                    }
                    state.loadMoreError -> item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Couldn't load more jobs.", color = AdMuted, fontSize = 13.sp)
                            PrimaryButton("Try again", onRetryLoadMore)
                        }
                    }
                    !state.hasNext -> item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("You're all caught up", color = AdMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
