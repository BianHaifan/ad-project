package com.adproject.candidate.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.CandidatePublicProfileRepository
import com.adproject.candidate.data.contract.CompanyPublicProfile
import com.adproject.candidate.data.contract.RecruiterPublicProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecruiterPublicProfileUiState(
    val loading: Boolean = true,
    val data: RecruiterPublicProfile? = null,
    val message: String? = null,
    val notFound: Boolean = false,
)

class RecruiterPublicProfileViewModel(
    private val recruiterId: String,
    private val repository: CandidatePublicProfileRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RecruiterPublicProfileUiState())
    val state: StateFlow<RecruiterPublicProfileUiState> = mutableState.asStateFlow()

    init { load() }
    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            mutableState.value = RecruiterPublicProfileUiState(loading = true)
            when (val result = repository.recruiter(recruiterId)) {
                is ApiResult.Success -> mutableState.value =
                    RecruiterPublicProfileUiState(data = result.value, loading = false)
                is ApiResult.Failure -> mutableState.value = RecruiterPublicProfileUiState(
                    loading = false, message = result.message, notFound = result.statusCode == 404,
                )
            }
        }
    }

    companion object {
        fun factory(recruiterId: String, repository: CandidatePublicProfileRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RecruiterPublicProfileViewModel(recruiterId, repository) as T
            }
    }
}

data class CompanyPublicProfileUiState(
    val loading: Boolean = true,
    val data: CompanyPublicProfile? = null,
    val message: String? = null,
    val notFound: Boolean = false,
)

class CompanyPublicProfileViewModel(
    private val companyId: String,
    private val repository: CandidatePublicProfileRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CompanyPublicProfileUiState())
    val state: StateFlow<CompanyPublicProfileUiState> = mutableState.asStateFlow()

    init { load() }
    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            mutableState.value = CompanyPublicProfileUiState(loading = true)
            when (val result = repository.company(companyId)) {
                is ApiResult.Success -> mutableState.value =
                    CompanyPublicProfileUiState(data = result.value, loading = false)
                is ApiResult.Failure -> mutableState.value = CompanyPublicProfileUiState(
                    loading = false, message = result.message, notFound = result.statusCode == 404,
                )
            }
        }
    }

    companion object {
        fun factory(companyId: String, repository: CandidatePublicProfileRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CompanyPublicProfileViewModel(companyId, repository) as T
            }
    }
}
