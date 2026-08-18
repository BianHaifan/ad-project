package com.adproject.candidate.feature.applications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.CandidateApplicationRepository
import com.adproject.candidate.data.contract.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ApplicationListUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val applications: List<CandidateApplicationSummary> = emptyList(),
    val counts: ApplicationCounts = ApplicationCounts(0, 0, 0),
    val filter: ApplicationListFilter? = ApplicationListFilter.ACTIVE,
    val page: Int = 1,
    val hasNext: Boolean = false,
    val message: String? = null,
)

class ApplicationListViewModel(private val repository: CandidateApplicationRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ApplicationListUiState())
    val state: StateFlow<ApplicationListUiState> = mutable.asStateFlow()

    fun load() = loadPage(1, replace = true, refreshing = false)
    fun retry() = load()
    fun refresh() = loadPage(1, replace = true, refreshing = true)
    fun reset() { mutable.value = ApplicationListUiState() }

    fun selectFilter(filter: ApplicationListFilter?) {
        if (mutable.value.filter == filter) return
        mutable.value = mutable.value.copy(filter = filter, applications = emptyList(), page = 1, hasNext = false)
        load()
    }

    fun loadMore() {
        val current = mutable.value
        if (!current.hasNext || current.loading || current.refreshing || current.loadingMore) return
        loadPage(current.page + 1, replace = false, refreshing = false)
    }

    private fun loadPage(page: Int, replace: Boolean, refreshing: Boolean) {
        val current = mutable.value
        if (current.loading || current.refreshing || current.loadingMore) return
        mutable.value = current.copy(
            loading = replace && current.applications.isEmpty() && !refreshing,
            refreshing = refreshing,
            loadingMore = !replace,
            message = null,
        )
        viewModelScope.launch {
            when (val result = repository.applications(mutable.value.filter, page)) {
                is ApiResult.Success -> mutable.value = mutable.value.copy(
                    loading = false, refreshing = false, loadingMore = false,
                    applications = if (replace) result.value.applications
                    else mutable.value.applications + result.value.applications,
                    counts = result.value.meta.counts,
                    page = result.value.meta.page,
                    hasNext = result.value.meta.hasNext,
                    message = null,
                )
                is ApiResult.Failure -> mutable.value = mutable.value.copy(
                    loading = false, refreshing = false, loadingMore = false, message = result.message,
                )
            }
        }
    }

    companion object {
        fun factory(repository: CandidateApplicationRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ApplicationListViewModel(repository) as T
            }
    }
}

data class ApplicationDetailUiState(
    val loading: Boolean = false,
    val application: CandidateApplication? = null,
    val notFound: Boolean = false,
    val message: String? = null,
    val confirmingWithdraw: Boolean = false,
    val withdrawReason: String = "",
    val withdrawing: Boolean = false,
)

class ApplicationDetailViewModel(
    private val applicationId: String,
    private val repository: CandidateApplicationRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(ApplicationDetailUiState())
    val state: StateFlow<ApplicationDetailUiState> = mutable.asStateFlow()

    init { load() }

    fun load() {
        if (mutable.value.loading || mutable.value.withdrawing) return
        mutable.value = mutable.value.copy(loading = true, notFound = false, message = null)
        viewModelScope.launch {
            when (val result = repository.application(applicationId)) {
                is ApiResult.Success -> mutable.value = mutable.value.copy(
                    loading = false, application = result.value, notFound = false, message = null,
                )
                is ApiResult.Failure -> mutable.value = mutable.value.copy(
                    loading = false, application = null, notFound = result.statusCode == 404,
                    message = result.message,
                )
            }
        }
    }

    fun requestWithdraw() {
        if (!canWithdraw(mutable.value.application?.status) || mutable.value.withdrawing) return
        mutable.value = mutable.value.copy(confirmingWithdraw = true, withdrawReason = "", message = null)
    }

    fun dismissWithdraw() {
        if (!mutable.value.withdrawing) mutable.value = mutable.value.copy(confirmingWithdraw = false)
    }

    fun updateWithdrawReason(value: String) {
        if (!mutable.value.withdrawing && value.length <= 500) mutable.value = mutable.value.copy(withdrawReason = value)
    }

    fun confirmWithdraw() {
        val current = mutable.value
        val application = current.application ?: return
        val reason = current.withdrawReason.trim()
        if (!current.confirmingWithdraw || current.withdrawing || !canWithdraw(application.status)) return
        if (reason.isEmpty()) {
            mutable.value = current.copy(message = "Please provide a reason for withdrawing.")
            return
        }
        mutable.value = current.copy(withdrawing = true, message = null)
        viewModelScope.launch {
            when (val result = repository.withdraw(application.applicationId,
                WithdrawApplicationRequest(reason, application.version))) {
                is ApiResult.Success -> mutable.value = mutable.value.copy(
                    withdrawing = false, confirmingWithdraw = false, application = result.value,
                    withdrawReason = "", message = "Application withdrawn.",
                )
                is ApiResult.Failure -> mutable.value = mutable.value.copy(
                    withdrawing = false, confirmingWithdraw = false, message = result.message,
                )
            }
        }
    }

    companion object {
        fun canWithdraw(status: ApplicationStatus?): Boolean = status == ApplicationStatus.APPLIED
                || status == ApplicationStatus.IN_REVIEW || status == ApplicationStatus.INTERVIEW

        fun factory(applicationId: String, repository: CandidateApplicationRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ApplicationDetailViewModel(applicationId, repository) as T
            }
    }
}
