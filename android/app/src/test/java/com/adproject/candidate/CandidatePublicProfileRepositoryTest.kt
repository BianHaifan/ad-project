package com.adproject.candidate

import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.CandidatePublicProfileHttpApi
import com.adproject.candidate.data.api.RealCandidatePublicProfileRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class CandidatePublicProfileRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var moshi: Moshi
    private lateinit var repository: RealCandidatePublicProfileRepository

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        repository = RealCandidatePublicProfileRepository(
            retrofit().create(CandidatePublicProfileHttpApi::class.java), moshi,
        )
    }

    @After fun tearDown() = server.shutdown()

    @Test fun recruiterProfileParsesEnvelopeAndPublicFields() = runTest {
        server.enqueue(jsonResponse("""{"data":${recruiterProfile()}}"""))
        val result = repository.recruiter("rec-1") as ApiResult.Success
        assertEquals("rec-1", result.value.recruiterId)
        assertEquals("Mia Chen", result.value.fullName)
        assertEquals("Hiring Manager", result.value.title)
        assertEquals("Builds teams", result.value.bio)
        assertEquals("co-1", result.value.company.companyId)
        assertEquals("APPROVED", result.value.company.verificationStatus)
        assertEquals("/api/v1/candidate/recruiters/rec-1", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test fun companyProfileParsesEnvelopeAndPublicFields() = runTest {
        server.enqueue(jsonResponse("""{"data":${companyProfile()}}"""))
        val result = repository.company("co-1") as ApiResult.Success
        assertEquals("co-1", result.value.companyId)
        assertEquals("Moonshot AI", result.value.name)
        assertEquals("Builds AI tools", result.value.description)
        assertEquals("Singapore", result.value.location)
        assertEquals("APPROVED", result.value.verificationStatus)
        assertEquals("/api/v1/candidate/companies/co-1", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test fun missingRecruiterUsesProfileSpecificMessage() = runTest {
        server.enqueue(jsonResponse(errorBody("NOT_FOUND", "Resource not found"), 404))
        val missing = repository.recruiter("missing") as ApiResult.Failure
        assertEquals(404, missing.statusCode)
        assertEquals("This recruiter is no longer available.", missing.message)
    }

    @Test fun missingCompanyUsesProfileSpecificMessage() = runTest {
        server.enqueue(jsonResponse(errorBody("NOT_FOUND", "Resource not found"), 404))
        val missing = repository.company("missing") as ApiResult.Failure
        assertEquals(404, missing.statusCode)
        assertEquals("This company is no longer available.", missing.message)
    }

    private fun retrofit(): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/api/v1/"))
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private fun jsonResponse(body: String, code: Int = 200) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body)

    private fun errorBody(code: String, message: String): String =
        """{"error":{"code":"$code","message":"$message","fieldErrors":{},"requestId":"req-test"}}"""

    private fun recruiterProfile() = """
        {"recruiterId":"rec-1","fullName":"Mia Chen","avatarUrl":null,"title":"Hiring Manager",
        "bio":"Builds teams","company":{"companyId":"co-1","name":"Moonshot AI","logoUrl":null,"verificationStatus":"APPROVED"}}
    """.trimIndent()

    private fun companyProfile() = """
        {"companyId":"co-1","name":"Moonshot AI","logoUrl":null,"description":"Builds AI tools",
        "location":"Singapore","verificationStatus":"APPROVED"}
    """.trimIndent()
}
