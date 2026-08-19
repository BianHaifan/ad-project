package com.adproject.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CommunityMySqlFlywayIntegrationTest {
    private static final String EMOJI = "\uD83D\uDE00";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("adproject_community_test")
            .withUsername("adproject")
            .withPassword("test-only-password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.auth.jwt-secret", () -> "test-only-secret-at-least-thirty-two-bytes-long");
    }

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void v26MigratesEmptyMySqlAndMatchesTheCompleteCommunitySchema() {
        // V24 adds categorized image posts, V25 adds isolated Community direct messages,
        // and V26 removes the obsolete company verification state. V27-V31 add the agent schema.
        assertThat(jdbcTemplate.queryForObject(
                "select version from flyway_schema_history where success = 1 order by installed_rank desc limit 1",
                String.class)).isEqualTo("31");
        assertThat(tableNames()).containsExactlyInAnyOrder(
                "community_posts", "community_post_likes", "community_comments", "community_post_images",
                "community_direct_conversations", "community_direct_messages");

        assertColumns("community_posts", List.of(
                column("id", "char", "char(36)", "NO", null, "utf8mb4"),
                column("author_id", "char", "char(36)", "NO", null, "utf8mb4"),
                column("body", "text", "text", "NO", null, "utf8mb4"),
                column("created_at", "datetime", "datetime(6)", "NO", 6, null),
                column("updated_at", "datetime", "datetime(6)", "NO", 6, null),
                column("category", "varchar", "varchar(32)", "NO", null, "utf8mb4")));
        assertColumns("community_post_likes", List.of(
                column("post_id", "char", "char(36)", "NO", null, "utf8mb4"),
                column("user_id", "char", "char(36)", "NO", null, "utf8mb4"),
                column("created_at", "datetime", "datetime(6)", "NO", 6, null)));
        assertColumns("community_comments", List.of(
                column("id", "char", "char(36)", "NO", null, "utf8mb4"),
                column("post_id", "char", "char(36)", "NO", null, "utf8mb4"),
                column("author_id", "char", "char(36)", "NO", null, "utf8mb4"),
                column("body", "text", "text", "NO", null, "utf8mb4"),
                column("created_at", "datetime", "datetime(6)", "NO", 6, null),
                column("updated_at", "datetime", "datetime(6)", "NO", 6, null)));

        assertThat(indexColumns("community_posts", "idx_community_posts_created_id"))
                .containsExactly("created_at", "id");
        assertThat(indexColumns("community_comments", "idx_community_comments_post_created_id"))
                .containsExactly("post_id", "created_at", "id");
        assertThat(indexColumns("community_post_likes", "PRIMARY"))
                .containsExactly("post_id", "user_id");
        assertThat(indexColumns("community_post_images", "idx_community_post_images_post"))
                .containsExactly("post_id", "position_index");
        assertThat(indexColumns("community_direct_messages", "idx_community_direct_messages_page"))
                .containsExactly("conversation_id", "sent_at", "id");

        List<ForeignKeyMetadata> foreignKeys = foreignKeys();
        assertThat(foreignKeys).extracting(ForeignKeyMetadata::mapping).containsExactlyInAnyOrder(
                "community_posts.author_id->users.id",
                "community_post_likes.post_id->community_posts.id",
                "community_post_likes.user_id->users.id",
                "community_comments.post_id->community_posts.id",
                "community_comments.author_id->users.id");
        assertThat(foreignKeys).allMatch(foreignKey -> !"CASCADE".equals(foreignKey.deleteRule()));
        assertThat(checkConstraints()).containsExactlyInAnyOrder(
                "chk_community_posts_body", "chk_community_comments_body");
    }

    @Test
    void v14EnforcesUnicodeBodyLimitsForeignKeysAndUniqueLikes() {
        String authorId = UUID.randomUUID().toString();
        String secondUserId = UUID.randomUUID().toString();
        insertUser(authorId);
        insertUser(secondUserId);
        Timestamp now = Timestamp.from(Instant.parse("2026-08-16T00:00:00Z"));

        String postId = UUID.randomUUID().toString();
        String twoThousandEmoji = EMOJI.repeat(2000);
        jdbcTemplate.update("insert into community_posts (id,author_id,body,created_at,updated_at) values (?,?,?,?,?)",
                postId, authorId, twoThousandEmoji, now, now);
        assertThat(jdbcTemplate.queryForObject(
                "select char_length(body) from community_posts where id = ?", Integer.class, postId)).isEqualTo(2000);
        assertThat(jdbcTemplate.queryForObject(
                "select body from community_posts where id = ?", String.class, postId)).isEqualTo(twoThousandEmoji);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into community_posts (id,author_id,body,created_at,updated_at) values (?,?,?,?,?)",
                UUID.randomUUID().toString(), authorId, EMOJI.repeat(2001), now, now))
                .isInstanceOf(DataAccessException.class);

        jdbcTemplate.update("insert into community_comments (id,post_id,author_id,body,created_at,updated_at) "
                        + "values (?,?,?,?,?,?)", UUID.randomUUID().toString(), postId, secondUserId,
                EMOJI.repeat(500), now, now);
        assertThat(jdbcTemplate.queryForObject(
                "select char_length(body) from community_comments where post_id = ?", Integer.class, postId))
                .isEqualTo(500);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into community_comments (id,post_id,author_id,body,created_at,updated_at) values (?,?,?,?,?,?)",
                UUID.randomUUID().toString(), postId, secondUserId, EMOJI.repeat(501), now, now))
                .isInstanceOf(DataAccessException.class);

        jdbcTemplate.update("insert into community_post_likes (post_id,user_id,created_at) values (?,?,?)",
                postId, secondUserId, now);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into community_post_likes (post_id,user_id,created_at) values (?,?,?)",
                postId, secondUserId, now)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("delete from users where id = ?", authorId))
                .isInstanceOf(DataAccessException.class);
    }

    private List<String> tableNames() {
        return jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = database() "
                        + "and table_name in ('community_posts','community_post_likes','community_comments',"
                        + "'community_post_images','community_direct_conversations','community_direct_messages')",
                String.class);
    }

    private void assertColumns(String table, List<ColumnMetadata> expected) {
        List<ColumnMetadata> actual = jdbcTemplate.query(
                "select column_name,data_type,column_type,is_nullable,datetime_precision,character_set_name "
                        + "from information_schema.columns where table_schema = database() and table_name = ? "
                        + "order by ordinal_position",
                (row, number) -> new ColumnMetadata(row.getString("column_name"), row.getString("data_type"),
                        row.getString("column_type"), row.getString("is_nullable"),
                        nullableInteger(row.getObject("datetime_precision")), row.getString("character_set_name")), table);
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    private List<String> indexColumns(String table, String index) {
        return jdbcTemplate.queryForList(
                "select column_name from information_schema.statistics where table_schema = database() "
                        + "and table_name = ? and index_name = ? order by seq_in_index",
                String.class, table, index);
    }

    private List<ForeignKeyMetadata> foreignKeys() {
        return jdbcTemplate.query(
                "select k.table_name,k.column_name,k.referenced_table_name,k.referenced_column_name,r.delete_rule "
                        + "from information_schema.key_column_usage k join information_schema.referential_constraints r "
                        + "on r.constraint_schema=k.constraint_schema and r.constraint_name=k.constraint_name "
                        + "where k.constraint_schema=database() and k.table_name in "
                        + "('community_posts','community_post_likes','community_comments')",
                (row, number) -> new ForeignKeyMetadata(
                        row.getString("table_name") + "." + row.getString("column_name") + "->"
                                + row.getString("referenced_table_name") + "."
                                + row.getString("referenced_column_name"), row.getString("delete_rule")));
    }

    private List<String> checkConstraints() {
        return jdbcTemplate.queryForList(
                "select constraint_name from information_schema.table_constraints where table_schema=database() "
                        + "and table_name in ('community_posts','community_comments') and constraint_type='CHECK'",
                String.class);
    }

    private void insertUser(String userId) {
        Timestamp now = Timestamp.from(Instant.parse("2026-08-16T00:00:00Z"));
        jdbcTemplate.update("insert into users (id,email,password_hash,full_name,role,status,accepted_terms_version,"
                        + "created_at,updated_at) values (?,?,?,?,?,?,?,?,?)", userId, userId + "@example.com",
                "test-hash", "Unicode Author", "CANDIDATE", "ACTIVE", "2026-08", now, now);
    }

    private static ColumnMetadata column(String name, String dataType, String columnType, String nullable,
                                         Integer precision, String charset) {
        return new ColumnMetadata(name, dataType, columnType, nullable, precision, charset);
    }

    private static Integer nullableInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private record ColumnMetadata(String name, String dataType, String columnType, String nullable,
                                  Integer precision, String charset) {}
    private record ForeignKeyMetadata(String mapping, String deleteRule) {}
}
