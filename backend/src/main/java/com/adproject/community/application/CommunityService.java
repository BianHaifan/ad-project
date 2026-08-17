package com.adproject.community.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.common.time.DatabaseTimePrecision;
import com.adproject.community.api.CommunityDtos.CommunityAuthor;
import com.adproject.community.api.CommunityDtos.CommunityComment;
import com.adproject.community.api.CommunityDtos.CommunityCommentListResponse;
import com.adproject.community.api.CommunityDtos.CommunityFeedResponse;
import com.adproject.community.api.CommunityDtos.CommunityInteraction;
import com.adproject.community.api.CommunityDtos.CommunityInteractionResponse;
import com.adproject.community.api.CommunityDtos.CommunityPost;
import com.adproject.community.api.CommunityDtos.CommunityPostResponse;
import com.adproject.community.api.CommunityDtos.CreateCommunityCommentRequest;
import com.adproject.community.api.CommunityDtos.CreateCommunityCommentResponse;
import com.adproject.community.api.CommunityDtos.CreateCommunityCommentResult;
import com.adproject.community.api.CommunityDtos.CreateCommunityPostRequest;
import com.adproject.community.api.CommunityDtos.PageMeta;
import com.adproject.community.infrastructure.CommunityCommentEntity;
import com.adproject.community.infrastructure.CommunityCommentRepository;
import com.adproject.community.infrastructure.CommunityPostEntity;
import com.adproject.community.infrastructure.CommunityPostLikeRepository;
import com.adproject.community.infrastructure.CommunityPostMetricsRepository;
import com.adproject.community.infrastructure.CommunityPostMetricsRepository.Metrics;
import com.adproject.community.infrastructure.CommunityPostRepository;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityService {
    private static final EnumSet<UserRole> ALLOWED_ROLES = EnumSet.of(UserRole.CANDIDATE, UserRole.RECRUITER);
    private static final Metrics ZERO_METRICS = new Metrics(0, 0, false);
    private final CommunityPostRepository postRepository;
    private final CommunityPostLikeRepository likeRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityPostMetricsRepository metricsRepository;
    private final UserRepository userRepository;
    private final CompanyMemberRepository memberRepository;
    private final CompanyRepository companyRepository;
    private final Clock clock;

    public CommunityService(CommunityPostRepository postRepository,
                            CommunityPostLikeRepository likeRepository,
                            CommunityCommentRepository commentRepository,
                            CommunityPostMetricsRepository metricsRepository,
                            UserRepository userRepository,
                            CompanyMemberRepository memberRepository,
                            CompanyRepository companyRepository,
                            Clock clock) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.metricsRepository = metricsRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.companyRepository = companyRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CommunityFeedResponse list(AuthenticatedUser currentUser, int page, int pageSize) {
        requireAllowedRole(currentUser);
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        var result = postRepository.findAll(PageRequest.of(page - 1, pageSize, sort));
        Map<String, UserEntity> authors = authors(result.getContent());
        Map<String, Metrics> metrics = metricsRepository.findForPosts(
                result.getContent().stream().map(CommunityPostEntity::getId).toList(), currentUser.userId());
        var data = result.getContent().stream()
                .map(post -> toPost(post, requireAuthor(authors, post.getAuthorId()),
                        metrics.getOrDefault(post.getId(), ZERO_METRICS)))
                .toList();
        return new CommunityFeedResponse(data,
                new PageMeta(page, pageSize, result.getTotalElements(), result.hasNext()));
    }

    @Transactional
    public CommunityPostResponse create(AuthenticatedUser currentUser, CreateCommunityPostRequest request) {
        requireAllowedRole(currentUser);
        String body = CommunityTextNormalizer.normalize(request == null ? null : request.body(), "body", 2000);
        UserEntity author = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User no longer exists"));
        Instant now = DatabaseTimePrecision.micros(clock.instant());
        CommunityPostEntity post = postRepository.save(new CommunityPostEntity(
                UUID.randomUUID().toString(), author.getId(), body, now, now));
        return new CommunityPostResponse(toPost(post, author, ZERO_METRICS));
    }

    @Transactional(readOnly = true)
    public CommunityPostResponse detail(AuthenticatedUser currentUser, String postId) {
        requireAllowedRole(currentUser);
        CommunityPostEntity post = requirePost(postId);
        UserEntity author = requireUser(post.getAuthorId());
        return new CommunityPostResponse(toPost(post, author, metrics(postId, currentUser.userId())));
    }

    @Transactional
    public CommunityInteractionResponse like(AuthenticatedUser currentUser, String postId) {
        requireAllowedRole(currentUser);
        requirePost(postId);
        requireCurrentUser(currentUser.userId());
        likeRepository.insertIfAbsent(postId, currentUser.userId(), DatabaseTimePrecision.micros(clock.instant()));
        return interaction(postId, currentUser.userId());
    }

    @Transactional
    public CommunityInteractionResponse unlike(AuthenticatedUser currentUser, String postId) {
        requireAllowedRole(currentUser);
        requirePost(postId);
        likeRepository.delete(postId, currentUser.userId());
        return interaction(postId, currentUser.userId());
    }

    @Transactional(readOnly = true)
    public CommunityCommentListResponse comments(AuthenticatedUser currentUser, String postId, int page, int pageSize) {
        requireAllowedRole(currentUser);
        requirePost(postId);
        Sort sort = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
        var result = commentRepository.findByPostId(postId, PageRequest.of(page - 1, pageSize, sort));
        Map<String, UserEntity> authors = authorsByIds(
                result.getContent().stream().map(CommunityCommentEntity::getAuthorId).toList());
        var data = result.getContent().stream()
                .map(comment -> toComment(comment, requireAuthor(authors, comment.getAuthorId())))
                .toList();
        return new CommunityCommentListResponse(data,
                new PageMeta(page, pageSize, result.getTotalElements(), result.hasNext()));
    }

    @Transactional
    public CreateCommunityCommentResponse comment(AuthenticatedUser currentUser, String postId,
                                                   CreateCommunityCommentRequest request) {
        requireAllowedRole(currentUser);
        requirePost(postId);
        String body = CommunityTextNormalizer.normalize(request == null ? null : request.body(), "body", 500);
        UserEntity author = requireCurrentUser(currentUser.userId());
        Instant now = DatabaseTimePrecision.micros(clock.instant());
        CommunityCommentEntity comment = commentRepository.save(new CommunityCommentEntity(
                UUID.randomUUID().toString(), postId, author.getId(), body, now, now));
        long commentCount = commentRepository.countByPostId(postId);
        return new CreateCommunityCommentResponse(
                new CreateCommunityCommentResult(toComment(comment, author), commentCount));
    }

    private Map<String, UserEntity> authors(Collection<CommunityPostEntity> posts) {
        return authorsByIds(posts.stream().map(CommunityPostEntity::getAuthorId).toList());
    }

    private Map<String, UserEntity> authorsByIds(Collection<String> authorIds) {
        return userRepository.findAllById(authorIds.stream().distinct().toList())
                .stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private CommunityPostEntity requirePost(String postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Community post not found"));
    }

    private UserEntity requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Community author is missing"));
    }

    private UserEntity requireCurrentUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User no longer exists"));
    }

    private Metrics metrics(String postId, String viewerId) {
        return metricsRepository.findForPosts(java.util.List.of(postId), viewerId)
                .getOrDefault(postId, ZERO_METRICS);
    }

    private CommunityInteractionResponse interaction(String postId, String viewerId) {
        long likeCount = likeRepository.count(postId);
        return new CommunityInteractionResponse(
                new CommunityInteraction(postId, likeCount, likeRepository.exists(postId, viewerId)));
    }

    private CommunityPost toPost(CommunityPostEntity post, UserEntity author, Metrics metrics) {
        return new CommunityPost(post.getId(), toAuthor(author), post.getBody(), metrics.likeCount(),
                metrics.commentCount(), metrics.likedByCurrentUser(), post.getCreatedAt(), post.getUpdatedAt());
    }

    private CommunityAuthor toAuthor(UserEntity user) {
        String companyName = null;
        if (user.getRole() == UserRole.RECRUITER) {
            companyName = memberRepository.findByUserId(user.getId())
                    .flatMap(member -> companyRepository.findById(member.getCompanyId()))
                    .map(company -> company.getName()).orElse(null);
        }
        return new CommunityAuthor(user.getId(), user.getFullName(), user.getAvatarUrl(),
                user.getRole().name(), companyName);
    }

    private CommunityComment toComment(CommunityCommentEntity comment, UserEntity author) {
        return new CommunityComment(comment.getId(), comment.getPostId(), toAuthor(author), comment.getBody(),
                comment.getCreatedAt(), comment.getUpdatedAt());
    }

    private static UserEntity requireAuthor(Map<String, UserEntity> authors, String authorId) {
        UserEntity author = authors.get(authorId);
        if (author == null) throw new IllegalStateException("Community post author is missing");
        return author;
    }

    private static void requireAllowedRole(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.role() == null || !ALLOWED_ROLES.contains(currentUser.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }
}
