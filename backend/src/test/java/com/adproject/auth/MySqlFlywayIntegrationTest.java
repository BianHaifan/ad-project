package com.adproject.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MySqlFlywayIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("adproject_test")
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
    void flywayMigratesAnEmptyMySqlDatabase() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() and table_name in " +
                        "('users','companies','company_members','refresh_tokens','jobs','job_audit_events')", Integer.class);
        assertThat(count).isEqualTo(6);
        Integer indexes = jdbcTemplate.queryForObject(
                "select count(distinct index_name) from information_schema.statistics where table_schema = database() " +
                        "and table_name = 'jobs' and index_name in " +
                        "('idx_jobs_company_status','idx_jobs_company_created_id','idx_jobs_company_owner')", Integer.class);
        assertThat(indexes).isEqualTo(3);
        Integer auditIndexes = jdbcTemplate.queryForObject(
                "select count(distinct index_name) from information_schema.statistics where table_schema = database() " +
                        "and table_name = 'job_audit_events' and index_name in " +
                        "('idx_job_audit_job_occurred','idx_job_audit_company_occurred')", Integer.class);
        assertThat(auditIndexes).isEqualTo(2);
    }

    @Test
    void mysqlPersistsCompleteJobRowsBeyondTheApplicationTransaction() {
        String userId = UUID.randomUUID().toString();
        String companyId = UUID.randomUUID().toString();
        String jobId = UUID.randomUUID().toString();
        Timestamp now = Timestamp.from(Instant.parse("2026-08-11T05:00:00Z"));
        jdbcTemplate.update("insert into users (id,email,password_hash,full_name,role,status,accepted_terms_version,created_at,updated_at) " +
                        "values (?,?,?,?,?,?,?,?,?)", userId, userId + "@example.com", "test-hash", "MySQL Recruiter",
                "RECRUITER", "ACTIVE", "2026-08", now, now);
        jdbcTemplate.update("insert into companies (id,name,verification_status,version,created_by,created_at,updated_at) " +
                "values (?,?,?,?,?,?,?)", companyId, "MySQL Company", "APPROVED", 1, userId, now, now);
        jdbcTemplate.update("insert into jobs (id,company_id,created_by,owner_id,title,employment_type,workplace_type," +
                        "location,salary_min,salary_max,salary_currency,salary_period,description,requirements_json," +
                        "skills_json,visibility,status,applicant_count,version,created_at,updated_at) " +
                        "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                jobId, companyId, userId, userId, "Persisted MySQL Role", "FULL_TIME", "HYBRID", "Singapore",
                5000, 8000, "SGD", "MONTH", "Persisted description", "[\"Reliable APIs\"]", "[\"Java\"]",
                "PUBLIC", "DRAFT", 0, 1, now, now);

        assertThat(jdbcTemplate.queryForObject("select title from jobs where id = ?", String.class, jobId))
                .isEqualTo("Persisted MySQL Role");
        assertThat(jdbcTemplate.queryForObject("select company_id from jobs where id = ?", String.class, jobId))
                .isEqualTo(companyId);
    }
}
