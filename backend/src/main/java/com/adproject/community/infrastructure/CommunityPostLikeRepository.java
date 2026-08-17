package com.adproject.community.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CommunityPostLikeRepository {
    private final JdbcTemplate jdbcTemplate;

    public CommunityPostLikeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean insertIfAbsent(String postId, String userId, Instant createdAt) {
        try {
            jdbcTemplate.update("insert into community_post_likes (post_id,user_id,created_at) values (?,?,?)",
                    postId, userId, Timestamp.from(createdAt));
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    public void delete(String postId, String userId) {
        jdbcTemplate.update("delete from community_post_likes where post_id = ? and user_id = ?", postId, userId);
    }

    public long count(String postId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from community_post_likes where post_id = ?", Long.class, postId);
        return count == null ? 0 : count;
    }

    public boolean exists(String postId, String userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from community_post_likes where post_id = ? and user_id = ?",
                Integer.class, postId, userId);
        return count != null && count > 0;
    }
}
