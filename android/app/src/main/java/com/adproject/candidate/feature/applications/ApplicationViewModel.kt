package com.adproject.candidate.feature.applications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adproject.candidate.data.api.*
import com.adproject.candidate.data.contract.*
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ApplicationFlowUiState(
    val jobId: String? = null,
    val loading: Boolean = false,
    val job: CandidateJobDetail? = null,
    val profile: CandidateProfileDto? = null,
    val resume: Resume? = null,
    val resumeMissing: Boolean = false,
    val message: String? = null,
    val submitting: Boolean = false,
    val shareProfile: Boolean = true,
    val result: CandidateApplication? = null,
)

class ApplicationViewModel(
    private val jobs: CandidateJobRepository,
    private val profiles: CandidateProfileRepository,
    private val resumes: CandidateResumeRepository,
    private val applications: CandidateApplicationRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(ApplicationFlowUiState())
    val state: StateFlow<ApplicationFlowUiState> = mutable.asStateFlow()
    private var submissionKey: String? = null

    fun start(jobId: String) {
        if (mutable.value.jobId == jobId && (mutable.value.loading || mutable.value.job != null)) return
        submissionKey = null
        load(jobId)
    }

    fun retryLoad() { mutable.value.jobId?.let(::load) }
    fun setShareProfile(value: Boolean) { mutable.value = mutable.value.copy(shareProfile = value) }

    private fun load(jobId: String) {
        mutable.value = ApplicationFlowUiState(jobId = jobId, loading = true)
        viewModelScope.launch {
            val job = jobs.job(jobId)
            if (job is ApiResult.Failure) {
                mutable.value = ApplicationFlowUiState(jobId = jobId, message = job.message)
                return@launch
            }
            val profile = profiles.get()
            if (profile is ApiResult.Failure) {
                mutable.value = ApplicationFlowUiState(jobId = jobId, message = profile.message)
                return@launch
            }
            when (val resume = resumes.get()) {
                is ApiResult.Failure -> mutable.value = ApplicationFlowUiState(
                    jobId = jobId,
                    job = (job as ApiResult.Success).value,
                    profile = (profile as ApiResult.Success).value,
                    resumeMissing = resume.statusCode == 404,
                    message = if (resume.statusCode == 404) null else resume.message,
                )
                is ApiResult.Success -> mutable.value = ApplicationFlowUiState(
                    jobId = jobId, job = (job as ApiResult.Success).value,
                    profile = (profile as ApiResult.Success).value, resume = resume.value,
                )
            }
        }
    }

    fun submit() {
        val current = mutable.value
        val jobId = current.jobId ?: return
        val resume = current.resume ?: return
        val profile = current.profile ?: return
        if (current.job?.applicationState != CandidateJobApplicationState.NOT_APPLIED) return
        if (current.submitting || current.result != null) return
        val key = submissionKey ?: UUID.randomUUID().toString().also { submissionKey = it }
        mutable.value = current.copy(submitting = true, message = null)
        viewModelScope.launch {
            when (val result = applications.submit(jobId, key,
                SubmitApplicationRequest(resume.resumeId, profile.email, current.shareProfile))) {
                is ApiResult.Success -> mutable.value = mutable.value.copy(submitting = false, result = result.value)
                is ApiResult.Failure -> mutable.value = mutable.value.copy(submitting = false, message = result.message)
            }
        }
    }

    fun clear() { submissionKey = null; mutable.value = ApplicationFlowUiState() }

    companion object {
        fun factory(jobs: CandidateJobRepository, profiles: CandidateProfileRepository,
                    resumes: CandidateResumeRepository, applications: CandidateApplicationRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ApplicationViewModel(jobs, profiles, resumes, applications) as T
            }
    }
}
