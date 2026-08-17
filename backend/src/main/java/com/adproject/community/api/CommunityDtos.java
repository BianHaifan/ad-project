package com.adproject.community.api;

import java.time.Instant;
import java.util.List;

public final class CommunityDtos {
    private CommunityDtos() {}

    public record CreateCommunityPostRequest(String body) {}
    public record CreateCommunityCommentRequest(String body) {}
    public record CommunityFeedResponse(List<CommunityPost> data, PageMeta meta) {}
    public record CommunityCommentListResponse(List<CommunityComment> data, PageMeta meta) {}
    public record CommunityPostResponse(CommunityPost data) {}
    public record CommunityInteractionResponse(CommunityInteraction data) {}
    public record CreateCommunityCommentResponse(CreateCommunityCommentResult data) {}
    public record PageMeta(int page, int pageSize, long total, boolean hasNext) {}
    public record CommunityAuthor(String userId, String fullName, String avatarUrl, String role,
                                  String companyName) {}
    public record CommunityPost(String id, CommunityAuthor author, String body, long likeCount,
                                long commentCount, boolean likedByCurrentUser, Instant createdAt,
                                Instant updatedAt) {}
    public record CommunityComment(String id, String postId, CommunityAuthor author, String body,
                                   Instant createdAt, Instant updatedAt) {}
    public record CommunityInteraction(String postId, long likeCount, boolean likedByCurrentUser) {}
    public record CreateCommunityCommentResult(CommunityComment comment, long commentCount) {}
}
