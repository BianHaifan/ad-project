package com.adproject.candidate.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.AuthRepository
import com.adproject.candidate.data.contract.CandidateOnboardingRequest
import com.adproject.candidate.data.contract.EmploymentType
import com.adproject.candidate.data.contract.WorkplaceType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val message: String? = null,
)

data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val agreed: Boolean = false,
    val submitting: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val message: String? = null,
)

enum class PasswordResetStep { EMAIL, CODE, COMPLETE }
data class PasswordResetUiState(
    val step: PasswordResetStep = PasswordResetStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val resendSeconds: Int = 0,
    val submitting: Boolean = false,
    val message: String? = null,
)

data class OnboardingUiState(
    val headline: String = "",
    val location: String = "",
    val age: String = "",
    val summary: String = "",
    val skills: String = "",
    val desiredTitle: String = "",
    val preferredLocation: String = "",
    val workplaceType: WorkplaceType = WorkplaceType.HYBRID,
    val employmentType: EmploymentType = EmploymentType.FULL_TIME,
    val submitting: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val message: String? = null,
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val mutableSignIn = MutableStateFlow(SignInUiState())
    val signIn: StateFlow<SignInUiState> = mutableSignIn.asStateFlow()
    private val mutableRegister = MutableStateFlow(RegisterUiState())
    val register: StateFlow<RegisterUiState> = mutableRegister.asStateFlow()
    private val mutableReset = MutableStateFlow(PasswordResetUiState())
    val reset: StateFlow<PasswordResetUiState> = mutableReset.asStateFlow()
    private val mutableOnboarding = MutableStateFlow(OnboardingUiState())
    val onboarding: StateFlow<OnboardingUiState> = mutableOnboarding.asStateFlow()

    fun updateSignInEmail(value: String) = mutableSignIn.update { it.copy(email = value, fieldErrors = it.fieldErrors - "email", message = null) }
    fun updateSignInPassword(value: String) = mutableSignIn.update { it.copy(password = value, fieldErrors = it.fieldErrors - "password", message = null) }
    fun updateFullName(value: String) = mutableRegister.update { it.copy(fullName = value, fieldErrors = it.fieldErrors - "fullName", message = null) }
    fun updateRegisterEmail(value: String) = mutableRegister.update { it.copy(email = value, fieldErrors = it.fieldErrors - "email", message = null) }
    fun updateRegisterPassword(value: String) = mutableRegister.update { it.copy(password = value, fieldErrors = it.fieldErrors - "password", message = null) }
    fun updateConfirmPassword(value: String) = mutableRegister.update { it.copy(confirmPassword = value, fieldErrors = it.fieldErrors - "confirmPassword", message = null) }
    fun updateAgreed(value: Boolean) = mutableRegister.update { it.copy(agreed = value, fieldErrors = it.fieldErrors - "agreed", message = null) }
    fun updateResetEmail(value: String) = mutableReset.update { it.copy(email = value, message = null) }
    fun updateResetCode(value: String) = mutableReset.update { it.copy(code = value.filter(Char::isDigit).take(6), message = null) }
    fun updateResetPassword(value: String) = mutableReset.update { it.copy(password = value, message = null) }
    fun updateResetConfirm(value: String) = mutableReset.update { it.copy(confirmPassword = value, message = null) }
    fun restartPasswordReset() = mutableReset.update { PasswordResetUiState(email = it.email) }

    fun updateOnboardingHeadline(value: String) = mutableOnboarding.update { it.copy(headline = value, fieldErrors = it.fieldErrors - "headline", message = null) }
    fun updateOnboardingLocation(value: String) = mutableOnboarding.update { it.copy(location = value, fieldErrors = it.fieldErrors - "location", message = null) }
    fun updateOnboardingAge(value: String) = mutableOnboarding.update { it.copy(age = value.filter(Char::isDigit).take(3), fieldErrors = it.fieldErrors - "age", message = null) }
    fun updateOnboardingSummary(value: String) = mutableOnboarding.update { it.copy(summary = value, fieldErrors = it.fieldErrors - "resumeSummary", message = null) }
    fun updateOnboardingSkills(value: String) = mutableOnboarding.update { it.copy(skills = value, fieldErrors = it.fieldErrors - "skills", message = null) }
    fun updateDesiredTitle(value: String) = mutableOnboarding.update { it.copy(desiredTitle = value, fieldErrors = it.fieldErrors - "desiredTitle", message = null) }
    fun updatePreferredLocation(value: String) = mutableOnboarding.update { it.copy(preferredLocation = value, fieldErrors = it.fieldErrors - "preferredLocation", message = null) }
    fun updateWorkplaceType(value: WorkplaceType) = mutableOnboarding.update { it.copy(workplaceType = value, message = null) }
    fun updateEmploymentType(value: EmploymentType) = mutableOnboarding.update { it.copy(employmentType = value, message = null) }

    fun signIn() {
        val state = mutableSignIn.value
        if (state.submitting) return
        val errors = validateCredentials(state.email, state.password)
        if (errors.isNotEmpty()) {
            mutableSignIn.update { it.copy(fieldErrors = errors) }
            return
        }
        mutableSignIn.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.login(state.email.trim(), state.password)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> mutableSignIn.update {
                    it.copy(submitting = false, fieldErrors = result.fieldErrors, message = result.message)
                }
            }
        }
    }

    fun register() {
        val state = mutableRegister.value
        if (state.submitting) return
        val errors = validateRegistration(state)
        if (errors.isNotEmpty()) {
            mutableRegister.update { it.copy(fieldErrors = errors) }
            return
        }
        mutableRegister.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.register(state.fullName.trim(), state.email.trim(), state.password)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> mutableRegister.update {
                    it.copy(submitting = false, fieldErrors = result.fieldErrors, message = result.message)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    fun requestPasswordReset() {
        val state = mutableReset.value
        if (state.submitting || !EMAIL.matches(state.email.trim())) {
            if (!EMAIL.matches(state.email.trim())) mutableReset.update { it.copy(message = "Enter a valid email address") }
            return
        }
        mutableReset.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.requestPasswordReset(state.email.trim())) {
                is ApiResult.Success -> {
                    mutableReset.update { it.copy(step = PasswordResetStep.CODE, submitting = false, resendSeconds = 60,
                        message = "If the account exists, a code was sent.") }
                    while (mutableReset.value.resendSeconds > 0) {
                        delay(1_000)
                        mutableReset.update { it.copy(resendSeconds = (it.resendSeconds - 1).coerceAtLeast(0)) }
                    }
                }
                is ApiResult.Failure -> mutableReset.update { it.copy(submitting = false, message = result.message) }
            }
        }
    }

    fun confirmPasswordReset() {
        val state = mutableReset.value
        if (state.submitting) return
        val error = when {
            state.code.length != 6 -> "Enter the 6-digit code"
            state.password.length !in 8..128 -> "Password must be 8–128 characters"
            state.password != state.confirmPassword -> "Passwords do not match"
            else -> null
        }
        if (error != null) { mutableReset.update { it.copy(message = error) }; return }
        mutableReset.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.confirmPasswordReset(state.email.trim(), state.code, state.password)) {
                is ApiResult.Success -> mutableReset.update { it.copy(step = PasswordResetStep.COMPLETE, submitting = false,
                    message = "Password changed. Sign in with your new password.") }
                is ApiResult.Failure -> mutableReset.update { it.copy(submitting = false, message = result.message) }
            }
        }
    }

    fun resendPasswordReset() {
        if (mutableReset.value.resendSeconds > 0) return
        requestPasswordReset()
    }

    fun completeOnboarding() {
        val state = mutableOnboarding.value
        if (state.submitting) return
        val skills = state.skills.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
        val age = state.age.toIntOrNull()
        val errors = buildMap {
            if (state.headline.isBlank()) put("headline", "Headline is required")
            if (state.location.isBlank()) put("location", "Location is required")
            if (age == null || age !in 16..100) put("age", "Age must be between 16 and 100")
            if (state.summary.isBlank()) put("resumeSummary", "Summary is required")
            if (skills.isEmpty()) put("skills", "Add at least one skill")
            if (state.desiredTitle.isBlank()) put("desiredTitle", "Target role is required")
            if (state.preferredLocation.isBlank()) put("preferredLocation", "Preferred location is required")
        }
        if (errors.isNotEmpty()) { mutableOnboarding.update { it.copy(fieldErrors = errors) }; return }
        val request = CandidateOnboardingRequest(state.headline.trim(), state.location.trim(), age!!,
            state.summary.trim(), skills, state.desiredTitle.trim(), state.preferredLocation.trim(),
            state.workplaceType, state.employmentType)
        mutableOnboarding.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.completeOnboarding(request)) {
                is ApiResult.Success -> mutableOnboarding.update { it.copy(submitting = false) }
                is ApiResult.Failure -> mutableOnboarding.update { it.copy(submitting = false,
                    fieldErrors = result.fieldErrors, message = result.message) }
            }
        }
    }

    private fun validateCredentials(email: String, password: String): Map<String, String> = buildMap {
        if (!EMAIL.matches(email.trim())) put("email", "Enter a valid email address")
        if (password.isBlank()) put("password", "Password is required")
    }

    private fun validateRegistration(state: RegisterUiState): Map<String, String> = buildMap {
        if (state.fullName.isBlank()) put("fullName", "Full name is required")
        putAll(validateCredentials(state.email, state.password))
        if (state.password.length !in 8..128) put("password", "Password must be 8–128 characters")
        if (state.confirmPassword != state.password) put("confirmPassword", "Passwords do not match")
        if (!state.agreed) put("agreed", "You must accept the terms")
    }

    companion object {
        private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        fun factory(repository: AuthRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(repository) as T
        }
    }
}
