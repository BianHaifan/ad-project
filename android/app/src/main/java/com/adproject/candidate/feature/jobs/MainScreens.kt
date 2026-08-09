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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdTealDark
import com.adproject.candidate.core.designsystem.AdTealSoft
import com.adproject.candidate.core.designsystem.AdText
import com.adproject.candidate.core.designsystem.FigmaSvg
import com.adproject.candidate.core.designsystem.MainTab
import com.adproject.candidate.core.designsystem.TagChip
import com.adproject.candidate.data.model.CandidateProfile
import com.adproject.candidate.data.model.Conversation
import com.adproject.candidate.data.model.Job
import com.adproject.candidate.data.model.JobFeedData
import com.adproject.candidate.data.model.LearningData
import com.adproject.candidate.data.model.ProfileTool

@Composable
fun JobFeedScreen(data: JobFeedData, onTab: (MainTab) -> Unit, onJob: (String) -> Unit) {
    Scaffold(bottomBar = { AdBottomBar(MainTab.Jobs, onTab) }, containerColor = AdBackground) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
                        Text("Full-time", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = AdText)
                        Text("Internship", fontSize = 22.sp, color = Color(0xFF6E7781))
                        Text("Part-time", fontSize = 22.sp, color = Color(0xFF6E7781), maxLines = 1)
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(AdBackground).padding(horizontal = 14.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Search", color = Color(0xFF6E7781), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(data.searchSuggestion, color = Color(0xFF6E7781), fontSize = 14.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Recommended", color = AdText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("AI / LLM", color = Color(0xFF6E7781), fontSize = 15.sp)
                        Text("Backend", color = Color(0xFF6E7781), fontSize = 15.sp)
                        Text("Data", color = Color(0xFF6E7781), fontSize = 15.sp)
                    }
                }
            }
            item { Text("Filter", Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp), color = Color(0xFF6E7781), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End) }
            items(data.jobs, key = { it.id }) { job -> JobCard(job, onJob) }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun JobCard(job: Job, onJob: (String) -> Unit) {
    AdCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp).clickable { onJob(job.id) }) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(job.title, Modifier.weight(1f), color = AdText, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
                Text(job.salary, color = AdTeal, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Text("${job.company} · ${job.companyMeta}", color = AdMuted, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { job.skills.forEach { TagChip(it) } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FigmaSvg(R.raw.recruiter_avatar, "Recruiter avatar", Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("${job.recruiterName} - ${job.recruiterRole}", Modifier.weight(1f), color = Color(0xFF34404B), fontSize = 12.sp)
                TagChip("AI Match ${job.match}%", accent = true)
            }
        }
    }
}

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
fun MessagesScreen(conversations: List<Conversation>, onTab: (MainTab) -> Unit, onConversation: (String) -> Unit) {
    Scaffold(bottomBar = { AdBottomBar(MainTab.Messages, onTab) }, containerColor = AdBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Messages", color = Color(0xFF0E1114), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Recruiters and hiring teams", color = Color(0xFF6B7885), fontSize = 12.sp)
                }
                Box(Modifier.size(36.dp).clip(CircleShape).background(AdTealSoft), contentAlignment = Alignment.Center) {
                    Text("+", color = AdTeal, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text("Search conversations", color = Color(0xFF8C96A1), fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White)) {
                items(conversations, key = { it.id }) { conversation ->
                    Row(
                        Modifier.fillMaxWidth().height(104.dp).clickable { onConversation(conversation.id) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(AdTealSoft), contentAlignment = Alignment.Center) {
                            Text(conversation.initial, color = AdTeal, fontWeight = FontWeight.SemiBold, fontSize = if (conversation.initial.length > 1) 11.sp else 15.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(conversation.name, color = AdText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(conversation.preview, color = AdMuted, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(conversation.time, color = Color(0xFF89939D), fontSize = 10.sp)
                            if (conversation.unread > 0) Box(Modifier.size(22.dp).clip(CircleShape).background(AdTeal), contentAlignment = Alignment.Center) {
                                Text(conversation.unread.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE8EDF0))
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(data: CandidateProfile, onTab: (MainTab) -> Unit, onApplications: () -> Unit, onResume: () -> Unit) {
    Scaffold(bottomBar = { AdBottomBar(MainTab.Me, onTab) }, containerColor = AdBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FigmaSvg(R.raw.profile_avatar, data.name, Modifier.size(72.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(data.name, color = AdText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
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
