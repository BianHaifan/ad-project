package com.adproject.candidate.feature.community

import com.adproject.candidate.data.api.ApiErrorParser
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.contract.DataEnvelope
import com.adproject.candidate.data.contract.ListEnvelope
import com.adproject.candidate.data.contract.PageMeta
import com.squareup.moshi.Moshi
import java.io.IOException
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.PUT

data class CommunityAuthorDto(val userId: String, val fullName: String, val avatarUrl: String?, val role: String, val companyName: String?)
data class CommunityPostDto(
    val id: String, val author: CommunityAuthorDto, val body: String, val likeCount: Int,
    val commentCount: Int, val likedByCurrentUser: Boolean, val createdAt: String, val updatedAt: String,
)
data class CreateCommunityPostRequest(val body: String)
data class CommunityCommentDto(
    val id: String, val postId: String, val author: CommunityAuthorDto, val body: String,
    val createdAt: String, val updatedAt: String,
)
data class CommunityInteractionDto(val postId: String, val likeCount: Int, val likedByCurrentUser: Boolean)
data class CreateCommunityCommentRequest(val body: String)
data class CreateCommunityCommentResultDto(val comment: CommunityCommentDto, val commentCount: Int)

interface CommunityHttpApi {
    @GET("community/posts")
    suspend fun posts(@Query("page") page: Int, @Query("pageSize") pageSize: Int = 20): Response<ListEnvelope<CommunityPostDto, PageMeta>>

    @POST("community/posts")
    suspend fun create(@Body request: CreateCommunityPostRequest): Response<DataEnvelope<CommunityPostDto>>

    @GET("community/posts/{postId}")
    suspend fun post(@retrofit2.http.Path("postId") postId: String): Response<DataEnvelope<CommunityPostDto>>

    @PUT("community/posts/{postId}/like")
    suspend fun like(@retrofit2.http.Path("postId") postId: String): Response<DataEnvelope<CommunityInteractionDto>>

    @DELETE("community/posts/{postId}/like")
    suspend fun unlike(@retrofit2.http.Path("postId") postId: String): Response<DataEnvelope<CommunityInteractionDto>>

    @GET("community/posts/{postId}/comments")
    suspend fun comments(@retrofit2.http.Path("postId") postId: String, @Query("page") page: Int, @Query("pageSize") pageSize: Int = 20): Response<ListEnvelope<CommunityCommentDto, PageMeta>>

    @POST("community/posts/{postId}/comments")
    suspend fun createComment(@retrofit2.http.Path("postId") postId: String, @Body request: CreateCommunityCommentRequest): Response<DataEnvelope<CreateCommunityCommentResultDto>>
}

data class CommunityAuthor(val userId: String, val fullName: String, val avatarUrl: String?, val role: String, val companyName: String?)
data class CommunityPost(
    val id: String, val author: CommunityAuthor, val body: String, val likeCount: Int,
    val commentCount: Int, val likedByCurrentUser: Boolean, val createdAt: String, val updatedAt: String,
)
data class CommunityPostPage(val posts: List<CommunityPost>, val meta: PageMeta)
data class CommunityComment(val id: String, val postId: String, val author: CommunityAuthor, val body: String, val createdAt: String, val updatedAt: String)
data class CommunityCommentPage(val comments: List<CommunityComment>, val meta: PageMeta)
data class CommunityCommentCreated(val comment: CommunityComment, val commentCount: Int)
data class CommunityInteraction(val postId: String, val likeCount: Int, val likedByCurrentUser: Boolean)

interface CommunityRepository {
    suspend fun posts(page: Int, pageSize: Int = 20): ApiResult<CommunityPostPage>
    suspend fun create(body: String): ApiResult<CommunityPost>
    suspend fun post(postId: String): ApiResult<CommunityPost>
    suspend fun like(postId: String): ApiResult<CommunityInteraction>
    suspend fun unlike(postId: String): ApiResult<CommunityInteraction>
    suspend fun comments(postId: String, page: Int, pageSize: Int = 20): ApiResult<CommunityCommentPage>
    suspend fun createComment(postId: String, body: String): ApiResult<CommunityCommentCreated>
}

class RealCommunityRepository(private val api: CommunityHttpApi, moshi: Moshi) : CommunityRepository {
    private val errors = ApiErrorParser(moshi)

    override suspend fun posts(page: Int, pageSize: Int): ApiResult<CommunityPostPage> = try {
        val response = api.posts(page, pageSize)
        val envelope = response.body()
        if (response.isSuccessful && envelope != null) {
            ApiResult.Success(CommunityPostPage(envelope.data.map(::toPost), envelope.meta))
        } else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load Community. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load Community right now.")
    }

    override suspend fun create(body: String): ApiResult<CommunityPost> = try {
        val response = api.create(CreateCommunityPostRequest(body))
        val post = response.body()?.data
        if (response.isSuccessful && post != null) ApiResult.Success(toPost(post))
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to publish. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to publish right now.")
    }

    override suspend fun post(postId: String): ApiResult<CommunityPost> = request(
        "Unable to load this post.", { api.post(postId) }, ::toPost,
    )
    override suspend fun like(postId: String): ApiResult<CommunityInteraction> = request(
        "Unable to update Like.", { api.like(postId) }, { CommunityInteraction(it.postId, it.likeCount, it.likedByCurrentUser) },
    )
    override suspend fun unlike(postId: String): ApiResult<CommunityInteraction> = request(
        "Unable to update Like.", { api.unlike(postId) }, { CommunityInteraction(it.postId, it.likeCount, it.likedByCurrentUser) },
    )

    override suspend fun comments(postId: String, page: Int, pageSize: Int): ApiResult<CommunityCommentPage> = try {
        val response = api.comments(postId, page, pageSize)
        val envelope = response.body()
        if (response.isSuccessful && envelope != null) ApiResult.Success(CommunityCommentPage(envelope.data.map(::toComment), envelope.meta))
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) { ApiResult.Failure("Unable to load comments. Check your network and try again.")
    } catch (_: Exception) { ApiResult.Failure("Unable to load comments right now.") }

    override suspend fun createComment(postId: String, body: String): ApiResult<CommunityCommentCreated> = try {
        val response = api.createComment(postId, CreateCommunityCommentRequest(body))
        val result = response.body()?.data
        if (response.isSuccessful && result != null) ApiResult.Success(CommunityCommentCreated(toComment(result.comment), result.commentCount))
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) { ApiResult.Failure("Unable to publish comment. Check your network and try again.")
    } catch (_: Exception) { ApiResult.Failure("Unable to publish comment right now.") }

    private suspend fun <T, R> request(message: String, call: suspend () -> Response<DataEnvelope<T>>, map: (T) -> R): ApiResult<R> = try {
        val response = call(); val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(map(data)) else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) { ApiResult.Failure("$message Check your network and try again.")
    } catch (_: Exception) { ApiResult.Failure("$message Try again.") }

    private fun toPost(post: CommunityPostDto) = CommunityPost(
        post.id,
        CommunityAuthor(post.author.userId, post.author.fullName, post.author.avatarUrl, post.author.role, post.author.companyName),
        post.body, post.likeCount, post.commentCount, post.likedByCurrentUser, post.createdAt, post.updatedAt,
    )
    private fun toComment(comment: CommunityCommentDto) = CommunityComment(
        comment.id, comment.postId,
        CommunityAuthor(comment.author.userId, comment.author.fullName, comment.author.avatarUrl, comment.author.role, comment.author.companyName),
        comment.body, comment.createdAt, comment.updatedAt,
    )
}
