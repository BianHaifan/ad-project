package com.adproject.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
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
                        "('users','companies','company_members','refresh_tokens','jobs','job_audit_events','candidate_profiles','resumes'," +
                        "'resume_snapshots','applications','application_status_events','idempotency_records'," +
                        "'conversations','messages','conversation_read_states','agent_runs','agent_steps')", Integer.class);
        assertThat(count).isEqualTo(17);
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
    void v27MigrationCreatesAgentAuditSchemaAndOwnershipIndexes() {
        Integer tables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() and table_name in " +
                        "('agent_runs','agent_steps')", Integer.class);
        assertThat(tables).isEqualTo(2);

        Integer uniqueSequence = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.table_constraints where table_schema = database() " +
                        "and constraint_type = 'UNIQUE' and constraint_name = 'uk_agent_steps_run_sequence'",
                Integer.class);
        assertThat(uniqueSequence).isEqualTo(1);

        Integer indexes = jdbcTemplate.queryForObject(
                "select count(distinct index_name) from information_schema.statistics where table_schema = database() " +
                        "and index_name in ('idx_agent_runs_user_created','idx_agent_runs_status_expiry'," +
                        "'idx_agent_steps_run_created')", Integer.class);
        assertThat(indexes).isEqualTo(3);
    }

    @Test
    void v28MigrationAddsConfirmationExecutionAndIdempotencySchema() {
        Integer columns = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = database() " +
                        "and table_name = 'agent_runs' and column_name in " +
                        "('confirmation_id','execution_idempotency_key','confirmed_at','completed_at','result_json')",
                Integer.class);
        assertThat(columns).isEqualTo(5);

        Integer constraints = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.table_constraints where table_schema = database() " +
                        "and table_name = 'agent_runs' and constraint_type = 'UNIQUE' and constraint_name in " +
                        "('uk_agent_runs_confirmation','uk_agent_runs_user_execution_key')", Integer.class);
        assertThat(constraints).isEqualTo(2);
    }

    @Test
    void v6MigrationCreatesConversationSchemaWithIdempotencyConstraints() {
        Integer tables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() and table_name in " +
                        "('conversations','messages','conversation_read_states')", Integer.class);
        assertThat(tables).isEqualTo(3);

        Integer uniqueConstraints = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.table_constraints where table_schema = database() " +
                        "and constraint_type = 'UNIQUE' and constraint_name in " +
                        "('uk_conversations_application','uk_messages_conversation_client','uk_messages_sender_idempotency')",
                Integer.class);
        assertThat(uniqueConstraints).isEqualTo(3);

        Integer indexes = jdbcTemplate.queryForObject(
                "select count(distinct index_name) from information_schema.statistics where table_schema = database() " +
                        "and index_name in " +
                        "('idx_conversations_candidate','idx_conversations_company','idx_messages_conversation_sent')",
                Integer.class);
        assertThat(indexes).isEqualTo(3);

        Integer checkConstraints = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.table_constraints where table_schema = database() " +
                        "and table_name = 'messages' and constraint_type = 'CHECK' and constraint_name = 'chk_messages_body'",
                Integer.class);
        assertThat(checkConstraints).isEqualTo(1);
    }

    @Test
    void v9MigrationAddsMeetingProviderColumnsAndManualDefaults() {
        Integer columns = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = database() " +
                        "and table_name = 'interviews' and column_name in " +
                        "('meeting_provider','meeting_sync_status','meeting_event_id','meeting_sync_error'," +
                        "'meeting_correlation_id')", Integer.class);
        assertThat(columns).isEqualTo(5);

        Integer providerDefault = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = database() " +
                        "and table_name = 'interviews' and column_name = 'meeting_provider' " +
                        "and is_nullable = 'NO' and column_default = 'MANUAL'", Integer.class);
        assertThat(providerDefault).isEqualTo(1);

        Integer syncDefault = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = database() " +
                        "and table_name = 'interviews' and column_name = 'meeting_sync_status' " +
                        "and is_nullable = 'NO' and column_default = 'NOT_APPLICABLE'", Integer.class);
        assertThat(syncDefault).isEqualTo(1);

        Integer checkConstraints = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.table_constraints where table_schema = database() " +
                        "and table_name = 'interviews' and constraint_type = 'CHECK' and constraint_name in " +
                        "('chk_interviews_meeting_provider','chk_interviews_meeting_sync'," +
                        "'chk_interviews_meeting_provider_sync')", Integer.class);
        assertThat(checkConstraints).isEqualTo(3);

        Integer indexes = jdbcTemplate.queryForObject(
                "select count(distinct index_name) from information_schema.statistics where table_schema = database() " +
                        "and table_name = 'interviews' and index_name in " +
                        "('uk_interviews_meeting_event','uk_interviews_meeting_correlation')", Integer.class);
        assertThat(indexes).isEqualTo(2);

        Integer uniqueIndexes = jdbcTemplate.queryForObject(
                "select count(distinct index_name) from information_schema.statistics where table_schema = database() " +
                        "and table_name = 'interviews' and non_unique = 0 and index_name in " +
                        "('uk_interviews_meeting_event','uk_interviews_meeting_correlation')", Integer.class);
        assertThat(uniqueIndexes).isEqualTo(2);
    }

    @Test
    void v9MigrationMigratesExistingInterviewsToManualDefaults() {
        String recruiterId = UUID.randomUUID().toString();
        String candidateId = UUID.randomUUID().toString();
        String companyId = UUID.randomUUID().toString();
        String jobId = UUID.randomUUID().toString();
        String resumeId = UUID.randomUUID().toString();
        String snapshotId = UUID.randomUUID().toString();
        String applicationId = UUID.randomUUID().toString();
        String interviewId = UUID.randomUUID().toString();
        Timestamp now = Timestamp.from(Instant.parse("2026-08-11T05:00:00Z"));

        jdbcTemplate.update("insert into users (id,email,password_hash,full_name,role,status,accepted_terms_version,created_at,updated_at) " +
                        "values (?,?,?,?,?,?,?,?,?)", recruiterId, recruiterId + "@example.com", "test-hash",
                "MySQL Recruiter", "RECRUITER", "ACTIVE", "2026-08", now, now);
        jdbcTemplate.update("insert into users (id,email,password_hash,full_name,role,status,accepted_terms_version,created_at,updated_at) " +
                        "values (?,?,?,?,?,?,?,?,?)", candidateId, candidateId + "@example.com", "test-hash",
                "MySQL Candidate", "CANDIDATE", "ACTIVE", "2026-08", now, now);
        jdbcTemplate.update("insert into companies (id,name,verification_status,version,created_by,created_at,updated_at) " +
                "values (?,?,?,?,?,?,?)", companyId, "MySQL Company", "APPROVED", 1, recruiterId, now, now);
        jdbcTemplate.update("insert into jobs (id,company_id,created_by,owner_id,title,employment_type,workplace_type," +
                        "location,salary_min,salary_max,salary_currency,salary_period,description,requirements_json," +
                        "skills_json,visibility,status,applicant_count,version,created_at,updated_at) " +
                        "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                jobId, companyId, recruiterId, recruiterId, "Meeting Job", "FULL_TIME", "HYBRID", "Singapore",
                5000, 8000, "SGD", "MONTH", "Description", "[\"Reliable APIs\"]", "[\"Java\"]",
                "PUBLIC", "DRAFT", 0, 1, now, now);
        jdbcTemplate.update("insert into resumes (id,candidate_id,full_name,age,location,headline,summary," +
                        "experiences_json,skills_json,version,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                resumeId, candidateId, "MySQL Candidate", 28, "Singapore", "Engineer", "Summary", "[]", "[]", 1,
                now, now);
        jdbcTemplate.update("insert into resume_snapshots (id,resume_id,candidate_id,full_name,age,location,headline," +
                        "summary,experiences_json,skills_json,resume_version,resume_created_at,resume_updated_at," +
                        "captured_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                snapshotId, resumeId, candidateId, "MySQL Candidate", 28, "Singapore", "Engineer", "Summary", "[]",
                "[]", 1, now, now, now);
        jdbcTemplate.update("insert into applications (id,job_id,candidate_id,resume_id,resume_snapshot_id,contact_email," +
                        "share_profile,status,applied_at,updated_at,version) values (?,?,?,?,?,?,?,?,?,?,?)",
                applicationId, jobId, candidateId, resumeId, snapshotId, candidateId + "@example.com",
                true, "IN_REVIEW", now, now, 1);
        jdbcTemplate.update("insert into interviews (id,application_id,scheduled_at,timezone,duration_minutes,mode," +
                        "location_or_meeting_url,note,status,version,created_at,updated_at) " +
                        "values (?,?,?,?,?,?,?,?,?,?,?,?)",
                interviewId, applicationId, now, "Asia/Singapore", 60, "ONLINE",
                "https://meet.example.com/manual", null, "SCHEDULED", 1, now, now);

        java.util.Map<String, Object> row = jdbcTemplate.queryForMap(
                "select meeting_provider, meeting_sync_status, meeting_event_id, meeting_sync_error, " +
                        "meeting_correlation_id, location_or_meeting_url from interviews where id = ?", interviewId);
        assertThat((String) row.get("meeting_provider")).isEqualTo("MANUAL");
        assertThat((String) row.get("meeting_sync_status")).isEqualTo("NOT_APPLICABLE");
        assertThat(row.get("meeting_event_id")).isNull();
        assertThat(row.get("meeting_sync_error")).isNull();
        assertThat(row.get("meeting_correlation_id")).isNull();
        assertThat((String) row.get("location_or_meeting_url")).isEqualTo("https://meet.example.com/manual");

        // The provider/status pairing CHECK is enforced: a MANUAL interview cannot be
        // moved into a Google provisioning state, nor a MANUAL interview flipped to
        // GOOGLE_MEET while still NOT_APPLICABLE.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update interviews set meeting_sync_status = 'PENDING' where id = ?", interviewId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update interviews set meeting_provider = 'GOOGLE_MEET' where id = ?", interviewId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void v10MigrationCreatesGoogleOAuthConnectionSchema() {
        Integer tables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() and table_name in " +
                        "('google_recruiter_connections','google_oauth_states')", Integer.class);
        assertThat(tables).isEqualTo(2);

        Integer uniqueConstraints = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.table_constraints where table_schema = database() " +
                        "and constraint_type = 'UNIQUE' and constraint_name in " +
                        "('uk_google_connections_recruiter','uk_google_oauth_states_hash')", Integer.class);
        assertThat(uniqueConstraints).isEqualTo(2);

        Integer foreignKeys = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.table_constraints where table_schema = database() " +
                        "and constraint_type = 'FOREIGN KEY' and constraint_name in " +
                        "('fk_google_connections_recruiter','fk_google_oauth_states_recruiter')", Integer.class);
        assertThat(foreignKeys).isEqualTo(2);

        Integer checkConstraints = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.table_constraints where table_schema = database() " +
                        "and table_name = 'google_recruiter_connections' and constraint_type = 'CHECK' " +
                        "and constraint_name = 'chk_google_connections_version'", Integer.class);
        assertThat(checkConstraints).isEqualTo(1);

        Integer indexes = jdbcTemplate.queryForObject(
                "select count(distinct index_name) from information_schema.statistics where table_schema = database() " +
                        "and table_name = 'google_oauth_states' and index_name = 'idx_google_oauth_states_expires'",
                Integer.class);
        assertThat(indexes).isEqualTo(1);
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
