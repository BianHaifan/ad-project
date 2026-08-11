package com.adproject.candidate.feature.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.CandidateJobRepository
import com.adproject.candidate.data.contract.CandidateJob
import com.adproject.candidate.data.contract.EmploymentType
import com.adproject.candidate.data.model.Job
import com.adproject.candidate.data.model.JobDetailData
import com.adproject.candidate.data.model.JobFeedData
import com.adproject.candidate.data.model.RecruiterContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JobFeedUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val data: JobFeedData? = null,
    val message: String? = null,
    val query: String = "",
    val employmentType: EmploymentType? = EmploymentType.FULL_TIME,
)

class JobFeedViewModel(private val repository: CandidateJobRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(JobFeedUiState())
    val state: StateFlow<JobFeedUiState> = mutableState.asStateFlow()

    init { load() }

    fun updateQuery(value: String) = mutableState.update { it.copy(query = value) }
    fun selectEmploymentType(value: EmploymentType?) {
        mutableState.update { it.copy(employmentType = value) }
        load()
    }
    fun search() = load()
    fun retry() = load()
    fun refresh() = load(refreshing = true)

    private fun load(refreshing: Boolean = false) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = it.data == null && !refreshing, refreshing = refreshing, message = null) }
            val snapshot = mutableState.value
            when (val result = repository.jobs(snapshot.query, snapshot.employmentType)) {
                is ApiResult.Success -> mutableState.update {
                    it.copy(loading = false, refreshing = false,
                        data = JobFeedData("Search job titles", result.value.jobs.map(::toUiJob)), message = null)
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(loading = false, refreshing = false, message = result.message)
                }
            }
        }
    }

    companion object {
        fun factory(repository: CandidateJobRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = JobFeedViewModel(repository) as T
        }
    }
}

data class JobDetailUiState(
    val loading: Boolean = true,
    val data: JobDetailData? = null,
    val message: String? = null,
    val notFound: Boolean = false,
)

class JobDetailViewModel(
    private val jobId: String,
    private val repository: CandidateJobRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(JobDetailUiState())
    val state: StateFlow<JobDetailUiState> = mutableState.asStateFlow()

    init { load() }
    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            mutableState.value = JobDetailUiState(loading = true)
            when (val result = repository.job(jobId)) {
                is ApiResult.Success -> {
                    val detail = result.value
                    mutableState.value = JobDetailUiState(data = toUiDetail(detail.job,
                        detail.matchAnalysis != null), loading = false)
                }
                is ApiResult.Failure -> mutableState.value = JobDetailUiState(
                    loading = false, message = result.message, notFound = result.statusCode == 404,
                )
            }
        }
    }

    companion object {
        fun factory(jobId: String, repository: CandidateJobRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = JobDetailViewModel(jobId, repository) as T
            }
    }
}

private fun toUiJob(job: CandidateJob) = Job(
    jobId = job.jobId,
    title = job.title,
    company = job.company.name,
    companyInitial = job.company.name.take(1).uppercase(),
    companyMeta = listOfNotNull(job.company.stage, job.company.employeeRange).joinToString(" · ").ifBlank { "Verified company" },
    salary = "${job.salary.currency} ${job.salary.min}–${job.salary.max} / ${job.salary.period.lowercase()}",
    skills = job.skills,
    match = job.matchScore,
    recruiter = job.recruiter?.let { RecruiterContact(it.recruiterId, it.fullName, it.title) },
)

private fun toUiDetail(job: CandidateJob, matchAnalysisAvailable: Boolean) = JobDetailData(
    job = toUiJob(job),
    location = job.location,
    employmentType = job.employmentType.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
    workplace = job.workplaceType.name.lowercase().replaceFirstChar(Char::uppercase),
    strongMatches = "",
    gap = "",
    description = job.description,
    requirements = job.requirements.joinToString("  ·  "),
    skills = job.skills,
    deadline = job.deadline,
    publishedAt = job.publishedAt,
    matchAnalysisAvailable = matchAnalysisAvailable,
)
