package com.adproject.candidate.feature.applications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adproject.candidate.core.designsystem.*
import com.adproject.candidate.data.contract.CandidateApplication
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun RealApplyConfirmationScreen(
    state: ApplicationFlowUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCreateResume: () -> Unit,
    onShareProfile: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    Scaffold(topBar = { AdTopBar("Confirm application", onBack) }, containerColor = AdBackground) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AdTeal)
            }
            state.resumeMissing -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Create your default resume before applying.", color = AdText)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Create resume", onCreateResume)
                Spacer(Modifier.height(10.dp))
                SecondaryButton("I created it · reload", onRetry)
            }
            state.job == null || state.profile == null || state.resume == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.message ?: "Unable to prepare this application.", color = AdMuted)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Try again", onRetry)
            }
            else -> {
                val job = state.job.job
                val resume = state.resume
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AdCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(job.company.name, color = AdTealDark, fontWeight = FontWeight.SemiBold)
                            Text(job.title, color = AdText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("${job.salary.currency} ${job.salary.min}–${job.salary.max} · ${job.location}", color = AdTeal)
                        }
                    }
                    AdCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Default resume", fontWeight = FontWeight.SemiBold)
                            Text(resume.fullName, color = AdText)
                            Text(resume.headline, color = AdMuted)
                            Text("Version ${resume.version} · updated ${formatTime(resume.updatedAt)}", color = AdMuted, fontSize = 11.sp)
                            Text("A permanent snapshot is captured when you submit.", color = AdTealDark, fontSize = 11.sp)
                        }
                    }
                    AdCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Application details", fontWeight = FontWeight.SemiBold)
                            Text("Contact email: ${state.profile.email}", color = AdMuted)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = state.shareProfile, onCheckedChange = onShareProfile,
                                    enabled = !state.submitting)
                                Text("Share my Candidate profile", color = AdText)
                            }
                        }
                    }
                    state.message?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
                    val canSubmit = state.job.applicationState == com.adproject.candidate.data.contract.CandidateJobApplicationState.NOT_APPLIED
                    PrimaryButton(
                        if (!canSubmit) "Already ${state.job.applicationState.name.lowercase().replace('_', ' ')}"
                        else if (state.submitting) "Submitting…" else "Submit application",
                        onSubmit, Modifier.fillMaxWidth(), enabled = canSubmit && !state.submitting,
                    )
                    SecondaryButton("Cancel", onBack, Modifier.fillMaxWidth(), enabled = !state.submitting)
                }
            }
        }
    }
}

@Composable
fun RealApplicationSubmittedScreen(
    application: CandidateApplication?,
    onJobs: () -> Unit,
) {
    if (application == null) {
        Column(Modifier.fillMaxSize().background(AdBackground).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Submission result is no longer available. Reload the job to see its current state.", color = AdMuted)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Back to jobs", onJobs)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().background(AdBackground).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("✓", color = AdTeal, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Text("Application submitted", color = AdText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        AdCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(application.jobTitle, fontWeight = FontWeight.SemiBold)
                Text(application.company.name, color = AdMuted)
                Text("Status: ${application.status.name}", color = AdTealDark)
                Text("Submitted: ${formatTime(application.appliedAt)}", color = AdMuted)
                Text("Application ID: ${application.applicationId}", color = AdMuted, fontSize = 11.sp)
                Text("Resume: ${application.resumeSnapshot.fullName} · v${application.resumeSnapshot.version}", color = AdMuted)
            }
        }
        AdCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What happens next", fontWeight = FontWeight.SemiBold)
                application.nextSteps.forEach { step ->
                    Text(step.title, color = AdText, fontWeight = FontWeight.SemiBold)
                    Text(step.description, color = AdMuted, fontSize = 11.sp)
                }
            }
        }
        PrimaryButton("Back to jobs", onJobs, Modifier.fillMaxWidth())
        SecondaryButton("My applications · not connected", {}, Modifier.fillMaxWidth(), enabled = false)
    }
}

private fun formatTime(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm"))
}.getOrDefault(value)
