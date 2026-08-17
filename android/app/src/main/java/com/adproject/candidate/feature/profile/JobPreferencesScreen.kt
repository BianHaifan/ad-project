package com.adproject.candidate.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
        titles: List<String>,
        locations: List<String>,
        workplaces: Set<WorkplaceType>,
        employments: Set<EmploymentType>,
        minimumSalary: Long?,
    ) {
        val current = mutable.value
        if (current.submitting || current.data == null) return
        val normalizedTitles = titles.map(String::trim).filter(String::isNotEmpty).distinct()
        val normalizedLocations = locations.map(String::trim).filter(String::isNotEmpty).distinct()
        val errors = buildMap {
            if (normalizedTitles.size > 20) put("desiredTitles", "Use at most 20 titles")
            if (normalizedTitles.any { it.length > 200 }) {
                put("desiredTitles", "Each title must be at most 200 characters")
            }
            if (normalizedLocations.size > 20) put("preferredLocations", "Use at most 20 locations")
            if (normalizedLocations.any { it.length > 200 }) {
                put("preferredLocations", "Each location must be at most 200 characters")
            }
        }
        if (errors.isNotEmpty()) {
            mutable.update { it.copy(message = "Please correct the form.", fieldErrors = errors) }
            return
        }
        mutable.update { it.copy(submitting = true, message = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val request = SaveJobPreferenceRequest(normalizedTitles, normalizedLocations, workplaces.toList(),
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
    onSave: (List<String>, List<String>, Set<WorkplaceType>, Set<EmploymentType>, Long?) -> Unit,
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
    val titles = remember(data) { mutableStateListOf<String>().apply { addAll(data.desiredTitles) } }
    val locations = remember(data) { mutableStateListOf<String>().apply { addAll(data.preferredLocations) } }
    var minimumSalary by remember(data) { mutableStateOf(data.minimumSalary) }
    var workplaces by remember(data) { mutableStateOf(data.workplaceTypes.toSet()) }
    var employments by remember(data) { mutableStateOf(data.employmentTypes.toSet()) }
    var showTitles by remember { mutableStateOf(false) }
    var showLocations by remember { mutableStateOf(false) }
    var showWorkplaces by remember { mutableStateOf(false) }
    var showEmployments by remember { mutableStateOf(false) }
    var showSalary by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Job preferences", style = MaterialTheme.typography.headlineSmall)
        Text("These fields become structured features for job matching.")
        SelectorField(
            label = "Desired titles",
            value = titles.joinToString(", ").ifBlank { null },
            placeholder = "Select titles",
            isError = "desiredTitles" in state.fieldErrors,
            errorText = state.fieldErrors["desiredTitles"],
            onClick = { showTitles = true },
        )
        SelectorField(
            label = "Preferred locations",
            value = locations.joinToString(", ").ifBlank { null },
            placeholder = "Select locations",
            isError = "preferredLocations" in state.fieldErrors,
            errorText = state.fieldErrors["preferredLocations"],
            onClick = { showLocations = true },
        )
        SelectorField(
            label = "Workplace type",
            value = workplaces.joinToString(", ") { it.name.replace('_', ' ') }.ifBlank { null },
            placeholder = "None selected",
            onClick = { showWorkplaces = true },
        )
        SelectorField(
            label = "Employment type",
            value = employments.joinToString(", ") { it.name.replace('_', ' ') }.ifBlank { null },
            placeholder = "None selected",
            onClick = { showEmployments = true },
        )
        SelectorField(
            label = "Minimum monthly salary (SGD)",
            value = minimumSalary?.let(::formatSalary),
            placeholder = "Not specified",
            onClick = { showSalary = true },
        )
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.saved) Text("Preferences saved. Refresh Recommended jobs to recalculate matches.")
        PrimaryButton(if (state.submitting) "Saving..." else "Save preferences",
            { onSave(titles.toList(), locations.toList(), workplaces, employments, minimumSalary) },
            Modifier.fillMaxWidth(), enabled = !state.submitting)
        SecondaryButton("Back", onBack, Modifier.fillMaxWidth())
    }

    if (showTitles) {
        SearchableMultiSelectSheet(
            title = "Desired titles",
            options = COMMON_JOB_TITLES,
            initialSelected = titles.toList(),
            searchPlaceholder = "Search titles",
            addPlaceholder = "Add a title",
            onConfirm = { result -> titles.clear(); titles.addAll(result); showTitles = false },
            onDismiss = { showTitles = false },
        )
    }
    if (showLocations) {
        SearchableMultiSelectSheet(
            title = "Preferred locations",
            options = COMMON_LOCATIONS,
            initialSelected = locations.toList(),
            searchPlaceholder = "Search locations",
            addPlaceholder = "Add a location",
            onConfirm = { result -> locations.clear(); locations.addAll(result); showLocations = false },
            onDismiss = { showLocations = false },
        )
    }
    if (showWorkplaces) {
        EnumMultiSelectSheet(
            title = "Workplace type",
            options = WorkplaceType.entries,
            optionLabel = { it.name.replace('_', ' ') },
            initialSelected = workplaces,
            onConfirm = { workplaces = it; showWorkplaces = false },
            onDismiss = { showWorkplaces = false },
        )
    }
    if (showEmployments) {
        EnumMultiSelectSheet(
            title = "Employment type",
            options = EmploymentType.entries,
            optionLabel = { it.name.replace('_', ' ') },
            initialSelected = employments,
            onConfirm = { employments = it; showEmployments = false },
            onDismiss = { showEmployments = false },
        )
    }
    if (showSalary) {
        NumberWheelSheet(
            title = "Minimum monthly salary (SGD)",
            values = SALARY_OPTIONS,
            labelOf = ::formatSalary,
            initialValue = minimumSalary,
            clearLabel = "Not specified",
            onConfirm = { minimumSalary = it; showSalary = false },
            onDismiss = { showSalary = false },
        )
    }
}

private fun formatSalary(value: Long): String = "S$%,d".format(value)
