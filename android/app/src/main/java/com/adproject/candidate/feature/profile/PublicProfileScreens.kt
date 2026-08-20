package com.adproject.candidate.feature.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adproject.candidate.core.designsystem.AdBackground
import com.adproject.candidate.core.designsystem.AdCard
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdTealDark
import com.adproject.candidate.core.designsystem.AdTealSoft
import com.adproject.candidate.core.designsystem.AdText
import com.adproject.candidate.core.designsystem.AdTopBar
import com.adproject.candidate.core.designsystem.PrimaryButton
import com.adproject.candidate.core.designsystem.TagChip
import com.adproject.candidate.data.contract.CompanyPublicProfile
import com.adproject.candidate.data.contract.RecruiterPublicProfile

@Composable
fun RecruiterPublicProfileScreen(
    state: RecruiterPublicProfileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.loading -> Box(Modifier.fillMaxSize().background(AdBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AdTeal)
        }
        state.data == null -> Column(Modifier.fillMaxSize().background(AdBackground)) {
            AdTopBar("Recruiter", onBack)
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (state.notFound) "This recruiter is no longer available." else state.message.orEmpty(),
                    color = AdMuted, fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                if (!state.notFound) PrimaryButton("Try again", onRetry)
            }
        }
        else -> RecruiterPublicProfileContent(state.data, onBack)
    }
}

@Composable
private fun RecruiterPublicProfileContent(data: RecruiterPublicProfile, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AdBackground)) {
        AdTopBar("Recruiter", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AdCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(18.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(AdTealSoft), contentAlignment = Alignment.Center) {
                        Text(data.fullName.take(1).uppercase(), color = AdTeal, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(data.fullName, color = AdText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (data.title.isNotBlank()) Text(data.title, color = AdMuted, fontSize = 13.sp)
                }
            }
            data.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                AdCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("About", color = AdText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(bio, color = AdMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
            AdCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Company", color = AdText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AdTealSoft), contentAlignment = Alignment.Center) {
                            Text(data.company.name.take(1).uppercase(), color = AdTeal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(data.company.name, color = AdText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    verificationStatus(data.company.verificationStatus)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun CompanyPublicProfileScreen(
    state: CompanyPublicProfileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.loading -> Box(Modifier.fillMaxSize().background(AdBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AdTeal)
        }
        state.data == null -> Column(Modifier.fillMaxSize().background(AdBackground)) {
            AdTopBar("Company", onBack)
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (state.notFound) "This company is no longer available." else state.message.orEmpty(),
                    color = AdMuted, fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                if (!state.notFound) PrimaryButton("Try again", onRetry)
            }
        }
        else -> CompanyPublicProfileContent(state.data, onBack)
    }
}

@Composable
private fun CompanyPublicProfileContent(data: CompanyPublicProfile, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AdBackground)) {
        AdTopBar("Company", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AdCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(18.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(AdTealSoft), contentAlignment = Alignment.Center) {
                        Text(data.name.take(1).uppercase(), color = AdTeal, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(data.name, color = AdText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    verificationStatus(data.verificationStatus)
                }
            }
            val hasDetails = !data.description.isNullOrBlank() || !data.location.isNullOrBlank()
                    || !data.stage.isNullOrBlank() || !data.employeeRange.isNullOrBlank() || !data.website.isNullOrBlank()
            if (hasDetails) AdCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("About", color = AdText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    data.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = AdMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    data.location?.takeIf { it.isNotBlank() }?.let {
                        Text("Location: $it", color = AdMuted, fontSize = 12.sp)
                    }
                    data.stage?.takeIf { it.isNotBlank() }?.let {
                        Text("Stage: $it", color = AdMuted, fontSize = 12.sp)
                    }
                    data.employeeRange?.takeIf { it.isNotBlank() }?.let {
                        Text("Team size: $it", color = AdMuted, fontSize = 12.sp)
                    }
                    data.website?.takeIf { it.isNotBlank() }?.let {
                        Text("Website: $it", color = AdTealDark, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
            if (data.activeJobCount > 0) AdCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Open roles (${data.activeJobCount})", color = AdText, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold)
                    data.openJobs.forEach { job ->
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(job.title, color = AdText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("${job.location} · ${job.employmentType.lowercase().replace('_', ' ')} · " +
                                    job.workplaceType.lowercase().replaceFirstChar(Char::uppercase),
                                color = AdMuted, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun verificationStatus(status: String?) {
    if (status == null) return
    val label = when (status) {
        "APPROVED" -> "Verified"
        "PENDING" -> "Verification pending"
        "REJECTED" -> "Not verified"
        else -> status.lowercase().replaceFirstChar(Char::uppercase)
    }
    TagChip(label, accent = status == "APPROVED")
}
