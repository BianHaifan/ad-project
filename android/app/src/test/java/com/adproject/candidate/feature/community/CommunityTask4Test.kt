package com.adproject.candidate.feature.community

import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.contract.PageMeta
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityTask4Test {
    private lateinit var server: MockWebServer
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown(); Dispatchers.resetMain() }

    @Test fun repositoryUsesRealFeedPaginationAndCreateContract() = runTest {
        val repository = RealCommunityRepository(retrofit().create(CommunityHttpApi::class.java), moshi)
        server.enqueue(json(feedEnvelope(hasNext = true)))
        val feed = repository.posts(2) as ApiResult.Success
        assertEquals("post-1", feed.value.posts.single().id)
        assertTrue(feed.value.meta.hasNext)
        assertEquals("/api/v1/community/posts?page=2&pageSize=20", server.takeRequest().path)

        server.enqueue(json("""{"data":${postJson("normalized")}}""", 201))
        val created = repository.create("  normalized  ") as ApiResult.Success
        assertEquals("normalized", created.value.body)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/community/posts", request.path)
        assertTrue(request.body.readUtf8().contains("  normalized  "))
    }

    @Test fun repositoryMapsSafeNetworkAndValidationFailures() = runTest {
        val repository = RealCommunityRepository(retrofit().create(CommunityHttpApi::class.java), moshi)
        server.enqueue(json("""{"error":{"code":"VALIDATION_ERROR","message":"Check body","fieldErrors":{"body":"too long"},"requestId":"r1"}}""", 422))
        val failure = repository.create("x") as ApiResult.Failure
        assertEquals("too long", failure.fieldErrors["body"])
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val network = repository.posts(1) as ApiResult.Failure
        assertFalse(network.message.contains("Exception"))
    }

    @Test fun viewModelCoversContentPagingRefreshAndEmpty() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = QueueRepository(
            pages = mutableListOf(
                ApiResult.Success(page(listOf(post("one")), 1, true)),
                ApiResult.Success(page(listOf(post("two")), 2, false)),
                ApiResult.Success(page(emptyList(), 1, false)),
            ),
        )
        val viewModel = CommunityViewModel(repository)
        advanceUntilIdle()
        assertEquals(CommunityScreenMode.CONTENT, communityScreenMode(viewModel.state.value))
        viewModel.loadMore(); advanceUntilIdle()
        assertEquals(listOf("one", "two"), viewModel.state.value.posts.map { it.id })
        viewModel.refresh(); advanceUntilIdle()
        assertEquals(CommunityScreenMode.EMPTY, communityScreenMode(viewModel.state.value))
    }

    @Test fun publishingPreventsDuplicatesAndRetainsDraftForRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<ApiResult<CommunityPost>>()
        val repository = QueueRepository(
            pages = mutableListOf(ApiResult.Success(page(emptyList(), 1, false))), create = pending,
        )
        val viewModel = CommunityViewModel(repository)
        advanceUntilIdle()
        viewModel.updateDraft("Keep me")
        viewModel.publish(); viewModel.publish(); advanceUntilIdle()
        assertEquals(1, repository.createCalls)
        assertTrue(viewModel.state.value.submitting)
        pending.complete(ApiResult.Failure("Try again")); advanceUntilIdle()
        assertEquals("Keep me", viewModel.state.value.draft)
        assertEquals("Try again", viewModel.state.value.publishError)
        assertFalse(viewModel.state.value.submitting)
    }

    @Test fun uiModesAndUnicodeCodePointBoundaryAreStable() {
        assertEquals(CommunityScreenMode.LOADING, communityScreenMode(CommunityUiState()))
        assertEquals(CommunityScreenMode.ERROR, communityScreenMode(CommunityUiState(loading = false, loadError = "safe")))
        assertEquals(CommunityScreenMode.EMPTY, communityScreenMode(CommunityUiState(loading = false)))
        val emoji = "😀".repeat(2000)
        assertEquals(2000, emoji.codePointCount(0, emoji.length))
    }

    private fun retrofit() = Retrofit.Builder().baseUrl(server.url("/api/v1/"))
        .addConverterFactory(MoshiConverterFactory.create(moshi)).build()
    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body)
    private fun feedEnvelope(hasNext: Boolean) = """{"data":[${postJson("hello")}],"meta":{"page":2,"pageSize":20,"total":21,"hasNext":$hasNext}}"""
    private fun postJson(body: String) = """{"id":"post-1","author":{"userId":"u1","fullName":"Candidate One","avatarUrl":null,"role":"CANDIDATE","companyName":null},"body":"$body","likeCount":2,"commentCount":3,"likedByCurrentUser":false,"createdAt":"2026-08-16T00:00:00Z","updatedAt":"2026-08-16T00:00:00Z"}"""
    private fun post(id: String) = CommunityPost(id, CommunityAuthor("u", "Candidate", null, "CANDIDATE", null), "body", 0, 0, false, "2026-08-16T00:00:00Z", "2026-08-16T00:00:00Z")
    private fun page(posts: List<CommunityPost>, page: Int, next: Boolean) = CommunityPostPage(posts, PageMeta(page, 20, posts.size, next))
}

private class QueueRepository(
    private val pages: MutableList<ApiResult<CommunityPostPage>>,
    private val create: CompletableDeferred<ApiResult<CommunityPost>>? = null,
) : CommunityRepository {
    var createCalls = 0
    override suspend fun posts(page: Int, pageSize: Int) = pages.removeFirst()
    override suspend fun create(body: String): ApiResult<CommunityPost> {
        createCalls++
        return create?.await() ?: ApiResult.Failure("not configured")
    }
    override suspend fun post(postId: String) = ApiResult.Failure("not configured")
    override suspend fun like(postId: String) = ApiResult.Failure("not configured")
    override suspend fun unlike(postId: String) = ApiResult.Failure("not configured")
    override suspend fun comments(postId: String, page: Int, pageSize: Int) = ApiResult.Failure("not configured")
    override suspend fun createComment(postId: String, body: String) = ApiResult.Failure("not configured")
}
