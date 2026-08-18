package com.adproject.candidate.feature.community

import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.contract.PageMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.adproject.candidate.feature.jobs.formatSalary
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityTask5Test {
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun detailUsesServerLikeStateAndFailureReloadsIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = DetailFake(postResults = mutableListOf(ApiResult.Success(post(liked = false)), ApiResult.Success(post(liked = false))),
            likeResult = ApiResult.Failure("Network failed"))
        val vm = CommunityDetailViewModel("p1", repo); advanceUntilIdle()
        vm.toggleLike(); vm.toggleLike(); advanceUntilIdle()
        assertEquals(1, repo.likeCalls)
        assertFalse(vm.state.value.post!!.likedByCurrentUser)
        assertEquals("Network failed", vm.state.value.error)
    }

    @Test fun commentsPageInServerOrderAndSuccessfulCreateUsesServerCount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val one = comment("1"); val two = comment("2"); val three = comment("3")
        val repo = DetailFake(postResults = mutableListOf(ApiResult.Success(post())), commentPages = mutableListOf(
            ApiResult.Success(CommunityCommentPage(listOf(one, two), PageMeta(1, 20, 3, true))),
            ApiResult.Success(CommunityCommentPage(listOf(three), PageMeta(2, 20, 3, false))),
        ), createCommentResult = ApiResult.Success(CommunityCommentCreated(comment("4", "saved"), 4)))
        val vm = CommunityDetailViewModel("p1", repo); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        assertEquals(listOf("1", "2", "3"), vm.state.value.comments.map { it.id })
        vm.updateComment("\u2003 saved \u2003"); vm.publishComment(); advanceUntilIdle()
        assertEquals("saved", repo.lastCommentBody)
        assertEquals(4, vm.state.value.post!!.commentCount)
        assertEquals("saved", vm.state.value.comments.last().body)
        assertEquals("", vm.state.value.commentDraft)
    }

    @Test fun commentValidationUsesUnicodeCodePointsAndKeepsDraftOnFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = DetailFake(postResults = mutableListOf(ApiResult.Success(post())), commentPages = mutableListOf(ApiResult.Success(CommunityCommentPage(emptyList(), PageMeta(1, 20, 0, false)))),
            createCommentResult = ApiResult.Failure("Try again"))
        val vm = CommunityDetailViewModel("p1", repo); advanceUntilIdle()
        vm.updateComment("😀".repeat(501)); vm.publishComment(); advanceUntilIdle()
        assertEquals(0, repo.createCommentCalls)
        vm.updateComment("a\u2003b"); vm.publishComment(); advanceUntilIdle()
        assertEquals("a\u2003b", repo.lastCommentBody)
        assertEquals("a\u2003b", vm.state.value.commentDraft)
        assertEquals("Try again", vm.state.value.commentError)
        assertEquals(500, "😀".repeat(500).codePointCount(0, "😀".repeat(500).length))
    }

    @Test fun missingPostAndCommentLoadErrorRemainRetryable() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = DetailFake(postResults = mutableListOf(ApiResult.Failure("Post not found")), commentPages = mutableListOf(ApiResult.Failure("Comments failed")))
        val vm = CommunityDetailViewModel("missing", repo); advanceUntilIdle()
        assertEquals("Post not found", vm.state.value.error)
        assertEquals("Comments failed", vm.state.value.commentError)
        assertTrue(vm.state.value.post == null)
    }

    @Test fun commentRetryRequestsTheSameInitialAndPaginationPage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val initial = DetailFake(postResults = mutableListOf(ApiResult.Success(post())), commentPages = mutableListOf(
            ApiResult.Failure("first failed"), ApiResult.Success(CommunityCommentPage(emptyList(), PageMeta(1, 20, 0, false))),
        ))
        val firstVm = CommunityDetailViewModel("p1", initial); advanceUntilIdle()
        firstVm.retryComments(); advanceUntilIdle()
        assertEquals(listOf(1, 1), initial.commentPagesRequested)

        val paged = DetailFake(postResults = mutableListOf(ApiResult.Success(post())), commentPages = mutableListOf(
            ApiResult.Success(CommunityCommentPage(listOf(comment("1")), PageMeta(1, 20, 2, true))),
            ApiResult.Failure("page two failed"),
            ApiResult.Success(CommunityCommentPage(listOf(comment("2")), PageMeta(2, 20, 2, false))),
        ))
        val pageVm = CommunityDetailViewModel("p1", paged); advanceUntilIdle()
        pageVm.loadMore(); advanceUntilIdle(); pageVm.retryComments(); advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), paged.commentPagesRequested)
        assertEquals(listOf("1", "2"), pageVm.state.value.comments.map { it.id })
    }

    @Test fun repositoryLikeAndUnlikeUseRealHttpContractAndServerState() = runTest {
        val server = MockWebServer(); server.start()
        try {
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val api = Retrofit.Builder().baseUrl(server.url("/api/v1/")).addConverterFactory(MoshiConverterFactory.create(moshi)).build().create(CommunityHttpApi::class.java)
            val repository = RealCommunityRepository(api, moshi)
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"data\":{\"postId\":\"p1\",\"likeCount\":3,\"likedByCurrentUser\":true}}"))
            val liked = repository.like("p1") as ApiResult.Success
            assertEquals(3, liked.value.likeCount); assertTrue(liked.value.likedByCurrentUser)
            val likeRequest = server.takeRequest(); assertEquals("PUT", likeRequest.method); assertEquals("/api/v1/community/posts/p1/like", likeRequest.path)
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"data\":{\"postId\":\"p1\",\"likeCount\":2,\"likedByCurrentUser\":false}}"))
            val unliked = repository.unlike("p1") as ApiResult.Success
            assertEquals(2, unliked.value.likeCount); assertFalse(unliked.value.likedByCurrentUser)
            val unlikeRequest = server.takeRequest(); assertEquals("DELETE", unlikeRequest.method); assertEquals("/api/v1/community/posts/p1/like", unlikeRequest.path)
        } finally { server.shutdown() }
    }

    @Test fun feedAppliesThePostStateReturnedFromDetail() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val initial = post(liked = false)
        val repo = DetailFake(
            postResults = mutableListOf(ApiResult.Success(initial)),
            postPageResult = ApiResult.Success(CommunityPostPage(listOf(initial), PageMeta(1, 20, 1, false))),
        )
        val vm = CommunityViewModel(repo); advanceUntilIdle()
        vm.applyPostUpdate(initial.copy(likeCount = 3, commentCount = 4, likedByCurrentUser = true))
        assertEquals(3, vm.state.value.posts.single().likeCount)
        assertEquals(4, vm.state.value.posts.single().commentCount)
        assertTrue(vm.state.value.posts.single().likedByCurrentUser)
    }

    @Test fun singaporeMonthlySalaryUsesHireXDisplayFormat() {
        assertEquals("S$3,200–4,800 / month", formatSalary("SGD", 3200, 4800, "MONTH"))
    }

    private fun post(liked: Boolean = false) = CommunityPost("p1", CommunityAuthor("u", "Alex", null, "CANDIDATE", null), "body", 2, 3, liked, "2026-08-16T00:00:00Z", "2026-08-16T00:00:00Z")
    private fun comment(id: String, body: String = "body") = CommunityComment(id, "p1", CommunityAuthor("u", "Alex", null, "CANDIDATE", null), body, "2026-08-16T00:00:00Z", "2026-08-16T00:00:00Z")
}

private class DetailFake(
    private val postResults: MutableList<ApiResult<CommunityPost>>,
    private val postPageResult: ApiResult<CommunityPostPage> = ApiResult.Failure("unused"),
    private val commentPages: MutableList<ApiResult<CommunityCommentPage>> = mutableListOf(ApiResult.Success(CommunityCommentPage(emptyList(), PageMeta(1, 20, 0, false)))),
    private val likeResult: ApiResult<CommunityInteraction> = ApiResult.Success(CommunityInteraction("p1", 3, true)),
    private val createCommentResult: ApiResult<CommunityCommentCreated> = ApiResult.Success(CommunityCommentCreated(CommunityComment("c", "p1", CommunityAuthor("u", "A", null, "CANDIDATE", null), "b", "2026-08-16T00:00:00Z", "2026-08-16T00:00:00Z"), 1)),
) : CommunityRepository {
    var likeCalls = 0; var createCommentCalls = 0; var lastCommentBody = ""; val commentPagesRequested = mutableListOf<Int>()
    override suspend fun posts(page: Int, pageSize: Int) = postPageResult
    override suspend fun create(body: String) = ApiResult.Failure("unused")
    override suspend fun post(postId: String) = postResults.removeFirstOrNull() ?: ApiResult.Failure("missing")
    override suspend fun like(postId: String): ApiResult<CommunityInteraction> { likeCalls++; return likeResult }
    override suspend fun unlike(postId: String) = ApiResult.Success(CommunityInteraction(postId, 0, false))
    override suspend fun comments(postId: String, page: Int, pageSize: Int): ApiResult<CommunityCommentPage> { commentPagesRequested += page; return commentPages.removeFirstOrNull() ?: ApiResult.Failure("missing") }
    override suspend fun createComment(postId: String, body: String): ApiResult<CommunityCommentCreated> { createCommentCalls++; lastCommentBody = body; return createCommentResult }
}
