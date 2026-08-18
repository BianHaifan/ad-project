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
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.adproject.candidate.BuildConfig

data class CommunityAuthorDto(val userId: String, val fullName: String, val avatarUrl: String?, val role: String, val companyName: String?)
data class CommunityPostDto(
    val id: String, val author: CommunityAuthorDto, val body: String, val likeCount: Int,
    val commentCount: Int, val likedByCurrentUser: Boolean, val createdAt: String, val updatedAt: String,
    val category: CommunityCategory = CommunityCategory.GENERAL, val images: List<CommunityImageDto> = emptyList(),
)
enum class CommunityCategory { JOB_SEEKING, RECRUITING, TECH_DISCUSSION, HELP, GENERAL }
data class CommunityImageDto(val imageId: String, val url: String, val contentType: String, val sizeBytes: Long)
data class CommunityImage(val imageId: String, val url: String, val contentType: String, val sizeBytes: Long)
data class CommunityImageUpload(val bytes: ByteArray, val contentType: String)
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
    suspend fun posts(@Query("page") page: Int, @Query("pageSize") pageSize: Int = 20,
                      @Query("q") q: String? = null, @Query("category") category: CommunityCategory? = null): Response<ListEnvelope<CommunityPostDto, PageMeta>>

    @POST("community/posts")
    suspend fun create(@Body request: CreateCommunityPostRequest): Response<DataEnvelope<CommunityPostDto>>

    @Multipart @POST("community/posts")
    suspend fun createWithImages(@Part("body") body: RequestBody, @Part("category") category: RequestBody,
                                 @Part images: List<MultipartBody.Part>): Response<DataEnvelope<CommunityPostDto>>

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

    @POST("community/posts/{postId}/direct-conversation")
    suspend fun startDirect(@retrofit2.http.Path("postId") postId: String): Response<DataEnvelope<CommunityDirectConversationDto>>
    @GET("community/direct-conversations")
    suspend fun directConversations(@Query("page") page: Int = 1, @Query("pageSize") pageSize: Int = 100): Response<ListEnvelope<CommunityDirectConversationDto, PageMeta>>
    @GET("community/direct-conversations/{id}")
    suspend fun direct(@retrofit2.http.Path("id") id: String): Response<DataEnvelope<CommunityDirectConversationDto>>
    @GET("community/direct-conversations/{id}/messages")
    suspend fun directMessages(@retrofit2.http.Path("id") id: String, @Query("page") page: Int = 1,
                               @Query("pageSize") pageSize: Int = 100): Response<ListEnvelope<CommunityDirectMessageDto, PageMeta>>
    @POST("community/direct-conversations/{id}/messages")
    suspend fun sendDirect(@retrofit2.http.Path("id") id: String, @Body request: CreateCommunityCommentRequest): Response<DataEnvelope<CommunityDirectMessageDto>>
}
data class CommunityDirectConversationDto(val conversationId: String, val participant: CommunityAuthorDto, val createdAt: String, val updatedAt: String)
data class CommunityDirectMessageDto(val messageId: String, val conversationId: String, val senderId: String, val body: String, val sentAt: String)
data class CommunityDirectConversation(val conversationId: String, val participant: CommunityAuthor, val createdAt: String, val updatedAt: String)
data class CommunityDirectMessage(val messageId: String, val conversationId: String, val senderId: String, val body: String, val sentAt: String)

data class CommunityAuthor(val userId: String, val fullName: String, val avatarUrl: String?, val role: String, val companyName: String?)
data class CommunityPost(
    val id: String, val author: CommunityAuthor, val body: String, val likeCount: Int,
    val commentCount: Int, val likedByCurrentUser: Boolean, val createdAt: String, val updatedAt: String,
    val category: CommunityCategory = CommunityCategory.GENERAL, val images: List<CommunityImage> = emptyList(),
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
    suspend fun search(page: Int, pageSize: Int = 20, q: String? = null, category: CommunityCategory? = null): ApiResult<CommunityPostPage> = posts(page, pageSize)
    suspend fun create(body: String, category: CommunityCategory, images: List<CommunityImageUpload>): ApiResult<CommunityPost> = create(body)
    suspend fun startDirect(postId: String): ApiResult<CommunityDirectConversation> = ApiResult.Failure("Community messaging is unavailable.")
    suspend fun directConversations(): ApiResult<List<CommunityDirectConversation>> = ApiResult.Success(emptyList())
    suspend fun direct(id: String): ApiResult<CommunityDirectConversation> = ApiResult.Failure("Community messaging is unavailable.")
    suspend fun directMessages(id: String): ApiResult<List<CommunityDirectMessage>> = ApiResult.Failure("Community messaging is unavailable.")
    suspend fun sendDirect(id: String, body: String): ApiResult<CommunityDirectMessage> = ApiResult.Failure("Community messaging is unavailable.")
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

    override suspend fun search(page: Int, pageSize: Int, q: String?, category: CommunityCategory?): ApiResult<CommunityPostPage> = try {
        val response = api.posts(page, pageSize, q, category); val envelope = response.body()
        if (response.isSuccessful && envelope != null) ApiResult.Success(CommunityPostPage(envelope.data.map(::toPost), envelope.meta))
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) { ApiResult.Failure("Unable to load Community. Check your network and try again.")
    } catch (_: Exception) { ApiResult.Failure("Unable to load Community right now.") }

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

    override suspend fun create(body: String, category: CommunityCategory, images: List<CommunityImageUpload>): ApiResult<CommunityPost> = try {
        val text = body.toRequestBody("text/plain".toMediaType())
        val categoryBody = category.name.toRequestBody("text/plain".toMediaType())
        val parts = images.mapIndexed { index, image -> MultipartBody.Part.createFormData("images", "image-$index",
            image.bytes.toRequestBody(image.contentType.toMediaType())) }
        val response = api.createWithImages(text, categoryBody, parts); val post = response.body()?.data
        if (response.isSuccessful && post != null) ApiResult.Success(toPost(post)) else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) { ApiResult.Failure("Unable to publish. Check your network and try again.")
    } catch (_: Exception) { ApiResult.Failure("Unable to publish right now.") }

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

    override suspend fun startDirect(postId: String) = request("Unable to message this author.", { api.startDirect(postId) }, ::toDirect)
    override suspend fun directConversations(): ApiResult<List<CommunityDirectConversation>> = try {
        val response = api.directConversations(); val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(body.data.map(::toDirect))
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) { ApiResult.Failure("Unable to load Community conversations. Check your network and try again.")
    } catch (_: Exception) { ApiResult.Failure("Unable to load Community conversations right now.") }
    override suspend fun direct(id: String) = request("Unable to load this conversation.", { api.direct(id) }, ::toDirect)
    override suspend fun directMessages(id: String): ApiResult<List<CommunityDirectMessage>> = try { val response=api.directMessages(id);val body=response.body();if(response.isSuccessful&&body!=null)ApiResult.Success(body.data.map(::toDirectMessage))else errors.failure(response.code(),response.errorBody()?.string()) } catch (_:Exception){ApiResult.Failure("Unable to load messages.")}
    override suspend fun sendDirect(id: String, body: String) = request("Unable to send this message.", { api.sendDirect(id, CreateCommunityCommentRequest(body)) }, ::toDirectMessage)

    private suspend fun <T, R> request(message: String, call: suspend () -> Response<DataEnvelope<T>>, map: (T) -> R): ApiResult<R> = try {
        val response = call(); val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(map(data)) else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) { ApiResult.Failure("$message Check your network and try again.")
    } catch (_: Exception) { ApiResult.Failure("$message Try again.") }

    private fun toPost(post: CommunityPostDto) = CommunityPost(
        post.id,
        CommunityAuthor(post.author.userId, post.author.fullName, post.author.avatarUrl, post.author.role, post.author.companyName),
        post.body, post.likeCount, post.commentCount, post.likedByCurrentUser, post.createdAt, post.updatedAt,
        post.category, post.images.map { CommunityImage(it.imageId, absoluteMediaUrl(it.url), it.contentType, it.sizeBytes) },
    )
    private fun toComment(comment: CommunityCommentDto) = CommunityComment(
        comment.id, comment.postId,
        CommunityAuthor(comment.author.userId, comment.author.fullName, comment.author.avatarUrl, comment.author.role, comment.author.companyName),
        comment.body, comment.createdAt, comment.updatedAt,
    )
    private fun toDirect(value: CommunityDirectConversationDto)=CommunityDirectConversation(value.conversationId,CommunityAuthor(value.participant.userId,value.participant.fullName,value.participant.avatarUrl,value.participant.role,value.participant.companyName),value.createdAt,value.updatedAt)
    private fun toDirectMessage(value: CommunityDirectMessageDto)=CommunityDirectMessage(value.messageId,value.conversationId,value.senderId,value.body,value.sentAt)
    private fun absoluteMediaUrl(url:String)=if(url.startsWith("/")) BuildConfig.API_BASE_URL.substringBefore("/api/v1")+url else url
}
