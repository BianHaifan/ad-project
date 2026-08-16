package com.adproject.candidate

import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.CandidatePublicProfileRepository
import com.adproject.candidate.data.contract.CompanyPublicProfile
import com.adproject.candidate.data.contract.PublicCompanySummary
import com.adproject.candidate.data.contract.RecruiterPublicProfile
import com.adproject.candidate.feature.profile.CompanyPublicProfileViewModel
import com.adproject.candidate.feature.profile.RecruiterPublicProfileViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PublicProfileViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun recruiterLoadsContentThenErrorAndNotFound() = runTest(main.dispatcher) {
        val repository = QueuePublicProfileRepository(
            recruiterResults = mutableListOf(
                ApiResult.Success(recruiter()),
                ApiResult.Failure("Network unavailable"),
                ApiResult.Failure("gone", statusCode = 404),
            ),
        )
        val viewModel = RecruiterPublicProfileViewModel("rec-1", repository)
        advanceUntilIdle()
        assertEquals("Mia Chen", viewModel.state.value.data?.fullName)
        assertFalse(viewModel.state.value.loading)

        viewModel.retry(); advanceUntilIdle()
        assertEquals("Network unavailable", viewModel.state.value.message)
        assertNull(viewModel.state.value.data)

        viewModel.retry(); advanceUntilIdle()
        assertTrue(viewModel.state.value.notFound)
        assertNull(viewModel.state.value.data)
    }

    @Test fun companyLoadsContentThenNotFound() = runTest(main.dispatcher) {
        val repository = QueuePublicProfileRepository(
            companyResults = mutableListOf(
                ApiResult.Success(company()),
                ApiResult.Failure("gone", statusCode = 404),
            ),
        )
        val viewModel = CompanyPublicProfileViewModel("co-1", repository)
        advanceUntilIdle()
        assertEquals("Moonshot AI", viewModel.state.value.data?.name)
        assertEquals("APPROVED", viewModel.state.value.data?.verificationStatus)
        assertFalse(viewModel.state.value.loading)

        viewModel.retry(); advanceUntilIdle()
        assertTrue(viewModel.state.value.notFound)
        assertNull(viewModel.state.value.data)
    }
}

private class QueuePublicProfileRepository(
    val recruiterResults: MutableList<ApiResult<RecruiterPublicProfile>> = mutableListOf(),
    val companyResults: MutableList<ApiResult<CompanyPublicProfile>> = mutableListOf(),
) : CandidatePublicProfileRepository {
    override suspend fun recruiter(recruiterId: String) = recruiterResults.removeFirst()
    override suspend fun company(companyId: String) = companyResults.removeFirst()
}

private fun recruiter() = RecruiterPublicProfile(
    recruiterId = "rec-1",
    fullName = "Mia Chen",
    avatarUrl = null,
    title = "Hiring Manager",
    bio = "Builds teams",
    company = PublicCompanySummary("co-1", "Moonshot AI", null, "APPROVED"),
)

private fun company() = CompanyPublicProfile(
    companyId = "co-1",
    name = "Moonshot AI",
    logoUrl = null,
    description = "Builds AI tools",
    location = "Singapore",
    verificationStatus = "APPROVED",
)
