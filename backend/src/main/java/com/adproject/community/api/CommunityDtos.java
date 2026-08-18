package com.adproject.community.api;

import java.time.Instant;
import java.util.List;
import com.adproject.community.domain.CommunityCategory;

public final class CommunityDtos {
    private CommunityDtos() {}

    public record CreateCommunityPostRequest(String body, CommunityCategory category) {
        public CreateCommunityPostRequest(String body) { this(body, CommunityCategory.GENERAL); }
    }
    public record CreateCommunityCommentRequest(String body) {}
    public record CommunityFeedResponse(List<CommunityPost> data, PageMeta meta) {}
    public record CommunityCommentListResponse(List<CommunityComment> data, PageMeta meta) {}
    public record CommunityPostResponse(CommunityPost data) {}
    public record CommunityInteractionResponse(CommunityInteraction data) {}
    public record CreateCommunityCommentResponse(CreateCommunityCommentResult data) {}
    public record PageMeta(int page, int pageSize, long total, boolean hasNext) {}
    public record CommunityAuthor(String userId, String fullName, String avatarUrl, String role,
                                  String companyName) {}
    public record CommunityImage(String imageId, String url, String contentType, long sizeBytes) {}
    public record CommunityPost(String id, CommunityAuthor author, String body, CommunityCategory category,
                                List<CommunityImage> images, long likeCount,
                                long commentCount, boolean likedByCurrentUser, Instant createdAt,
                                Instant updatedAt) {}
    public record CommunityComment(String id, String postId, CommunityAuthor author, String body,
                                   Instant createdAt, Instant updatedAt) {}
    public record CommunityInteraction(String postId, long likeCount, boolean likedByCurrentUser) {}
    public record CreateCommunityCommentResult(CommunityComment comment, long commentCount) {}
}
