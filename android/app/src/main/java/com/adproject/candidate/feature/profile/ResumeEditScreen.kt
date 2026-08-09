package com.adproject.candidate.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adproject.candidate.core.designsystem.AdBackground
import com.adproject.candidate.core.designsystem.AdBorder
import com.adproject.candidate.core.designsystem.AdCard
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdTealDark
import com.adproject.candidate.core.designsystem.AdTopBar
import com.adproject.candidate.core.designsystem.PrimaryButton
import com.adproject.candidate.core.designsystem.SecondaryButton
import com.adproject.candidate.data.model.ResumeData

@Composable
fun ResumeEditScreen(data: ResumeData, onBack: () -> Unit, onSave: (ResumeData) -> Unit) {
    var fullName by remember(data) { mutableStateOf(data.fullName) }
    var age by remember(data) { mutableStateOf(data.age.toString()) }
    var location by remember(data) { mutableStateOf(data.location) }
    var headline by remember(data) { mutableStateOf(data.headline) }
    var summary by remember(data) { mutableStateOf(data.summary) }
    var saved by remember(data) { mutableStateOf(true) }

    fun save() {
        onSave(data.copy(fullName = fullName, age = age.toIntOrNull() ?: data.age, location = location, headline = headline, summary = summary))
        saved = true
    }

    Scaffold(
        topBar = { AdTopBar("Edit resume", onBack) { Text(if (saved) "Saved" else "Editing", color = AdTealDark, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) } },
        bottomBar = {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Preview", {}, Modifier.width(108.dp))
                PrimaryButton("Save changes", ::save, Modifier.weight(1f), enabled = !saved)
            }
        },
        containerColor = AdBackground,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AdCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Personal information", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Required", color = Color(0xFF8A939C), fontSize = 9.sp)
                    }
                    ResumeField("FULL NAME", fullName, Modifier.fillMaxWidth()) { fullName = it; saved = false }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ResumeField("AGE", age, Modifier.width(100.dp)) { age = it; saved = false }
                        ResumeField("LOCATION", location, Modifier.weight(1f)) { location = it; saved = false }
                    }
                    ResumeField("PROFESSIONAL HEADLINE", headline, Modifier.fillMaxWidth()) { headline = it; saved = false }
                }
            }
            AdCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Professional summary", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = summary,
                        onValueChange = { summary = it; saved = false },
                        modifier = Modifier.fillMaxWidth().height(88.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color(0xFF607080)),
                        colors = fieldColors(),
                    )
                }
            }
            AdCard(Modifier.fillMaxWidth().height(150.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Experience", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("+ Add", color = AdTealDark, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    data.experiences.firstOrNull()?.let { experience ->
                        Text(experience.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(experience.description, color = AdMuted, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumeField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label, fontSize = 8.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
        colors = fieldColors(),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AdTeal,
    unfocusedBorderColor = AdBorder,
    focusedContainerColor = Color(0xFFFAFBFB),
    unfocusedContainerColor = Color(0xFFFAFBFB),
)
