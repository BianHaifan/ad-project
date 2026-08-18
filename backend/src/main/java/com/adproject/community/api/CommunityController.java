package com.adproject.community.api;

import com.adproject.common.security.AuthenticatedUser;
import com.adproject.community.api.CommunityDtos.CommunityFeedResponse;
import com.adproject.community.api.CommunityDtos.CommunityCommentListResponse;
import com.adproject.community.api.CommunityDtos.CommunityInteractionResponse;
import com.adproject.community.api.CommunityDtos.CommunityPostResponse;
import com.adproject.community.api.CommunityDtos.CreateCommunityCommentRequest;
import com.adproject.community.api.CommunityDtos.CreateCommunityCommentResponse;
import com.adproject.community.api.CommunityDtos.CreateCommunityPostRequest;
import com.adproject.community.application.CommunityService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.adproject.community.domain.CommunityCategory;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/community/posts")
public class CommunityController {
    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping
    CommunityFeedResponse list(@AuthenticationPrincipal AuthenticatedUser currentUser,
                               @RequestParam(required = false) String q,
                               @RequestParam(required = false) CommunityCategory category,
                               @RequestParam(defaultValue = "1") @Min(1) int page,
                               @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return communityService.list(currentUser, q, category, page, pageSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CommunityPostResponse create(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                 @RequestBody CreateCommunityPostRequest request) {
        return communityService.create(currentUser, request);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CommunityPostResponse createWithImages(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                           @RequestPart String body,
                                           @RequestPart String category,
                                           @RequestPart(required = false) List<MultipartFile> images) {
        CommunityCategory parsedCategory;
        try {
            parsedCategory = CommunityCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new com.adproject.common.api.ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR",
                    "Request validation failed",
                    java.util.Map.of("category", "Unsupported community category"));
        }
        return communityService.create(currentUser, new CreateCommunityPostRequest(body, parsedCategory), images);
    }

    @GetMapping("/{postId}/images/{imageId}")
    ResponseEntity<byte[]> image(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                 @PathVariable String postId, @PathVariable String imageId) {
        var image = communityService.image(currentUser, postId, imageId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.getContentType()))
                .contentLength(image.getSizeBytes()).body(image.getContent());
    }

    @GetMapping("/{postId}")
    CommunityPostResponse detail(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                 @PathVariable String postId) {
        return communityService.detail(currentUser, postId);
    }

    @PutMapping("/{postId}/like")
    CommunityInteractionResponse like(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                      @PathVariable String postId) {
        return communityService.like(currentUser, postId);
    }

    @DeleteMapping("/{postId}/like")
    CommunityInteractionResponse unlike(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @PathVariable String postId) {
        return communityService.unlike(currentUser, postId);
    }

    @GetMapping("/{postId}/comments")
    CommunityCommentListResponse comments(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                          @PathVariable String postId,
                                          @RequestParam(defaultValue = "1") @Min(1) int page,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return communityService.comments(currentUser, postId, page, pageSize);
    }

    @PostMapping("/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CreateCommunityCommentResponse comment(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                           @PathVariable String postId,
                                           @RequestBody CreateCommunityCommentRequest request) {
        return communityService.comment(currentUser, postId, request);
    }
}
