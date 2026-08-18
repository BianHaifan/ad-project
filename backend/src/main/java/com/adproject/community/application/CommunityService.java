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
import com.adproject.community.infrastructure.CommunityPostImageEntity;
import com.adproject.community.infrastructure.CommunityPostImageRepository;
import com.adproject.community.domain.CommunityCategory;
import com.adproject.community.api.CommunityDtos.CommunityImage;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
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
    private final CommunityPostImageRepository imageRepository;

    @Autowired
    public CommunityService(CommunityPostRepository postRepository,
                            CommunityPostLikeRepository likeRepository,
                            CommunityCommentRepository commentRepository,
                            CommunityPostMetricsRepository metricsRepository,
                            UserRepository userRepository,
                            CompanyMemberRepository memberRepository,
                            CompanyRepository companyRepository,
                            Clock clock, CommunityPostImageRepository imageRepository) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.metricsRepository = metricsRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.companyRepository = companyRepository;
        this.clock = clock;
        this.imageRepository = imageRepository;
    }

    public CommunityService(CommunityPostRepository postRepository, CommunityPostLikeRepository likeRepository,
                     CommunityCommentRepository commentRepository, CommunityPostMetricsRepository metricsRepository,
                     UserRepository userRepository, CompanyMemberRepository memberRepository,
                     CompanyRepository companyRepository, Clock clock) {
        this(postRepository, likeRepository, commentRepository, metricsRepository, userRepository, memberRepository,
                companyRepository, clock, null);
    }

    public CommunityFeedResponse list(AuthenticatedUser currentUser, int page, int pageSize) {
        return list(currentUser, null, null, page, pageSize);
    }

    @Transactional(readOnly = true)
    public CommunityFeedResponse list(AuthenticatedUser currentUser, String q, CommunityCategory category, int page, int pageSize) {
        requireAllowedRole(currentUser);
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Specification<CommunityPostEntity> specification = Specification.where(null);
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> cb.like(cb.lower(root.get("body")), pattern));
        }
        if (category != null) specification = specification.and((root, query, cb) -> cb.equal(root.get("category"), category));
        var result = postRepository.findAll(specification, PageRequest.of(page - 1, pageSize, sort));
        Map<String, UserEntity> authors = authors(result.getContent());
        Map<String, Metrics> metrics = metricsRepository.findForPosts(
                result.getContent().stream().map(CommunityPostEntity::getId).toList(), currentUser.userId());
        Map<String,List<CommunityImage>> images = images(result.getContent().stream().map(CommunityPostEntity::getId).toList());
        var data = result.getContent().stream()
                .map(post -> toPost(post, requireAuthor(authors, post.getAuthorId()),
                        metrics.getOrDefault(post.getId(), ZERO_METRICS), images.getOrDefault(post.getId(), List.of())))
                .toList();
        return new CommunityFeedResponse(data,
                new PageMeta(page, pageSize, result.getTotalElements(), result.hasNext()));
    }

    @Transactional
    public CommunityPostResponse create(AuthenticatedUser currentUser, CreateCommunityPostRequest request) {
        return create(currentUser, request, List.of());
    }

    @Transactional
    public CommunityPostResponse create(AuthenticatedUser currentUser, CreateCommunityPostRequest request, List<MultipartFile> files) {
        requireAllowedRole(currentUser);
        String body = CommunityTextNormalizer.normalize(request == null ? null : request.body(), "body", 2000);
        UserEntity author = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User no longer exists"));
        Instant now = DatabaseTimePrecision.micros(clock.instant());
        CommunityCategory category = request.category() == null ? CommunityCategory.GENERAL : request.category();
        CommunityPostEntity post = postRepository.save(new CommunityPostEntity(
                UUID.randomUUID().toString(), author.getId(), body, category, now, now));
        List<CommunityPostImageEntity> stored = storeImages(post.getId(), files, now);
        return new CommunityPostResponse(toPost(post, author, ZERO_METRICS, stored.stream().map(this::toImage).toList()));
    }

    @Transactional(readOnly = true)
    public CommunityPostResponse detail(AuthenticatedUser currentUser, String postId) {
        requireAllowedRole(currentUser);
        CommunityPostEntity post = requirePost(postId);
        UserEntity author = requireUser(post.getAuthorId());
        return new CommunityPostResponse(toPost(post, author, metrics(postId, currentUser.userId()),
                imageRepository.findByPostIdOrderByPositionAsc(postId).stream().map(this::toImage).toList()));
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

    public CommunityPostImageEntity image(AuthenticatedUser currentUser, String postId, String imageId) {
        requirePost(postId);
        return imageRepository.findById(imageId).filter(value -> value.getPostId().equals(postId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Community image not found"));
    }

    private CommunityPost toPost(CommunityPostEntity post, UserEntity author, Metrics metrics, List<CommunityImage> images) {
        return new CommunityPost(post.getId(), toAuthor(author), post.getBody(), post.getCategory(), images, metrics.likeCount(),
                metrics.commentCount(), metrics.likedByCurrentUser(), post.getCreatedAt(), post.getUpdatedAt());
    }

    private List<CommunityPostImageEntity> storeImages(String postId, List<MultipartFile> files, Instant now) {
        List<MultipartFile> safe = files == null ? List.of() : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (safe.size() > 4) throw validation("images", "must contain at most 4 images");
        java.util.ArrayList<CommunityPostImageEntity> stored = new java.util.ArrayList<>();
        for (int index = 0; index < safe.size(); index++) {
            byte[] bytes;
            try { bytes = safe.get(index).getBytes(); }
            catch (IOException exception) { throw validation("images", "contains an unreadable image"); }
            if (bytes.length > 5 * 1024 * 1024) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Each image must be at most 5 MB");
            String type = detectImageType(bytes);
            var image = new CommunityPostImageEntity(UUID.randomUUID().toString(), postId, index, type, bytes, now);
            imageRepository.save(image); stored.add(image);
        }
        return List.copyOf(stored);
    }

    private static String detectImageType(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0]==(byte)0x89 && bytes[1]==0x50 && bytes[2]==0x4e && bytes[3]==0x47) return "image/png";
        if (bytes.length >= 3 && bytes[0]==(byte)0xff && bytes[1]==(byte)0xd8 && bytes[2]==(byte)0xff) return "image/jpeg";
        if (bytes.length >= 12 && bytes[0]=='R' && bytes[1]=='I' && bytes[2]=='F' && bytes[3]=='F'
                && bytes[8]=='W' && bytes[9]=='E' && bytes[10]=='B' && bytes[11]=='P') return "image/webp";
        throw validation("images", "must contain only PNG, JPEG, or WebP images");
    }

    private Map<String,List<CommunityImage>> images(List<String> postIds) {
        if (postIds.isEmpty()) return Map.of();
        return imageRepository.findByPostIdInOrderByPostIdAscPositionAsc(postIds).stream()
                .collect(Collectors.groupingBy(CommunityPostImageEntity::getPostId, Collectors.mapping(this::toImage, Collectors.toList())));
    }

    private CommunityImage toImage(CommunityPostImageEntity image) {
        return new CommunityImage(image.getId(), "/api/v1/community/posts/" + image.getPostId() + "/images/" + image.getId(),
                image.getContentType(), image.getSizeBytes());
    }

    private static ApiException validation(String field, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed", Map.of(field, detail));
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
