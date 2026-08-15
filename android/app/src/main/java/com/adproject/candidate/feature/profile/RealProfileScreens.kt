package com.adproject.candidate.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adproject.candidate.core.designsystem.PrimaryButton
import com.adproject.candidate.core.designsystem.SecondaryButton
import com.adproject.candidate.core.designsystem.AdBottomBar
import com.adproject.candidate.core.designsystem.MainTab
import com.adproject.candidate.data.contract.Experience

@Composable
fun RealProfileScreen(state: ProfileUiState, onRetry: () -> Unit, onEdit: () -> Unit,
                      onSave: (String, String, String) -> Unit, onResume: () -> Unit,
                      onApplications: () -> Unit, onPreferences: () -> Unit,
                      onLogout: () -> Unit,
                      onTab: (MainTab) -> Unit) {
    when {
        state.loading -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator() }
        state.data == null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.message ?: "Unable to load profile")
            SecondaryButton("Retry", onRetry)
        }
        else -> {
            val data = state.data
            var name by remember(data, state.editing) { mutableStateOf(data.fullName) }
            var headline by remember(data, state.editing) { mutableStateOf(data.headline) }
            var location by remember(data, state.editing) { mutableStateOf(data.location) }
            Scaffold(bottomBar = { AdBottomBar(MainTab.Me, onTab) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("My profile", style = MaterialTheme.typography.headlineMedium)
                Text(data.email)
                if (state.editing) {
                    OutlinedTextField(name, { name = it }, label = { Text("Full name") }, isError = "fullName" in state.fieldErrors)
                    OutlinedTextField(headline, { headline = it }, label = { Text("Headline") })
                    OutlinedTextField(location, { location = it }, label = { Text("Location") })
                    state.message?.let { Text(it) }
                    PrimaryButton(if (state.submitting) "Saving…" else "Save", { onSave(name, headline, location) }, Modifier.fillMaxWidth(), enabled = !state.submitting)
                } else {
                    Text(data.fullName, style = MaterialTheme.typography.titleLarge)
                    Text(data.headline.ifBlank { "No headline yet" })
                    Text(data.location.ifBlank { "No location yet" })
                    SecondaryButton("Edit profile", onEdit, Modifier.fillMaxWidth())
                }
                if (state.saved) Text("Profile saved")
                PrimaryButton("Online resume", onResume, Modifier.fillMaxWidth())
                SecondaryButton("My applications", onApplications, Modifier.fillMaxWidth())
                SecondaryButton("Job preferences", onPreferences, Modifier.fillMaxWidth())
                SecondaryButton("Sign out", onLogout, Modifier.fillMaxWidth())
            } }
        }
    }
}

@Composable
fun RealResumeScreen(state: ResumeUiState, onBack: () -> Unit, onRetry: () -> Unit,
                     onSave: (String, String, String, String, String, String, List<Experience>) -> Unit) {
    if (state.loading) { CircularProgressIndicator(); return }
    if (state.data == null && !state.notCreated) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.message ?: "Unable to load resume")
            SecondaryButton("Retry", onRetry)
            SecondaryButton("Back", onBack)
        }
        return
    }
    val data = state.data
    var name by remember(data) { mutableStateOf(data?.fullName.orEmpty()) }
    var age by remember(data) { mutableStateOf(data?.age?.toString().orEmpty()) }
    var location by remember(data) { mutableStateOf(data?.location.orEmpty()) }
    var headline by remember(data) { mutableStateOf(data?.headline.orEmpty()) }
    var summary by remember(data) { mutableStateOf(data?.summary.orEmpty()) }
    var skills by remember(data) { mutableStateOf(data?.skills?.joinToString(", ").orEmpty()) }
    val experiences = remember(data) { mutableStateListOf<Experience>().apply { addAll(data?.experiences.orEmpty()) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (data == null) "Create resume" else "Edit resume", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it }, label = { Text("Full name") },
            isError = "fullName" in state.fieldErrors,
            supportingText = { state.fieldErrors["fullName"]?.let { Text(it) } })
        OutlinedTextField(age, { age = it }, label = { Text("Age") },
            isError = "age" in state.fieldErrors,
            supportingText = { state.fieldErrors["age"]?.let { Text(it) } })
        OutlinedTextField(location, { location = it }, label = { Text("Location") },
            isError = "location" in state.fieldErrors,
            supportingText = { state.fieldErrors["location"]?.let { Text(it) } })
        OutlinedTextField(headline, { headline = it }, label = { Text("Headline") },
            isError = "headline" in state.fieldErrors,
            supportingText = { state.fieldErrors["headline"]?.let { Text(it) } })
        OutlinedTextField(summary, { summary = it }, label = { Text("Summary") }, minLines = 3,
            isError = "summary" in state.fieldErrors,
            supportingText = { state.fieldErrors["summary"]?.let { Text(it) } })
        OutlinedTextField(skills, { skills = it }, label = { Text("Skills, comma separated") },
            isError = "skills" in state.fieldErrors,
            supportingText = { state.fieldErrors["skills"]?.let { Text(it) } })
        Text("Experience", style = MaterialTheme.typography.titleMedium)
        experiences.forEachIndexed { index, experience ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val prefix = "experiences[$index]"
                OutlinedTextField(experience.title, { experiences[index] = experience.copy(title = it) }, label = { Text("Title") },
                    isError = "$prefix.title" in state.fieldErrors,
                    supportingText = { state.fieldErrors["$prefix.title"]?.let { Text(it) } })
                OutlinedTextField(experience.company, { experiences[index] = experience.copy(company = it) }, label = { Text("Company") },
                    isError = "$prefix.company" in state.fieldErrors,
                    supportingText = { state.fieldErrors["$prefix.company"]?.let { Text(it) } })
                OutlinedTextField(experience.description, { experiences[index] = experience.copy(description = it) }, label = { Text("Description") })
                OutlinedTextField(experience.startDate, { experiences[index] = experience.copy(startDate = it) }, label = { Text("Start YYYY-MM") },
                    isError = "$prefix.startDate" in state.fieldErrors,
                    supportingText = { state.fieldErrors["$prefix.startDate"]?.let { Text(it) } })
                OutlinedTextField(experience.endDate.orEmpty(), { experiences[index] = experience.copy(endDate = it.ifBlank { null }) }, label = { Text("End YYYY-MM") },
                    isError = "$prefix.endDate" in state.fieldErrors,
                    supportingText = { state.fieldErrors["$prefix.endDate"]?.let { Text(it) } })
                TextButton(onClick = { experiences.removeAt(index) }) { Text("Remove") }
            } }
        }
        TextButton(onClick = { experiences.add(Experience(null, "", "", "", "2026-01", null)) }) { Text("+ Add experience") }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryButton(if (state.submitting) "Saving…" else "Save changes",
            { onSave(name, age, location, headline, summary, skills, experiences.toList()) },
            Modifier.fillMaxWidth(), enabled = !state.submitting)
        SecondaryButton("Back", onBack, Modifier.fillMaxWidth())
    }
}
