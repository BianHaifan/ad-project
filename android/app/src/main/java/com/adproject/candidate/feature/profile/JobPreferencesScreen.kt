package com.adproject.candidate.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adproject.candidate.core.designsystem.PrimaryButton
import com.adproject.candidate.core.designsystem.SecondaryButton
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.CandidateRecommendationRepository
import com.adproject.candidate.data.contract.EmploymentType
import com.adproject.candidate.data.contract.JobPreference
import com.adproject.candidate.data.contract.SaveJobPreferenceRequest
import com.adproject.candidate.data.contract.WorkplaceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JobPreferenceUiState(
    val loading: Boolean = true,
    val data: JobPreference? = null,
    val submitting: Boolean = false,
    val message: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val saved: Boolean = false,
)

class JobPreferenceViewModel(
    private val repository: CandidateRecommendationRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(JobPreferenceUiState())
    val state: StateFlow<JobPreferenceUiState> = mutable

    init { load() }

    fun load() {
        mutable.value = JobPreferenceUiState()
        viewModelScope.launch {
            mutable.value = when (val result = repository.preferences()) {
                is ApiResult.Success -> JobPreferenceUiState(loading = false, data = result.value)
                is ApiResult.Failure -> JobPreferenceUiState(loading = false, message = result.message)
            }
        }
    }

    fun save(
        titlesText: String,
        locationsText: String,
        workplaces: Set<WorkplaceType>,
        employments: Set<EmploymentType>,
        minimumSalaryText: String,
    ) {
        val current = mutable.value
        if (current.submitting || current.data == null) return
        val titles = splitValues(titlesText)
        val locations = splitValues(locationsText)
        val minimumSalary = minimumSalaryText.trim().takeIf(String::isNotEmpty)?.toLongOrNull()
        val errors = buildMap {
            if (titles.size > 20) put("desiredTitles", "Use at most 20 titles")
            if (titles.any { it.length > 200 }) {
                put("desiredTitles", "Each title must be at most 200 characters")
            }
            if (locations.size > 20) put("preferredLocations", "Use at most 20 locations")
            if (locations.any { it.length > 200 }) {
                put("preferredLocations", "Each location must be at most 200 characters")
            }
            if (minimumSalaryText.isNotBlank() && (minimumSalary == null || minimumSalary < 0)) {
                put("minimumSalary", "Enter a non-negative whole number")
            }
        }
        if (errors.isNotEmpty()) {
            mutable.update { it.copy(message = "Please correct the form.", fieldErrors = errors) }
            return
        }
        mutable.update { it.copy(submitting = true, message = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val request = SaveJobPreferenceRequest(titles, locations, workplaces.toList(),
                employments.toList(), minimumSalary, expectedVersion = current.data.version)
            when (val result = repository.savePreferences(request)) {
                is ApiResult.Success -> mutable.value = JobPreferenceUiState(
                    loading = false, data = result.value, saved = true)
                is ApiResult.Failure -> mutable.update {
                    it.copy(submitting = false, message = result.message, fieldErrors = result.fieldErrors)
                }
            }
        }
    }

    companion object {
        fun factory(repository: CandidateRecommendationRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { JobPreferenceViewModel(repository) } }
    }
}

@Composable
fun JobPreferencesScreen(
    state: JobPreferenceUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSave: (String, String, Set<WorkplaceType>, Set<EmploymentType>, String) -> Unit,
) {
    if (state.loading) {
        Column(Modifier.fillMaxSize().padding(32.dp)) { CircularProgressIndicator() }
        return
    }
    val data = state.data
    if (data == null) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.message ?: "Unable to load job preferences")
            SecondaryButton("Retry", onRetry)
            SecondaryButton("Back", onBack)
        }
        return
    }
    var titles by remember(data) { mutableStateOf(data.desiredTitles.joinToString(", ")) }
    var locations by remember(data) { mutableStateOf(data.preferredLocations.joinToString(", ")) }
    var minimumSalary by remember(data) { mutableStateOf(data.minimumSalary?.toString().orEmpty()) }
    var workplaces by remember(data) { mutableStateOf(data.workplaceTypes.toSet()) }
    var employments by remember(data) { mutableStateOf(data.employmentTypes.toSet()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Job preferences", style = MaterialTheme.typography.headlineSmall)
        Text("These fields become structured features for job matching.")
        OutlinedTextField(titles, { titles = it }, Modifier.fillMaxWidth(),
            label = { Text("Desired titles, comma separated") },
            isError = "desiredTitles" in state.fieldErrors)
        OutlinedTextField(locations, { locations = it }, Modifier.fillMaxWidth(),
            label = { Text("Preferred locations, comma separated") },
            isError = "preferredLocations" in state.fieldErrors)
        Text("Workplace type", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkplaceType.entries.forEach { type -> FilterChip(
                selected = type in workplaces,
                onClick = { workplaces = workplaces.toggle(type) },
                label = { Text(type.name.replace('_', ' ')) },
            ) }
        }
        Text("Employment type", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EmploymentType.entries.forEach { type -> FilterChip(
                selected = type in employments,
                onClick = { employments = employments.toggle(type) },
                label = { Text(type.name.replace('_', ' ')) },
            ) }
        }
        OutlinedTextField(minimumSalary, { minimumSalary = it }, Modifier.fillMaxWidth(),
            label = { Text("Minimum monthly salary (SGD)") },
            isError = "minimumSalary" in state.fieldErrors)
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.saved) Text("Preferences saved. Refresh Recommended jobs to recalculate matches.")
        PrimaryButton(if (state.submitting) "Saving..." else "Save preferences",
            { onSave(titles, locations, workplaces, employments, minimumSalary) },
            Modifier.fillMaxWidth(), enabled = !state.submitting)
        SecondaryButton("Back", onBack, Modifier.fillMaxWidth())
    }
}

private fun splitValues(value: String): List<String> = value.split(',')
    .map(String::trim).filter(String::isNotEmpty).distinct()

private fun <T> Set<T>.toggle(value: T): Set<T> =
    if (value in this) this - value else this + value
