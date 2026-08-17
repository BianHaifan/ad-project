package com.adproject.candidate.feature.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adproject.candidate.R
import com.adproject.candidate.core.designsystem.AdBackground
import com.adproject.candidate.core.designsystem.AdBottomBar
import com.adproject.candidate.core.designsystem.AdCard
import com.adproject.candidate.core.designsystem.AdChip
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdTealDark
import com.adproject.candidate.core.designsystem.AdTealSoft
import com.adproject.candidate.core.designsystem.AdText
import com.adproject.candidate.core.designsystem.FigmaSvg
import com.adproject.candidate.core.designsystem.MainTab
import com.adproject.candidate.core.designsystem.TagChip
import com.adproject.candidate.core.designsystem.PrimaryButton
import com.adproject.candidate.data.contract.EmploymentType
import com.adproject.candidate.data.contract.WorkplaceType
import com.adproject.candidate.data.model.CandidateProfile
import com.adproject.candidate.data.model.Job
import com.adproject.candidate.data.model.JobFeedData
import com.adproject.candidate.data.model.LearningData
import com.adproject.candidate.data.model.ProfileTool
import com.adproject.candidate.feature.profile.COMMON_LOCATIONS
import com.adproject.candidate.feature.profile.SALARY_OPTIONS
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun JobFeedScreen(
    state: JobFeedUiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onEmploymentType: (EmploymentType?) -> Unit,
    onWorkplaceType: (WorkplaceType?) -> Unit,
    onLocation: (String?) -> Unit,
    onMinimumSalary: (Long?) -> Unit,
    onClearFilters: () -> Unit,
    onToggleSave: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onTab: (MainTab) -> Unit,
    onJob: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    var showFilters by remember { mutableStateOf(false) }
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
    Scaffold(bottomBar = { AdBottomBar(MainTab.Jobs, onTab) }, containerColor = AdBackground) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        item(key = "all") {
                            Text(
                                "All",
                                Modifier.clickable { onEmploymentType(null) },
                                fontSize = if (state.employmentType == null) 22.sp else 17.sp,
                                fontWeight = if (state.employmentType == null) FontWeight.Bold else FontWeight.Normal,
                                color = if (state.employmentType == null) AdText else Color(0xFF6E7781),
                                maxLines = 1,
                            )
                        }
                        items(EmploymentType.entries, key = { it.name }) { type ->
                            Text(
                                type.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
                                Modifier.clickable { onEmploymentType(type) },
                                fontSize = if (state.employmentType == type) 22.sp else 17.sp,
                                fontWeight = if (state.employmentType == type) FontWeight.Bold else FontWeight.Normal,
                                color = if (state.employmentType == type) AdText else Color(0xFF6E7781),
                                maxLines = 1,
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQuery,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Search job titles", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                        )
                        Text("Search", Modifier.clickable(onClick = onSearch), color = AdTealDark,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Recommended for you", color = AdText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            state.data?.recommendationSource?.let { source ->
                                Text(if (source == "MODEL") "ML model • ${state.data.modelVersion}"
                                    else "Rules fallback • model temporarily unavailable",
                                    color = if (source == "MODEL") AdTealDark else Color(0xFFFF8500), fontSize = 10.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Filter", Modifier.clickable(onClick = { showFilters = true }), color = AdTealDark, fontSize = 13.sp)
                            Text(if (state.refreshing) "Refreshing…" else "Refresh",
                                Modifier.clickable(enabled = !state.refreshing, onClick = onRefresh),
                                color = AdTealDark, fontSize = 13.sp)
                        }
                    }
                    val activeFilters = buildList {
                        state.employmentType?.let { add("Job type: ${it.label()}") }
                        state.workplaceType?.let { add("Workplace: ${it.label()}") }
                        state.location?.takeIf { it.isNotBlank() }?.let { add("Location: $it") }
                        state.minimumSalary?.let { add("Salary: S$%,d+".format(it)) }
                    }
                    if (activeFilters.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            activeFilters.forEach { label ->
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = filterChipColors(),
                                )
                            }
                            Text("Clear all", Modifier.clickable(onClick = onClearFilters)
                                .padding(vertical = 8.dp), color = AdTealDark, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            when {
                state.loading -> item {
                    Box(Modifier.fillParentMaxSize().padding(72.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AdTeal)
                    }
                }
                state.message != null && state.data == null -> item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(state.message, color = AdMuted, fontSize = 13.sp)
                        PrimaryButton("Try again", onRetry)
                    }
                }
                state.data?.jobs.isNullOrEmpty() -> item {
                    Column(Modifier.fillMaxWidth().padding(42.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No matching jobs found", color = AdText, fontWeight = FontWeight.SemiBold)
                        Text("Try another title or employment type.", color = AdMuted, fontSize = 12.sp)
                    }
                }
                else -> {
                    state.message?.let { message -> item {
                        Text(message, Modifier.fillMaxWidth().padding(16.dp), color = Color(0xFFB42318), fontSize = 12.sp)
                    } }
                    state.saveError?.let { message -> item {
                        Text(message, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                            color = Color(0xFFB42318), fontSize = 12.sp)
                    } }
                    items(state.data?.jobs.orEmpty(), key = { it.jobId }) { job -> JobCard(job, onJob, onToggleSave) }
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
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
    if (showFilters) {
        JobFilterSheet(
            state = state,
            onEmploymentType = onEmploymentType,
            onWorkplaceType = onWorkplaceType,
            onLocation = onLocation,
            onMinimumSalary = onMinimumSalary,
            onClearAll = onClearFilters,
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
internal fun JobCard(job: Job, onJob: (String) -> Unit, onToggleSave: (String) -> Unit) {
    AdCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp).clickable { onJob(job.jobId) }) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(job.title, Modifier.weight(1f), color = AdText, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
                Text(job.salary, color = AdTeal, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Text("${job.company} · ${job.companyMeta}", color = AdMuted, fontSize = 14.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { job.skills.forEach { JobSkillChip(it) } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FigmaSvg(R.raw.recruiter_avatar, "Recruiter avatar", Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text(job.recruiter?.let { "${it.fullName} - ${it.title}" } ?: "Recruiter contact unavailable",
                    Modifier.weight(1f), color = Color(0xFF34404B), fontSize = 12.sp)
                job.match?.let { TagChip("AI Match $it%", accent = true) }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (job.isSaved) "Saved" else "Save",
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleSave(job.jobId) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (job.isSaved) AdTealDark else AdMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun JobSkillChip(text: String) {
    Box(
        Modifier
            .widthIn(max = 160.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(AdChip)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            color = Color(0xFF687385),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobFilterSheet(
    state: JobFeedUiState,
    onEmploymentType: (EmploymentType?) -> Unit,
    onWorkplaceType: (WorkplaceType?) -> Unit,
    onLocation: (String?) -> Unit,
    onMinimumSalary: (Long?) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Filter jobs", color = AdText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Clear all", Modifier.clickable(onClick = onClearAll), color = AdTealDark,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            FilterSection("Job type") {
                EmploymentType.entries.forEach { type ->
                    FilterChip(
                        selected = state.employmentType == type,
                        onClick = { onEmploymentType(if (state.employmentType == type) null else type) },
                        label = { Text(type.label()) },
                        colors = filterChipColors(),
                    )
                }
            }
            FilterSection("Workplace") {
                WorkplaceType.entries.forEach { type ->
                    FilterChip(
                        selected = state.workplaceType == type,
                        onClick = { onWorkplaceType(if (state.workplaceType == type) null else type) },
                        label = { Text(type.label()) },
                        colors = filterChipColors(),
                    )
                }
            }
            FilterSection("Location") {
                COMMON_LOCATIONS.forEach { location ->
                    FilterChip(
                        selected = state.location == location,
                        onClick = { onLocation(if (state.location == location) null else location) },
                        label = { Text(location) },
                        colors = filterChipColors(),
                    )
                }
            }
            FilterSection("Minimum salary") {
                SALARY_OPTIONS.forEach { salary ->
                    FilterChip(
                        selected = state.minimumSalary == salary,
                        onClick = { onMinimumSalary(if (state.minimumSalary == salary) null else salary) },
                        label = { Text("S$%,d+".format(salary)) },
                        colors = filterChipColors(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = AdText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AdTealSoft,
    selectedLabelColor = AdTealDark,
)

private fun EmploymentType.label(): String = name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
private fun WorkplaceType.label(): String = name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)

@Composable
fun LearningScreen(data: LearningData, onTab: (MainTab) -> Unit) {
    Scaffold(bottomBar = { AdBottomBar(MainTab.Learn, onTab) }, containerColor = AdBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Learning", Modifier.fillMaxWidth(), color = Color(0xFF0E1114), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(72.dp))
            AdCard(Modifier.fillMaxWidth().height(320.dp)) {
                Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(AdTealSoft), contentAlignment = Alignment.Center) {
                        Text("L", color = AdTeal, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                    TagChip(data.badge, accent = true)
                    Spacer(Modifier.height(14.dp))
                    Text(data.title, color = AdText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(26.dp))
                    Text(data.description, color = Color(0xFF6B7885), fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(data: CandidateProfile, onTab: (MainTab) -> Unit, onApplications: () -> Unit,
                  onResume: () -> Unit, onLogout: () -> Unit) {
    Scaffold(bottomBar = { AdBottomBar(MainTab.Me, onTab) }, containerColor = AdBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FigmaSvg(R.raw.profile_avatar, data.fullName, Modifier.size(72.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(data.fullName, color = AdText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(data.headline, color = Color(0xFF6E7781), fontSize = 15.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    data.stats.forEach { stat ->
                        ProfileStat(
                            stat.value,
                            stat.label,
                            if (stat.label == "Applied") Modifier.clickable(onClick = onApplications) else Modifier,
                        )
                    }
                }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                data.toolGroups.forEach { group -> ToolCard(group.title, group.tools, onResume) }
                PrimaryButton("Sign out", onLogout, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = AdText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFF6E7781), fontSize = 12.sp)
    }
}

@Composable
private fun ToolCard(title: String, tools: List<ProfileTool>, onResume: () -> Unit) {
    AdCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(title, color = AdText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            tools.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { tool ->
                        Column(
                            Modifier.width(66.dp).clickable(enabled = tool.action == "resume", onClick = onResume),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(AdTealSoft), contentAlignment = Alignment.Center) {
                                Text(tool.symbol, color = AdTeal, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text(tool.label, color = AdText, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
