package com.adproject.candidate.feature.applications

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adproject.candidate.R
import com.adproject.candidate.core.designsystem.AdBackground
import com.adproject.candidate.core.designsystem.AdBottomBar
import com.adproject.candidate.core.designsystem.AdCard
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdTealDark
import com.adproject.candidate.core.designsystem.AdTealSoft
import com.adproject.candidate.core.designsystem.AdText
import com.adproject.candidate.core.designsystem.AdTopBar
import com.adproject.candidate.core.designsystem.FigmaSvg
import com.adproject.candidate.core.designsystem.MainTab
import com.adproject.candidate.core.designsystem.PrimaryButton
import com.adproject.candidate.core.designsystem.SecondaryButton
import com.adproject.candidate.core.designsystem.StatusDot
import com.adproject.candidate.core.designsystem.TagChip
import com.adproject.candidate.data.model.Application
import com.adproject.candidate.data.model.ApplicationsData
import com.adproject.candidate.data.model.ApplyConfirmationData
import com.adproject.candidate.data.model.SubmissionData

@Composable
fun ApplyConfirmationScreen(data: ApplyConfirmationData, onBack: () -> Unit, onSubmit: () -> Unit) {
    Scaffold(
        topBar = { AdTopBar("Confirm application", onBack) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Cancel", onBack, Modifier.width(108.dp))
                PrimaryButton("Submit application", onSubmit, Modifier.weight(1f))
            }
        },
        containerColor = AdBackground,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AdCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AdTealSoft), contentAlignment = Alignment.Center) { Text(data.companyInitial, color = AdTeal, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(data.company, color = Color(0xFF111827), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(data.companyMeta, color = Color(0xFF7B8491), fontSize = 11.sp)
                        }
                    }
                    Text(data.jobTitle, color = Color(0xFF111827), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(data.salaryAndLocation, color = AdTeal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            AdCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Resume to submit", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Change", color = AdTealDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(AdTealSoft), contentAlignment = Alignment.Center) { FigmaSvg(R.raw.icon_resume, "Resume", Modifier.size(22.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(data.resumeName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(data.resumeMeta, color = AdMuted, fontSize = 12.sp)
                        }
                        Text(data.resumeStatus, color = AdTealDark, fontSize = 11.sp)
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF0FAF9)).padding(10.dp), verticalAlignment = Alignment.Top) {
                        FigmaSvg(R.raw.icon_snapshot, "Snapshot", Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("A snapshot is saved when you submit, so later edits won't change this application.", color = Color(0xFF60727B), fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
            AdCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Application details", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    DetailRow("Contact email", data.contactEmail)
                    DetailRow("Visible to recruiter", data.visibleInformation)
                    HorizontalDivider(color = Color(0xFFE1E6E9))
                    Text("By submitting, you confirm the information is accurate and agree to share it with ${data.company}.", color = Color(0xFF8B949E), fontSize = 9.sp, lineHeight = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AdMuted, fontSize = 12.sp)
        Text(value, color = Color(0xFF34404B), fontSize = 12.sp)
    }
}

@Composable
fun ApplicationSubmittedScreen(data: SubmissionData, onApplications: () -> Unit, onJobs: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(AdBackground).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFDFF7F5)), contentAlignment = Alignment.Center) { Text("✓", color = AdTeal, fontSize = 34.sp, fontWeight = FontWeight.Bold) }
        Text("Application submitted", color = AdText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("${data.company} has received your application.", color = AdMuted, fontSize = 12.sp)
        AdCard(Modifier.fillMaxWidth().height(206.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AdTealSoft), contentAlignment = Alignment.Center) { Text(data.companyInitial, color = AdTealDark, fontSize = 17.sp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(data.jobTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(data.jobMeta, color = Color(0xFF7B8491), fontSize = 10.sp)
                    }
                    TagChip(data.status, accent = true)
                }
                HorizontalDivider(color = Color(0xFFE7ECEF))
                DetailRow("Submitted", data.submittedAt)
                DetailRow("Resume", "${data.resumeSnapshot.fullName} · captured ${formatDateTime(data.resumeSnapshot.capturedAt)}")
                DetailRow("Application ID", data.applicationId)
            }
        }
        AdCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("What happens next", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                data.nextSteps.forEachIndexed { index, step ->
                    NextStep((index + 1).toString(), step.title, step.description)
                }
            }
        }
        PrimaryButton("View my applications", onApplications, Modifier.fillMaxWidth())
        SecondaryButton("Back to jobs", onJobs, Modifier.fillMaxWidth())
    }
}

@Composable
private fun NextStep(number: String, title: String, copy: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(AdTealSoft), contentAlignment = Alignment.Center) { Text(number, color = AdTealDark, fontSize = 9.sp) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = Color(0xFF34404B), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(copy, color = Color(0xFF8A939C), fontSize = 9.sp)
        }
    }
}

private fun formatDateTime(value: String): String = runCatching {
    java.time.OffsetDateTime.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm"))
}.getOrDefault(value)
