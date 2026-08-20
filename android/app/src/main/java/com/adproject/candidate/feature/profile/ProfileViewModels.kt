package com.adproject.candidate.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.AvatarUpload
import com.adproject.candidate.data.api.CandidateAvatarRepository
import com.adproject.candidate.data.api.CandidateProfileRepository
import com.adproject.candidate.data.api.CandidateResumeRepository
import com.adproject.candidate.data.contract.CandidateProfileDto
import com.adproject.candidate.data.contract.Experience
import com.adproject.candidate.data.contract.Gender
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
    val avatar: AvatarUiState = AvatarUiState(),
)

// Avatar editing is isolated from profile text/resume editing: a failed upload or
// delete never blanks the profile, and the pending bytes only ever live in memory.
data class AvatarUiState(
    val uploading: Boolean = false,
    val deleting: Boolean = false,
    val message: String? = null,
    val pending: PendingAvatar? = null,
    val revision: Long = 0,
)

data class PendingAvatar(val fileName: String, val contentType: String, val bytes: ByteArray)

class CandidateProfileViewModel(
    private val repository: CandidateProfileRepository,
    private val avatarRepository: CandidateAvatarRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = mutable
    init { load() }
    fun reset() { mutable.value = ProfileUiState() }
    fun load() {
        mutable.value = ProfileUiState()
        viewModelScope.launch {
            when (val result = repository.get()) {
                is ApiResult.Success -> mutable.update { it.copy(loading = false, data = result.value) }
                is ApiResult.Failure -> mutable.update { it.copy(loading = false, message = result.message) }
            }
        }
    }
    /** Re-fetch without clearing the screen, so returning from another tab shows fresh data. */
    fun refresh() {
        viewModelScope.launch {
            when (val result = repository.get()) {
                is ApiResult.Success -> mutable.update { it.copy(loading = false, data = result.value, message = null) }
                is ApiResult.Failure -> Unit
            }
        }
    }
    fun edit() = mutable.update { it.copy(editing = true, saved = false, message = null) }
    fun cancelEdit() = mutable.update { it.copy(editing = false, saved = false, message = null, fieldErrors = emptyMap()) }
    fun clearSaved() = mutable.update { it.copy(saved = false) }
    fun save(fullName: String, gender: Gender?, age: Int?, location: String, headline: String, phone: String, birthplace: String) {
        val current = mutable.value
        if (current.submitting || current.data == null) return
        val trimmedLocation = location.trim()
        val trimmedPhone = phone.trim()
        val trimmedBirthplace = birthplace.trim()
        val errors = buildMap {
            if (fullName.isBlank()) put("fullName", "Full name is required")
            if (fullName.length > 100) put("fullName", "Maximum 100 characters")
            if (trimmedLocation.isBlank()) put("location", "Location is required")
            if (trimmedLocation.length > 100) put("location", "Maximum 100 characters")
            if (headline.length > 200) put("headline", "Maximum 200 characters")
            if (age != null && age !in 16..100) put("age", "Age must be 16–100")
            if (trimmedPhone.isNotEmpty() && !PHONE_PATTERN.matches(trimmedPhone)) put("phone", "Enter a valid phone number")
            if (trimmedBirthplace.length > 100) put("birthplace", "Maximum 100 characters")
        }
        if (errors.isNotEmpty()) {
            mutable.update { it.copy(fieldErrors = errors, message = "Please correct the highlighted fields.") }
            return
        }
        mutable.update { it.copy(submitting = true, fieldErrors = emptyMap(), message = null) }
        viewModelScope.launch {
            val request = UpdateProfileRequest(
                fullName = fullName,
                headline = headline,
                location = trimmedLocation,
                age = age,
                gender = gender,
                phone = trimmedPhone.ifBlank { null },
                birthplace = trimmedBirthplace.ifBlank { null },
                expectedVersion = current.data.version,
            )
            when (val result = repository.update(request)) {
                is ApiResult.Success -> mutable.update {
                    it.copy(loading = false, data = result.value, submitting = false, saved = true, editing = false)
                }
                is ApiResult.Failure -> mutable.update { it.copy(submitting = false, message = result.message, fieldErrors = result.fieldErrors) }
            }
        }
    }
    fun selectAvatar(pending: PendingAvatar?) {
        mutable.update { it.copy(avatar = it.avatar.copy(pending = pending, message = null)) }
    }
    fun cancelAvatar() = selectAvatar(null)
    fun rejectAvatarTooLarge() {
        mutable.update { it.copy(avatar = it.avatar.copy(pending = null, message = AVATAR_TOO_LARGE_MESSAGE)) }
    }
    fun uploadAvatar() {
        val current = mutable.value
        val pending = current.avatar.pending ?: return
        if (current.avatar.uploading) return
        if (!isSupportedAvatarType(pending.contentType)) {
            mutable.update { it.copy(avatar = it.avatar.copy(message = "Only PNG or JPEG images are supported.")) }
            return
        }
        if (pending.bytes.size > MAX_AVATAR_BYTES) {
            mutable.update { it.copy(avatar = it.avatar.copy(message = AVATAR_TOO_LARGE_MESSAGE)) }
            return
        }
        mutable.update { it.copy(avatar = it.avatar.copy(uploading = true, message = null)) }
        viewModelScope.launch {
            when (val result = avatarRepository.upload(AvatarUpload(pending.fileName, pending.contentType, pending.bytes))) {
                is ApiResult.Success -> mutable.update { state ->
                    state.copy(
                        data = state.data?.copy(avatarUrl = result.value.avatarUrl),
                        avatar = state.avatar.copy(uploading = false, pending = null, message = null, revision = state.avatar.revision + 1),
                    )
                }
                is ApiResult.Failure -> mutable.update { it.copy(avatar = it.avatar.copy(uploading = false, message = result.message)) }
            }
        }
    }
    fun deleteAvatar() {
        val current = mutable.value
        if (current.avatar.deleting) return
        mutable.update { it.copy(avatar = it.avatar.copy(deleting = true, pending = null, message = null)) }
        viewModelScope.launch {
            when (val result = avatarRepository.delete()) {
                is ApiResult.Success -> mutable.update { state ->
                    state.copy(
                        data = state.data?.copy(avatarUrl = null),
                        avatar = state.avatar.copy(deleting = false, pending = null, message = null, revision = state.avatar.revision + 1),
                    )
                }
                is ApiResult.Failure -> mutable.update { it.copy(avatar = it.avatar.copy(deleting = false, message = result.message)) }
            }
        }
    }
    companion object {
        fun factory(repository: CandidateProfileRepository, avatarRepository: CandidateAvatarRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { CandidateProfileViewModel(repository, avatarRepository) } }
    }
}

data class ResumeUiState(
    val loading: Boolean = true, val data: Resume? = null, val notCreated: Boolean = false,
    val editing: Boolean = false, val submitting: Boolean = false, val message: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(), val saved: Boolean = false,
)

class CandidateResumeViewModel(private val repository: CandidateResumeRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ResumeUiState())
    val state: StateFlow<ResumeUiState> = mutable
    init { load() }
    fun reset() { mutable.value = ResumeUiState() }
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
    /** Re-fetch without clearing the screen, so agent edits show up when returning to Me. */
    fun refresh() {
        viewModelScope.launch {
            when (val result = repository.get()) {
                is ApiResult.Success -> mutable.update {
                    it.copy(loading = false, data = result.value, message = null, notCreated = false)
                }
                is ApiResult.Failure -> Unit
            }
        }
    }
    fun edit() = mutable.update { it.copy(editing = true, saved = false, message = null) }
    fun cancelEdit() = mutable.update { it.copy(editing = false, saved = false, message = null, fieldErrors = emptyMap()) }
    fun clearSaved() = mutable.update { it.copy(saved = false) }
    fun save(summary: String, skills: List<String>, experiences: List<Experience>) {
        val current = mutable.value
        if (current.submitting) return
        val normalizedSkills = skills.map(String::trim).filter(String::isNotEmpty).distinct()
        val errors = buildMap {
            if (summary.isBlank()) put("summary", "Summary is required")
            if (normalizedSkills.size > 100) put("skills", "Use at most 100 skills")
            if (normalizedSkills.any { it.length > 200 }) put("skills", "Each skill must be at most 200 characters")
            val month = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
            experiences.forEachIndexed { index, experience ->
                if (experience.title.isBlank()) put("experiences[$index].title", "Title is required")
                if (experience.company.isBlank()) put("experiences[$index].company", "Company is required")
                if (!month.matches(experience.startDate)) put("experiences[$index].startDate", "Use YYYY-MM")
                if (experience.endDate != null && !month.matches(experience.endDate)) put("experiences[$index].endDate", "Use YYYY-MM")
            }
        }
        if (errors.isNotEmpty()) {
            mutable.update { it.copy(fieldErrors = errors, message = "Please correct the highlighted fields.") }
            return
        }
        mutable.update { it.copy(submitting = true, fieldErrors = emptyMap(), message = null) }
        viewModelScope.launch {
            val resume = current.data
            val request = SaveResumeRequest(summary, experiences, resume?.version ?: 0, normalizedSkills)
            when (val result = repository.save(request)) {
                is ApiResult.Success -> mutable.value = ResumeUiState(loading = false, data = result.value, saved = true)
                is ApiResult.Failure -> mutable.update { it.copy(submitting = false, message = result.message, fieldErrors = result.fieldErrors) }
            }
        }
    }
    companion object { fun factory(repository: CandidateResumeRepository): ViewModelProvider.Factory = viewModelFactory { initializer { CandidateResumeViewModel(repository) } } }
}

internal const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
private const val AVATAR_TOO_LARGE_MESSAGE = "This image is larger than 5 MB."
// Mirrors the backend's phone format: optional leading '+', then a digit, then 4–19
// digits/spaces/dashes/parentheses.
private val PHONE_PATTERN = Regex("^\\+?[0-9][0-9\\s\\-()]{4,19}$")
private fun isSupportedAvatarType(contentType: String) = contentType == "image/png" || contentType == "image/jpeg"
