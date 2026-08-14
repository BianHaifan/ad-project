package com.adproject.candidate.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.AuthRepository
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

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val mutableSignIn = MutableStateFlow(SignInUiState())
    val signIn: StateFlow<SignInUiState> = mutableSignIn.asStateFlow()
    private val mutableRegister = MutableStateFlow(RegisterUiState())
    val register: StateFlow<RegisterUiState> = mutableRegister.asStateFlow()

    fun updateSignInEmail(value: String) = mutableSignIn.update { it.copy(email = value, fieldErrors = it.fieldErrors - "email", message = null) }
    fun updateSignInPassword(value: String) = mutableSignIn.update { it.copy(password = value, fieldErrors = it.fieldErrors - "password", message = null) }
    fun updateFullName(value: String) = mutableRegister.update { it.copy(fullName = value, fieldErrors = it.fieldErrors - "fullName", message = null) }
    fun updateRegisterEmail(value: String) = mutableRegister.update { it.copy(email = value, fieldErrors = it.fieldErrors - "email", message = null) }
    fun updateRegisterPassword(value: String) = mutableRegister.update { it.copy(password = value, fieldErrors = it.fieldErrors - "password", message = null) }
    fun updateConfirmPassword(value: String) = mutableRegister.update { it.copy(confirmPassword = value, fieldErrors = it.fieldErrors - "confirmPassword", message = null) }
    fun updateAgreed(value: Boolean) = mutableRegister.update { it.copy(agreed = value, fieldErrors = it.fieldErrors - "agreed", message = null) }

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
