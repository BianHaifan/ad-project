package com.adproject.community.infrastructure;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CommunityPostMetricsRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CommunityPostMetricsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Metrics> findForPosts(Collection<String> postIds, String viewerId) {
        if (postIds.isEmpty()) return Map.of();
        Map<String, Long> likes = counts("community_post_likes", postIds);
        Map<String, Long> comments = counts("community_comments", postIds);
        Set<String> liked = Set.copyOf(jdbcTemplate.queryForList(
                "select post_id from community_post_likes where user_id = :viewerId and post_id in (:postIds)",
                new MapSqlParameterSource("viewerId", viewerId).addValue("postIds", postIds), String.class));
        Map<String, Metrics> result = new HashMap<>();
        for (String postId : postIds) {
            result.put(postId, new Metrics(likes.getOrDefault(postId, 0L),
                    comments.getOrDefault(postId, 0L), liked.contains(postId)));
        }
        return Map.copyOf(result);
    }

    private Map<String, Long> counts(String table, Collection<String> postIds) {
        Map<String, Long> result = new HashMap<>();
        jdbcTemplate.queryForList("select post_id, count(*) as item_count from " + table
                        + " where post_id in (:postIds) group by post_id",
                new MapSqlParameterSource("postIds", postIds)).forEach(row ->
                        result.put((String) row.get("post_id"), ((Number) row.get("item_count")).longValue()));
        return result;
    }

    public record Metrics(long likeCount, long commentCount, boolean likedByCurrentUser) {}
}
