package com.adproject.candidate.feature.applications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adproject.candidate.core.designsystem.*
import com.adproject.candidate.data.contract.*
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun RealMyApplicationsScreen(
    state: ApplicationListUiState,
    onBack: () -> Unit,
    onTab: (MainTab) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onFilter: (ApplicationListFilter) -> Unit,
    onLoadMore: () -> Unit,
    onApplication: (String) -> Unit,
) {
    Scaffold(
        topBar = { AdTopBar("My applications", onBack, action = {
            TextButton(onClick = onRefresh, enabled = !state.refreshing && !state.loading) {
                Text(if (state.refreshing) "Refreshing…" else "Refresh", color = AdTealDark, fontSize = 11.sp)
            }
        }) },
        bottomBar = { AdBottomBar(MainTab.Me, onTab) },
        containerColor = AdBackground,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ApplicationFilters(state, onFilter)
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AdTeal)
                }
                state.message != null && state.applications.isEmpty() -> ApplicationMessage(state.message, onRetry)
                state.applications.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No applications in this group yet.", color = AdMuted)
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                    items(state.applications, key = { it.applicationId }) { application ->
                        RealApplicationCard(application) { onApplication(application.applicationId) }
                    }
                    if (state.hasNext) item {
                        SecondaryButton(
                            if (state.loadingMore) "Loading…" else "Load more",
                            onLoadMore, Modifier.fillMaxWidth(), enabled = !state.loadingMore,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicationFilters(state: ApplicationListUiState, onFilter: (ApplicationListFilter) -> Unit) {
    val filters = listOf(
        Triple(ApplicationListFilter.ACTIVE, "Active", state.counts.active),
        Triple(ApplicationListFilter.INTERVIEW, "Interview", state.counts.interview),
        Triple(ApplicationListFilter.ARCHIVED, "Archived", state.counts.archived),
    )
    Row(Modifier.fillMaxWidth().background(Color.White).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        filters.forEach { (filter, label, count) ->
            val selected = state.filter == filter
            Box(
                Modifier.weight(1f).background(
                    if (selected) AdTealSoft else Color(0xFFF4F6F7), RoundedCornerShape(11.dp),
                ).clickable(enabled = !state.loading && !state.refreshing) { onFilter(filter) }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("$label  $count", color = if (selected) AdTealDark else AdMuted, fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun RealApplicationCard(application: CandidateApplicationSummary, onClick: () -> Unit) {
    AdCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(application.jobTitle, color = AdText, fontWeight = FontWeight.SemiBold)
                    Text(application.company.name, color = AdMuted, fontSize = 11.sp)
                }
                TagChip(application.status.label(), accent = true)
            }
            Text("Applied ${application.appliedAt.displayTime()}", color = AdMuted, fontSize = 10.sp)
            application.scheduledAt?.let { Text("Interview ${it.displayTime()}", color = AdTealDark, fontSize = 10.sp) }
            application.matchScore?.let { Text("$it% match", color = AdTealDark, fontSize = 10.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                application.timeline.forEach { StatusDot(it.status.label(), it.completed) }
            }
        }
    }
}

@Composable
private fun InterviewCard(interview: Interview?) {
    val uriHandler = LocalUriHandler.current
    AdCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Interview", fontWeight = FontWeight.SemiBold)
            if (interview == null) {
                Text("Interview not scheduled", color = AdMuted, fontSize = 12.sp)
            } else {
                val meeting = meetingDisplay(interview)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TagChip(interview.status.label(), accent = interview.status == InterviewStatus.SCHEDULED)
                    meeting.providerLabel?.let { TagChip(it) }
                }
                InterviewRow("When", interview.scheduledAt.displayTime())
                InterviewRow("Timezone", interview.timezone)
                InterviewRow("Duration", "${interview.durationMinutes} minutes")
                InterviewRow("Mode", interview.mode.label())
                if (interview.status != InterviewStatus.CANCELLED) {
                    interview.locationOrMeetingUrl?.takeIf { it.isNotBlank() }?.let { location ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(locationLabel(interview.mode), color = AdMuted, fontSize = 11.sp,
                                modifier = Modifier.width(86.dp))
                            if (meeting.linkOpenable) {
                                Text(
                                    text = location,
                                    color = AdTealDark,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                        .clickable { runCatching { uriHandler.openUri(location) } },
                                )
                            } else {
                                Text(location, color = AdText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                meeting.statusHint?.let { Text(it, color = AdMuted, fontSize = 11.sp) }
                when (interview.status) {
                    InterviewStatus.CANCELLED -> Text("This interview was cancelled.", color = AdMuted, fontSize = 11.sp)
                    InterviewStatus.COMPLETED -> Text("This interview is completed.", color = AdMuted, fontSize = 11.sp)
                    InterviewStatus.SCHEDULED -> Unit
                }
            }
        }
    }
}

@Composable
private fun InterviewRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AdMuted, fontSize = 11.sp, modifier = Modifier.width(86.dp))
        Text(value, color = AdText, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ApplicationMessage(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(message, color = AdMuted)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Try again", onRetry)
    }
}

@Composable
fun RealApplicationDetailScreen(
    state: ApplicationDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRequestWithdraw: () -> Unit,
    onDismissWithdraw: () -> Unit,
    onWithdrawReason: (String) -> Unit,
    onConfirmWithdraw: () -> Unit,
) {
    if (state.confirmingWithdraw) {
        AlertDialog(
            onDismissRequest = onDismissWithdraw,
            title = { Text("Withdraw application?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("This action is final. Your application and submitted resume snapshot will remain in your history.")
                    OutlinedTextField(
                        value = state.withdrawReason,
                        onValueChange = onWithdrawReason,
                        label = { Text("Reason") },
                        minLines = 2,
                        enabled = !state.withdrawing,
                    )
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmWithdraw, enabled = !state.withdrawing) {
                    Text(if (state.withdrawing) "Withdrawing…" else "Withdraw")
                }
            },
            dismissButton = { TextButton(onClick = onDismissWithdraw, enabled = !state.withdrawing) { Text("Cancel") } },
        )
    }

    Scaffold(topBar = { AdTopBar("Application detail", onBack) }, containerColor = AdBackground) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AdTeal)
            }
            state.application == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(if (state.notFound) "This application was not found." else state.message
                    ?: "Unable to load this application.", color = AdMuted)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Try again", onRetry)
            }
            else -> ApplicationDetailContent(state, onRequestWithdraw, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ApplicationDetailContent(state: ApplicationDetailUiState, onRequestWithdraw: () -> Unit,
                                     modifier: Modifier = Modifier) {
    val application = state.application ?: return
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AdCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(application.company.name, color = AdTealDark, fontWeight = FontWeight.SemiBold)
                Text(application.jobTitle, color = AdText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                TagChip(application.status.label(), accent = true)
                Text("Applied ${application.appliedAt.displayTime()}", color = AdMuted, fontSize = 11.sp)
                Text("Updated ${application.updatedAt.displayTime()} · version ${application.version}", color = AdMuted, fontSize = 11.sp)
                application.matchScore?.let { Text("$it% match", color = AdTealDark) }
            }
        }
        InterviewCard(application.interview)
        AdCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Status timeline", fontWeight = FontWeight.SemiBold)
                application.timeline.forEach { step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(step.status.label(), step.completed)
                        Spacer(Modifier.width(8.dp))
                        Text(step.occurredAt?.displayTime().orEmpty(), color = AdMuted, fontSize = 10.sp)
                    }
                }
            }
        }
        val snapshot = application.resumeSnapshot
        AdCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Submitted resume snapshot", fontWeight = FontWeight.SemiBold)
                Text(snapshot.fullName, color = AdText, fontWeight = FontWeight.SemiBold)
                Text("${snapshot.headline} · age ${snapshot.age} · ${snapshot.location}", color = AdMuted)
                Text(snapshot.summary, color = AdText)
                Text("Captured ${snapshot.capturedAt.displayTime()} · resume v${snapshot.version}", color = AdTealDark,
                    fontSize = 10.sp)
                snapshot.experiences.forEach { experience ->
                    HorizontalDivider(color = Color(0xFFE7ECEF))
                    Text(experience.title, fontWeight = FontWeight.SemiBold)
                    Text(experience.company, color = AdMuted)
                    Text(experience.description, color = AdText, fontSize = 11.sp)
                    Text("${experience.startDate} – ${experience.endDate ?: "Present"}", color = AdMuted, fontSize = 10.sp)
                }
            }
        }
        state.message?.let { Text(it, color = if (application.status == ApplicationStatus.WITHDRAWN) AdTealDark
            else MaterialTheme.colorScheme.error) }
        if (ApplicationDetailViewModel.canWithdraw(application.status)) {
            SecondaryButton("Withdraw application", onRequestWithdraw, Modifier.fillMaxWidth(), enabled = !state.withdrawing)
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun ApplicationStatus.label() = when (this) {
    ApplicationStatus.APPLIED -> "Applied"
    ApplicationStatus.IN_REVIEW -> "In review"
    ApplicationStatus.INTERVIEW -> "Interview"
    ApplicationStatus.OFFERED -> "Offer received"
    ApplicationStatus.REJECTED -> "Rejected"
    ApplicationStatus.WITHDRAWN -> "Withdrawn"
}

private fun InterviewStatus.label() = when (this) {
    InterviewStatus.SCHEDULED -> "Scheduled"
    InterviewStatus.COMPLETED -> "Completed"
    InterviewStatus.CANCELLED -> "Cancelled"
}

private fun InterviewMode.label() = when (this) {
    InterviewMode.ONLINE -> "Online"
    InterviewMode.ONSITE -> "On-site"
    InterviewMode.PHONE -> "Phone"
}

private fun locationLabel(mode: InterviewMode) = when (mode) {
    InterviewMode.ONLINE -> "Meeting link"
    InterviewMode.ONSITE -> "Location"
    InterviewMode.PHONE -> "Phone / contact"
}

private fun String.displayTime(): String = runCatching {
    OffsetDateTime.parse(this).format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm"))
}.getOrDefault(this)
