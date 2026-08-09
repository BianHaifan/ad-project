package com.adproject.candidate.feature.jobs

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adproject.candidate.R
import com.adproject.candidate.core.designsystem.AdBackground
import com.adproject.candidate.core.designsystem.AdCard
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdTealDark
import com.adproject.candidate.core.designsystem.AdTealSoft
import com.adproject.candidate.core.designsystem.AdText
import com.adproject.candidate.core.designsystem.AdTopBar
import com.adproject.candidate.core.designsystem.FigmaSvg
import com.adproject.candidate.core.designsystem.PrimaryButton
import com.adproject.candidate.core.designsystem.SecondaryButton
import com.adproject.candidate.core.designsystem.TagChip
import com.adproject.candidate.data.model.JobDetailData

@Composable
fun JobDetailScreen(data: JobDetailData, onBack: () -> Unit, onApply: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AdBackground)) {
        AdTopBar("Job details", onBack) { FigmaSvg(R.raw.icon_save, "Save job", Modifier.size(24.dp)) }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("✦  Tailor with Agent", {}, Modifier.weight(1f))
            PrimaryButton("Apply now", onApply, Modifier.weight(1f))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AdCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(AdTealSoft), contentAlignment = Alignment.Center) {
                            Text(data.job.companyInitial, color = AdTeal, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(data.job.company, color = Color(0xFF111827), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(data.job.companyMeta, color = AdMuted, fontSize = 12.sp)
                        }
                    }
                    Text(data.job.title, color = Color(0xFF111827), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(data.job.salary, color = AdTeal, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${data.location} · ${data.employmentType} · ${data.workplace}", color = AdMuted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { data.job.skills.take(3).forEach { TagChip(it) } }
                }
            }
            AdCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("AI Match Analysis", color = Color(0xFF111827), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        TagChip("${data.job.match}% match", accent = true)
                    }
                    Text("ML model compares your resume skills and experience with this job.", color = AdMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    Text("✓ Strong: ${data.strongMatches}", color = AdTealDark, fontSize = 12.sp)
                    Text("△ Gap: ${data.gap}", color = Color(0xFFFF8500), fontSize = 12.sp)
                    TagChip("Ask AI Agent to explain →", accent = true)
                }
            }
            AdCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("About this role", color = Color(0xFF111827), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(data.description, color = AdMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    Text(data.requirements, color = AdTealDark, fontSize = 11.sp)
                }
            }
            AdCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AdTealSoft), contentAlignment = Alignment.Center) { Text(data.job.recruiter.fullName.take(1), color = AdTeal) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(data.job.recruiter.fullName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${data.job.recruiter.title} · ${data.job.company}", color = AdMuted, fontSize = 10.sp)
                    }
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFE5FAF7)).padding(horizontal = 18.dp, vertical = 12.dp)) { Text("Message", color = AdTealDark, fontSize = 11.sp) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
