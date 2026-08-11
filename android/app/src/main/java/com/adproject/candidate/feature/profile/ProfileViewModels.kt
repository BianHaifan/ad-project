package com.adproject.candidate.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.CandidateProfileRepository
import com.adproject.candidate.data.api.CandidateResumeRepository
import com.adproject.candidate.data.contract.CandidateProfileDto
import com.adproject.candidate.data.contract.Experience
import com.adproject.candidate.data.contract.Resume
import com.adproject.candidate.data.contract.SaveResumeRequest
import com.adproject.candidate.data.contract.UpdateProfileRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true, val data: CandidateProfileDto? = null, val editing: Boolean = false,
    val submitting: Boolean = false, val message: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(), val saved: Boolean = false,
)

class CandidateProfileViewModel(private val repository: CandidateProfileRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = mutable
    init { load() }
    fun load() {
        mutable.value = ProfileUiState()
        viewModelScope.launch {
            mutable.value = when (val result = repository.get()) {
                is ApiResult.Success -> ProfileUiState(loading = false, data = result.value)
                is ApiResult.Failure -> ProfileUiState(loading = false, message = result.message)
            }
        }
    }
    fun edit() = mutable.update { it.copy(editing = true, saved = false, message = null) }
    fun save(fullName: String, headline: String, location: String) {
        val current = mutable.value
        if (current.submitting || current.data == null) return
        val errors = buildMap {
            if (fullName.isBlank()) put("fullName", "Full name is required")
            if (fullName.length > 100) put("fullName", "Maximum 100 characters")
            if (headline.length > 200) put("headline", "Maximum 200 characters")
            if (location.length > 100) put("location", "Maximum 100 characters")
        }
        if (errors.isNotEmpty()) { mutable.update { it.copy(fieldErrors = errors) }; return }
        mutable.update { it.copy(submitting = true, fieldErrors = emptyMap(), message = null) }
        viewModelScope.launch {
            when (val result = repository.update(UpdateProfileRequest(fullName, headline, location, current.data.version))) {
                is ApiResult.Success -> mutable.value = ProfileUiState(loading = false, data = result.value, saved = true)
                is ApiResult.Failure -> mutable.update { it.copy(submitting = false, message = result.message, fieldErrors = result.fieldErrors) }
            }
        }
    }
    companion object { fun factory(repository: CandidateProfileRepository): ViewModelProvider.Factory = viewModelFactory { initializer { CandidateProfileViewModel(repository) } } }
}

data class ResumeUiState(
    val loading: Boolean = true, val data: Resume? = null, val notCreated: Boolean = false,
    val submitting: Boolean = false, val message: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(), val saved: Boolean = false,
)

class CandidateResumeViewModel(private val repository: CandidateResumeRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ResumeUiState())
    val state: StateFlow<ResumeUiState> = mutable
    init { load() }
    fun load() {
        mutable.value = ResumeUiState()
        viewModelScope.launch {
            mutable.value = when (val result = repository.get()) {
                is ApiResult.Success -> ResumeUiState(loading = false, data = result.value)
                is ApiResult.Failure -> if (result.statusCode == 404) ResumeUiState(loading = false, notCreated = true)
                else ResumeUiState(loading = false, message = result.message)
            }
        }
    }
    fun save(fullName: String, ageText: String, location: String, headline: String, summary: String, experiences: List<Experience>) {
        val current = mutable.value
        if (current.submitting) return
        val age = ageText.toIntOrNull()
        val errors = buildMap {
            if (fullName.isBlank()) put("fullName", "Full name is required")
            if (age == null || age !in 16..100) put("age", "Age must be 16–100")
            if (location.isBlank()) put("location", "Location is required")
            if (headline.isBlank()) put("headline", "Headline is required")
            if (summary.isBlank()) put("summary", "Summary is required")
            val month = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
            experiences.forEachIndexed { index, experience ->
                if (experience.title.isBlank()) put("experiences[$index].title", "Title is required")
                if (experience.company.isBlank()) put("experiences[$index].company", "Company is required")
                if (!month.matches(experience.startDate)) put("experiences[$index].startDate", "Use YYYY-MM")
                if (experience.endDate != null && !month.matches(experience.endDate)) put("experiences[$index].endDate", "Use YYYY-MM")
            }
        }
        if (errors.isNotEmpty()) { mutable.update { it.copy(fieldErrors = errors) }; return }
        mutable.update { it.copy(submitting = true, fieldErrors = emptyMap(), message = null) }
        viewModelScope.launch {
            val resume = current.data
            val request = SaveResumeRequest(fullName, age!!, location, headline, summary, experiences, resume?.version ?: 0)
            when (val result = repository.save(request)) {
                is ApiResult.Success -> mutable.value = ResumeUiState(loading = false, data = result.value, saved = true)
                is ApiResult.Failure -> mutable.update { it.copy(submitting = false, message = result.message, fieldErrors = result.fieldErrors) }
            }
        }
    }
    companion object { fun factory(repository: CandidateResumeRepository): ViewModelProvider.Factory = viewModelFactory { initializer { CandidateResumeViewModel(repository) } } }
}
